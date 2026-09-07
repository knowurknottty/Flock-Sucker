#!/bin/bash
set -euo pipefail

# OEM Setup Script - Integrates Flock-Sucker app into GrapheneOS build
# This script is called during the build process

DEVICE="${DEVICE:-husky}"
OEM_APP_DIR="/src/vendor/flocksucker"
PREBUILT_APK="/oem-apps/flock-sucker.apk"

echo "=== Setting up Flock-Sucker OEM Integration ==="

# Create vendor directory structure
mkdir -p "${OEM_APP_DIR}/apps/FlockSucker"
mkdir -p "${OEM_APP_DIR}/overlay"
mkdir -p "${OEM_APP_DIR}/sepolicy"

# Copy prebuilt APK if available
if [ -f "${PREBUILT_APK}" ]; then
    echo "Using prebuilt APK..."
    cp "${PREBUILT_APK}" "${OEM_APP_DIR}/apps/FlockSucker/FlockSucker.apk"
fi

# Create Android.bp for the app module
cat > "${OEM_APP_DIR}/apps/FlockSucker/Android.bp" << 'EOF'
android_app_import {
    name: "FlockSucker",
    owner: "flocksucker",
    apk: "FlockSucker.apk",
    presigned: true,
    privileged: true,
    dex_preopt: {
        enabled: true,
    },
    required: [
        "privapp_whitelist_com.inversionlabs.flocksucker",
    ],
}
EOF

# Create privapp permissions whitelist
cat > "${OEM_APP_DIR}/apps/FlockSucker/privapp_whitelist_com.inversionlabs.flocksucker.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.inversionlabs.flocksucker">
        <!-- Bluetooth scanning for tracker detection -->
        <permission name="android.permission.BLUETOOTH_SCAN"/>
        <permission name="android.permission.BLUETOOTH_CONNECT"/>
        <permission name="android.permission.BLUETOOTH_PRIVILEGED"/>

        <!-- Location for surveillance detection -->
        <permission name="android.permission.ACCESS_FINE_LOCATION"/>
        <permission name="android.permission.ACCESS_BACKGROUND_LOCATION"/>

        <!-- Network analysis -->
        <permission name="android.permission.ACCESS_WIFI_STATE"/>
        <permission name="android.permission.CHANGE_WIFI_STATE"/>
        <permission name="android.permission.ACCESS_NETWORK_STATE"/>

        <!-- Cellular analysis -->
        <permission name="android.permission.READ_PHONE_STATE"/>
        <permission name="android.permission.READ_PRIVILEGED_PHONE_STATE"/>

        <!-- Background operation -->
        <permission name="android.permission.FOREGROUND_SERVICE"/>
        <permission name="android.permission.RECEIVE_BOOT_COMPLETED"/>
        <permission name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

        <!-- Notifications -->
        <permission name="android.permission.POST_NOTIFICATIONS"/>
    </privapp-permissions>
</permissions>
EOF

# Create Android.bp for privapp whitelist
cat > "${OEM_APP_DIR}/apps/FlockSucker/privapp_whitelist_Android.bp" << 'EOF'
prebuilt_etc {
    name: "privapp_whitelist_com.inversionlabs.flocksucker",
    src: "privapp_whitelist_com.inversionlabs.flocksucker.xml",
    sub_dir: "permissions",
    filename_from_src: true,
}
EOF

# Main vendor Android.bp
cat > "${OEM_APP_DIR}/Android.bp" << 'EOF'
soong_namespace {
    imports: [
        "vendor/flocksucker/apps/FlockSucker",
    ],
}
EOF

# Create device makefile fragment
cat > "${OEM_APP_DIR}/flocksucker.mk" << 'EOF'
# Flock-Sucker OEM Integration

# Include the app in the build
PRODUCT_PACKAGES += \
    FlockSucker \
    privapp_whitelist_com.inversionlabs.flocksucker

# Grant default permissions
PRODUCT_COPY_FILES += \
    vendor/flocksucker/apps/FlockSucker/privapp_whitelist_com.inversionlabs.flocksucker.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp_whitelist_com.inversionlabs.flocksucker.xml

# OEM branding
PRODUCT_BRAND := FlockSucker
PRODUCT_MODEL := $(PRODUCT_MODEL) Security Edition

