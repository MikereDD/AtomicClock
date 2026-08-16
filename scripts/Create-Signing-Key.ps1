param(
    [string]$KeystorePath = (Join-Path $HOME ".typezero\signing\atomicclock-release.jks"),
    [string]$Alias = "atomicclock-release",
    [string]$KeytoolPath
)

$ErrorActionPreference = "Stop"

function Find-Keytool {
    param([string]$ExplicitPath)

    $candidates = @()

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $candidates += $ExplicitPath
    }

    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) {
        $candidates += $onPath.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe")
        $candidates += (Join-Path $env:JAVA_HOME "bin\keytool")
    }

    $candidates += @(
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\keytool.exe"),
        "G:\Android Studio\jbr\bin\keytool.exe"
    )

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw @"
keytool was not found.

Checked:
- keytool on PATH
- JAVA_HOME\bin
- Program Files\Android\Android Studio\jbr\bin
- G:\Android Studio\jbr\bin

Pass an explicit path if needed:
.\scripts\Create-Signing-Key.ps1 -KeytoolPath 'G:\Android Studio\jbr\bin\keytool.exe'
"@
}

$keytool = Find-Keytool -ExplicitPath $KeytoolPath

if (Test-Path $KeystorePath) {
    throw "Refusing to overwrite existing keystore: $KeystorePath"
}

$parent = Split-Path -Parent $KeystorePath
New-Item -ItemType Directory -Force -Path $parent | Out-Null

Write-Host "Creating the permanent Atomic Clock Android release/upload key." -ForegroundColor Cyan
Write-Host "keytool : $keytool" -ForegroundColor Cyan
Write-Host "Keystore: $KeystorePath" -ForegroundColor Cyan
Write-Host "Alias   : $Alias" -ForegroundColor Cyan
Write-Host ""
Write-Host "Back this .jks file up securely. Never commit it to Git." -ForegroundColor Yellow
Write-Host "keytool will now prompt for passwords and certificate identity information." -ForegroundColor Yellow
Write-Host ""

& $keytool `
    -genkeypair `
    -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "Atomic Clock signing key created." -ForegroundColor Green
Write-Host "Keystore: $KeystorePath" -ForegroundColor Green
Write-Host "Alias:    $Alias" -ForegroundColor Green
Write-Host ""
Write-Host "Build-Release.ps1 will use these defaults and securely prompt for passwords when needed." -ForegroundColor Cyan
