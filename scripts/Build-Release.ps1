param(
    [ValidateSet("stable", "development")][string]$Channel,
    [switch]$SkipCleanCheck
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $RepoRoot "app\build.gradle.kts"
$gradlew = Join-Path $RepoRoot "gradlew.bat"

$defaultKeystore = Join-Path $HOME ".typezero\signing\atomicclock-release.jks"
$defaultAlias = "atomicclock-release"
$defaultReleasePrivateKey = Join-Path $HOME ".typezero\signing\atomicclock-release-signing-private.pem"
$defaultReleasePublicKey = Join-Path $HOME ".typezero\signing\atomicclock-release-signing-public.pem"
$releaseKeyId = "typezero-atomicclock-release-01"
$releasePublicKeySha256 = "c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5"

function Read-PlainTextSecret {
    param([Parameter(Mandatory = $true)][string]$Prompt)
    $secure = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found: $gradlew"
}

if (-not $SkipCleanCheck) {
    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($git) {
        Push-Location $RepoRoot
        try {
            $dirty = git status --porcelain
            if ($dirty) {
                throw "Working tree is not clean. Commit/stash changes or rerun with -SkipCleanCheck for a test build."
            }
        } finally {
            Pop-Location
        }
    }
}

# Atomic Clock Android package signing identity.
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_ANDROID_KEYSTORE)) {
    $env:TYPEZERO_ANDROID_KEYSTORE = $defaultKeystore
}
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_ANDROID_KEY_ALIAS)) {
    $env:TYPEZERO_ANDROID_KEY_ALIAS = $defaultAlias
}

if (-not (Test-Path $env:TYPEZERO_ANDROID_KEYSTORE)) {
    throw @"
Atomic Clock release keystore was not found:
$($env:TYPEZERO_ANDROID_KEYSTORE)

Expected default:
$defaultKeystore
"@
}

# Detached Typezer∅ release-signing identity.
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_RELEASE_PRIVATE_KEY)) {
    $env:TYPEZERO_RELEASE_PRIVATE_KEY = $defaultReleasePrivateKey
}
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_RELEASE_PUBLIC_KEY)) {
    $env:TYPEZERO_RELEASE_PUBLIC_KEY = $defaultReleasePublicKey
}

if (-not (Test-Path $env:TYPEZERO_RELEASE_PRIVATE_KEY)) {
    throw "Detached release-signing private key not found: $($env:TYPEZERO_RELEASE_PRIVATE_KEY)"
}
if (-not (Test-Path $env:TYPEZERO_RELEASE_PUBLIC_KEY)) {
    throw "Detached release-signing public key not found: $($env:TYPEZERO_RELEASE_PUBLIC_KEY)"
}

# Passwords are never written to disk. Prompt only when this shell has not
# already supplied them through environment variables.
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_ANDROID_STORE_PASSWORD)) {
    $env:TYPEZERO_ANDROID_STORE_PASSWORD = Read-PlainTextSecret "Atomic Clock keystore password"
}
if ([string]::IsNullOrWhiteSpace($env:TYPEZERO_ANDROID_KEY_PASSWORD)) {
    $samePassword = Read-Host "Is the key password the same as the keystore password? [Y/n]"
    if ([string]::IsNullOrWhiteSpace($samePassword) -or $samePassword -match '^[Yy]') {
        $env:TYPEZERO_ANDROID_KEY_PASSWORD = $env:TYPEZERO_ANDROID_STORE_PASSWORD
    } else {
        $env:TYPEZERO_ANDROID_KEY_PASSWORD = Read-PlainTextSecret "Atomic Clock key password"
    }
}

$text = Get-Content $gradleFile -Raw
$versionNameMatch = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($text, 'versionCode\s*=\s*(\d+)')
if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Could not read versionName/versionCode from app/build.gradle.kts"
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = $versionCodeMatch.Groups[1].Value

$expectedChannel = if ($versionName -match '-dev(?:\.|$)') { "development" } else { "stable" }
if ([string]::IsNullOrWhiteSpace($Channel)) {
    $Channel = $expectedChannel
} elseif ($Channel -ne $expectedChannel) {
    throw "Version $versionName must be published on the '$expectedChannel' channel, not '$Channel'."
}

