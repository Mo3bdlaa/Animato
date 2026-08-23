# Extension template

A working skeleton for an Animato extension: the build, the manifest, the source class, and the CI
that produces the index a store URL points at. The one thing it does not contain is the part that
cannot be templated — how to get a playable URL out of one particular website.

**This directory is not part of Animato's build.** It is not in `settings.gradle.kts` and nothing
here compiles with the app. Copy it out into a repository of its own.

---

## What an extension is

An APK, installed on the device like any other app. Animato scans installed packages, finds the
ones declaring the `tachiyomi.animeextension` feature, and loads a class named in their manifest
metadata.

Four things decide whether the loader accepts it, and **each one fails silently** — the APK
installs, appears in Android's app list, and never shows up in Animato, with nothing said anywhere:

| | where | |
|---|---|---|
| `<uses-feature android:name="tachiyomi.animeextension">` | `AndroidManifest.xml` | what makes it an extension at all — this, not the package name, is what the loader filters on |
| `tachiyomi.animeextension.class` | `AndroidManifest.xml` | where the source class is |
| the API version | **`versionName`**, in `build.gradle.kts` | must read as **14.0** or 16.0 — see below |
| a signature | `signingConfigs`, in `build.gradle.kts` | an unsigned APK is refused before the source is ever asked for |

### The API version is in the version name, not the manifest

`tachiyomi.animeextension.lib` is the obvious place to declare it and is what most guides tell you
to write. Nothing in Animato reads it. The loader takes everything before the **last dot of the
version name** and requires that to be `14.0` or `16.0`:

```
versionName = "14.0.3"   ->  API 14, release 3     accepted
versionName = "1.0.3"    ->  API 1.0               rejected, silently
```

So a template that declares `14` in its manifest and leaves `versionName` at `1.0.x` produces an
extension nothing will load. `build.gradle.kts` builds the name out of `extLibVersion` and
`extVersionCode` for that reason, and `tools/build-index.py` fails the build rather than publishing
one the app would reject.

## Getting it into a repository of its own

```
cp -r docs/extension-template ../animato-extensions
cd ../animato-extensions
mkdir -p .github/workflows && mv workflows/build.yml .github/workflows/
git init && git add . && git commit -m "Extension repository"
```

## Adding a source

1. Copy `src/example` to `src/<yoursite>`.
2. Change the four values at the top of its `build.gradle.kts` — the name, the package suffix, the
   class, and the version code — and the `namespace` below them.
3. Rename the package directory under `src/` to match, and rename the class.
4. Add `include(":src:<yoursite>")` to `settings.gradle.kts`.
5. Optionally drop an `icon.png` in the module directory; CI publishes it under the name the store
   looks for, and without one the listing shows a broken image.

Then fill in the source class. The listing methods are usually straightforward; `videoListParse`
is the real work, and the file says why.

`src/shahedpro` is a filled-in one to read alongside the empty one.

## Signing

Generate the keystore on a machine you control and keep it:

```
keytool -genkey -v -keystore signing.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Never commit it. Base64 it into repository secrets — `SIGNING_KEYSTORE`,
`SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — which is what the
workflow reads. Locally, put `signing.jks` at the root of the extensions repository (or point
`KEYSTORE_PATH` at it) and pass `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` in the
environment; without them the release build stays unsigned rather than failing, and CI catches that
with `apksigner` instead.

**Keep this key for as long as the extension exists.** The store records its fingerprint. Sign a
later release with a different key and the update is refused as a different app; everybody has to
uninstall and reinstall, losing nothing but being asked to do it for no visible reason.

## What CI produces

`assembleRelease`, then the APKs collected into `repo/apk/`, then a check that every one of them is
actually signed, then `tools/build-index.py` reading each APK's own manifest back out to build
`index.min.json` — so what is advertised is what shipped rather than what the build files claimed.
It is pushed to a `repo` branch, which is why the address below is stable.

## The address people add

```
https://raw.githubusercontent.com/<user>/<repository>/repo/index.min.json
```

In Animato: **Sources → Extension stores → Anime → +**.

## When it installs but does not appear

In order, because each is silent and the first two are the common ones:

1. **Version name.** `aapt dump badging <apk> | grep versionName` — everything before the last dot
   has to be `14.0` or `16.0`.
2. **Signature.** `apksigner verify <apk>`.
3. **Feature flag.** `aapt dump badging <apk> | grep tachiyomi.animeextension` — the `uses-feature`
   line has to be there.
4. **The class.** `tachiyomi.animeextension.class` has to name a class that exists and has a
   no-argument constructor; a typo here is caught at load time and logged, not shown.
5. **NSFW.** An extension marked `nsfw=1` is not loaded at all unless NSFW sources are enabled.

`adb logcat | grep -i extension` shows the loader's reason for every one of these.

## Two things worth knowing before starting

**The version code is the only thing compared.** Animato offers an update when the index's `code`
is higher than the installed one. The version *name* is never compared for updates. A release that
forgets to raise the code is a release nobody is offered.

**This is maintenance, not a project.** Sites change their markup, and an extension breaks with the
first change — usually silently, as a source that returns an empty list. Expect to fix it
periodically rather than to finish it.

## Before publishing one

Check the terms of the site you are targeting and what it is serving. Whether an extension is
something to share publicly or to keep to yourself depends on the answer, and the answer is
different for a site that licenses its content than for one that does not.
