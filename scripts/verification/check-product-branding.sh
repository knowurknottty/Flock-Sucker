#!/bin/sh
set -eu

# Reject legacy visible/source product names in any capitalization. Compatibility
# identifiers such as com.flockyou and flockyou_database_encrypted intentionally
# remain valid because they do not match a space or hyphen separator.
hits=$(grep -RIniE \
  --exclude='check-product-branding.sh' \
  --exclude-dir=build \
  --exclude-dir=.git \
  'flock([[:space:]]+|-)you' \
  app/src/main/res app/src/main/java app/build.gradle.kts 2>/dev/null || true)
if [ -n "$hits" ]; then
  echo "Legacy visible/source branding remains:" >&2
  echo "$hits" >&2
  exit 1
fi
printf '%s\n' 'Branding check PASS: Flock-Sucker visible/source branding is consistent.'
