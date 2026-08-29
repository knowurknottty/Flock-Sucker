#!/system/bin/sh
MODDIR=${0%/*}
LOG="$MODDIR/daemon.log"
BIN="$MODDIR/bin/arm64-v8a/flockyou-diagd"
ALLOW="$MODDIR/allowed_packages"

# The current module packages arm64-v8a because the validated target device is arm64.
ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
  arm64-v8a) ;;
  *) echo "Unsupported ABI: $ABI" >> "$LOG"; exit 0 ;;
esac

chmod 0755 "$BIN"
"$BIN" "$ALLOW" >> "$LOG" 2>&1 &
echo "started pid=$! abi=$ABI" >> "$LOG"
