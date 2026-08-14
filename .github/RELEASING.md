# Releasing Animato

## One-time setup

### 1. Enable GitHub Actions

Settings → Actions → General → "Allow all actions and reusable workflows". Without
this, no workflow in this repo runs at all.

### 2. Create the signing keystore

Android will only install an update over an existing app if both are signed with the
**same key**. Whatever key signs the first public release is the key this project is
stuck with forever — losing it means users must uninstall and reinstall to move to a
new one, losing their library.

Generate it on a machine you control (not in CI, not in a throwaway container), and
back up both the `.jks` file and its passwords somewhere durable:

```sh
keytool -genkey -v \
  -keystore animato.jks \
  -alias animato \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

Then base64-encode it for GitHub:

```sh
base64 -w 0 animato.jks > animato.jks.base64   # macOS: base64 -i animato.jks -o animato.jks.base64
```

### 3. Add the signing secrets

Settings → Secrets and variables → Actions → New repository secret. All four are
required; the release job fails fast with a clear error if `SIGNING_KEY` is missing.

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | contents of `animato.jks.base64` |
| `ALIAS` | the `-alias` you chose above (`animato`) |
| `KEY_STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password (same as above unless you set it separately) |

Never commit the `.jks` or the base64 file. Both are gitignored.

## Cutting a release

1. Bump `versionCode` (integer, must increase every release) and `versionName` in
   `app/build.gradle.kts`.
2. Commit, then tag and push:

   ```sh
   git tag v0.19.0.0
   git push origin v0.19.0.0
   ```

3. The `CI` workflow builds, signs, and creates a **draft** release with APKs for
   universal, arm64-v8a, armeabi-v7a, x86, and x86_64 plus their SHA-256 checksums.
4. Review the draft on the Releases page and publish it.

The release is a draft on purpose: the in-app updater only sees published releases, so
nothing reaches users until you click publish.

## Notes

- Only `Mo3bdlaa/Animato` publishes releases. That guard lives in one place — the
  `RELEASE_REPO` env var at the top of `.github/workflows/build_push.yml`. Update it if
  the repo is ever renamed or transferred.
- Every branch push also runs the build (format check, release build, unit tests) and
  uploads an unsigned arm64 APK as a workflow artifact, so you can test without tagging.
- `workflow_dispatch` is enabled, so you can also trigger a build by hand from the
  Actions tab.
- The in-app updater reads this repo's releases via `GITHUB_REPO` in
  `AppUpdateChecker.kt`. Preview builds look for `r<commit count>` tags rather than
  `v<version>` tags.
