# Releasing

*Русская версия: [RELEASING.ru.md](RELEASING.ru.md)*

Releases are built by GitHub Actions ([`.github/workflows/release.yml`](../.github/workflows/release.yml))
when a tag like `v1.2.3` is pushed. Every push to `main` and every pull request runs the unit tests and
builds a debug APK ([`build.yml`](../.github/workflows/build.yml)).

## One-time setup

1. Create the release key once and keep it (plus a backup) outside the repository. Updates install over
   the previous version only if they are signed with the same key.
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias p22 -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | `release.jks` encoded as base64 |
   | `RELEASE_STORE_PASSWORD` | keystore password |
   | `RELEASE_KEY_ALIAS` | key alias (`p22` above) |
   | `RELEASE_KEY_PASSWORD` | key password |

   To copy the base64 keystore to the clipboard on Windows:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
   ```

## Publishing a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow takes `versionName` from the tag and computes `versionCode` as
`major*10000 + minor*100 + patch`, runs the tests, builds and verifies the signed APK, and creates the
release with the APK and its SHA-256.

## Signed build without CI

`app/build.gradle.kts` signs the release build only when `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` are set; otherwise it builds unsigned.

```bash
./gradlew assembleRelease -PappVersionCode=10000 -PappVersionName=1.0.0
```

The release build enables R8 (minify + resource shrinking), so smoke-test the signed APK on a device.
