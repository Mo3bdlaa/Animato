#!/usr/bin/env bash
#
# Fails if something asks Injekt for a type Animato's Metro graph has no accessor for.
#
# Every binding lives in animato.di.AnimatoGraph, which the compiler checks. What it cannot see is a
# new `Injekt.get<Foo>()` — Injekt resolves by type at run time — so the graph carries one accessor
# per requested type, generated from the call sites into InjektAccessors.kt. Metro then has to be
# able to build each of them, and a type nothing provides fails the build.
#
# This only checks the generated file is current. If it is not, regenerate it:
#
#     python3 .github/generate-injekt-accessors.py

set -euo pipefail

cd "$(dirname "$0")/.."

echo "Checking every Injekt request has a graph accessor…"
exec python3 .github/generate-injekt-accessors.py --check
