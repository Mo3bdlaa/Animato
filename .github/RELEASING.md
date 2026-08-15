# Releasing

A tagged build is signed by `build_push.yml` using four repository secrets. Until they are set, the
workflow fails the tag on purpose rather than publishing an unsigned APK.

## The signing key

**Generate it on a machine you control, and nowhere else.** Not in a CI runner, not in a container,
not in a chat window. Anything that generates the key can keep it, and this key is the app's
identity: Android will refuse to install an update signed by a different one.

```sh
keytool -genkeypair -v \
  -keystore animato.jks \
  -alias animato \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12
```

It asks for a keystore password, then a name and organisation — those become the certificate's
subject and are visible to anyone who inspects the APK, so use whatever you are happy publishing.

Then back it up somewhere you will still have in five years, and encode a copy for the secret:

```sh
base64 -w0 animato.jks > animato.jks.base64   # macOS: base64 -i animato.jks -o animato.jks.base64
```

**Never commit `animato.jks` or the base64 file.** `.gitignore` excludes `*.jks` and `*.keystore`
for exactly this reason. Losing the key means every existing install is stranded on its last
version; leaking it means someone else can publish an update your users' phones will accept.

## The four secrets

Repository → Settings → Secrets and variables → Actions → *New repository secret*:

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | the contents of `animato.jks.base64`, one line, no newline |
| `ALIAS` | the `-alias` you used, e.g. `animato` |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password — the same as above unless you set a separate one |

## Cutting a release

```sh
git tag v0.1.0
git push origin v0.1.0
```

The workflow builds, signs, and opens a GitHub release with the APK attached. It only does this for
tags on this repository, so a fork pushing a tag builds but does not publish.

## Changing the key later

You can, and it costs something. Android identifies an app by *package name plus signing
certificate*, so an APK signed with a new key is a different app to the system: existing users get
"App not installed" and must uninstall first, losing their library unless they back it up.

APK Signature Scheme v3 supports **key rotation**, which lets a new key inherit the old one's
identity — but it needs the old key to sign the rotation, and only works on Android 9 and above. So
it helps when you *choose* to rotate; it does nothing if the key is lost or leaked.

Since Animato has never published a release, changing the key today costs nothing at all. After the
first release it costs every installed user. Decide before tagging `v0.1.0`.
