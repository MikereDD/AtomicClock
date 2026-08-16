package com.typezero.atomicclock.update

import java.net.URI

/** Local trust anchors. Remote manifests are never allowed to expand this trust boundary. */
object UpdateTrust {
    const val APP_ID = "atomicclock"
    const val PACKAGE_ID = "com.typezero.atomicclock"
    const val PLATFORM = "android"
    const val LOCAL_UPDATER_PROTOCOL = 2
    const val REQUIRED_SCHEMA_VERSION = 2

    const val RELEASE_SIGNING_KEY_ID = "typezero-atomicclock-release-01"
    const val RELEASE_SIGNING_PUBLIC_KEY_SHA256 =
        "c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5"

    const val APK_SIGNING_CERT_SHA256 =
        "3653e8b4e6f6bea2c5f79fc88110f039e740100ce677f0f6b4051d47b530959b"

    const val RELEASES_API = "https://api.github.com/repos/MikereDD/AtomicClock/releases?per_page=30"

    private val approvedHosts = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun requireApprovedHttps(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Update origin must use HTTPS." }
        require(uri.host?.lowercase() in approvedHosts) { "Unapproved update origin: ${uri.host}" }
    }
}
