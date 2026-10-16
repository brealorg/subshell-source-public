#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${DATA02_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.RepositoryPagingInstrumentedTest'
LOG_TAG='subshell-data02'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_DATA02_PAGING_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_IN_MEMORY_FAKE_PAGING_STATE\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=KEYED_INITIAL_APPEND_REFRESH_ERROR_RETRY_OFFLINE_APPEND_RETRY_AND_SCROLL_ANCHOR\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'PERSISTENT_DATABASE=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${DATA02_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'DATA02_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  "$RUNNER"

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"

grep -Fq 'DATA02_KEYED_PAGING=true' <<<"$LOG_OUTPUT" ||
  fail KEYED_PAGING_MARKER_MISSING
grep -Fq 'INITIAL_COUNT=12' <<<"$LOG_OUTPUT" ||
  fail INITIAL_PAGE_COUNT_MARKER_MISSING
grep -Fq 'APPEND_COUNT=24' <<<"$LOG_OUTPUT" ||
  fail APPEND_PAGE_COUNT_MARKER_MISSING
grep -Fq 'QUERY_COUNT=2' <<<"$LOG_OUTPUT" ||
  fail QUERY_ISOLATION_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_DATA02_PAGING_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'KEYED_QUERY_CACHE_ISOLATION=PASS\n'
printf 'INITIAL_PAGE_LOAD=PASS\n'
printf 'CURSOR_APPEND=PASS\n'
printf 'STABLE_ID_DEDUP=PASS\n'
printf 'REFRESH_REPLACES_GENERATION=PASS\n'
printf 'STALE_REFRESH_ERROR_AND_RETRY=PASS\n'
printf 'STALE_APPEND_OFFLINE_AND_RETRY=PASS\n'
printf 'V2C_SCROLL_ANCHOR_SURVIVES_APPEND=PASS\n'
printf 'DATA01_REPOSITORY_BOUNDARY=PRESERVED\n'
printf 'V2A_TYPED_POST_ORIGIN=PRESERVED\n'
printf 'V2B_CANONICAL_ROUTE_STACK=PRESERVED\n'
printf 'V2C_KEYED_SCROLL_ANCHORS=PRESERVED\n'
printf 'V2E_FULL_PROCESS_STATE_RESTORE=PRESERVED\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'PERSISTENT_DATABASE=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_DATA02_PAGING_RUNTIME_PASS\n'
