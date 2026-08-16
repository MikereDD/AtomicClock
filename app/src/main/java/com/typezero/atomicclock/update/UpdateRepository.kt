package com.typezero.atomicclock.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.typezero.atomicclock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

sealed interface UpdateCheckResult {
    data object Checking : UpdateCheckResult
    data class Current(val version: String) : UpdateCheckResult
    data class Available(
        val installedVersion: String,
        val manifest: ReleaseManifest,
        val asset: AndroidReleaseAsset,
    ) : UpdateCheckResult
    data class Downloading(val version: String, val percent: Int) : UpdateCheckResult
    data class Verifying(val version: String) : UpdateCheckResult
    data class ReadyToInstall(val prepared: PreparedUpdate) : UpdateCheckResult
    data class PermissionRequired(val prepared: PreparedUpdate) : UpdateCheckResult
    data class Installing(val version: String) : UpdateCheckResult
    data class Rejected(val reason: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

data class PreparedUpdate(
    val version: String,
    val apk: File,
    val signature: File,
    val manifest: ReleaseManifest,
    val asset: AndroidReleaseAsset,
)

class UpdateRepository(private val context: Context) {
    init {
        // After a successful upgrade the staged directory name equals the newly
        // installed version. Remove it as soon as the updated app starts.
        cleanupInstalledVersionStaging()
        cleanupExpiredStaging()
    }

    suspend fun check(channel: UpdateChannel): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            cleanupExpiredStaging()
            val release = discoverRelease(channel)
                ?: return@withContext UpdateCheckResult.Current(BuildConfig.VERSION_NAME)

            val manifestUrl = release.manifestUrl
                ?: return@withContext UpdateCheckResult.Rejected("Release has no release-manifest.json asset.")

            UpdateTrust.requireApprovedHttps(manifestUrl)
            val manifest = ReleaseManifest.parse(fetchText(manifestUrl))
            validateManifest(manifest, channel)

            val installed = TypezeroVersion.parse(BuildConfig.VERSION_NAME)
            val candidate = TypezeroVersion.parse(manifest.version)
            if (candidate <= installed) {
                return@withContext UpdateCheckResult.Current(BuildConfig.VERSION_NAME)
            }

            val asset = manifest.assets.singleOrNull { it.packageId == UpdateTrust.PACKAGE_ID }
                ?: return@withContext UpdateCheckResult.Rejected(
                    "Manifest does not contain exactly one Atomic Clock APK asset."
                )

            validateAsset(asset, manifest.version)
            UpdateCheckResult.Available(BuildConfig.VERSION_NAME, manifest, asset)
        }.getOrElse { error ->
            UpdateCheckResult.Failed(friendlyFailure(error))
        }
    }

    suspend fun prepare(
        manifest: ReleaseManifest,
        asset: AndroidReleaseAsset,
        onProgress: (String, Int) -> Unit,
        onVerifying: (String) -> Unit,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "updates/${manifest.version}")

