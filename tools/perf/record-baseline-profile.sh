#!/usr/bin/env bash
# ISSUE259_BASELINE_PROFILE_IS_A_RELEASE_STEP_V1 (owner 2026-10-29: "gjør det
# permanent"): re-records app/src/main/baseline-prof.txt on a phone, as the
# first step of every release preparation (before the prepare commit).
#
#   tools/perf/record-baseline-profile.sh --lane dev1|dev2 --serial SERIAL
#
# Run it in that lane's worktree on a clean tree. It builds the unminified
# benchmark app with the lane's package (so it installs over the signed-in lane
# app: a signed-out app records only the onboarding), runs
# BaselineProfileGenerator, merges the recording into the current profile
# (tools/perf/merge-baseline-profile.py), removes the generator app and puts the
# normal lane build back. The profile change is left for the caller to commit.
# Gradle and the phone are shared: announce "taking" to the other lane first.
set -euo pipefail

LANE=''
SERIAL=''
while [[ $# -gt 0 ]]; do
    case "$1" in
        --lane) LANE="${2:-}"; shift 2 ;;
        --serial) SERIAL="${2:-}"; shift 2 ;;
        *) echo "STOP=UNKNOWN_ARGUMENT $1"; exit 2 ;;
    esac
done
stop() { echo "STOP=$1"; echo 'RESULT=ISSUE259_BASELINE_PROFILE_NOT_RECORDED'; exit 1; }
[[ "$LANE" == dev1 || "$LANE" == dev2 ]] || stop LANE_MUST_BE_DEV1_OR_DEV2
[[ -n "$SERIAL" ]] || stop SERIAL_MISSING

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
PROFILE=app/src/main/baseline-prof.txt
[[ -z "$(git status --porcelain)" ]] || stop LANE_NOT_CLEAN

ADB=(adb -s "$SERIAL")
TARGET="io.github.brealorg.subshell.debug.$LANE"
GENERATOR=app.subshell.prototype.benchmark
DEVICE_FILE="/sdcard/Android/media/$GENERATOR/BaselineProfileGenerator_coldStart-startup-prof.txt"

[[ "$("${ADB[@]}" get-state 2>/dev/null || true)" == device ]] || stop DEVICE_NOT_ATTACHED
"${ADB[@]}" shell pm path "$TARGET" >/dev/null 2>&1 || stop LANE_APP_NOT_INSTALLED_SIGN_IN_FIRST
# The generator needs the phone awake and unlocked; it never unlocks it.
WINDOW="$("${ADB[@]}" shell dumpsys window)"
grep -q 'isKeyguardShowing=true' <<<"$WINDOW" && stop PHONE_LOCKED_UNLOCK_IT
"${ADB[@]}" shell dumpsys power | grep -q 'mWakefulness=Awake' || stop PHONE_SCREEN_OFF

./gradlew --offline --no-daemon --no-configuration-cache -q \
    :app:assembleBenchmark :benchmark:assembleBenchmark \
    -Psubshell.baselineProfileSource "-Psubshell.debugSlot=$LANE" || stop BENCHMARK_BUILD_FAILED

"${ADB[@]}" install -r -t app/build/outputs/apk/benchmark/app-benchmark.apk >/dev/null || stop APP_INSTALL_FAILED
"${ADB[@]}" install -r -t benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk >/dev/null || stop GENERATOR_INSTALL_FAILED

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-baseline-profile.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT
restore_lane() {
    "${ADB[@]}" uninstall "$GENERATOR" >/dev/null 2>&1 || true
    SUBSHELL_ADB_SERIAL="$SERIAL" ./tools/subshell "$LANE" >"$TMP/lane.log" 2>&1 &&
        echo 'LANE_BUILD_RESTORED=YES' || echo 'LANE_BUILD_RESTORED=NO'
}

"${ADB[@]}" shell am instrument -w \
    -e class app.subshell.prototype.benchmark.BaselineProfileGenerator \
    -e androidx.benchmark.suppressErrors EMULATOR \
    -e targetPackage "$TARGET" \
    "$GENERATOR/androidx.test.runner.AndroidJUnitRunner" >"$TMP/instrument.log" 2>&1 || true
if ! grep -q '^OK (1 test)' "$TMP/instrument.log"; then
    tail -20 "$TMP/instrument.log"
    restore_lane
    stop GENERATOR_FAILED
fi
"${ADB[@]}" pull "$DEVICE_FILE" "$TMP/recorded.txt" >/dev/null || { restore_lane; stop RECORDING_NOT_PULLED; }

python3 -I tools/perf/merge-baseline-profile.py \
    --current "$PROFILE" --recorded "$TMP/recorded.txt" \
    --classes app/build/intermediates/built_in_kotlinc/benchmark/compileBenchmarkKotlin/classes \
    --classes app/build/intermediates/javac/benchmark/compileBenchmarkJavaWithJavac/classes \
    --output "$TMP/merged.txt" || { restore_lane; stop MERGE_FAILED; }
command cp -f "$TMP/merged.txt" "$PROFILE"

restore_lane
echo "RECORDED_FROM=$(git rev-parse --short HEAD)"
echo 'RESULT=ISSUE259_BASELINE_PROFILE_RECORDED'
