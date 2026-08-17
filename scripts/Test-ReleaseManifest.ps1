[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath,

    [ValidateSet("stable", "development")]
    [string]$ExpectedChannel = "development"
)

$ErrorActionPreference = "Stop"

$ExpectedSchemaVersion = 2
$ExpectedAppId = "atomicclock"
$ExpectedPlatform = "android"
$ExpectedPackageId = "com.typezero.atomicclock"
$LocalUpdaterProtocol = 2

$ExpectedApkSignerSha256 =
    "3653e8b4e6f6bea2c5f79fc88110f039e740100ce677f0f6b4051d47b530959b"

$ExpectedReleaseKeyId =
    "typezero-atomicclock-release-01"

$ExpectedReleasePublicKeySha256 =
    "c41a57138eecf3e79190d7bc348a1cd76996dfd48f052753a060d2b3e9eb15f5"

function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )
    if (-not $Condition) { throw $Message }
}

function Assert-HexSha256 {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Label
    )
    Assert-Condition ($Value -match '^[0-9a-f]{64}$') "$Label is not a lowercase 64-character SHA-256 value."
}

function Assert-ApprovedHttpsUrl {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $uri = [Uri]$Url
    $approvedHosts = @(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    Assert-Condition ($uri.Scheme -eq "https") "$Label must use HTTPS."
    Assert-Condition ($approvedHosts -contains $uri.Host.ToLowerInvariant()) "$Label uses unapproved host '$($uri.Host)'."
}

$resolved = (Resolve-Path -LiteralPath $ManifestPath).Path
$manifest = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json

Write-Host ""
Write-Host "Atomic Clock local release-manifest validation" -ForegroundColor Cyan
Write-Host "Manifest : $resolved" -ForegroundColor Cyan
Write-Host "Channel  : $ExpectedChannel" -ForegroundColor Cyan
Write-Host ""

Assert-Condition ($manifest.schemaVersion -eq $ExpectedSchemaVersion) `
    "Unsupported manifest schema $($manifest.schemaVersion)."
Assert-Condition ($manifest.appId -eq $ExpectedAppId) `
    "Manifest app ID mismatch."
Assert-Condition ($manifest.platform -eq $ExpectedPlatform) `
    "Manifest platform mismatch."
Assert-Condition ($manifest.channel -eq $ExpectedChannel) `
    "Manifest channel mismatch."
Assert-Condition ($manifest.minimumUpdaterProtocolVersion -le $manifest.updaterProtocolVersion) `
    "Invalid updater protocol range."
Assert-Condition ($LocalUpdaterProtocol -ge $manifest.minimumUpdaterProtocolVersion) `
    "Updater protocol $LocalUpdaterProtocol is too old for this release."
Assert-Condition (-not [string]::IsNullOrWhiteSpace($manifest.version)) `
    "Manifest version is missing."

$assets = @($manifest.assets)
Assert-Condition ($assets.Count -eq 1) `
    "Manifest must contain exactly one Android APK asset."

$asset = $assets[0]

Assert-Condition ($asset.packageId -eq $ExpectedPackageId) `
    "APK package ID mismatch."
Assert-Condition ($asset.fileName -eq "AtomicClock-v$($manifest.version).apk") `
    "Unexpected APK asset name."
Assert-Condition ([int64]$asset.size -gt 0) `
    "APK asset size is invalid."
Assert-HexSha256 $asset.sha256 "APK SHA-256"
Assert-Condition ($asset.signingCertificateSha256 -eq $ExpectedApkSignerSha256) `
    "APK signer identity does not match Atomic Clock's pinned certificate."
Assert-ApprovedHttpsUrl $asset.downloadUrl "APK download URL"

$signature = $asset.signature
Assert-Condition ($null -ne $signature) `
    "Detached release signature metadata is missing."
Assert-Condition ($signature.algorithm.ToLowerInvariant() -eq "rsa-sha256") `
    "Unsupported detached signature algorithm."
Assert-Condition ($signature.keyId -eq $ExpectedReleaseKeyId) `
    "Detached release signing-key ID is not approved."
Assert-Condition ($signature.publicKeySha256 -eq $ExpectedReleasePublicKeySha256) `
    "Detached release public-key identity does not match Atomic Clock's pinned trust anchor."
Assert-Condition ($signature.fileName -eq "$($asset.fileName).sig") `
    "Unexpected detached signature filename."
Assert-Condition ([int64]$signature.size -gt 0) `
    "Detached signature size is invalid."
Assert-HexSha256 $signature.sha256 "Detached signature SHA-256"
Assert-ApprovedHttpsUrl $signature.downloadUrl "Detached signature download URL"

Write-Host "PASS: manifest trust metadata accepted." -ForegroundColor Green
Write-Host "Version: $($manifest.version)" -ForegroundColor Green
Write-Host "Updater protocol: local=$LocalUpdaterProtocol, release=$($manifest.updaterProtocolVersion), minimum=$($manifest.minimumUpdaterProtocolVersion)" -ForegroundColor Green
