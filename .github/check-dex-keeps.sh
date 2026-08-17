#!/usr/bin/env bash
#
# Fails if R8 removed a class that only ever gets found by name at runtime.
#
# R8 keeps what it can see being used. Most of the app it can see; these it cannot:
#
#   - a WorkManager worker, which is instantiated from a class name stored in WorkManager's own
#     database, so a job scheduled before an update can name a class that no longer exists
#   - a manifest component, which the system resolves by name
#   - a class native code calls back into, where the only reference is inside a `.so`
#
# Every one of those fails at runtime, in the specific case that reaches it, on a device we do not
# have. So the check is: build the APK, list what is actually in its dex, and assert these are
# there. It runs on the release build because that is the only one R8 touches.
#
# The lists are derived from the source wherever deriving is possible, so that adding a worker or a
# manifest entry cannot quietly go unchecked. Only the native ones are written out by hand, because
# nothing in the source says a `.so` calls them.

set -euo pipefail

cd "$(dirname "$0")/.."

APK="animato-app/build/outputs/apk/release/animato-app-arm64-v8a-release-unsigned.apk"

# Always, not only when the file is missing. Reusing an APK that happens to be lying around means
# checking whatever was built last — which is exactly the situation where the answer matters and
# exactly the situation where it would be wrong. It reported a missing activity that was in fact
# present, because the APK predated it. Gradle is incremental; an unchanged tree costs seconds.
echo "Building the release APK…"
./gradlew --quiet :animato-app:assembleRelease

echo "Listing classes in $(basename "$APK")…"
present="$(mktemp)"
expected="$(mktemp)"
trap 'rm -f "$present" "$expected"' EXIT

python3 .github/list-dex-classes.py "$APK" | sort -u > "$present"

# Components the system resolves by name. Parsed rather than grepped, because `android:name` also
# spells permissions, intent actions and metadata keys, and none of those are classes.
python3 - >> "$expected" <<'MANIFESTS'
import pathlib
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

# An <activity-alias> name is a component name and need not be a class at all; what has to exist is
# the activity it points at.
CLASS_ATTRS = {
    "application": ["name"],
    "activity": ["name"],
    "activity-alias": ["targetActivity"],
    "service": ["name"],
    "receiver": ["name"],
    "provider": ["name"],
}

for manifest in pathlib.Path(".").rglob("AndroidManifest.xml"):
    if "build" in manifest.parts:
        continue
    try:
        root = ET.parse(manifest).getroot()
    except ET.ParseError:
        continue
    for element in root.iter():
        for attr in CLASS_ATTRS.get(element.tag, []):
            name = element.get(ANDROID + attr)
            # A leading dot resolves against the merged manifest's package, which is not something
            # to reconstruct here. Every component this fork declares is written out in full.
            if name and not name.startswith("."):
                print(name)
MANIFESTS

# WorkManager workers, found the same way a reader would: the classes that extend one.
for file in $(grep -rl 'CoroutineWorker(' --include='*.kt' anime animato-app animato-ui-kit 2>/dev/null); do
    pkg="$(sed -n 's/^package //p' "$file" | head -1)"
    grep -o '^class [A-Za-z0-9_]*' "$file" | sed "s/^class /$pkg./" >> "$expected"
done

# Called from native code, so no reference to them exists in any dex.
cat >> "$expected" <<'NATIVE'
is.xyz.mpv.MPVLib
xyz.secozzi.torrserver.TorrServer
com.arthenica.ffmpegkit.FFmpegKitConfig
NATIVE

sort -u -o "$expected" "$expected"

missing="$(comm -23 "$expected" "$present" || true)"

if [ -n "$missing" ]; then
    echo
    echo "R8 removed classes that are only ever found by name at runtime:"
    echo
    echo "$missing" | sed 's/^/  /'
    echo
    echo "Each of these fails on a device rather than here. Add a keep rule for it in"
    echo "animato-app/proguard-rules.pro."
    exit 1
fi

echo "OK: all $(wc -l < "$expected" | tr -d ' ') reflectively-reached classes are in the APK."

