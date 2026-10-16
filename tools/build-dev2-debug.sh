#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
exec ./gradlew     :app:testDebugUnitTest     :app:assembleDebug     -Psubshell.debugSlot=dev2     "$@"
