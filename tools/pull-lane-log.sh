#!/usr/bin/env bash
# LANELOG01_THE_LANE_BUILD_KEEPS_ITS_LOG_V1: fetches the rolling log a dev1/dev2 build keeps on the
# phone. Usage: tools/pull-lane-log.sh --lane dev1|dev2 --serial SERIAL [--out DIRECTORY]
# The files hold account and post IDs: keep them outside every repo. The phone's copy stays.
set -euo pipefail

LANE=""
SERIAL=""
OUT=""
while (($#)); do
    case "$1" in
        --lane) LANE="$2"; shift 2 ;;
        --serial) SERIAL="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        *) echo "STOP=UNKNOWN_ARGUMENT arg=$1" >&2; exit 2 ;;
    esac
done
[[ "$LANE" == dev1 || "$LANE" == dev2 ]] || { echo "STOP=LANE_MUST_BE_DEV1_OR_DEV2" >&2; exit 2; }
[[ -n "$SERIAL" ]] || { echo "STOP=SERIAL_REQUIRED" >&2; exit 2; }

PACKAGE="io.github.brealorg.subshell.debug.$LANE"
OUT="${OUT:-$HOME/dev/subshell/worklog/lane-logs/$LANE-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

adb -s "$SERIAL" exec-out run-as "$PACKAGE" tar -C no_backup -cf - lane-log | tar -xf - -C "$OUT"

FILES="$(find "$OUT/lane-log" -name '*.log' | wc -l)"
[[ "$FILES" -gt 0 ]] || { echo "STOP=NO_LOG_FILES out=$OUT" >&2; exit 1; }
echo "LOG_FILES=$FILES"
echo "LOG_BYTES=$(du -sb "$OUT/lane-log" | cut -f1)"
echo "OUT=$OUT/lane-log"
echo "RESULT=LANE_LOG_PULLED"
