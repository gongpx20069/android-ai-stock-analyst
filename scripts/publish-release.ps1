[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

foreach ($command in @("git", "gh")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command was not found on PATH."
    }
}
if (-not (Test-Path (Join-Path $repoRoot "keystore.properties"))) {
    throw "Release signing is not configured. Run scripts/setup-release-signing.ps1 first."
}
if (git status --porcelain) {
    throw "The worktree must be clean before publishing a release."
}
if ((git branch --show-current) -ne "main") {
    throw "Local releases must be published from the main branch."
}

& gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run gh auth login."
}

& git fetch origin main --tags
if ($LASTEXITCODE -ne 0) {
    throw "Failed to fetch origin/main and release tags."
}
$head = git rev-parse HEAD
$remoteMain = git rev-parse origin/main
if ($head -ne $remoteMain) {
    throw "Local HEAD must match origin/main before publishing."
}

$maxPatch = -1
$remoteTags = @(git ls-remote --tags --refs origin "refs/tags/v1.0.*")
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read remote release tags."
}
foreach ($line in $remoteTags) {
    if ($line -match "refs/tags/v1\.0\.(\d+)$") {
        $patch = [int]$Matches[1]
        if ($patch -gt $maxPatch) {
            $maxPatch = $patch
        }
    }
}

$nextPatch = $maxPatch + 1
if ($nextPatch -gt 999) {
    throw "The 1.0.x release line has exhausted its supported patch range."
}
$versionName = "1.0.$nextPatch"
$versionCode = 1_000_000 + $nextPatch
$tag = "v$versionName"

Write-Host "Building signed release $versionName (versionCode $versionCode)..."
& (Join-Path $repoRoot "gradlew.bat") `
    "lint" `
    "test" `
    ":app:assembleRelease" `
    "-PreleaseVersionName=$versionName" `
    "-PreleaseVersionCode=$versionCode"
if ($LASTEXITCODE -ne 0) {
    throw "Release build failed."
}

$builtApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $builtApk)) {
    throw "Signed release APK was not found at $builtApk."
}
$assetDirectory = Join-Path $repoRoot "build\release"
New-Item $assetDirectory -ItemType Directory -Force | Out-Null
$apkAsset = Join-Path $assetDirectory "ai-stock-analyst-$versionName.apk"
$checksumAsset = "$apkAsset.sha256"
Copy-Item $builtApk $apkAsset -Force
$hash = (Get-FileHash $apkAsset -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumLine = "$hash  $([IO.Path]::GetFileName($apkAsset))"
[IO.File]::WriteAllText(
    $checksumAsset,
    $checksumLine,
    (New-Object Text.UTF8Encoding($false))
)

& git fetch origin --tags
if ($LASTEXITCODE -ne 0) {
    throw "Failed to refresh tags before publishing."
}
& git ls-remote --exit-code --tags origin "refs/tags/$tag" *> $null
if ($LASTEXITCODE -eq 0) {
    throw "$tag was created while this build was running. Rerun to use the next patch version."
}

& gh release create `
    $tag `
    $apkAsset `
    $checksumAsset `
    --target $head `
    --title "AI Stock Analyst $versionName" `
    --generate-notes
if ($LASTEXITCODE -ne 0) {
    throw "GitHub Release creation failed. Rerun after resolving any tag collision."
}

Write-Host "Published $tag with the signed APK and SHA-256 checksum."
