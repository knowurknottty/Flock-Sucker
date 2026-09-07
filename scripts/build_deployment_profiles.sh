#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

AAPT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/build-tools/37.0.0/aapt"
OUT="$ROOT/dist/deployment-profiles"
mkdir -p "$OUT"

profiles=(generic-stock generic-root tonga-stock tonga-root)
source_sha="$(git rev-parse HEAD)"
short_sha="${source_sha:0:7}"

for profile in "${profiles[@]}"; do
  echo "=== Building $profile from $source_sha ==="
  ./gradlew clean :app:testSideloadDebugUnitTest :app:assembleSideloadDebug \
    -PDEPLOYMENT_PROFILE="$profile" --console=plain --no-build-cache

  src="app/build/outputs/apk/sideload/debug/app-sideload-debug.apk"
  apk="$OUT/Flock-Sucker-${profile}-${short_sha}.apk"
  receipt="$OUT/Flock-Sucker-${profile}-${short_sha}.receipt.txt"
  cp "$src" "$apk"

  sha256="$(shasum -a 256 "$apk" | awk '{print $1}')"
  tests="$(python3 - <<'PYTEST'
from pathlib import Path
import xml.etree.ElementTree as ET
n=f=e=s=0
for p in Path('app/build/test-results/testSideloadDebugUnitTest').glob('TEST-*.xml'):
    r=ET.parse(p).getroot()
    n += int(r.attrib.get('tests',0))
    f += int(r.attrib.get('failures',0))
    e += int(r.attrib.get('errors',0))
    s += int(r.attrib.get('skipped',0))
print(f"{n}/{f}/{e}/{s}")
PYTEST
)"
  badging="$($AAPT dump badging "$apk" | head -n 2)"

  cat > "$receipt" <<EOF
Flock-Sucker deployment artifact receipt
source_sha=$source_sha
profile=$profile
install_mode=sideload
sha256=$sha256
unit_tests_tests_failures_errors_skipped=$tests
$badging
EOF

done

printf '\nArtifacts:\n'
ls -lh "$OUT"/*.apk "$OUT"/*.receipt.txt
