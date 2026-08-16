#!/usr/bin/env bash
#
# Fails if a module uses @Serializable without applying the serialization plugin.
#
# `@Serializable` is an annotation. Without the compiler plugin it generates nothing, the module
# compiles perfectly, and the first attempt to encode or decode throws:
#
#   SerializationException: Serializer for class 'X' is not found.
#   Please ensure that class is marked as '@Serializable' and that the serialization
#   compiler plugin is applied.
#
# The message is exactly right and nobody ever reads it, because it arrives at runtime on someone
# else's device — twice here inside a `catch` that logs and carries on.
#
# It has happened twice: the updater's GitHub DTOs in :animato-app, which is why the update check
# would have stayed broken even after the flag that disabled it was fixed; and `AnimeDetails` and
# `EpisodeDetails` in :anime:source-local, which meant a local anime's details.json and
# episodes.json were read as failures rather than as metadata.
#
# Two occurrences is a bug class. This is the check.

set -euo pipefail

cd "$(dirname "$0")/.."

echo "Checking for @Serializable without the serialization plugin…"

missing=0
while IFS= read -r buildfile; do
    module="$(dirname "$buildfile")"
    [ -d "$module/src" ] || continue

    # `|| true` because grep exits 1 when it matches nothing, and `pipefail` would make that the
    # pipeline's status and `set -e` would end the script — silently, and looking like a failure.
    users="$(grep -rl "@Serializable" "$module/src" 2>/dev/null | wc -l || true)"
    [ "$users" -gt 0 ] || continue

    if ! grep -q "kotlin.serialization" "$buildfile"; then
        echo "  $module uses @Serializable in $users file(s) and does not apply the plugin"
        missing=$((missing + 1))
    fi
done < <(git ls-files '*/build.gradle.kts' | grep -v '^gradle/build-logic/')

if [ "$missing" -gt 0 ]; then
    echo
    echo "Add 'alias(libs.plugins.kotlin.serialization)' to the plugins block of each."
    echo "These fail at runtime, not here, and usually inside a catch."
    exit 1
fi

echo "OK: every module using @Serializable applies the plugin."
