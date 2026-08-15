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

Windows can produce a PKCS12 keystore without Java. `apksigner` reads PKCS12 directly, and takes
the certificate's **friendly name** as the key alias — which is why `-FriendlyName` below is not
cosmetic. Run in a normal (non-admin) PowerShell:

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

The workflow checks the alias before it signs, and if it does not match it fails with a message
naming the alias your keystore actually contains — so a wrong `-FriendlyName` costs you one edited
secret, not a lost afternoon.

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

## The four secrets

Repository → Settings → Secrets and variables → Actions → *New repository secret*:

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | the contents of `animato.jks.base64`, one line, no trailing newline |
| `ALIAS` | `animato` — the `-alias`, or the `-FriendlyName`, you used |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password. On the PowerShell path a PFX has **one** password for both, so set this to the same value; `keytool` only differs if you deliberately gave the key its own |

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
