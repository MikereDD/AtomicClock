param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$apk = (Resolve-Path $ApkPath).Path
$releaseDir = Split-Path -Parent $apk
$checksumFile = Join-Path $releaseDir "SHA256SUMS.txt"

if (-not (Test-Path $checksumFile)) {
    throw "Missing checksum file: $checksumFile"
}

$apkName = Split-Path -Leaf $apk
$expectedLine = Get-Content $checksumFile |
    Where-Object { $_ -match [regex]::Escape($apkName) } |
    Select-Object -First 1

if (-not $expectedLine) {
    throw "No checksum entry for $apkName"
}

$expected = ($expectedLine -split '\s+')[0].ToLowerInvariant()
$actual = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actual -ne $expected) {
    throw "SHA-256 mismatch for $apk"
}

function Resolve-AndroidSdkPath {
    param([string]$Root)

    $candidates = New-Object System.Collections.Generic.List[string]

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates.Add($env:ANDROID_SDK_ROOT)
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates.Add($env:ANDROID_HOME)
    }

    $localProperties = Join-Path $Root "local.properties"
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1

        if ($line) {
            $value = $line.Substring("sdk.dir=".Length)
            $value = $value -replace '\\:', ':'
            $value = $value -replace '\\\\', '\'
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                $candidates.Add($value)
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk"))
    }

    # Additional common Windows locations. Harmless if absent.
    if (-not [string]::IsNullOrWhiteSpace($HOME)) {
        $candidates.Add((Join-Path $HOME "AppData\Local\Android\Sdk"))
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        $expanded = [Environment]::ExpandEnvironmentVariables($candidate)

        if (Test-Path $expanded) {
            $resolved = (Resolve-Path $expanded).Path
            $buildTools = Join-Path $resolved "build-tools"

            if (Test-Path $buildTools) {
                return $resolved
            }
        }
    }

    $checked = ($candidates | Select-Object -Unique) -join "`n - "

    throw @"
Unable to locate the Android SDK.

Checked:
 - $checked

Set ANDROID_SDK_ROOT or ANDROID_HOME if the SDK is installed elsewhere.
"@
}

function Find-ApkSigner {
    param([Parameter(Mandatory = $true)][string]$SdkPath)

    $buildToolsRoot = Join-Path $SdkPath "build-tools"

    $versions = Get-ChildItem $buildToolsRoot -Directory |
        Sort-Object {
            try {
                [version]$_.Name
            } catch {
                [version]"0.0"
            }
        } -Descending

    foreach ($version in $versions) {
        foreach ($candidateName in @("apksigner.bat", "apksigner.exe", "apksigner")) {
            $candidate = Join-Path $version.FullName $candidateName
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }

    throw "apksigner was not found under $buildToolsRoot"
}

$sdk = Resolve-AndroidSdkPath -Root $RepoRoot
$apksigner = Find-ApkSigner -SdkPath $sdk

Write-Host "Android SDK: $sdk" -ForegroundColor DarkCyan
Write-Host "apksigner : $apksigner" -ForegroundColor DarkCyan

$output = & $apksigner verify --verbose --print-certs $apk 2>&1
$exitCode = $LASTEXITCODE

$verifyLog = Join-Path $releaseDir "VERIFY.txt"

# Persist the raw verifier output first. This makes future Build Tools output
# changes diagnosable even if certificate parsing needs adjustment.
@(
    "APK: $apkName"
    "SHA-256: $actual"
    "Android SDK: $sdk"
    "apksigner: $apksigner"
    "apksigner exit code: $exitCode"
    ""
    "apksigner output:"
    $output
) | Set-Content -Path $verifyLog -Encoding utf8

if ($exitCode -ne 0) {
    $output | ForEach-Object { Write-Host $_ }
    throw "apksigner verification failed with exit code $exitCode. Raw output: $verifyLog"
}

# Build Tools versions have varied the exact signer-prefix formatting. Parse the
# semantic label rather than requiring the whole line to match one exact form.
$outputText = ($output | ForEach-Object { "$_" }) -join "`n"

$fingerprintMatch = [regex]::Match(
    $outputText,
    '(?im)certificate\s+SHA-256\s+digest\s*:\s*([0-9A-Fa-f:]{32,})'
)

if (-not $fingerprintMatch.Success) {
    # Some tools omit punctuation or spell SHA256 without the hyphen.
    $fingerprintMatch = [regex]::Match(
        $outputText,
        '(?im)certificate\s+SHA-?256(?:\s+digest)?\s*[:=]\s*([0-9A-Fa-f:]{32,})'
    )
}

if (-not $fingerprintMatch.Success) {
    throw "APK signature verified, but the signer certificate SHA-256 fingerprint could not be parsed. Raw apksigner output was saved to: $verifyLog"
}

$fingerprint = $fingerprintMatch.Groups[1].Value.Replace(":", "").Trim().ToLowerInvariant()

# Add the normalized signer fingerprint to the verification record.
Add-Content -Path $verifyLog -Encoding utf8 -Value @(
    ""
    "Normalized signer certificate SHA-256: $fingerprint"
)

Write-Host "Checksum verified." -ForegroundColor Green
Write-Host "APK signature verified." -ForegroundColor Green
Write-Host "Signer certificate SHA-256: $fingerprint" -ForegroundColor Cyan
Write-Host "Verification record: $verifyLog" -ForegroundColor DarkCyan

# Keep the fingerprint as the only success object written to the pipeline.
Write-Output $fingerprint
