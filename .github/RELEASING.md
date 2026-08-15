# Releasing

A tagged build is signed by `build_push.yml` using four repository secrets. Until they are set, the
workflow fails the tag on purpose rather than publishing an unsigned APK.

## The signing key

**Generate it on a machine you control, and nowhere else.** Not in a CI runner, not in a container,
not in a chat window. Anything that generates the key can keep it, and this key is the app's
identity: Android refuses to install an update signed by a different one.

Pick whichever path matches what you have installed. Both produce the same thing — a PKCS12
keystore holding one private key under the alias `animato`.

### With a JDK (any OS) — recommended

If you have no JDK, on Windows 10/11 this installs one in a couple of minutes:

```powershell
winget install Microsoft.OpenJDK.21
```

Close and reopen the terminal, then:

```sh
keytool -genkeypair -v -keystore animato.jks -alias animato \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

It asks for a keystore password, then a name and organisation. Those become the certificate's
subject and anyone can read them out of the APK, so put in something you are happy publishing.

### Windows with no JDK — PowerShell only

Windows can produce a PKCS12 keystore without Java, and `apksigner` reads PKCS12 directly. Run in a
normal (non-admin) PowerShell:

```powershell
$cert = New-SelfSignedCertificate `
  -Type Custom `
  -Subject "CN=Animato" `
  -FriendlyName "animato" `
  -KeyAlgorithm RSA -KeyLength 4096 `
  -KeyExportPolicy Exportable `
  -NotAfter (Get-Date).AddYears(30) `
  -CertStoreLocation "Cert:\CurrentUser\My"

$password = Read-Host "Keystore password" -AsSecureString
Export-PfxCertificate -Cert $cert -FilePath "$HOME\animato.jks" -Password $password

# remove it from the Windows certificate store; the file is the copy that matters
Remove-Item -Path "Cert:\CurrentUser\My\$($cert.Thumbprint)"
```

**`-FriendlyName` does not become the key alias, despite appearances.** `Export-PfxCertificate`
ignores it and writes the CNG key container name into the PKCS12 friendly-name attribute, which is
what `keytool` and `apksigner` read back. So the alias of a keystore made this way is a GUID —
something like `te-2d68bf83-6d8c-469a-9713-0d94e8385975` — and there is no way to choose it.

That is harmless. The alias is a label for telling several keys apart inside one keystore, and this
keystore holds one key, so the workflows read the alias out of it rather than being told. Set
`-FriendlyName` anyway if you like; it shows up in the Windows certificate store and nowhere else.

### Then, on either path

Back the keystore up somewhere you will still have in five years, and encode a copy for the secret:

```powershell
# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\animato.jks")) | Set-Content -NoNewline "$HOME\animato.jks.base64"
```

```sh
# macOS / Linux
base64 -w0 animato.jks > animato.jks.base64      # macOS: base64 -i animato.jks -o animato.jks.base64
```

**Never commit `animato.jks` or the base64 file.** `.gitignore` excludes `*.jks` and `*.keystore`
for exactly this reason. Losing the key strands every existing install on its last version; leaking
it lets someone else publish an update your users' phones will accept.

## The secrets

On GitHub: **your repository → Settings → Secrets and variables → Actions → New repository secret**.
Add each one by name — the names are exact and case-sensitive.

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | the whole contents of `animato.jks.base64`, as one line |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password. On the PowerShell path a PFX has **one** password for both, so set this to the same value; `keytool` only differs if you deliberately gave the key its own |
| `ALIAS` | **optional.** Only needed if your keystore holds more than one private key, to say which signs the app. With one key the workflows read the alias from the keystore, and a stale `ALIAS` is a warning rather than a failure |

The one that goes wrong is `SIGNING_KEY`, because it is a few thousand characters and selecting it
by hand invites a missing character or a stray line break. Put it on the clipboard instead of
reading it off the screen:

```powershell
# PowerShell
Get-Content "$HOME\animato.jks.base64" -Raw | Set-Clipboard
```

```sh
# macOS
pbcopy < animato.jks.base64
# Linux
xclip -selection clipboard < animato.jks.base64
```

Then paste into the secret's value box. GitHub trims trailing whitespace, so a newline at the end is
harmless; line breaks *inside* the value are not, which is why the encoding step uses `-w0` /
`-NoNewline`.

Secrets cannot be read back once saved — GitHub only lets you overwrite them. That is expected; if
you are unsure a value is right, replace it rather than trying to check it.

## Checking they are right, without publishing anything

The signing in `build_push.yml` only runs on a release tag, so the obvious way to test a secret
would be to cut a real release. Do not do that. There is a workflow that answers the question on its
own:

**Actions → Check signing secrets → Run workflow.**

It takes about thirty seconds and builds nothing. It reports, in order: whether the required secrets
are set, whether `SIGNING_KEY` decodes, whether the store password opens the keystore, which alias
it will sign with, and whether `KEY_PASSWORD` reads that key. On success it prints the certificate's
owner and fingerprint, which is worth keeping a note of: it is what identifies your app to Android
for the rest of its life.

There is one more way to get a signed build, and it is the quicker one while the app is still in
progress: **Actions → Alpha release → Run workflow** builds, signs, and publishes a prerelease with
the four per-ABI APKs attached. It uses the same secrets, so a successful alpha proves them too.

It never prints a password.

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
