# Updater implementation — v0.6.1-dev.3

This milestone adds the secure download and Android installer handoff required by
the Typezer∅ Android Updater Standard.

Trust boundary:

1. Discover only the selected Stable/Development release class.
2. Parse and validate schema-2 manifest metadata.
3. Require approved HTTPS origins.
4. Download APK and `.apk.sig` into app-controlled cache storage.
5. Enforce manifest sizes while downloading.
6. Verify SHA-256 for APK and detached signature.
7. Verify RSA/SHA-256 detached signature using the embedded public key.
8. Inspect APK package ID, version, and Android signer certificate.
9. Repeat all security checks immediately before installer handoff.
10. Expose the APK only through a private FileProvider URI.
11. Invoke Android's system package installer; never silently install.

The next dev build is used as the first real update target.
