#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_SDK_ROOT:-"$HOME/Library/Android/sdk"}
NDK_VERSION=${NDK_VERSION:-28.2.13676358}
TOOLCHAIN="$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/darwin-x86_64/bin"
CC="$TOOLCHAIN/aarch64-linux-android26-clang"
OUT="$ROOT/bin/arm64-v8a/flockyou-diagd"

[[ -x "$CC" ]] || { echo "missing Android NDK compiler: $CC" >&2; exit 2; }
mkdir -p "$(dirname "$OUT")"
"$CC" -std=c17 -O2 -fPIE -pie -Wall -Wextra -Werror \
  -D_FORTIFY_SOURCE=2 -fstack-protector-strong \
  "$ROOT/src/flockyou-diagd.c" -o "$OUT"
chmod 0755 "$OUT"
echo "built $OUT"
file "$OUT"
