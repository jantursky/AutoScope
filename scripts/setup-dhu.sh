#!/usr/bin/env bash
# One-time setup: installs the Desktop Head Unit and creates a Play Store emulator.
# Android Auto itself must then be updated and signed in by hand inside the emulator.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
AVD_NAME="autoscope_api36"

yes | "$SDKMANAGER" --licenses >/dev/null || true
"$SDKMANAGER" "extras;google;auto" "$IMAGE" "emulator" "platform-tools"

if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD_NAME"; then
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" -d pixel_8
fi

echo "DHU:  $SDK/extras/google/auto/desktop-head-unit"
echo "AVD:  $AVD_NAME  (start: $SDK/emulator/emulator -avd $AVD_NAME)"
