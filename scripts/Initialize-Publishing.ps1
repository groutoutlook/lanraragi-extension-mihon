#Requires -Version 7.0
[CmdletBinding()]
param([string] $Repository = 'groutoutlook/lanraragi-extension-mihon')

$ErrorActionPreference = 'Stop'
gh api user --silent
if ($LASTEXITCODE -ne 0) { throw 'Log in first: gh auth login -h github.com' }

$repoRoot = Split-Path -Parent $PSScriptRoot
$keyPath = Join-Path $repoRoot 'signingkey.jks'
$passwordPath = Join-Path $repoRoot 'signingkey.password'
$oldPassword = $env:KEY_STORE_PASSWORD
try {
    if (-not (Test-Path -LiteralPath $keyPath)) {
        $passwordBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        $env:KEY_STORE_PASSWORD = [Convert]::ToBase64String($passwordBytes)
        keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias personal `
            -storepass:env KEY_STORE_PASSWORD -keypass:env KEY_STORE_PASSWORD `
            -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Personal LANraragi' -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Could not generate the signing key.' }
        Set-Content -LiteralPath $passwordPath -Value $env:KEY_STORE_PASSWORD -NoNewline
    } else {
        $env:KEY_STORE_PASSWORD = Get-Content -LiteralPath $passwordPath -Raw
    }

    $secrets = @{
        SIGNING_KEY = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
        KEY_STORE_PASSWORD = $env:KEY_STORE_PASSWORD
        KEY_PASSWORD = $env:KEY_STORE_PASSWORD
        ALIAS = 'personal'
    }
    foreach ($entry in $secrets.GetEnumerator()) {
        $entry.Value | gh secret set $entry.Key --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw "Could not save secret $($entry.Key)." }
    }
} finally {
    $env:KEY_STORE_PASSWORD = $oldPassword
}
gh workflow run build_push.yml --repo $Repository --ref main
if ($LASTEXITCODE -ne 0) { throw 'Could not start the publishing workflow.' }
Write-Output "After the workflow succeeds, add this URL in Mihon: https://github.com/$Repository/releases/latest/download/index.pb"
