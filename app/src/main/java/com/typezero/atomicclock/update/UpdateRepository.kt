package com.typezero.atomicclock.update

import com.typezero.atomicclock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data object Checking : UpdateCheckResult
    data class Current(val version: String) : UpdateCheckResult
    data class Available(val installedVersion: String, val manifest: ReleaseManifest, val asset: AndroidReleaseAsset) : UpdateCheckResult
    data class Rejected(val reason: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class UpdateRepository {
    suspend fun check(channel: UpdateChannel): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
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
                ?: return@withContext UpdateCheckResult.Rejected("Manifest does not contain exactly one Atomic Clock APK asset.")
            validateAsset(asset)
            UpdateCheckResult.Available(BuildConfig.VERSION_NAME, manifest, asset)
        }.getOrElse { error ->
            UpdateCheckResult.Failed(error.message ?: error.javaClass.simpleName)
        }
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

    private fun validateAsset(asset: AndroidReleaseAsset) {
        require(asset.packageId == UpdateTrust.PACKAGE_ID) { "APK package ID mismatch." }
        require(asset.signingCertificateSha256 == UpdateTrust.APK_SIGNING_CERT_SHA256) {
            "APK signer identity does not match Atomic Clock's pinned certificate."
        }
        require(asset.signature.keyId == UpdateTrust.RELEASE_SIGNING_KEY_ID) {
            "Detached release signing-key ID is not approved."
        }
        require(asset.signature.algorithm.lowercase() == "rsa-sha256") {
            "Unsupported detached signature algorithm."
        }
        UpdateTrust.requireApprovedHttps(asset.downloadUrl)
        UpdateTrust.requireApprovedHttps(asset.signature.downloadUrl)
    }

    private fun fetchText(url: String): String {
        UpdateTrust.requireApprovedHttps(url)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json, application/json")
            setRequestProperty("User-Agent", "AtomicClock/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                val redirect = connection.getHeaderField("Location")
                    ?: error("Update server returned a redirect without a destination.")
                UpdateTrust.requireApprovedHttps(redirect)
                return fetchText(redirect)
            }
            require(code in 200..299) { "Update server returned HTTP $code." }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
