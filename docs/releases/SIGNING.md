# Android release signing

Atomic Clock has its own permanent Android release/upload signing identity.

## Permanent key

Default keystore:

```text
$HOME\.typezero\signing\atomicclock-release.jks
```

Alias:

```text
atomicclock-release
```

The keystore lives **outside this repository** and must never be committed to Forgejo or GitHub.

## Create the key once

For a new machine or a future clean setup:

```powershell
.\scripts\Create-Signing-Key.ps1
```

The script searches for `keytool` in:

1. `PATH`
2. `$JAVA_HOME\bin`
3. the standard Android Studio JBR installation
4. `G:\Android Studio\jbr\bin\keytool.exe`

An explicit path can also be supplied:

```powershell
.\scripts\Create-Signing-Key.ps1 `
  -KeytoolPath 'G:\Android Studio\jbr\bin\keytool.exe'
```

Do **not** run key creation again after publishing unless you intentionally want a new signing identity.

## Build a signed release

The normal release build uses the Atomic Clock keystore and alias automatically:

```powershell
.\scripts\Build-Release.ps1
```

If signing passwords have not been placed in the current shell's environment, the script
prompts for them securely. Passwords are not written to the repository or a properties file.

You may optionally preload the current PowerShell process:

```powershell
$env:TYPEZERO_ANDROID_KEYSTORE = "$HOME\.typezero\signing\atomicclock-release.jks"
$env:TYPEZERO_ANDROID_KEY_ALIAS = "atomicclock-release"
$env:TYPEZERO_ANDROID_STORE_PASSWORD = "<store password>"
$env:TYPEZERO_ANDROID_KEY_PASSWORD = "<key password>"
```

## Backups

Keep at least two secure backups of:

```text
atomicclock-release.jks
```

A practical arrangement is:

- primary copy under `$HOME\.typezero\signing\`
- one encrypted/offline backup
- one additional secure backup stored separately

Keep the passwords with your password manager, not alongside an unencrypted keystore copy.

## Certificate fingerprint

Release verification records the signer certificate SHA-256 fingerprint in the generated
verification output and release manifest. Preserve that fingerprint as part of Atomic Clock's
release provenance.

## Google Play

If Atomic Clock is later published through Google Play, this certificate can be used as the
upload-key identity while Play App Signing manages the Play-distribution app-signing key.
