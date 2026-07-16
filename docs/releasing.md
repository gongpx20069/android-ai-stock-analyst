# Android APK Release Process

This document owns APK signing, version allocation, and GitHub Release
automation.

## 1. Shared release contract

Local publishing and the manually dispatched GitHub Actions workflow use the
same rules:

- Releases are created only from `main`.
- Remote tags matching `v1.0.x` are the version source of truth.
- With no matching tag, the first release is `v1.0.0`; each later run selects
  the next patch number.
- Android `versionName` is `1.0.x`.
- Android `versionCode` is `1_000_000 + x`, so every patch can upgrade the
  previous APK.
- Both paths must use the same release keystore.
- Each GitHub Release contains the signed APK and its SHA-256 checksum.

Do not run local and Actions publishing simultaneously. Each path checks for a
tag collision immediately before creating the release and fails safely if the
other path claimed the version first; rerun to select the next patch.

## 2. One-time signing setup

Run from PowerShell with JDK 17 available:

```powershell
.\scripts\setup-release-signing.ps1
```

The script creates ignored `release-signing.jks` and `keystore.properties`
files. Back up both securely. Losing the signing key prevents future APKs from
upgrading installations signed by that key.

If GitHub CLI is installed and authenticated, the script can configure these
repository Actions secrets:

| Secret | Content |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64-encoded PKCS12 keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Signing alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | Key password |

Never commit the keystore, properties file, passwords, or encoded keystore.

## 3. Local build and direct GitHub Release

Prerequisites:

- JDK 17
- authenticated GitHub CLI (`gh auth login`)
- clean `main` worktree aligned with `origin/main`
- signing setup from §2

Run:

```powershell
.\scripts\publish-release.ps1
```

The script fetches remote tags, allocates the next `1.0.x`, runs lint and
tests, builds the signed release APK, calculates SHA-256, and creates the
GitHub Release directly.

## 4. Manual GitHub Actions release

Open the repository's **Actions** page, select **Release APK**, choose
**Run workflow** on `main`, and start the run. The workflow serializes Actions
releases, allocates the next `1.0.x`, builds with the same signing key, and
publishes the APK and checksum to GitHub Releases.

If required signing secrets are missing, the workflow fails before building.