$actualPublicKeySha256 = & (Join-Path $PSScriptRoot "Get-ReleaseSigningKeyFingerprint.ps1") `
    -PublicKeyPath $env:TYPEZERO_RELEASE_PUBLIC_KEY |
    Select-Object -Last 1

if ($actualPublicKeySha256 -ne $releasePublicKeySha256) {
    throw @"
Detached release public-key identity mismatch.

Expected:
$releasePublicKeySha256

Actual:
$actualPublicKeySha256

Refusing to sign a release with an unapproved key.
"@
}

$outputDir = Join-Path $HOME "Downloads\AtomicClock-v$versionName"
Remove-Item $outputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $outputDir | Out-Null

Write-Host ""
Write-Host "Atomic Clock release build" -ForegroundColor Cyan
Write-Host "Version     : $versionName ($versionCode)" -ForegroundColor Cyan
Write-Host "Channel     : $Channel" -ForegroundColor Cyan
Write-Host "APK key     : $($env:TYPEZERO_ANDROID_KEYSTORE)" -ForegroundColor Cyan
Write-Host "APK alias   : $($env:TYPEZERO_ANDROID_KEY_ALIAS)" -ForegroundColor Cyan
Write-Host "Release key : $releaseKeyId" -ForegroundColor Cyan
Write-Host "Release SPKI: $actualPublicKeySha256" -ForegroundColor Cyan
Write-Host "Output      : $outputDir" -ForegroundColor Cyan
Write-Host ""

$builtApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"

# Avoid Gradle clean on Windows because lint/IDE processes may temporarily lock
# cache JARs. Remove the exact publication APK so a successful run must produce
# fresh output.
Remove-Item $builtApk -Force -ErrorAction SilentlyContinue

Push-Location $RepoRoot
try {
    & $gradlew :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle release build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $builtApk)) {
    throw "Signed release APK was not produced at expected path: $builtApk"
}

$assetName = "AtomicClock-v$versionName.apk"
$releaseApk = Join-Path $outputDir $assetName
Copy-Item $builtApk $releaseApk -Force

# Establish APK checksum before Android signing-certificate verification.
& (Join-Path $PSScriptRoot "Generate-Checksums.ps1") -Directory $outputDir

$apkFingerprint = & (Join-Path $PSScriptRoot "Verify-Release.ps1") `
    -ApkPath $releaseApk `
    -RepoRoot $RepoRoot |
    Select-Object -Last 1

# Produce and immediately verify the independent Typezer∅ detached release signature.
$signaturePath = & (Join-Path $PSScriptRoot "Sign-ReleaseAsset.ps1") `
    -AssetPath $releaseApk `
    -PrivateKeyPath $env:TYPEZERO_RELEASE_PRIVATE_KEY |
    Select-Object -Last 1

& (Join-Path $PSScriptRoot "Verify-DetachedSignature.ps1") `
    -AssetPath $releaseApk `
    -SignaturePath $signaturePath `
    -PublicKeyPath $env:TYPEZERO_RELEASE_PUBLIC_KEY

& (Join-Path $PSScriptRoot "Generate-Manifest.ps1") `
    -ApkPath $releaseApk `
    -SignaturePath $signaturePath `
    -CertificateSha256 $apkFingerprint `
    -ReleaseKeyId $releaseKeyId `
    -ReleasePublicKeySha256 $actualPublicKeySha256 `
    -Channel $Channel `
    -RepoRoot $RepoRoot

$notes = Join-Path $RepoRoot "docs\releases\v$versionName.md"
if (Test-Path $notes) {
    Copy-Item $notes (Join-Path $outputDir "RELEASE-NOTES.md") -Force
}
Copy-Item (Join-Path $RepoRoot "CHANGELOG.md") (Join-Path $outputDir "CHANGELOG.md") -Force

# Final checksums cover all publication assets, including the detached signature.
& (Join-Path $PSScriptRoot "Generate-Checksums.ps1") -Directory $outputDir

Write-Host ""
Write-Host "Release bundle is ready:" -ForegroundColor Green
Get-ChildItem $outputDir | Format-Table Name, Length -AutoSize
Write-Host ""
Write-Host "APK signer certificate SHA-256:" -ForegroundColor Cyan
Write-Host $apkFingerprint
Write-Host ""
Write-Host "Detached release-key SPKI SHA-256:" -ForegroundColor Cyan
Write-Host $actualPublicKeySha256
Write-Host ""
Write-Host "Next: verify the release directory, commit the exact source, tag it, then publish the GitHub Release." -ForegroundColor Cyan
