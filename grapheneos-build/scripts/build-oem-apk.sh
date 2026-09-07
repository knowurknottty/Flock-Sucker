#!/bin/bash
set -euo pipefail

# Build the Flock-Sucker APK from source and prepare for OEM integration

FLOCKSUCKER_SRC="${FLOCKSUCKER_SRC:-/flocksucker-src}"
OUTPUT_DIR="${OUTPUT_DIR:-/oem-apps}"
BUILD_DIR="${BUILD_DIR:-/tmp/flocksucker-build}"

echo "=== Building Flock-Sucker APK for OEM Integration ==="
echo "Source: ${FLOCKSUCKER_SRC}"
echo "Build dir: ${BUILD_DIR}"
echo "Output: ${OUTPUT_DIR}"

if [ ! -d "${FLOCKSUCKER_SRC}" ]; then
    echo "ERROR: Flock-Sucker source not found at ${FLOCKSUCKER_SRC}"
    echo "Mount the source directory when running the container"
    exit 1
fi

# Source is mounted read-only, so copy to a writable location
echo "Copying source to build directory..."
rm -rf "${BUILD_DIR}"
cp -a "${FLOCKSUCKER_SRC}" "${BUILD_DIR}"

cd "${BUILD_DIR}"

# Check for gradlew
if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found in source directory"
    exit 1
fi

chmod +x ./gradlew

# Ensure Android SDK is available (may need to be installed separately)
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    echo "WARNING: ANDROID_HOME/ANDROID_SDK_ROOT not set"
    echo "Checking for SDK in common locations..."

    for sdk_path in /opt/android-sdk /usr/lib/android-sdk "${HOME}/Android/Sdk"; do
        if [ -d "${sdk_path}" ]; then
            export ANDROID_HOME="${sdk_path}"
            export ANDROID_SDK_ROOT="${sdk_path}"
            echo "Found SDK at: ${sdk_path}"
            break
        fi
    done

    if [ -z "${ANDROID_HOME:-}" ]; then
        echo "ERROR: Android SDK not found"
        echo "Install SDK or set ANDROID_HOME environment variable"
        exit 1
    fi
fi

# Build release APK
echo "Building release APK..."
./gradlew assembleRelease \
    -Porg.gradle.parallel=true \
    -Porg.gradle.workers.max=32 \
    -Porg.gradle.caching=true \
    --no-daemon \
    --stacktrace

# Find and copy the APK
APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" -type f 2>/dev/null | head -1)
if [ -z "${APK_PATH}" ]; then
    # Try alternate location
    APK_PATH=$(find . -path "*/build/outputs/apk/release/*.apk" -type f 2>/dev/null | head -1)
fi

if [ -z "${APK_PATH}" ]; then
    echo "ERROR: APK not found after build"
    echo "Build output structure:"
    find . -name "*.apk" -type f 2>/dev/null || echo "No APK files found"
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
cp "${APK_PATH}" "${OUTPUT_DIR}/flock-sucker.apk"

# Cleanup build directory to save space
echo "Cleaning up build directory..."
rm -rf "${BUILD_DIR}"

echo "=== APK Build Complete ==="
echo "APK: ${OUTPUT_DIR}/flock-sucker.apk"
ls -la "${OUTPUT_DIR}/flock-sucker.apk"
