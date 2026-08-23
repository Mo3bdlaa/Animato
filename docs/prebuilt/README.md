# Prebuilt

`ShahedPro-14.0.3-testkey.apk` — the ShahedPro source in `../extension-template/src/shahedpro`,
built from that template so it can be installed and tried without setting up a build.

|  |  |
|---|---|
| version | 14.0.3 (version code 3) |
| package | `eu.kanade.tachiyomi.animeextension.ar.shahedpro` |
| SHA-256 | `a50045ef8cc4ef9c39a123ec99f14f1e90b5df3fe6731c739ef147dbcffb0573` |

## The key it is signed with is a throwaway

It was generated for this build and is not kept. Two consequences:

- Animato lists the extension as **untrusted** until you accept it, because the loader recognises
  no fingerprint. That is the app working, not a fault.
- A later release signed with a real key is a different app to Android. Whoever installed this has
  to uninstall it first; the update will not apply over it.

So this is for trying the source, not a release. A published extension needs a key generated and
kept per `../extension-template/README.md`.

## Installing

Install the APK, open Animato, and find it under **Sources** — it is installed like any app, so no
extension store is involved. Accept the untrusted prompt.

## Rebuilding it

From a copy of `../extension-template`, with the Android SDK present:

```
KEYSTORE_PASSWORD=… KEY_ALIAS=… KEY_PASSWORD=… ./gradlew assembleRelease
```

The APK lands in `src/shahedpro/build/outputs/apk/release/`.
