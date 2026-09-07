# Flock-Sucker - Device makefile include
#
# Include this file in your device.mk or vendor configuration to add
# Flock-Sucker as a privileged system app in your GrapheneOS/AOSP build.
#
# Usage in device.mk:
#   $(call inherit-product, vendor/flocksucker/flocksucker.mk)
#
# Or in BoardConfig.mk:
#   include vendor/flocksucker/flocksucker.mk
#
# Make sure to copy the built APK (FlockSucker.apk) to vendor/flocksucker/ first

FLOCKSUCKER_PATH := vendor/flocksucker

# Add FlockSucker to the build
PRODUCT_PACKAGES += \
    FlockSucker

# Alternatively, for Soong-only builds, you may need:
# PRODUCT_SOONG_NAMESPACES += $(FLOCKSUCKER_PATH)

# SELinux policy (if custom policies are needed)
# BOARD_SEPOLICY_DIRS += $(FLOCKSUCKER_PATH)/sepolicy

# Optional: Pre-grant runtime permissions on first boot
# This requires a custom PermissionController or framework modification
# PRODUCT_COPY_FILES += \
#     $(FLOCKSUCKER_PATH)/default-permissions-flocksucker.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/default-permissions/default-permissions-flocksucker.xml
