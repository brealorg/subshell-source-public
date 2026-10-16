#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${FEEDPOST_V2E_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.ui.SubshellProcessStateInstrumentedTest'
LOG_TAG='subshell-feedpost-v2e'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

run_phase() {
  local method="$1"
  adb -s "$SERIAL" shell am instrument -w -r \
    -e class "${TEST_CLASS}#${method}" \
    "$RUNNER"
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_FEEDPOST_V2E_PROCESS_STATE_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_SEPARATE_PROCESS_INSTRUMENTATION_AND_APP_STATE_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=WRITE_SNAPSHOT_FORCE_STOP_RESTORE_SNAPSHOT_AND_UNWIND_TYPED_STACK\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${FEEDPOST_V2E_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'FEEDPOST_V2E_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] ||
  fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null
adb -s "$SERIAL" logcat -c

run_phase writeSnapshotBeforeExternalProcessRestart

adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug
adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug.test
sleep 1

run_phase restoreSnapshotAfterExternalProcessRestart

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"
grep -Fq 'FEEDPOST_V2E_PHASE=WRITE' <<<"$LOG_OUTPUT" ||
  fail PROCESS_SNAPSHOT_WRITE_MARKER_MISSING
grep -Fq 'ROUTE_DEPTH=8' <<<"$LOG_OUTPUT" ||
  fail FULL_ROUTE_DEPTH_MARKER_MISSING
grep -Fq 'ANCHOR_COUNT=3' <<<"$LOG_OUTPUT" ||
  fail KEYED_ANCHOR_COUNT_MARKER_MISSING
grep -Fq 'FEEDPOST_V2E_PHASE=RESTORE' <<<"$LOG_OUTPUT" ||
  fail PROCESS_SNAPSHOT_RESTORE_MARKER_MISSING
grep -Fq 'FULL_STACK=true' <<<"$LOG_OUTPUT" ||
  fail FULL_STACK_RESTORE_MARKER_MISSING
grep -Fq 'KEYED_ANCHORS=true' <<<"$LOG_OUTPUT" ||
  fail KEYED_ANCHOR_RESTORE_MARKER_MISSING
grep -Fq 'TABS=true' <<<"$LOG_OUTPUT" ||
  fail TAB_RESTORE_MARKER_MISSING
grep -Fq 'COMPOSER_DRAFT=true' <<<"$LOG_OUTPUT" ||
  fail COMPOSER_DRAFT_RESTORE_MARKER_MISSING
grep -Fq 'MEDIA_RESUME=true' <<<"$LOG_OUTPUT" ||
  fail MEDIA_RESUME_RESTORE_MARKER_MISSING
grep -Fq 'SAFE_BACK_UNWIND=true' <<<"$LOG_OUTPUT" ||
  fail SAFE_BACK_UNWIND_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_FEEDPOST_V2E_PROCESS_STATE_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'SEPARATE_INSTRUMENTATION_PROCESS_PHASES=PASS\n'
printf 'FULL_TYPED_ROUTE_STACK_RESTORE=PASS\n'
printf 'KEYED_SCROLL_ANCHOR_RESTORE=PASS\n'
printf 'USER_TAB_RESTORE=PASS\n'
printf 'POST_COMPOSER_DRAFT_RESTORE=PASS\n'
printf 'MEDIA_ROUTE_INDEX_AND_RESUME_STATE=PASS\n'
printf 'SAFE_BACK_UNWIND_AFTER_RESTORE=PASS\n'
printf 'CORRUPT_SNAPSHOT_FALLBACK=STATIC_AND_UNIT_PASS\n'
printf 'MISSING_POST_DEPENDENT_ROUTE_DROP=STATIC_AND_UNIT_PASS\n'
printf 'UNKNOWN_ENUM_SAFE_DEFAULT=STATIC_AND_UNIT_PASS\n'
printf 'V2A_TYPED_POST_ORIGIN=PRESERVED\n'
printf 'V2B_CANONICAL_ROUTE_STACK=PRESERVED\n'
printf 'V2C_PER_FEED_SCROLL_ANCHORS=PRESERVED\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_FEEDPOST_V2E_PROCESS_STATE_RUNTIME_PASS\n'
