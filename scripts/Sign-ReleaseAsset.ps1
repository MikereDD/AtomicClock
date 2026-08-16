[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$AssetPath,
    [string]$PrivateKeyPath = (Join-Path $HOME ".typezero\signing\atomicclock-release-signing-private.pem"),
    [string]$SignaturePath,
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
$key = (Resolve-Path -LiteralPath $PrivateKeyPath).Path
if (-not $SignaturePath) { $SignaturePath = "$asset.sig" }

Remove-Item -LiteralPath $SignaturePath -Force -ErrorAction SilentlyContinue

Write-Host "Signing detached release payload with $openssl" -ForegroundColor DarkCyan
& $openssl dgst -sha256 -sign $key -out $SignaturePath $asset
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $SignaturePath -Force -ErrorAction SilentlyContinue
    throw "Detached signature creation failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path -LiteralPath $SignaturePath -PathType Leaf)) {
    throw "Signature was not created: $SignaturePath"
}

$resolvedSignature = (Resolve-Path -LiteralPath $SignaturePath).Path
Write-Host "Detached signature created: $resolvedSignature" -ForegroundColor Green

# Only the path is emitted to the success pipeline.
Write-Output $resolvedSignature
