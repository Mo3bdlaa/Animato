#!/usr/bin/env bash
#
# Asserts that the classes :app ships are the classes we compiled against.
#
# Mihon's :app is an Android library in Animato, and a library has two outputs: a *compile* jar that
# consumers build against, and the *runtime* classes that actually reach the APK. Nothing forces
# them to agree. If the library minifies itself, R8 optimises on the assumption that it can see
# every caller — which stopped being true the moment our modules started calling into it — and the
# two diverge silently. Everything compiles; the app dies at class-load time.
#
# That is not hypothetical. It shipped in v0.1.0-alpha.2: R8 saw only in-library callers passing a
# BaseActivity and narrowed
#
#   registerSecureActivity(AppCompatActivity)  ->  registerSecureActivity(BaseActivity)
#
# so our MainActivity crashed with NoSuchMethodError before drawing a frame. 57,438 compile-time
# method signatures against 38,721 runtime ones — that one was merely the first we called.
#
# The fix was to stop minifying the library. This is the guard that keeps it fixed, and it checks
# the property we actually care about rather than the setting that happened to break it.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPILE_JAR=app/build/intermediates/compile_library_classes_jar/release/bundleLibCompileToJarRelease/classes.jar
RUNTIME_DIR=app/build/intermediates/runtime_library_classes_dir/release/bundleLibRuntimeToDirRelease

echo "Building :app's release library outputs…"
./gradlew --quiet :app:bundleLibCompileToJarRelease :app:bundleLibRuntimeToDirRelease

[ -f "$COMPILE_JAR" ] || { echo "::error::No compile jar at $COMPILE_JAR"; exit 1; }
[ -d "$RUNTIME_DIR" ] || { echo "::error::No runtime classes at $RUNTIME_DIR"; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/compile"
(cd "$WORK/compile" && unzip -oq "$OLDPWD/$COMPILE_JAR")
cp -r "$RUNTIME_DIR" "$WORK/runtime"

list() { (cd "$1" && find . -name '*.class' | sed 's|^\./||' | sort); }
list "$WORK/compile" > "$WORK/compile.list"
list "$WORK/runtime" > "$WORK/runtime.list"

# R classes are regenerated per application rather than shipped by the library, so they are expected
# to be absent from the runtime output. Nothing else is.
missing=$(comm -23 "$WORK/compile.list" "$WORK/runtime.list" | grep -v '^eu/kanade/tachiyomi/R\(\$[a-z]*\)\?\.class$' || true)
if [ -n "$missing" ]; then
  echo "::error::Classes in :app's compile jar are missing from what it ships:"
  printf '%s\n' "$missing" | head -40
  echo "This means the library is being shrunk. Minify the application module, not the library."
  exit 1
fi

# Same classes is not enough — the alpha.2 crash was a class that shipped with a rewritten method.
common=$(comm -12 "$WORK/compile.list" "$WORK/runtime.list" | sed 's|\.class$||;s|/|.|g')
count=$(printf '%s\n' "$common" | grep -c .)
echo "Comparing the API of $count shared classes…"

# shellcheck disable=SC2086
(cd "$WORK/compile" && javap -p -classpath . $common 2>/dev/null) | grep -v '^Picked up' > "$WORK/compile.sigs"
# shellcheck disable=SC2086
(cd "$WORK/runtime" && javap -p -classpath . $common 2>/dev/null) | grep -v '^Picked up' > "$WORK/runtime.sigs"

if ! diff -q "$WORK/compile.sigs" "$WORK/runtime.sigs" > /dev/null; then
  echo "::error:::app ships a different API from the one consumers compile against."
  echo "Lines starting '<' exist only at compile time; '>' only at runtime."
  diff "$WORK/compile.sigs" "$WORK/runtime.sigs" | head -60
  exit 1
fi

echo "OK: $(wc -l < "$WORK/compile.sigs") method signatures identical across $count classes."
