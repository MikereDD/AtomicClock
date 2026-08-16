package com.typezero.atomicclock.update

import android.content.Context
import com.typezero.atomicclock.R
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Cryptographic verifier for Typezer∅ detached release signatures.
 *
 * The public key is compiled into the application as a raw resource and is
 * independently checked against the locally pinned SPKI SHA-256 fingerprint
 * before it can authorize an update payload.
 */
object ReleaseSignatureVerifier {
    fun verify(context: Context, payload: File, detachedSignature: File): Boolean {
        val publicKey = loadPinnedPublicKey(context)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        payload.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                verifier.update(buffer, 0, read)
            }
        }

        return verifier.verify(detachedSignature.readBytes())
    }

    fun requirePinnedPublicKey(context: Context): PublicKey {
        val publicKey = loadPublicKey(context)
        val actual = sha256(publicKey.encoded)
        require(actual == UpdateTrust.RELEASE_SIGNING_PUBLIC_KEY_SHA256) {
            "Embedded detached release public key does not match the pinned Atomic Clock trust anchor."
        }
        return publicKey
    }

    private fun loadPinnedPublicKey(context: Context): PublicKey =
        requirePinnedPublicKey(context)

    private fun loadPublicKey(context: Context): PublicKey {
        val pem = context.resources
            .openRawResource(R.raw.atomicclock_release_signing_public)
            .bufferedReader()
            .use { it.readText() }

        val base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")

        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
