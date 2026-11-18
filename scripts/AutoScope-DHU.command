#!/usr/bin/env bash
# Double-click in Finder: opens Terminal and runs the whole setup.
cd "$(dirname "$0")/.." && scripts/dev.sh
echo; read -r -p "Press Enter to close"
