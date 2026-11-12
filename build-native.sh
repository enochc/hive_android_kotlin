#!/usr/bin/env bash
# Builds libhive.so for every Android ABI from the hive crate into
# hive/src/main/jniLibs, and refreshes NativeHive.kt, the Kotlin class that
# has to match it.
#
#   ./build-native.sh [path to the hive crate]     default: ../hive, or $HIVE_CRATE
#
# HIVE_FEATURES picks cargo features, e.g. HIVE_FEATURES=tls,auth.
# Needs cargo-ndk (cargo install cargo-ndk), the four Android rust targets
# and an NDK (ANDROID_NDK_HOME, or the newest one in the Android SDK).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
crate="${1:-${HIVE_CRATE:-$here/../hive}}"

if [ ! -f "$crate/src/jni_api.rs" ]; then
    echo "No hive crate with JNI bindings at $crate (needs Hive 0.3.0 with src/jni_api.rs)." >&2
    exit 1
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    ndk="$(ls -d "$sdk"/ndk/*/ 2>/dev/null | sort -V | tail -1)"
    if [ -z "$ndk" ]; then
        echo "No NDK found under $sdk/ndk; set ANDROID_NDK_HOME." >&2
        exit 1
    fi
    export ANDROID_NDK_HOME="${ndk%/}"
fi

cd "$crate"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 --platform 24 \
    -o "$here/hive/src/main/jniLibs" \
    build --lib --release ${HIVE_FEATURES:+--features "$HIVE_FEATURES"}

cp bindings/kotlin/com/enochc/hive/NativeHive.kt "$here/hive/src/main/kotlin/com/enochc/hive/NativeHive.kt"
echo "libhive.so and NativeHive.kt are up to date with $crate"
