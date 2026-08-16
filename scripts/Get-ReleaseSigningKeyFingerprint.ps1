[CmdletBinding()]
param(
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
$key = (Resolve-Path -LiteralPath $PublicKeyPath).Path
$derPath = [IO.Path]::GetTempFileName()

try {
    & $openssl pkey -pubin -in $key -outform DER -out $derPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to canonicalize public key to DER SubjectPublicKeyInfo."
    }

    $fingerprint = (Get-FileHash -LiteralPath $derPath -Algorithm SHA256).Hash.ToLowerInvariant()
} finally {
    Remove-Item -LiteralPath $derPath -Force -ErrorAction SilentlyContinue
}

Write-Output $fingerprint
