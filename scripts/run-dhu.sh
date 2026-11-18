#!/usr/bin/env bash
# Starts the Desktop Head Unit against a phone/emulator that runs the Android Auto head unit server.
# Before: on the device open Android Auto > developer settings > "Start head unit server".
# Screenshots: type `screenshot <name>.png` in the DHU terminal; this script also supports
#   scripts/run-dhu.sh shot <name>   to send the command to a running DHU session started by this script.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
DHU="$SDK/extras/google/auto/desktop-head-unit"
SHOT_DIR="$(cd "$(dirname "$0")/.." && pwd)/dhu-screenshots"
FIFO="${TMPDIR:-/tmp}/autoscope-dhu.fifo"

adb forward tcp:5277 tcp:5277

case "${1:-run}" in
  run)
    mkdir -p "$SHOT_DIR"
    rm -f "$FIFO" && mkfifo "$FIFO"
    # Keep the FIFO open for writing so DHU does not see EOF on stdin.
    ( exec 3<>"$FIFO"; cd "$SHOT_DIR" && "$DHU" < "$FIFO" ) &
    DHU_PID=$!
    trap 'kill $DHU_PID 2>/dev/null || true; rm -f "$FIFO"' EXIT
    # Forward the terminal to the FIFO so normal DHU commands still work.
    cat > "$FIFO"
    ;;
  shot)
    name="${2:-screen-$(date +%Y%m%d-%H%M%S)}"
    echo "screenshot ${name}.png" > "$FIFO"
    echo "saved to $SHOT_DIR/${name}.png"
    ;;
  *)
    echo "usage: $0 [run|shot <name>]" >&2; exit 1 ;;
esac
