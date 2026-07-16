[CmdletBinding()]
param(
    [switch]$SkipGitHubSecrets
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$keystorePath = Join-Path $repoRoot "release-signing.jks"
$propertiesPath = Join-Path $repoRoot "keystore.properties"
$keyAlias = "ai-stock-analyst"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install JDK 17 and ensure keytool is on PATH."
}
if ((Test-Path $keystorePath) -or (Test-Path $propertiesPath)) {
    throw "Release signing files already exist. Back them up instead of replacing the signing key."
}

$passwordBytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($passwordBytes)
} finally {
    $random.Dispose()
}
$password = ($passwordBytes | ForEach-Object { $_.ToString("x2") }) -join ""

$keytoolArguments = @(
    "-genkeypair",
    "-v",
    "-keystore", $keystorePath,
    "-storetype", "PKCS12",
    "-storepass", $password,
    "-keypass", $password,
    "-alias", $keyAlias,
    "-keyalg", "RSA",
    "-keysize", "4096",
    "-validity", "10000",
    "-dname", "CN=AI Stock Analyst, OU=Android, O=Personal",
    "-noprompt"
)

& keytool @keytoolArguments
if ($LASTEXITCODE -ne 0) {
    Remove-Item $keystorePath -Force -ErrorAction SilentlyContinue
    throw "keytool failed to create the release keystore."
}

$properties = @"
storeFile=release-signing.jks
storePassword=$password
keyAlias=$keyAlias
keyPassword=$password
"@
$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText($propertiesPath, $properties, $utf8WithoutBom)

Write-Host "Created ignored local signing files:"
Write-Host "  $keystorePath"
Write-Host "  $propertiesPath"
Write-Host ""
Write-Warning "Back up both files securely. Losing this key prevents upgrades to installed releases."

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $SkipGitHubSecrets -and $null -ne $gh) {
    & gh auth status *> $null
    if ($LASTEXITCODE -eq 0) {
        $answer = Read-Host "Configure the four GitHub Actions secrets now? [Y/n]"
        if ($answer -notmatch "^[Nn]") {
            $keystoreBase64 = [Convert]::ToBase64String(
                [IO.File]::ReadAllBytes($keystorePath)
            )
            $secrets = @{
                ANDROID_RELEASE_KEYSTORE_BASE64 = $keystoreBase64
                ANDROID_RELEASE_STORE_PASSWORD = $password
                ANDROID_RELEASE_KEY_ALIAS = $keyAlias
                ANDROID_RELEASE_KEY_PASSWORD = $password
            }
            foreach ($entry in $secrets.GetEnumerator()) {
                $entry.Value | & gh secret set $entry.Key
                if ($LASTEXITCODE -ne 0) {
                    throw "Failed to configure GitHub secret $($entry.Key)."
                }
            }
            Write-Host "GitHub Actions release-signing secrets configured."
        }
    }
}
