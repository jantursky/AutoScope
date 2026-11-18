#!/usr/bin/env bash
# Builds and installs all Step 0 spikes on the connected device/emulator.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew assembleDebug
for apk in spikes/car/build/outputs/apk/*/debug/*.apk spikes/media/build/outputs/apk/debug/*.apk; do
  echo "installing $apk"
  adb install -r "$apk"
done
