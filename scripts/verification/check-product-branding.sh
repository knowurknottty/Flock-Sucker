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

# The primary title used to evade the visible-name regex by composing two
# resource tokens. Forbid those legacy split-brand resource identifiers entirely.
split_hits=$(grep -RInE --exclude-dir=build --exclude-dir=.git 'app_title_(flock|you)' app/src/main 2>/dev/null || true)
if [ -n "$split_hits" ]; then
  echo "Legacy split-title branding remains:" >&2
  echo "$split_hits" >&2
  exit 1
fi
printf '%s\n' 'Branding check PASS: Flock-Sucker visible/source branding is consistent.'
