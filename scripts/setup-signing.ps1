<#
.SYNOPSIS
  Creates Porchlight's release signing key and stores it as GitHub Actions secrets.

.DESCRIPTION
  Run once, from your own terminal. The password you type never leaves this
  machine except to GitHub's encrypted secrets via the GitHub CLI.

  The keystore is saved OUTSIDE the repo, in %USERPROFILE%\.porchlight\.
  Back that file up: without it you cannot ship updates that install over
  this version.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\setup-signing.ps1
#>
param(
    [string]$Repo = "ImtiazHossain-Eshan/porchlight",
    [string]$Alias = "porchlight"
)

$ErrorActionPreference = "Stop"

function Find-Tool($name, $candidates) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "Could not find $name. Install it or add it to PATH."
}

$keytool = Find-Tool "keytool" @(
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
    "$env:JAVA_HOME\bin\keytool.exe"
)
$gh = Find-Tool "gh" @("C:\Program Files\GitHub CLI\gh.exe")

& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "The GitHub CLI is not signed in. Run: gh auth login" }

$dir = Join-Path $env:USERPROFILE ".porchlight"
$keystore = Join-Path $dir "porchlight-release.jks"
New-Item -ItemType Directory -Force $dir | Out-Null

if (Test-Path $keystore) {
    Write-Host "Using the existing keystore at $keystore" -ForegroundColor Yellow
} else {
    Write-Host "Creating a release keystore at $keystore" -ForegroundColor Cyan
    Write-Host "keytool will ask for a password (at least 6 characters) and your name. Remember the password." -ForegroundColor Cyan
    & $keytool -genkeypair -v -keystore $keystore -storetype PKCS12 -alias $Alias -keyalg RSA -keysize 4096 -validity 10000
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) { throw "keytool did not create the keystore." }
}

$secure = Read-Host "Type the keystore password again to upload it to GitHub" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

# Check the password before uploading anything.
& $keytool -list -keystore $keystore -storepass $plain -alias $Alias *> $null
if ($LASTEXITCODE -ne 0) { throw "That password does not open $keystore (or the alias '$Alias' is missing). Nothing was uploaded." }

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

# PKCS12 keystores use one password for the store and the key.
$secrets = [ordered]@{
    "PORCHLIGHT_KEYSTORE_BASE64"   = $base64
    "PORCHLIGHT_KEYSTORE_PASSWORD" = $plain
    "PORCHLIGHT_KEY_ALIAS"         = $Alias
    "PORCHLIGHT_KEY_PASSWORD"      = $plain
}
foreach ($name in $secrets.Keys) {
    $secrets[$name] | & $gh secret set $name --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw "Failed to set $name on $Repo." }
}
$plain = $null

Write-Host ""
Write-Host "Done. $Repo now signs tagged releases with this key." -ForegroundColor Green
Write-Host "BACK UP $keystore and its password somewhere safe." -ForegroundColor Yellow
