#!/usr/bin/env bash
# One command from a clean checkout to AutoScope on the Desktop Head Unit:
#   scripts/dev.sh            build, install, grant permissions, start the DHU (retries until it connects)
#   scripts/dev.sh --no-build install the last built APK
# Needs: a phone or emulator with Android Auto (adb visible), USB debugging on.
# One step stays manual: Android Auto > three dot menu > "Start head unit server". Android Auto
# does not let other apps start it, and it stops after every DHU session.
set -euo pipefail

cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DHU="$SDK/extras/google/auto/desktop-head-unit"
PKG=com.jantursky.autoscope
AA=com.google.android.projection.gearhead
APK=app/build/outputs/apk/debug/app-debug.apk
ADB=adb

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

say "Checking tools"
command -v adb >/dev/null || die "adb not found. Install Android platform-tools or set ANDROID_HOME."
[ -x "$DHU" ] || { say "DHU missing, running scripts/setup-dhu.sh"; scripts/setup-dhu.sh; }

say "Looking for a device"
devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
count=$(printf '%s\n' "$devices" | grep -c . || true)
[ "$count" -ge 1 ] || die "No device. Connect a phone with USB debugging, or start the emulator (scripts/setup-dhu.sh creates 'autoscope_api36')."
if [ "$count" -gt 1 ]; then
  echo "Several devices:"; printf '%s\n' "$devices"
  die "Unplug one, or run with ANDROID_SERIAL=<serial> scripts/dev.sh"
fi
export ANDROID_SERIAL="${ANDROID_SERIAL:-$devices}"
echo "Using $ANDROID_SERIAL"

if [ "${1:-}" != "--no-build" ]; then
  say "Building"
  ./gradlew --console=plain :app:assembleDebug -q
fi

say "Installing AutoScope"
$ADB install -r "$APK" >/dev/null
# Car data permissions are runtime permissions; granting them here saves the dialog in the car.
for p in com.google.android.gms.permission.CAR_SPEED com.google.android.gms.permission.CAR_FUEL \
         com.google.android.gms.permission.CAR_MILEAGE android.permission.ACCESS_FINE_LOCATION; do
  $ADB shell pm grant "$PKG" "$p" >/dev/null 2>&1 || echo "  could not grant $p (grant it from the Hardware tab)"
done

say "Resetting Android Auto and the port forward"
pkill -f desktop-head-unit 2>/dev/null || true
$ADB shell am force-stop "$AA"
$ADB forward --remove-all
$ADB forward tcp:5277 tcp:5277
$ADB shell monkey -p "$AA" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

cat <<MSG

  ON THE PHONE, now:
    Android Auto  >  three dot menu (top right)  >  Start head unit server
  (Developer mode off? Settings > About > tap "Version" 10 times first.)

  The DHU starts and retries until the phone answers. Ctrl+C stops it.
  In the DHU terminal: type  screenshot name.png  to save a screenshot, or  quit  to leave.

MSG

mkdir -p dhu-screenshots
cd dhu-screenshots
for attempt in $(seq 1 30); do
  start=$SECONDS
  "$DHU" || true
  # A session that lasted a while was a real run: stop retrying.
  [ $((SECONDS - start)) -ge 15 ] && break
  echo "DHU did not connect (attempt $attempt). Waiting for 'Start head unit server' on the phone..."
  sleep 3
done
