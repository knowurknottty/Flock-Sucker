#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")" && pwd)
DIST="$ROOT/../dist"
ZIP="$DIST/Flock-Sucker-Diagnostics-Magisk-1.1.0.zip"
"$ROOT/build.sh"
mkdir -p "$DIST"
rm -f "$ZIP"
cd "$ROOT"
zip -q -r "$ZIP" module.prop customize.sh service.sh sepolicy.rule allowed_packages bin/arm64-v8a/flockyou-diagd
unzip -t "$ZIP"
echo "$ZIP"
