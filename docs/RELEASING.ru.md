# Выпуск релизов

*English version: [RELEASING.md](RELEASING.md)*

Релизы собирает GitHub Actions ([`.github/workflows/release.yml`](../.github/workflows/release.yml))
при пуше тега вида `v1.2.3`. На каждый push в `main` и каждый pull request прогоняются unit-тесты и
собирается debug-APK ([`build.yml`](../.github/workflows/build.yml)).

## Разовая настройка

1. Один раз создайте ключ и храните его (и резервную копию) вне репозитория. Обновление встаёт поверх
   прежней версии, только если подписано тем же ключом.
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias p22 -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Добавьте секреты репозитория (Settings → Secrets and variables → Actions):

   | Секрет | Значение |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | `release.jks` в base64 |
   | `RELEASE_STORE_PASSWORD` | пароль keystore |
   | `RELEASE_KEY_ALIAS` | alias ключа (`p22` в примере) |
   | `RELEASE_KEY_PASSWORD` | пароль ключа |

   Скопировать keystore в base64 в буфер обмена на Windows:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
   ```

## Выпуск релиза

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow берёт `versionName` из тега, а `versionCode` считает как `major*10000 + minor*100 + patch`,
прогоняет тесты, собирает и проверяет подписанный APK и создаёт релиз с APK и его SHA-256.

## Подписанная сборка без CI

`app/build.gradle.kts` подписывает release, только если заданы `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` и `RELEASE_KEY_PASSWORD`; иначе собирает неподписанным.

```bash
./gradlew assembleRelease -PappVersionCode=10000 -PappVersionName=1.0.0
```

Release включает R8 (minify + shrink ресурсов), так что подписанный APK стоит проверить на устройстве.
