# APK size

Measured on `animato-app-arm64-v8a-release-unsigned.apk`: **102 MB** with R8 on, down from 124 MB
without it. The per-architecture builds are what ship; the universal one is 361 MB because it
carries four copies of every native library, and nobody should install it.

Numbers below are bytes *in the APK* — compressed for code and resources, uncompressed for native
libraries, which Android stores flat so they can be mapped without extracting.

| | in APK | Whose |
| --- | ---: | --- |
| `classes*.dex` — all Java/Kotlin, after R8 | 8.7 MB | both |
| FFmpeg (`libav*`, `libsw*`, `libffmpegkit`) | 22.1 MB | anime |
| Image decoders (`libimagedecoder`, `libimagedecoder2`) | 21.5 MB | **Mihon** |
| torrserver | 19.0 MB | anime |
| Other native (conscrypt, archive, xml2, sqlite, quickjs, zstd, libc++) | 10.6 MB | Mihon |
| Resources (`res/`, `resources.arsc`) | 7.5 MB | both |
| `libwebgpu_c_bundled` | 5.8 MB | **Mihon** |
| mpv | 5.3 MB | anime |
| `subfont.ttf` — subtitle fallback font | 2.8 MB | anime |
| Assets (CA bundle, public-suffix list, baseline profile) | 0.2 MB | both |

The first thing this says is that **the anime side is not the whole story**. mpv + FFmpeg +
torrserver + subfont come to ~49 MB, which is real, but Mihon's own two image decoders and its
WebGPU bundle come to 27 MB before we add anything. A stock Mihon arm64 build is not small either.

The universal APK is 399 MB because it carries four architectures. Nobody should install it; it
exists for the case where the architecture is not known up front. Per-ABI is the shipping artifact.

## What is actually wasted today

### 1. R8 runs in the wrong place — done

**Done.** R8 now runs on `:animato-app`, and the dex went from 95.1 MB to 22.3 MB uncompressed —
seven dex files down to three, and 22 MB off the APK. What follows is the history, because the way
this was wrong the first time is the reason the check in CI exists.

**Corrected after v0.1.0-alpha.2.** This section previously said R8 never ran. It did: on the
**library**, which is worse than not at all.

`app/build.gradle.kts` set `isMinifyEnabled = true`, and converting `:app` from an application to a
library changed what that means. R8 ran over `:app` alone and optimised on the assumption that it
could see every caller — true when `:app` *was* the application, false once our modules started
calling into it. The two outputs a library produces then disagree: consumers compile against the
*compile* jar, and the APK gets the *runtime* classes. Measured on the release build, **57,438
method signatures at compile time against 38,721 at runtime**, including

```
registerSecureActivity(AppCompatActivity)  ->  registerSecureActivity(BaseActivity)
```

— R8 saw only in-library callers passing a `BaseActivity` and narrowed the parameter. Our
`MainActivity` compiled against the first and crashed with `NoSuchMethodError` before drawing a
frame. Every other Mihon symbol we call was equally exposed; that one was simply the first to run.

Meanwhile the application module has no `buildTypes` block at all, so it takes AGP's default of
`isMinifyEnabled = false`. So our code was never shrunk, and Mihon's `proguard-rules.pro` was never
applied to anything — `proguardFiles` on a library configures only the library's own R8 run, and it
is `consumerProguardFiles` that propagates.

`:app` no longer minifies itself, and its keep rules now travel as consumer rules.
`.github/check-library-abi.sh` runs in `quick_check.yml` and fails the build if the shipped classes
ever diverge from the compiled-against ones again.

That leaves the dex unshrunk, unoptimised and fully symbolised: 84.5 MB raw compressing to 26.2 MB.

The remaining edit is on the application module, where R8 sees the whole program:

