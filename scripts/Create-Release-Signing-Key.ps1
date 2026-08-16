[CmdletBinding()]
param(
    [string]$PrivateKeyPath = (Join-Path $HOME ".typezero\signing\atomicclock-release-signing-private.pem"),
    [string]$PublicKeyPath  = (Join-Path $HOME ".typezero\signing\atomicclock-release-signing-public.pem"),
    [string]$KeyId = "typezero-atomicclock-release-01",
    [string]$OpenSslPath
)

$ErrorActionPreference = "Stop"

function Find-OpenSsl {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
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

    throw @"
OpenSSL was not found.

Atomic Clock detached release signing follows the Typezer∅ Release Standards and
uses OpenSSL-compatible RSA/SHA-256 signatures.

Install/use an OpenSSL executable, or rerun with:
  -OpenSslPath "C:\path\to\openssl.exe"

Git for Windows commonly provides:
  C:\Program Files\Git\usr\bin\openssl.exe
"@
}

$openssl = Find-OpenSsl $OpenSslPath

$privateDir = Split-Path -Parent $PrivateKeyPath
$publicDir = Split-Path -Parent $PublicKeyPath
New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
New-Item -ItemType Directory -Force -Path $publicDir | Out-Null

if (Test-Path -LiteralPath $PrivateKeyPath) {
    throw "Refusing to overwrite existing private release-signing key: $PrivateKeyPath"
}
if (Test-Path -LiteralPath $PublicKeyPath) {
    throw "Refusing to overwrite existing public release-signing key: $PublicKeyPath"
}

Write-Host ""
Write-Host "Atomic Clock detached release-signing key" -ForegroundColor Cyan
Write-Host "Algorithm : RSA 4096 / SHA-256" -ForegroundColor Cyan
Write-Host "Key ID    : $KeyId" -ForegroundColor Cyan
Write-Host "Private   : $PrivateKeyPath" -ForegroundColor Cyan
Write-Host "Public    : $PublicKeyPath" -ForegroundColor Cyan
Write-Host "OpenSSL   : $openssl" -ForegroundColor Cyan
Write-Host ""
Write-Host "OpenSSL will ask you for a PEM pass phrase." -ForegroundColor Yellow
Write-Host "Use a strong unique password and store it in your password manager." -ForegroundColor Yellow
Write-Host "Do NOT paste that password into chat and do NOT commit the private key." -ForegroundColor Yellow
Write-Host ""

& $openssl genpkey `
    -algorithm RSA `
    -aes-256-cbc `
    -pkeyopt rsa_keygen_bits:4096 `
    -out $PrivateKeyPath

if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $PrivateKeyPath -Force -ErrorAction SilentlyContinue
    throw "Private release-signing key generation failed with exit code $LASTEXITCODE."
}

& $openssl pkey `
    -in $PrivateKeyPath `
    -pubout `
    -out $PublicKeyPath

if ($LASTEXITCODE -ne 0) {
    throw "Public release-signing key export failed with exit code $LASTEXITCODE."
}

$derPath = [IO.Path]::GetTempFileName()
try {
    & $openssl pkey -pubin -in $PublicKeyPath -outform DER -out $derPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to canonicalize the public key to DER SubjectPublicKeyInfo."
    }

    $fingerprint = (Get-FileHash -LiteralPath $derPath -Algorithm SHA256).Hash.ToLowerInvariant()
}
finally {
    Remove-Item -LiteralPath $derPath -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "Atomic Clock detached release-signing identity created." -ForegroundColor Green
Write-Host ""
Write-Host "Key ID:" -ForegroundColor Cyan
Write-Host $KeyId
Write-Host ""
Write-Host "Public-key SPKI SHA-256:" -ForegroundColor Cyan
Write-Host $fingerprint
Write-Host ""
Write-Host "Public key:" -ForegroundColor Cyan
Write-Host $PublicKeyPath
Write-Host ""
Write-Host "Private key:" -ForegroundColor Cyan
Write-Host $PrivateKeyPath
Write-Host ""
Write-Host "BACK UP THE PRIVATE KEY SECURELY BEFORE THE FIRST RELEASE USING IT." -ForegroundColor Yellow
Write-Host "The public key/fingerprint may be committed. The private key and pass phrase must not be." -ForegroundColor Yellow
