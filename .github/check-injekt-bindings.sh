#!/usr/bin/env bash
#
# Fails if anything asks Injekt for a type nothing registers.
#
# Injekt resolves by type at runtime. Nothing about a missing binding is visible to the compiler,
# and nothing about it is visible to a unit test that does not build the graph — which none of ours
# do, because building it needs an Application. It appears as an exception the moment the screen
# that needs it opens.
#
# That is not hypothetical. Eleven of them were sitting in the anime side at once, and between them
# they crashed the anime source list, the extension list and its filter, extension details,
# migration, and the anime entry screen with its tracking dialog — every one a `= Injekt.get()`
# default argument on a screen model, so the failure came while the screen was being constructed.
# The code compiled, the tests passed, and none of it had ever run on a device.
#
# The work is in list-injekt-bindings.py, which also checks the other direction: that the arguments
# a registration passes to a constructor are themselves registered.

set -euo pipefail

cd "$(dirname "$0")/.."

echo "Checking Injekt bindings…"
exec python3 .github/list-injekt-bindings.py
