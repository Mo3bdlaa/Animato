#!/usr/bin/env bash
#
# Fails if a library Mihon compiles against is missing from the app we actually ship.
#
# ## The bug this exists because of
#
# Installing an extension — the single thing the app cannot work without — died on a device with
# `NoClassDefFoundError: androidx/localbroadcastmanager/content/LocalBroadcastManager`, thrown from
# Mihon's own `ExtensionInstaller`. Every build here was green.
#
# Nobody declares that library. Mihon reaches it through `dynamicanimation:1.0.0`, which brings
# `legacy-support-core-utils`, which brings it. Resolved on its own, `:app` gets 1.0.0 and compiles.
# Resolved as part of `:animato-app`, something else raises `dynamicanimation` to 1.1.0 — a version
# that dropped the legacy dependency — and the class is simply not in the APK.
#
# So the module compiles against one dependency graph and runs inside a different one, and nothing
# in the build compares them. This does.
#
# ## Why an allowlist rather than a fix
#
# Some of what disappears is genuinely unreached — `androidx.print` is in the same lost subtree and
# no line of either project mentions it. Adding those back would be cargo cult. The allowlist is the
# record of which ones were looked at and why they are safe, so that the next disappearance shows up
# as a decision to make rather than as noise to ignore.

set -euo pipefail

cd "$(dirname "$0")/.."

# Artifacts that leave the graph and are known not to be reached.
#
# Each one has been checked against both projects' sources. Removing a line from here is how you
# ask the question again; adding one is a claim you have grepped for the package and found nothing.
ALLOWED=(
  # The rest of the legacy-support-core-utils subtree that leaves with it. Neither Mihon nor
  # Animato names either package anywhere; UniFile carries its own document-tree implementation and
  # nothing in an anime or manga app prints.
  "androidx.documentfile:documentfile"
  "androidx.print:print"
  "androidx.legacy:legacy-support-core-utils"
)

compile_list="$(mktemp)"
runtime_list="$(mktemp)"
missing="$(mktemp)"
trap 'rm -f "$compile_list" "$runtime_list" "$missing"' EXIT

# `group:artifact` only. Versions differ between the two graphs by design — that is what dependency
# resolution is for — and a version bump is not a class going missing.
coordinates() {
  sed 's/^[|+\\ -]*//' |
    grep -E '^[a-z][a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:' |
    sed 's/ ->.*//; s/ (\*)//; s/ (n)//; s/ (c)//' |
    awk -F: '{print $1":"$2}' |
    sort -u
}

echo "Resolving Mihon's compile classpath…"
./gradlew --quiet :app:dependencies --configuration releaseCompileClasspath | coordinates > "$compile_list"

echo "Resolving the shipped application's runtime classpath…"
./gradlew --quiet :animato-app:dependencies --configuration releaseRuntimeClasspath | coordinates > "$runtime_list"

comm -23 "$compile_list" "$runtime_list" > "$missing"

for allowed in "${ALLOWED[@]}"; do
  grep -vxF "$allowed" "$missing" > "$missing.tmp" || true
  mv "$missing.tmp" "$missing"
done

if [ -s "$missing" ]; then
  echo
  echo "These are on Mihon's compile classpath and not in the app that ships:"
  sed 's/^/  - /' "$missing"
  echo
  echo "Mihon compiles against them, so its code may call into them at runtime and find nothing."
  echo "Either declare the artifact in animato-app/build.gradle.kts, or — having checked that no"
  echo "line of either project reaches it — add it to ALLOWED in $(basename "$0") with the reason."
  exit 1
fi

echo "OK: every library Mihon compiles against is in the shipped runtime classpath."