```kotlin
// animato-app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

Two things turned out differently from what this predicted.

**Mihon's rules cannot arrive as consumer rules.** `app/proguard-rules.pro` opens with
`-dontobfuscate`, and AGP rejects a global option in a consumer file — it would change the terms for
every consumer without saying so. The check only fires once a consumer actually minifies, which is
why it stayed quiet until the day R8 was switched on. `:animato-app` names the file directly in its
own `proguardFiles` instead, which is where a global option is legal, and `:app` is left with one
edit fewer.

**`-dontobfuscate` is doing a lot of work.** Nothing is renamed, so the usual reflection failure —
a class found by name that no longer has that name — cannot happen at all. What remains is R8
removing a class it cannot see being used, and `.github/check-dex-keeps.sh` is the answer to that:
it builds the release APK, lists what is genuinely in its dex, and asserts that every WorkManager
job, every manifest component and every class native code calls into is still there. It runs in
`quick_check.yml`, and it has been watched failing.

The keep rules in `animato-app/proguard-rules.pro` for `animato.**` and `is.xyz.mpv.**` are, as of
today, redundant: removing them and rebuilding changes nothing, because everything on the list is
statically reachable in this build. They stay. Reachability is a property of the current call
graph, and a refactor that makes a worker reachable only from a preference would remove it
silently.

**Still not verified on a device.** The dex check covers the failure mode that gets found by
looking; it cannot cover a native library that wanted a method rather than a class, or an
optimisation that changed behaviour. Before any release, an R8 build has to be installed and driven
through the player, a download, a backup and a restore.

### 2. Two image decoders, 21.5 MB

`app/build.gradle.kts` pulls both `com.github.mihonapp:image-decoder` *and* `ca.mpreg:imagedecoder`,
and ships both `.so` files. This is **Mihon's** dependency choice, not ours — presumably a migration
in progress upstream. Watch it; if Mihon drops one, we get ~5–17 MB for free at the next sync. Do not
remove one ourselves: that is editing Mihon's dependency graph to guess at which code path it uses.

### 3. torrserver, 19 MB — the one real product decision

A full BitTorrent daemon, and the largest single file in the APK. Everyone pays for it; only people
who use torrent sources benefit.

Three options, in increasing order of effort:

1. **Ship it.** Simplest, and what Aniyomi did.
2. **A product flavour without it.** `torrserver` and `full`, differing by one dependency. Cheap to
   implement, but doubles the release matrix — 8 APKs per release instead of 4.
3. **A Play Feature Delivery module,** downloaded on first use of a torrent source. Correct answer
   technically, and it requires an app bundle and a Play listing, which we do not have.

Not a decision to make now. Option 1 until there are users to ask.

### 4. FFmpeg, 22 MB — answered: there is no duplicate

`readelf -d` on the shipped libraries settles it. Both link against the same files:

```
libmpv.so       NEEDED  libavcodec.so libavfilter.so libavformat.so libavutil.so
                        libswresample.so libswscale.so libavdevice.so …
libffmpegkit.so NEEDED  libavfilter.so libavformat.so libavcodec.so libavutil.so
                        libswresample.so libavdevice.so libswscale.so …
```

One set of FFmpeg libraries, dynamically linked, shared. mpv does not carry a static copy and
FFmpegKit does not bring a second one. So FFmpegKit costs 0.47 MB — its own stub plus
`libffmpegkit_abidetect.so` — and the 22 MB of `libav*` is mpv's requirement, which is to say it is
the price of playing video at all.

Nothing to do here. Recorded so nobody spends the afternoon on it again.

### 5. `subfont.ttf`, 2.8 MB

mpv's fallback font for subtitles that name a font the device does not have. Small enough to keep;
listed so it is not mistaken for an oversight.

## Order to do this in

Not before phase 6. Every item here is measured against a build, and the build is about to gain
~17,600 lines of UI — measurements taken now would be re-taken anyway.

1. ~~`readelf` the FFmpeg question.~~ Done — no duplicate, nothing to reclaim.
2. ~~Enable R8 with the keep rules.~~ Done — 22 MB off, guarded by a check in CI.
3. **Test an R8 build end to end on a device** — every screen, the player, a download, a backup and
   a restore. This is the one that is still open, and it gates any release.
4. Build stock Mihon at our merge base and record its arm64 APK size. Without that baseline the
   remaining 102 MB is unattributable: we cannot tell our bloat from the one we inherited.
5. Decide about torrserver with real numbers, once there are users to ask.

## How to measure

```sh
./gradlew :animato-app:assembleRelease
unzip -l animato-app/build/outputs/apk/release/animato-app-arm64-v8a-release-unsigned.apk \
  | sort -rn | head -40
```

For dex specifically, `dexdump` or Android Studio's APK Analyzer breaks it down by package, which is
what tells you *whose* code did not get shrunk.
