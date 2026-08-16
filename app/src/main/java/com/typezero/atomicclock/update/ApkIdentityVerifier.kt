package com.typezero.atomicclock.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkIdentityVerifier {
    @Suppress("DEPRECATION")
    fun verify(
        context: Context,
        apk: File,
        expectedVersion: String,
        expectedPackageId: String,
        expectedSignerSha256: String,
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Android could not inspect the downloaded APK.")

        require(info.packageName == expectedPackageId) {
            "Downloaded APK package ID mismatch."
        }
        require(info.versionName == expectedVersion) {
            "Downloaded APK version mismatch."
        }

        val signerBytes: List<ByteArray> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo
                    ?: error("Downloaded APK has no signing information.")
                signingInfo.apkContentsSigners.map { it.toByteArray() }
            } else {
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }

        require(signerBytes.isNotEmpty()) { "Downloaded APK has no signer certificate." }

        val signerDigests = signerBytes.map(::sha256)
        require(expectedSignerSha256.lowercase() in signerDigests) {
            "Downloaded APK signer certificate does not match Atomic Clock's pinned certificate."
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
