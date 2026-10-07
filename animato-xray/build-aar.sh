#!/usr/bin/env bash
#
# Builds animato-xray/build/libxray.aar: Xray-core with the small bridge in libxray/, for the three
# Android ABIs the app ships (arm64-v8a, armeabi-v7a) plus x86 for emulators. The app's Gradle
# build depends on the file, so this has to have run first — CI does it in its own step, cached on
# the inputs below.
#
# Needs Go (any recent one; the module pins the toolchain it wants and Go fetches it) and the
# Android NDK, found through ANDROID_NDK_HOME or the newest one under $ANDROID_HOME/ndk.

set -euo pipefail

cd "$(dirname "$0")"

export GOTOOLCHAIN=auto
export GOFLAGS=-mod=mod
export PATH="$PATH:$(go env GOPATH)/bin"

# gomobile wants the SDK as well as the NDK, and looks for it in ANDROID_HOME only.
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)"
    export ANDROID_NDK_HOME
fi
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "No Android NDK found. Install one with sdkmanager, or set ANDROID_NDK_HOME." >&2
    exit 1
fi

# The same x/mobile version go.mod names, so the bind tool and the bind package agree.
MOBILE_VERSION="$(go list -m -f '{{.Version}}' golang.org/x/mobile)"
go install "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION" "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"

mkdir -p build
gomobile bind \
    -target=android/arm64,android/arm,android/386 \
    -androidapi 26 \
    -javapkg animato.xray \
    -trimpath \
    -ldflags="-s -w -buildid=" \
    -o build/libxray.aar \
    ./libxray

rm -f build/libxray-sources.jar
ls -la build/libxray.aar
