param(
    [Parameter(Mandatory = $true)][string]$Directory
)

$ErrorActionPreference = "Stop"
$dir = (Resolve-Path $Directory).Path
$files = Get-ChildItem $dir -File | Where-Object {
    $_.Extension -in ".apk", ".aab"
} | Sort-Object Name

if (-not $files) {
    throw "No APK/AAB release assets found in $dir"
}

$lines = foreach ($file in $files) {
    $hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($file.Name)"
}

$path = Join-Path $dir "SHA256SUMS.txt"
$lines | Set-Content -Path $path -Encoding utf8
Write-Host "Wrote $path" -ForegroundColor Green
