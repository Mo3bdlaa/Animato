#!/usr/bin/env bash
#
# Asserts that no two modules declare the same class.
#
# Kotlin compiles each module on its own, so two modules holding byte-identical copies of a file
# both compile without a word of complaint. The copies only collide when their dex outputs are
# merged into the APK, which is a step the quick check does not run:
#
#   Type eu.kanade.tachiyomi.util.episode.EpisodeFilterDownloadedKt$... is defined multiple times
#
# That one sat green through :anime:ui's whole port — every module compiled, every test passed —
# and only appeared when the APK was first packaged after :anime:ui joined the build. The same
# helper existed in :anime:player and in :anime:ui, because both needed it and copying was easier
# than deciding where it lived.
#
# Comparing source paths rather than compiled classes is the cheap version of this check: it needs
# no build at all, and a duplicate that a package statement hides rather than a directory would be
# a class in a package that does not match its folder, which ktlint already rejects.
set -euo pipefail

cd "$(dirname "$0")/.."

# Every module with sources, by source-root-relative path. Modules are read from settings.gradle.kts
# so a new one is covered the day it is added.
modules=$(
    grep -oE '^include\("[^"]+"\)' settings.gradle.kts |
        sed -e 's/^include("://' -e 's/")$//' -e 's/:/\//g'
)

listing=$(mktemp)
trap 'rm -f "$listing"' EXIT

for module in $modules; do
    for root in "$module/src/main/java" "$module/src/main/kotlin"; do
        [ -d "$root" ] || continue
        find "$root" \( -name '*.kt' -o -name '*.java' \) -printf '%P\t'"$module"'\n' >> "$listing"
    done
done

total=$(wc -l < "$listing")
echo "Checking $total source files across $(printf '%s\n' "$modules" | grep -c .) modules…"

duplicates=$(sort "$listing" | awk -F'\t' '
    { if ($1 == previous) { print $1 } ; previous = $1 }
' | sort -u)

if [ -z "$duplicates" ]; then
    echo "OK: no class is declared in more than one module."
    exit 0
fi

echo
echo "FAIL: the same class is declared in more than one module."
echo "Dex merging rejects this, so the APK cannot be packaged. Move the file to a module both"
echo "of these depend on, and delete the copies."
echo
while IFS= read -r path; do
    printf '  %s\n' "$path"
    awk -F'\t' -v p="$path" '$1 == p { printf "      %s\n", $2 }' "$listing"
done <<< "$duplicates"
exit 1