        try {
            validateManifest(manifest, UpdateChannel.fromManifestValue(manifest.channel))
            validateAsset(asset, manifest.version)

            cleanupExpiredStaging(keepVersion = manifest.version)
            if (staging.exists()) staging.deleteRecursively()
            require(staging.mkdirs() || staging.isDirectory) {
                "Could not create update staging directory."
            }

            val apk = File(staging, asset.fileName)
            val sig = File(staging, asset.signature.fileName)

            downloadVerifiedSize(asset.downloadUrl, apk, asset.size) { read ->
                val percent = ((read * 100L) / asset.size.coerceAtLeast(1L))
                    .toInt()
                    .coerceIn(0, 100)
                onProgress(manifest.version, percent)
            }

            downloadVerifiedSize(asset.signature.downloadUrl, sig, asset.signature.size) { }
            onProgress(manifest.version, 100)

            val prepared = PreparedUpdate(
                version = manifest.version,
                apk = apk,
                signature = sig,
                manifest = manifest,
                asset = asset,
            )

            onVerifying(manifest.version)
            verifyPreparedUpdate(prepared)
            UpdateCheckResult.ReadyToInstall(prepared)
        } catch (error: Throwable) {
            // Never retain a download that failed validation or did not finish.
            staging.deleteRecursively()
            UpdateCheckResult.Failed(friendlyFailure(error))
        }
    }

    fun launchInstaller(prepared: PreparedUpdate): UpdateCheckResult {
        return runCatching {
            // Final trust-boundary verification immediately before Android receives the APK.
            verifyPreparedUpdate(prepared)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return UpdateCheckResult.PermissionRequired(prepared)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.updatefiles",
                prepared.apk,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            UpdateCheckResult.Installing(prepared.version)
        }.getOrElse { error ->
            UpdateCheckResult.Failed(friendlyFailure(error))
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun verifyPreparedUpdate(prepared: PreparedUpdate) {
        val asset = prepared.asset
        require(prepared.apk.length() == asset.size) { "Downloaded APK size mismatch." }
        require(prepared.signature.length() == asset.signature.size) { "Downloaded signature size mismatch." }

        require(sha256(prepared.apk) == asset.sha256) { "APK SHA-256 verification failed." }
        require(sha256(prepared.signature) == asset.signature.sha256) {
            "Detached signature SHA-256 verification failed."
        }

        require(ReleaseSignatureVerifier.verify(context, prepared.apk, prepared.signature)) {
            "Detached RSA/SHA-256 release signature verification failed."
        }

        ApkIdentityVerifier.verify(
            context = context,
            apk = prepared.apk,
            expectedVersion = prepared.version,
            expectedPackageId = UpdateTrust.PACKAGE_ID,
            expectedSignerSha256 = UpdateTrust.APK_SIGNING_CERT_SHA256,
        )
    }

    private data class ReleaseDiscovery(val manifestUrl: String?)

    private fun discoverRelease(channel: UpdateChannel): ReleaseDiscovery? {
        UpdateTrust.requireApprovedHttps(UpdateTrust.RELEASES_API)
        val releases = JSONArray(fetchText(UpdateTrust.RELEASES_API))

        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft", false)) continue

            val prerelease = release.optBoolean("prerelease", false)
            val eligible = when (channel) {
                UpdateChannel.STABLE -> !prerelease
                UpdateChannel.DEVELOPMENT -> prerelease
            }
            if (!eligible) continue

            val assets = release.optJSONArray("assets") ?: continue
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                if (asset.optString("name") == "release-manifest.json") {
                    return ReleaseDiscovery(asset.optString("browser_download_url"))
                }
            }
        }
        return null
    }

    private fun validateManifest(manifest: ReleaseManifest, channel: UpdateChannel) {
        require(manifest.schemaVersion == UpdateTrust.REQUIRED_SCHEMA_VERSION) {
            "Unsupported manifest schema ${manifest.schemaVersion}."
        }
        require(manifest.appId == UpdateTrust.APP_ID) { "Manifest app ID mismatch." }
        require(manifest.platform == UpdateTrust.PLATFORM) { "Manifest platform mismatch." }
        require(manifest.channel == channel.manifestValue) { "Manifest channel mismatch." }
        require(manifest.minimumUpdaterProtocolVersion <= manifest.updaterProtocolVersion) {
            "Invalid updater protocol range."
        }
        require(UpdateTrust.LOCAL_UPDATER_PROTOCOL >= manifest.minimumUpdaterProtocolVersion) {
            "Updater protocol ${UpdateTrust.LOCAL_UPDATER_PROTOCOL} is too old for this release."
        }
        TypezeroVersion.parse(manifest.version)
    }

    private fun validateAsset(asset: AndroidReleaseAsset, manifestVersion: String) {
        require(asset.fileName == "AtomicClock-v$manifestVersion.apk") { "Unexpected APK asset name." }
        require(asset.size > 0) { "APK asset size is invalid." }
        require(asset.sha256.matches(Regex("^[0-9a-f]{64}$"))) { "APK SHA-256 is invalid." }
        require(asset.packageId == UpdateTrust.PACKAGE_ID) { "APK package ID mismatch." }
        require(asset.signingCertificateSha256 == UpdateTrust.APK_SIGNING_CERT_SHA256) {
            "APK signer identity does not match Atomic Clock's pinned certificate."
        }

        val signature = asset.signature
        require(signature.keyId == UpdateTrust.RELEASE_SIGNING_KEY_ID) {
            "Detached release signing-key ID is not approved."
        }
        require(signature.publicKeySha256 == UpdateTrust.RELEASE_SIGNING_PUBLIC_KEY_SHA256) {
            "Detached release public-key identity does not match Atomic Clock's pinned trust anchor."
        }
        require(signature.algorithm.lowercase() == "rsa-sha256") {
            "Unsupported detached signature algorithm."
        }
        require(signature.fileName == "${asset.fileName}.sig") {
            "Unexpected detached signature filename."
        }
        require(signature.size > 0) { "Detached signature size is invalid." }
        require(signature.sha256.matches(Regex("^[0-9a-f]{64}$"))) {
            "Detached signature SHA-256 is invalid."
        }

        UpdateTrust.requireApprovedHttps(asset.downloadUrl)
        UpdateTrust.requireApprovedHttps(signature.downloadUrl)
    }

    private fun downloadVerifiedSize(
        url: String,
        destination: File,
        expectedSize: Long,
        onBytes: (Long) -> Unit,
    ) {
        UpdateTrust.requireApprovedHttps(url)
        val part = File(destination.parentFile, "${destination.name}.part")
        part.delete()

        var connection = openConnection(url)
        try {
            var redirectCount = 0
            while (connection.responseCode in 300..399) {
                require(redirectCount++ < 5) { "Too many update download redirects." }
                val redirect = connection.getHeaderField("Location")
                    ?: error("Update server returned a redirect without a destination.")
                UpdateTrust.requireApprovedHttps(redirect)
                connection.disconnect()
                connection = openConnection(redirect)
            }

            require(connection.responseCode in 200..299) {
                "Update download returned HTTP ${connection.responseCode}."
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength >= 0) {
                require(declaredLength == expectedSize) { "Update server size does not match manifest." }
            }

            var total = 0L
            connection.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        total += count
                        require(total <= expectedSize) { "Downloaded update exceeded manifest size." }
                        output.write(buffer, 0, count)
                        onBytes(total)
                    }
                }
            }

            require(total == expectedSize) { "Downloaded update size mismatch." }
            require(part.renameTo(destination)) { "Could not finalize downloaded update asset." }
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        UpdateTrust.requireApprovedHttps(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream, application/vnd.github+json, application/json")
            setRequestProperty("User-Agent", "AtomicClock/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun fetchText(url: String): String {
        UpdateTrust.requireApprovedHttps(url)
        var connection = openConnection(url)
        try {
            var redirectCount = 0
            while (connection.responseCode in 300..399) {
                require(redirectCount++ < 5) { "Too many update metadata redirects." }
                val redirect = connection.getHeaderField("Location")
                    ?: error("Update server returned a redirect without a destination.")
                UpdateTrust.requireApprovedHttps(redirect)
                connection.disconnect()
                connection = openConnection(redirect)
            }
            require(connection.responseCode in 200..299) { "Update server returned HTTP ${connection.responseCode}." }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanupInstalledVersionStaging() {
        val installed = File(context.cacheDir, "updates/${BuildConfig.VERSION_NAME}")
        if (installed.exists()) installed.deleteRecursively()
        removeEmptyUpdateRoot()
    }

    private fun cleanupExpiredStaging(keepVersion: String? = null) {
        val root = File(context.cacheDir, "updates")
        if (!root.isDirectory) return

        val cutoff = System.currentTimeMillis() - STAGING_MAX_AGE_MS
        root.listFiles()?.forEach { candidate ->
            if (candidate.name == keepVersion) return@forEach

            val expired = candidate.lastModified() <= 0L || candidate.lastModified() < cutoff
            if (expired) candidate.deleteRecursively()
        }
        removeEmptyUpdateRoot()
    }

    private fun removeEmptyUpdateRoot() {
        val root = File(context.cacheDir, "updates")
        if (root.isDirectory && root.listFiles().isNullOrEmpty()) {
            root.delete()
        }
    }

    private fun friendlyFailure(error: Throwable): String = when (error) {
        is UnknownHostException -> "No internet connection or update host unavailable."
        is SocketTimeoutException -> "Update request timed out. Please try again."
        is SecurityException -> "Android blocked the update operation: ${error.message ?: "permission denied"}"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private companion object {
        const val STAGING_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }

}
