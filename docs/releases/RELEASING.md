# Atomic Clock release procedure

## 1. Prepare

- Work from the intended release commit.
- Ensure `versionName` and monotonically increasing `versionCode` are correct in `app/build.gradle.kts`.
- Update `CHANGELOG.md` and `docs/releases/v<version>.md`.
- Confirm `git status --short` is clean.

## 2. Configure signing

See [SIGNING.md](SIGNING.md). The signing key must remain outside the repository.

## 3. Build and verify

```powershell
.\scripts\Build-Release.ps1
```

For a local test before committing release-prep changes:

```powershell
.\scripts\Build-Release.ps1 -SkipCleanCheck
```

The release bundle is written to:

```text
$HOME\Downloads\AtomicClock-v<version>\
```

It contains the signed APK, SHA-256 checksums, signature verification log, generated release manifest, changelog, and release notes.

## 4. Tag

Use the Atomic Clock tag convention:

```powershell
git tag -s atomicclock-v0.6.0 -m "Atomic Clock v0.6.0"
git push origin atomicclock-v0.6.0
git push github atomicclock-v0.6.0
```

If Git signing is not configured yet, configure it before creating a public release tag rather than silently substituting an unsigned tag.

## 5. Publish on GitHub

Create a GitHub Release from the exact tag and upload:

- `AtomicClock-v<version>.apk`
- `SHA256SUMS.txt`
- `release-manifest.json`
- `RELEASE-NOTES.md`
- `VERIFY.txt`

Never upload a debug or unsigned APK as a release asset.

## 6. Preserve

Retain the release bundle and signing-key backups. Never silently replace the binary attached to an existing public version/tag; publish a new version instead.
