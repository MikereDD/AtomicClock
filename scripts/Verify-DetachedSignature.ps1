[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$AssetPath,
    [Parameter(Mandatory)][string]$SignaturePath,
    [string]$PublicKeyPath = (Join-Path $HOME ".typezero\signing\atomicclock-release-signing-public.pem"),
    [string]$OpenSslPath
)

$ErrorActionPreference = "Stop"

function Resolve-TypezeroOpenSsl {
    param([string]$ExplicitPath)

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (Test-Path -LiteralPath $ExplicitPath -PathType Leaf) {
            return (Resolve-Path -LiteralPath $ExplicitPath).Path
        }
        $cmd = Get-Command $ExplicitPath -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
        throw "OpenSSL was not found at: $ExplicitPath"
    }

    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @(
        (Join-Path $env:ProgramFiles "Git\usr\bin\openssl.exe"),
        (Join-Path $env:ProgramFiles "Git\mingw64\bin\openssl.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Git\usr\bin\openssl.exe"),
        "C:\Program Files\OpenSSL-Win64\bin\openssl.exe",
        "C:\Program Files\OpenSSL\bin\openssl.exe"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }

    if ($candidates.Count -gt 0) { return $candidates[0] }

    throw "OpenSSL was not found. Pass -OpenSslPath or install/use Git for Windows OpenSSL."
}


$openssl = Resolve-TypezeroOpenSsl $OpenSslPath
$asset = (Resolve-Path -LiteralPath $AssetPath).Path
$sig = (Resolve-Path -LiteralPath $SignaturePath).Path
$key = (Resolve-Path -LiteralPath $PublicKeyPath).Path

& $openssl dgst -sha256 -verify $key -signature $sig $asset
if ($LASTEXITCODE -ne 0) {
    throw "Detached signature verification failed with exit code $LASTEXITCODE."
}

Write-Host "Detached release signature verified." -ForegroundColor Green
