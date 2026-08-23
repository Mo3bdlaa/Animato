# Extension template

A working skeleton for an Animato extension: the build, the manifest, the source class, and the CI
that produces the index a store URL points at. The one thing it does not contain is the part that
cannot be templated — how to get a playable URL out of one particular website.

**This directory is not part of Animato's build.** It is not in `settings.gradle.kts` and nothing
here compiles with the app. Copy it out into a repository of its own.

---

**Start with [GUIDE.md](GUIDE.md)** — it walks the whole thing through, including doing it from
a phone with no computer involved. This file is the reference for what is in the directory.

## What's here

| | |
|---|---|
| `src/example/` | the skeleton, with a worked-through shape for each method and notes on what belongs in it |
| `src/shahedpro/` | an empty copy, named and wired up, with every method still a `TODO` |
| `tools/build-index.py` | builds `index.min.json` from the APKs CI produced |
| `workflows/build.yml` | build, sign, publish to the `repo` branch |

## What an extension is

An APK, installed on the device like any other app. Animato scans installed packages, finds the
ones declaring the `tachiyomi.animeextension` feature, and loads a class named in their manifest
metadata.

Three things decide whether the loader accepts it — all of them are in
`src/example/AndroidManifest.xml`, with the reasons written beside them:

| | |
|---|---|
| `<uses-feature android:name="tachiyomi.animeextension">` | what makes it an extension at all — this, not the package name, is what the loader filters on |
| `tachiyomi.animeextension.class` | where the source class is |
| `tachiyomi.animeextension.lib` | the API version: **14** or 16, and 14 is what is published |

Get any of the three wrong and the APK installs, appears in the app list, and is never seen by
Animato — with nothing said anywhere.

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
   class, and the version code.
3. Rename the package directory under `src/` to match, and rename the class.
4. Add `include(":src:<yoursite>")` to `settings.gradle.kts`.

Then fill in `ExampleSource.kt`. The listing methods are usually straightforward; `videoListParse`
is the real work, and the file says why.

## Signing

Generate the keystore on a machine you control and keep it:

```
keytool -genkey -v -keystore signing.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Never commit it. Base64 it into repository secrets — `SIGNING_KEYSTORE`,
`SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — which is what the
workflow reads.

**Keep this key for as long as the extension exists.** The store records its fingerprint. Sign a
later release with a different key and the update is refused as a different app; everybody has to
uninstall and reinstall, losing nothing but being asked to do it for no visible reason.

## What CI produces

`assembleRelease`, then the APKs collected into `repo/apk/`, then `tools/build-index.py` reading
each APK's own manifest back out to build `index.min.json` — so what is advertised is what shipped
rather than what the build files claimed. It is pushed to a `repo` branch, which is why the address
below is stable.

## The address people add

```
https://raw.githubusercontent.com/<user>/<repository>/repo/index.min.json
```

In Animato: **Sources → Extension stores → Anime → +**.

## Two things worth knowing before starting

**The version code is the only thing compared.** Animato offers an update when the index's `code`
is higher than the installed one. The version *name* is never looked at. A release that forgets to
raise the code is a release nobody is offered.

**This is maintenance, not a project.** Sites change their markup, and an extension breaks with the
first change — usually silently, as a source that returns an empty list. Expect to fix it
periodically rather than to finish it.

## Before publishing one

Check the terms of the site you are targeting and what it is serving. Whether an extension is
something to share publicly or to keep to yourself depends on the answer, and the answer is
different for a site that licenses its content than for one that does not.
