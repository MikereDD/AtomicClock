param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$CertificateSha256,
    [ValidateSet("stable", "development")][string]$Channel = "stable",
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$apk = (Resolve-Path $ApkPath).Path
$releaseDir = Split-Path -Parent $apk
$gradleFile = Join-Path $RepoRoot "app\build.gradle.kts"
$text = Get-Content $gradleFile -Raw

$versionNameMatch = [regex]::Match($text, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($text, 'versionCode\s*=\s*(\d+)')
if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    throw "Could not read versionName/versionCode from app/build.gradle.kts"
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = [int]$versionCodeMatch.Groups[1].Value
$assetName = Split-Path -Leaf $apk
$assetSize = (Get-Item $apk).Length
$sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$tag = "atomicclock-v$versionName"

$manifest = [ordered]@{
    schemaVersion = 1
    appId = "com.typezero.atomicclock"
    platform = "android"
    channel = $Channel
    version = $versionName
    versionCode = $versionCode
    publishedAt = (Get-Date).ToUniversalTime().ToString("o")
    minimumVersion = "0.5.1"
    minimumUpdaterProtocolVersion = 1
    mandatory = $false
    releaseNotes = "docs/releases/v$versionName.md"
    releaseNotesUrl = "https://github.com/MikereDD/AtomicClock/releases/tag/$tag"
    rollback = [ordered]@{
        allowed = $false
        previousVersion = $null
    }
    assets = @(
        [ordered]@{
            name = $assetName
            type = "apk"
            size = $assetSize
            sha256 = $sha256
            url = "https://github.com/MikereDD/AtomicClock/releases/download/$tag/$assetName"
        }
    )
    signature = [ordered]@{
        scheme = "Android APK signing"
        certificateSha256 = $CertificateSha256.ToLowerInvariant()
        keyIdentity = "Typezer∅ Android release key"
    }
}

$path = Join-Path $releaseDir "release-manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $path -Encoding utf8
Write-Host "Wrote $path" -ForegroundColor Green
