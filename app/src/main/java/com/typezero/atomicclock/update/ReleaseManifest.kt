package com.typezero.atomicclock.update

import org.json.JSONObject

data class ReleaseSignature(
    val algorithm: String,
    val fileName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val keyId: String,
    val publicKeySha256: String,
)

data class AndroidReleaseAsset(
    val fileName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val signature: ReleaseSignature,
    val packageId: String,
    val signingCertificateSha256: String,
)

data class ReleaseManifest(
    val schemaVersion: Int,
    val appId: String,
    val platform: String,
    val channel: String,
    val version: String,
    val updaterProtocolVersion: Int,
    val minimumUpdaterProtocolVersion: Int,
    val mandatory: Boolean,
    val releaseNotesUrl: String?,
    val assets: List<AndroidReleaseAsset>,
) {
    companion object {
        fun parse(json: String): ReleaseManifest {
            val root = JSONObject(json)
            val assetsJson = root.getJSONArray("assets")
            val assets = buildList {
                for (i in 0 until assetsJson.length()) {
                    val item = assetsJson.getJSONObject(i)
                    val sig = item.getJSONObject("signature")
                    add(
                        AndroidReleaseAsset(
                            fileName = item.getString("fileName"),
                            downloadUrl = item.getString("downloadUrl"),
                            size = item.getLong("size"),
                            sha256 = item.getString("sha256").lowercase(),
                            signature = ReleaseSignature(
                                algorithm = sig.getString("algorithm"),
                                fileName = sig.getString("fileName"),
                                downloadUrl = sig.getString("downloadUrl"),
                                size = sig.getLong("size"),
                                sha256 = sig.getString("sha256").lowercase(),
                                keyId = sig.getString("keyId"),
                                publicKeySha256 = sig.getString("publicKeySha256").lowercase(),
                            ),
                            packageId = item.getString("packageId"),
                            signingCertificateSha256 = item.getString("signingCertificateSha256").lowercase(),
                        )
                    )
                }
            }

            return ReleaseManifest(
                schemaVersion = root.getInt("schemaVersion"),
                appId = root.getString("appId"),
                platform = root.getString("platform"),
                channel = root.getString("channel"),
                version = root.getString("version"),
                updaterProtocolVersion = root.getInt("updaterProtocolVersion"),
                minimumUpdaterProtocolVersion = root.getInt("minimumUpdaterProtocolVersion"),
                mandatory = root.optBoolean("mandatory", false),
                releaseNotesUrl = root.optString("releaseNotesUrl").takeIf { it.isNotBlank() },
                assets = assets,
            )
        }
    }
}
