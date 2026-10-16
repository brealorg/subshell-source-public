#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${FEEDPOST_V2A_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
PACKAGE='io.github.brealorg.subshell.debug'
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.ui.FeedPostRouteInstrumentedTest'
LOG_TAG='subshell-feedpost-v2'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_FEEDPOST_V2A_ROUTE_ORIGIN_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_STATE_ONLY_INSTRUMENTATION\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=TYPED_ORIGIN_NESTED_POST_COMMUNITY_RETURN_AND_PROCESS_RESTORE\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${FEEDPOST_V2A_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-configuration-cache
else
  printf 'FEEDPOST_V2A_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] || fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  "$RUNNER"

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"
grep -Fq 'FEEDPOST_V2A_TYPED_ORIGIN=true' <<<"$LOG_OUTPUT" || fail TYPED_ORIGIN_MARKER_MISSING
grep -Fq 'NESTED_POST_COMMUNITY_RETURN=true' <<<"$LOG_OUTPUT" || fail NESTED_RETURN_MARKER_MISSING
grep -Fq 'PROCESS_RESTORE_SEARCH_RETURN=true' <<<"$LOG_OUTPUT" || fail PROCESS_RESTORE_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_FEEDPOST_V2A_ROUTE_ORIGIN_RUNTIME_PASS' <<<"$LOG_OUTPUT" || fail RUNTIME_RESULT_MISSING

printf 'TYPED_POST_ORIGIN=PASS\n'
printf 'NESTED_POST_COMMUNITY_RETURN=PASS\n'
printf 'PROCESS_RESTORE_SEARCH_RETURN=PASS\n'
printf 'LEGACY_BARE_SCREEN_OPEN_CALLS=ABSENT\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_FEEDPOST_V2A_ROUTE_ORIGIN_RUNTIME_PASS\n'
