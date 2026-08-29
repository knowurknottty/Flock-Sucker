#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Installing Flock-Sucker diagnostic companion"
ui_print "- Read-only /dev/umts_dm0 bridge; no modem writes"
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/bin/arm64-v8a/flockyou-diagd" 0 0 0755
set_perm "$MODPATH/allowed_packages" 0 0 0644
