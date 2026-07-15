#!/usr/bin/env bash
# Build the MoChat release APK inside the container.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Building MoChat release APK (R8 obfuscated)"
./gradlew --no-daemon assembleRelease

echo "==> APKs:"
find app/build/outputs/apk -name '*.apk' -exec ls -la {} \;
