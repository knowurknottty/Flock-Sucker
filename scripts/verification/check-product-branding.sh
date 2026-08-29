#!/bin/sh
set -eu
hits=$(grep -RIn --exclude='check-product-branding.sh' --exclude-dir=build --exclude-dir=.git 'Flock You' app/src/main/res app/src/main/java app/build.gradle.kts 2>/dev/null || true)
if [ -n "$hits" ]; then
  echo "Legacy user/source branding remains:" >&2
  echo "$hits" >&2
  exit 1
fi
printf '%s\n' 'Branding check PASS: Flock-Sucker visible/source branding is consistent.'
