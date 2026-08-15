# APK size

Measured on `animato-app-arm64-v8a-release-unsigned.apk`, commit `543b26cbf`: **127 MB**.

Numbers below are bytes *in the APK* — compressed for code and resources, uncompressed for native
libraries, which Android stores flat so they can be mapped without extracting.

| | in APK | Whose |
| --- | ---: | --- |
| `classes*.dex` — all Java/Kotlin | 26.2 MB | both |
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

### 1. R8 never runs — the biggest single win

`app/build.gradle.kts` sets `isMinifyEnabled = true` and names `proguardFiles(...)`, and it has no
effect. Converting `:app` from an application to a library changed what those settings mean:

- **minification happens in the application module**, not its libraries — and `animato-app` has no
  `buildTypes` block at all, so it takes AGP's default of `isMinifyEnabled = false`
- **`proguardFiles` on a library is ignored by consumers.** Only `consumerProguardFiles` propagates.
  Mihon's `proguard-rules.pro` — the keep rules its reflection-using dependencies need — is
  currently not applied to anything.

So the shipped dex is unshrunk, unoptimised and fully symbolised: 84.5 MB raw compressing to 26.2 MB.

The fix is two edits, and the second is not optional:

```kotlin
// animato-app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

```kotlin
// app/build.gradle.kts — one of the files we already own
consumerProguardFiles("proguard-rules.pro")
```

Then add our own keep rules for the anime modules: SQLDelight, Injekt's reflective construction, the
serialization of backup models, and mpv's JNI entry points. **Turning R8 on without them produces an
app that builds, installs, and crashes at runtime** — which is why this is a task with a test pass
attached, not a one-line change.

Expect a large reduction; do not quote a number before measuring one. Verify with
`unzip -l` on the output, and verify the app still runs.

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

### 4. FFmpeg, 22 MB — check for a duplicate before assuming

`libffmpegkit.so` (FFmpegKit, used for the video-conversion path in `:anime:services`) and mpv both
want FFmpeg. What ships is one set of `libav*` libraries at 22 MB, so they appear to already share —
but that should be confirmed by reading which `.so` each links against, not inferred from the file
list. If mpv carries FFmpeg statically inside `libmpv.so` and FFmpegKit brings its own on top, part
of that 22 MB is a second copy and FFmpegKit's real cost is the whole set rather than its 0.4 MB stub.

`readelf -d` on both, checking `NEEDED` entries, settles it in a minute.

### 5. `subfont.ttf`, 2.8 MB

mpv's fallback font for subtitles that name a font the device does not have. Small enough to keep;
listed so it is not mistaken for an oversight.

## Order to do this in

Not before phase 6. Every item here is measured against a build, and the build is about to gain
~17,600 lines of UI — measurements taken now would be re-taken anyway.

1. Build stock Mihon at our merge base and record its arm64 APK size. Without that baseline every
   later number is unattributable: we cannot tell our bloat from the one we inherited.
2. Enable R8 with the keep rules, and **test the app end to end** — every screen, the player, a
   download, a backup restore. R8 failures are runtime failures.
3. `readelf` the FFmpeg question.
4. Re-measure. Decide about torrserver with real numbers.

## How to measure

```sh
./gradlew :animato-app:assembleRelease
unzip -l animato-app/build/outputs/apk/release/animato-app-arm64-v8a-release-unsigned.apk \
  | sort -rn | head -40
```

For dex specifically, `dexdump` or Android Studio's APK Analyzer breaks it down by package, which is
what tells you *whose* code did not get shrunk.
