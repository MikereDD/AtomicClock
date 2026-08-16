# Updater implementation — v0.6.1-dev.2

This milestone implements the detached release-signature trust layer required by
the Typezer∅ Android Updater Standard.

## Permanent trust anchors

- APK signing certificate SHA-256:
  `3653e8b4e6f6bea2c5f79fc88110f039e740100ce677f0f6b4051d47b530959b`
- Detached release signing key ID:
  `typezero-atomicclock-release-01`
- Detached release public-key SPKI SHA-256:
  `c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5`

The private release-signing key remains outside Git at:

`$HOME\.typezero\signing\atomicclock-release-signing-private.pem`

The public key is copied into the application as:

`app/src/main/res/raw/atomicclock_release_signing_public.pem`

The apply overlay verifies the public key's canonical DER SubjectPublicKeyInfo
SHA-256 before copying it into the application.

## Release output

A development release now produces:

- `AtomicClock-v<version>.apk`
- `AtomicClock-v<version>.apk.sig`
- `release-manifest.json`
- `SHA256SUMS.txt`
- `VERIFY.txt`
- release notes/changelog

The manifest is schema revision 2 and records exact payload/signature metadata,
the pinned release key identity, updater protocol compatibility, and source
repository/tag/commit provenance.

## Next milestone

Download/install remains disabled. The next pass will stage APK + signature in
app-controlled storage, verify both digests and the detached signature, inspect
package/signing identity, repeat security checks at the installer trust boundary,
and invoke Android's package installer.
