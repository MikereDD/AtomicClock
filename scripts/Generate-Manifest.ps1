param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$SignaturePath,
    [Parameter(Mandatory = $true)][string]$CertificateSha256,
    [Parameter(Mandatory = $true)][string]$ReleaseKeyId,
    [Parameter(Mandatory = $true)][string]$ReleasePublicKeySha256,
    [ValidateSet("stable", "development")][string]$Channel = "stable",
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$apk = (Resolve-Path $ApkPath).Path
$sig = (Resolve-Path $SignaturePath).Path
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
$apkSha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$sigName = Split-Path -Leaf $sig
$sigSize = (Get-Item $sig).Length
$sigSha256 = (Get-FileHash $sig -Algorithm SHA256).Hash.ToLowerInvariant()

$tag = "v$versionName"

$commit = $null
$git = Get-Command git -ErrorAction SilentlyContinue
if ($git) {
    Push-Location $RepoRoot
    try {
        $commit = (git rev-parse HEAD 2>$null).Trim()
    } finally {
        Pop-Location
    }
}
if ([string]::IsNullOrWhiteSpace($commit) -or $commit -notmatch '^[A-Fa-f0-9]{40}$') {
    throw "Could not resolve the exact 40-character Git commit for release provenance."
}

$manifest = [ordered]@{
    schemaVersion = 2
    appId = "atomicclock"
    displayName = "Atomic Clock"
    platform = "android"
    architecture = "android-universal"
    channel = $Channel
    version = $versionName
    publishedAt = (Get-Date).ToUniversalTime().ToString("o")
    minimumVersion = "0.5.1"
    updaterProtocolVersion = 2
    minimumUpdaterProtocolVersion = 2
    mandatory = $false
    releaseNotesUrl = "https://github.com/MikereDD/AtomicClock/releases/tag/$tag"
    changelogUrl = "CHANGELOG.md"
    assets = @(
        [ordered]@{
            fileName = $assetName
            downloadUrl = "https://github.com/MikereDD/AtomicClock/releases/download/$tag/$assetName"
            size = $assetSize
            sha256 = $apkSha256
            signature = [ordered]@{
                algorithm = "rsa-sha256"
                fileName = $sigName
                downloadUrl = "https://github.com/MikereDD/AtomicClock/releases/download/$tag/$sigName"
                size = $sigSize
                sha256 = $sigSha256
                keyId = $ReleaseKeyId
                publicKeySha256 = $ReleasePublicKeySha256.ToLowerInvariant()
            }
            packageId = "com.typezero.atomicclock"
            signingCertificateSha256 = $CertificateSha256.ToLowerInvariant()
        }
    )
    source = [ordered]@{
        repositoryUrl = "https://github.com/MikereDD/AtomicClock"
        tag = $tag
        commit = $commit.ToLowerInvariant()
    }
    rollback = [ordered]@{
        supported = $false
        retainVersions = 0
    }
}

$path = Join-Path $releaseDir "release-manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -Path $path -Encoding utf8
Write-Host "Wrote schema-2 manifest: $path" -ForegroundColor Green
