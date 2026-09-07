#!/usr/bin/env bash
set -euo pipefail

APK="${1:?usage: compact_onboarding_cta.sh /path/to.apk [serial]}"
SERIAL="${2:-emulator-5554}"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n 1)"
[[ -x "$AAPT" ]] || { echo "FAIL: aapt not found under $SDK_ROOT/build-tools" >&2; exit 1; }
PKG="$($AAPT dump badging "$APK" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -n1)"
ACTIVITY="$($AAPT dump badging "$APK" | sed -n "s/launchable-activity: name='\([^']*\)'.*/\1/p" | head -n1)"
[[ -n "$PKG" && -n "$ACTIVITY" ]] || { echo "FAIL: unable to resolve package/activity" >&2; exit 1; }

REMOTE_XML="/sdcard/flock_compact_onboarding.xml"
LOCAL_XML="${TMPDIR:-/tmp}/flock_compact_onboarding.xml"
LOCAL_PNG="${TMPDIR:-/tmp}/flock_compact_onboarding.png"
orig_font="$(adb -s "$SERIAL" shell settings get system font_scale | tr -d '\r')"
restore() {
  adb -s "$SERIAL" shell wm size reset >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell wm density reset >/dev/null 2>&1 || true
  if [[ "$orig_font" == "null" || -z "$orig_font" ]]; then
    adb -s "$SERIAL" shell settings delete system font_scale >/dev/null 2>&1 || true
  else
    adb -s "$SERIAL" shell settings put system font_scale "$orig_font" >/dev/null 2>&1 || true
  fi
}
trap restore EXIT

adb -s "$SERIAL" shell wm size 720x1600 >/dev/null
adb -s "$SERIAL" shell wm density 320 >/dev/null
adb -s "$SERIAL" shell settings put system font_scale 1.0 >/dev/null
adb -s "$SERIAL" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" install "$APK" >/dev/null
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am start -W -n "$PKG/$ACTIVITY" >/dev/null
sleep 1
adb -s "$SERIAL" shell uiautomator dump "$REMOTE_XML" >/dev/null
adb -s "$SERIAL" pull "$REMOTE_XML" "$LOCAL_XML" >/dev/null 2>&1
adb -s "$SERIAL" exec-out screencap -p > "$LOCAL_PNG"

read -r X Y BOUNDS < <(python3 - "$LOCAL_XML" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
nodes=[n for n in root.iter('node') if n.attrib.get('text')=='See Required Permissions']
if not nodes: raise SystemExit('FAIL: compact onboarding CTA text is not visible in the UI tree')
b=nodes[0].attrib.get('bounds','')
m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
if not m: raise SystemExit(f'FAIL: malformed CTA bounds: {b}')
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2, (y1+y2)//2, b)
PY
)
echo "PASS: compact onboarding CTA visible at $BOUNDS"
adb -s "$SERIAL" shell input tap "$X" "$Y"
sleep 1
adb -s "$SERIAL" shell uiautomator dump "$REMOTE_XML" >/dev/null
adb -s "$SERIAL" pull "$REMOTE_XML" "$LOCAL_XML" >/dev/null 2>&1
python3 - "$LOCAL_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts={n.attrib.get('text') for n in root.iter('node')}
if 'Location Access' not in texts:
    raise SystemExit('FAIL: CTA tap did not advance to Location Access')
print('PASS: CTA tap advanced to Location Access')
PY
if adb -s "$SERIAL" logcat -d -b crash '*:E' 2>/dev/null | grep -q 'FATAL EXCEPTION'; then
  echo 'FAIL: crash buffer contains FATAL EXCEPTION' >&2; exit 1
fi
echo "PASS: crash buffer clean"
echo "artifact=$APK"
echo "package=$PKG"
echo "activity=$ACTIVITY"
echo "screenshot=$LOCAL_PNG"
