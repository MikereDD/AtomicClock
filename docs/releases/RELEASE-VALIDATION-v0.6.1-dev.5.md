# Atomic Clock v0.6.1-dev.5 — Release Validation

Status: **in progress**

This record follows the Typezer∅ Release Validation Standard. Fill the final
artifact hashes/source commit/tag from the signed release bundle before publishing.

## Identity

- Application: Atomic Clock
- Version: 0.6.1-dev.5
- Channel: Development
- Manifest schema: 2
- Updater protocol: 2
- Minimum updater protocol: 2
- Repository: `https://github.com/MikereDD/AtomicClock`
- APK signer SHA-256:
  `3653e8b4e6f6bea2c5f79fc88110f039e740100ce677f0f6b4051d47b530959b`
- Detached release key ID: `typezero-atomicclock-release-01`
- Detached public-key SPKI SHA-256:
  `c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5`

## Required validation

- [ ] Debug build succeeds.
- [ ] Signed release build succeeds.
- [ ] Release manifest verifies.
- [ ] Tag/version/source commit correspond exactly.
- [ ] Development release discovery succeeds.
- [ ] Already-current build reports no update.
- [ ] Older/same-version candidate is not presented as an update.
- [ ] Installer permission flow distinguishes permission from availability.
- [ ] Installer cancellation is recoverable and abandoned staging expires/cleans.
- [ ] Normal signed upgrade succeeds.
- [ ] Settings and widget state survive the normal signed upgrade.
- [ ] Altered APK is rejected using a throwaway copy.
- [ ] Altered detached signature is rejected using a throwaway copy.
- [ ] Unapproved release key is rejected.
- [ ] Wrong package ID is rejected.
- [ ] Wrong APK signing certificate is rejected.
- [ ] Incompatible minimum updater protocol is rejected safely.

## Publication evidence

- Source commit: pending
- Git tag: pending
- APK filename / size / SHA-256: pending
- `.apk.sig` filename / size / SHA-256: pending
- Publication location: pending
- Upgrade path tested from: pending
- Known deviations/issues: none recorded yet