# Enable additional security features
PRODUCT_PROPERTY_OVERRIDES += \
    ro.flocksucker.enabled=true \
    ro.flocksucker.version=1.0.0 \
    persist.flocksucker.auto_scan=true
EOF

# Create SEPolicy for privileged permissions
cat > "${OEM_APP_DIR}/sepolicy/flocksucker.te" << 'EOF'
# SELinux policy for Flock-Sucker privileged app

type flocksucker_app, domain;
app_domain(flocksucker_app)
net_domain(flocksucker_app)

# Allow bluetooth operations
allow flocksucker_app bluetooth_socket:sock_file write;
allow flocksucker_app bluetooth:unix_stream_socket connectto;

# Allow WiFi scanning
allow flocksucker_app wifi_data_file:dir search;
allow flocksucker_app wifi_data_file:file r_file_perms;

# Allow reading cell info
allow flocksucker_app radio_service:service_manager find;

# Allow location access
allow flocksucker_app location_service:service_manager find;

# Allow foreground service
allow flocksucker_app activity_service:service_manager find;
EOF

cat > "${OEM_APP_DIR}/sepolicy/file_contexts" << 'EOF'
/system/priv-app/FlockSucker(/.*)?    u:object_r:system_file:s0
EOF

cat > "${OEM_APP_DIR}/sepolicy/seapp_contexts" << 'EOF'
user=_app seinfo=platform name=com.inversionlabs.flocksucker domain=flocksucker_app type=app_data_file levelFrom=all
EOF

# Create SEPolicy Android.bp to include policy files
cat > "${OEM_APP_DIR}/sepolicy/Android.bp" << 'EOF'
se_policy_conf {
    name: "flocksucker_sepolicy",
    srcs: ["flocksucker.te"],
    installable: false,
}
EOF

# Map device codenames to their device tree directories
get_device_dir() {
    local device="$1"
    case "${device}" in
        husky|shiba)
            echo "shusky"  # Pixel 8/8 Pro share shusky
            ;;
        felix)
            echo "felix"   # Pixel Fold
            ;;
        tangorpro)
            echo "tangorpro"  # Pixel Tablet
            ;;
        lynx)
            echo "lynx"    # Pixel 7a
            ;;
        cheetah|panther)
            echo "pantah"  # Pixel 7/7 Pro share pantah
            ;;
        bluejay)
            echo "bluejay" # Pixel 6a
            ;;
        oriole|raven)
            echo "raviole" # Pixel 6/6 Pro share raviole
            ;;
        *)
            echo "${device}"
            ;;
    esac
}

DEVICE_DIR=$(get_device_dir "${DEVICE}")

# Try multiple possible locations for device makefile
DEVICE_MK_LOCATIONS=(
    "/src/device/google/${DEVICE_DIR}/device.mk"
    "/src/device/google/${DEVICE}/device.mk"
    "/src/device/google/${DEVICE_DIR}/${DEVICE}.mk"
)

DEVICE_MK=""
for mk_path in "${DEVICE_MK_LOCATIONS[@]}"; do
    if [ -f "${mk_path}" ]; then
        DEVICE_MK="${mk_path}"
        break
    fi
done

if [ -n "${DEVICE_MK}" ]; then
    if ! grep -q "flocksucker.mk" "${DEVICE_MK}"; then
        echo "" >> "${DEVICE_MK}"
        echo "# Flock-Sucker OEM Integration" >> "${DEVICE_MK}"
        echo "\$(call inherit-product-if-exists, vendor/flocksucker/flocksucker.mk)" >> "${DEVICE_MK}"
        echo "Injected Flock-Sucker into device makefile: ${DEVICE_MK}"
    else
        echo "Flock-Sucker already in device makefile"
    fi
else
    echo "WARNING: Device makefile not found for ${DEVICE}"
    echo "Searched locations:"
    for mk_path in "${DEVICE_MK_LOCATIONS[@]}"; do
        echo "  - ${mk_path}"
    done
    echo ""
    echo "You may need to manually include vendor/flocksucker/flocksucker.mk"
    echo "Or add to build/make/target/product/base_system.mk"
fi

echo "=== OEM Integration Complete ==="
echo "Files created in ${OEM_APP_DIR}:"
find "${OEM_APP_DIR}" -type f