# Assets are the same problem in a different alphabet: `assets.open("x")` compiles whatever "x" is,
# and a file that was never carried across a port is only discovered by whoever opens the screen.
#
# The player did exactly this. It opens `aniyomi.lua` — the bridge mpv's Lua scripts call back
# through — and the file lived in Aniyomi's app module, which nothing here inherits. Every build was
# green and pressing play died in onCreate with FileNotFoundException.
echo
echo "Checking assets opened by name…"
asset_missing=0
while IFS= read -r asset; do
    if unzip -l "$APK" "assets/$asset" > /dev/null 2>&1; then
        echo "  ok: $asset"
    else
        echo "  MISSING: $asset"
        asset_missing=1
    fi
done < <(
    grep -rho 'assets\.open("[^"]*")' --include=*.kt --include=*.java . |
        sed 's/.*("//; s/")//' |
        sort -u
)

if [ "$asset_missing" -ne 0 ]; then
    echo
    echo "An asset is opened by name and is not in the APK. Whichever module owns the code that"
    echo "opens it needs the file under src/main/assets/ — AGP merges a library module's assets"
    echo "into the application."
    exit 1
fi

# ---------------------------------------------------------------------------------------------
# Classes the APK refers to and does not contain.
#
# ## The bug, and why nothing caught it
#
# Installing an extension died with NoClassDefFoundError on LocalBroadcastManager, a library Mihon's
# installer uses that arrived transitively and was dropped by a version bump elsewhere.
#
# R8 knew. It always knows — it resolves every reference and reports the ones it cannot find. What
# silenced it is a single line in the merged configuration:
#
#     -dontwarn androidx.**
#
# Not ours and not Mihon's; it comes in through some dependency's consumer rules, and it switches
# off the warning for the entire androidx namespace. A rule that broad cannot be narrowed from here,
# because `-dontwarn` is additive and there is no way to un-say one.
#
# So the question is asked of the APK instead, where it cannot be suppressed: every type the dex
# refers to, minus every type it defines, minus what the platform provides. What is left is what the
# app will look for on a device and not find.
#
# This replaced a check that diffed Mihon's compile classpath against the shipped runtime classpath.
# That was a proxy — an artifact leaving the graph only matters if something reaches into it — and
# its allowlist rested on grepping our own sources, which says nothing about what a library's own
# bytecode reaches for. This measures the thing itself.
#
# ## The allowlist
#
# Optional dependencies, probed for and used only when present. Each is already `-dontwarn`n
# somewhere in the merged configuration by the library that probes for it, which is the difference
# between a deliberate absence and this check's whole subject.
echo
echo "Checking classes the APK refers to but does not contain…"

# Provided by the platform, so absent from the APK by design. `android.` does not cover `androidx.`,
# which ships in the APK like any other library — that distinction is the entire point.
PLATFORM='^(java|javax|android|dalvik|sun|com\.android|org\.w3c|org\.xml|org\.xmlpull|org\.json|org\.apache\.http|junit)\.'

ALLOWED_REFS=(
  # OEM window extensions, loaded reflectively on devices that implement them. Mihon's own
  # proguard-rules.pro carries -dontwarn for both of these.
  "androidx.window.extensions."
  "androidx.window.sidecar."
  # okhttp's optional alternative regex engine.
  "com.google.re2j."
  # An optional TLS provider okhttp looks for before falling back.
  "org.bouncycastle.jsse."
)

referenced="$(mktemp)"
dangling="$(mktemp)"
trap 'rm -f "$present" "$expected" "$referenced" "$dangling"' EXIT

python3 .github/list-dex-classes.py --referenced "$APK" | sort -u > "$referenced"
comm -23 "$referenced" "$present" | grep -vE "$PLATFORM" > "$dangling" || true

for allowed in "${ALLOWED_REFS[@]}"; do
    grep -v "^${allowed//./\\.}" "$dangling" > "$dangling.tmp" || true
    mv "$dangling.tmp" "$dangling"
done

if [ -s "$dangling" ]; then
    echo
    echo "These classes are referenced by the APK and are not in it:"
    sed 's/^/  - /' "$dangling"
    echo
    echo "Each one throws NoClassDefFoundError on a device, in whichever feature reaches it. Either"
    echo "declare the missing dependency in animato-app/build.gradle.kts, or — if it is genuinely"
    echo "optional and guarded — add it to ALLOWED_REFS in $(basename "$0") with the reason."
    exit 1
fi

echo "OK: every class the APK refers to is either in it or provided by the platform."
