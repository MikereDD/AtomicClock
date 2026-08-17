# Atomic Clock v0.7.0 — Release Validation

Status: **stable-promotion validation passed; final publication evidence pending**

This record follows the Typezer∅ Release Validation Standard.

## Identity

- Application: Atomic Clock
- Version: 0.7.0
- Version code: 16
- Channel: Stable
- Manifest schema: 2
- Updater protocol: 2
- Minimum updater protocol: 2
- Repository: `https://github.com/MikereDD/AtomicClock`
- APK signer SHA-256:
  `3653e8b4e6f6bea2c5f79fc88110f039e740100ce677f0f6b4051d47b530959b`
- Detached release key ID:
  `typezero-atomicclock-release-01`
- Detached public-key SPKI SHA-256:
  `c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5`

## Updater validation inherited from the release-identical dev line

- [x] Debug build succeeds.
- [x] Signed development release build succeeds.
- [x] Canonical schema-2 release manifest is accepted.
- [x] Development release discovery succeeds.
- [x] Already-current build reports no update.
- [x] Older/same-version candidate is not presented as an update.
- [x] Offline failure is handled without a crash.
- [x] Installer permission flow distinguishes permission from update availability.
- [x] Returning from install permission can continue installation.
- [x] Installer cancellation is recoverable and retry succeeds.
- [x] Normal release-signed dev.5 → dev.6 upgrade succeeds.
- [x] Settings and widget state survive the normal signed upgrade.
- [x] Altered APK is rejected using a throwaway copy.
- [x] Altered detached signature is rejected using a throwaway copy.
- [x] Valid signature from an unapproved release key is rejected.
- [x] Wrong package ID is rejected.
- [x] Wrong APK signing certificate is rejected.
- [x] Wrong detached release-key ID is rejected.
- [x] Wrong detached public-key fingerprint is rejected.
- [x] Wrong channel is rejected.
- [x] Invalid updater protocol range is rejected.
- [x] Incompatible minimum updater protocol is rejected safely.

## Final v0.7.0 publication checks

- [ ] Clean Stable build succeeds from the exact committed `main` source.
- [ ] v0.7.0 APK signer matches the pinned certificate.
- [ ] v0.7.0 detached signature verifies.
- [ ] v0.7.0 schema-2 manifest passes `Test-ReleaseManifest.ps1`.
- [ ] Git tag `v0.7.0`, manifest version, and source commit correspond exactly.
- [ ] Stable GitHub release assets are published together.

## Publication evidence

- Source commit: pending final merge/commit
- Git tag: `v0.7.0` pending
- APK filename / size / SHA-256: pending final build
- `.apk.sig` filename / size / SHA-256: pending final build
- Publication location: GitHub Releases, pending
- Upgrade path validated: release-signed `0.6.1-dev.5` → `0.6.1-dev.6`
- Known deviations/issues: none currently recorded
