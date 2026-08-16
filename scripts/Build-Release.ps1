param(
    [ValidateSet("stable", "development")][string]$Channel = "stable",
    [switch]$SkipCleanCheck
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $RepoRoot "app\build.gradle.kts"
$gradlew = Join-Path $RepoRoot "gradlew.bat"

$defaultKeystore = Join-Path $HOME ".typezero\signing\atomicclock-release.jks"
$defaultAlias = "atomicclock-release"

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

# Atomic Clock has its own permanent signing identity.
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

# Passwords are never written to disk. Prompt only when this shell has not already
# supplied them through environment variables.
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
$outputDir = Join-Path $HOME "Downloads\AtomicClock-v$versionName"

Remove-Item $outputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $outputDir | Out-Null

Write-Host ""
Write-Host "Atomic Clock release build" -ForegroundColor Cyan
Write-Host "Version  : $versionName ($versionCode)" -ForegroundColor Cyan
Write-Host "Channel  : $Channel" -ForegroundColor Cyan
Write-Host "Keystore : $($env:TYPEZERO_ANDROID_KEYSTORE)" -ForegroundColor Cyan
Write-Host "Alias    : $($env:TYPEZERO_ANDROID_KEY_ALIAS)" -ForegroundColor Cyan
Write-Host "Output   : $outputDir" -ForegroundColor Cyan
Write-Host ""

$builtApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"

# Do not run Gradle clean here. On Windows, Android Studio/Gradle/lint can hold
# transient cache JARs open under app\build, causing a release to fail before
# compilation even begins. A release build does not require a full clean.
#
# Instead, remove the exact publication APK before assembling. This guarantees
# that a successful run must produce a fresh APK while leaving Gradle free to
# manage its own incremental/intermediate files.
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

& (Join-Path $PSScriptRoot "Generate-Checksums.ps1") -Directory $outputDir

$fingerprint = & (Join-Path $PSScriptRoot "Verify-Release.ps1") `
    -ApkPath $releaseApk `
    -RepoRoot $RepoRoot |
    Select-Object -Last 1

& (Join-Path $PSScriptRoot "Generate-Manifest.ps1") `
    -ApkPath $releaseApk `
    -CertificateSha256 $fingerprint `
    -Channel $Channel `
    -RepoRoot $RepoRoot

$notes = Join-Path $RepoRoot "docs\releases\v$versionName.md"
if (Test-Path $notes) {
    Copy-Item $notes (Join-Path $outputDir "RELEASE-NOTES.md") -Force
}
Copy-Item (Join-Path $RepoRoot "CHANGELOG.md") (Join-Path $outputDir "CHANGELOG.md") -Force

# Regenerate checksums after all publication assets have been copied/generated.
& (Join-Path $PSScriptRoot "Generate-Checksums.ps1") -Directory $outputDir

Write-Host ""
Write-Host "Release bundle is ready:" -ForegroundColor Green
Get-ChildItem $outputDir | Format-Table Name, Length -AutoSize
Write-Host ""
Write-Host "Signer certificate SHA-256:" -ForegroundColor Cyan
Write-Host $fingerprint
Write-Host ""
Write-Host "Next: verify the release directory, commit the exact source, tag it, then publish the GitHub Release." -ForegroundColor Cyan
