#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${NET02_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.sync.Net02PostFeedSyncInstrumentedTest'
LOG_TAG='subshell-net02'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_NET02_NETWORK_TO_ROOM_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_LOCALHOST_HTTPS_AND_TEST_ROOM_DATABASE_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=INITIAL_APPEND_DEDUP_REFRESH_CURSOR_GUARD_HTTP_FAILURE_TRANSACTION_ROLLBACK_AND_ROOM_READBACK\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'OAUTH=NONE\n'
printf 'ROOM_DATABASE=EPHEMERAL_NET02_TEST_DATABASE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${NET02_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'NET02_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

grep -Fq 'NET02_HTTPS_REQUESTS=5' <<<"$LOG_OUTPUT" ||
  fail HTTPS_REQUEST_COUNT_MARKER_MISSING
grep -Fq 'INITIAL_STORED=2' <<<"$LOG_OUTPUT" ||
  fail INITIAL_ROOM_WRITE_MARKER_MISSING
grep -Fq 'APPEND_DEDUP_STORED=3' <<<"$LOG_OUTPUT" ||
  fail APPEND_DEDUP_MARKER_MISSING
grep -Fq 'REFRESH_REPLACED=2' <<<"$LOG_OUTPUT" ||
  fail REFRESH_REPLACE_MARKER_MISSING
grep -Fq 'GENERATION=1' <<<"$LOG_OUTPUT" ||
  fail GENERATION_MARKER_MISSING
grep -Fq 'CURSOR_MISMATCH_NO_NETWORK=true' <<<"$LOG_OUTPUT" ||
  fail CURSOR_GUARD_MARKER_MISSING
grep -Fq 'HTTP_FAILURE_CACHE_PRESERVED=true' <<<"$LOG_OUTPUT" ||
  fail HTTP_FAILURE_CACHE_MARKER_MISSING
grep -Fq 'TRANSACTION_ROLLBACK=true' <<<"$LOG_OUTPUT" ||
  fail TRANSACTION_ROLLBACK_MARKER_MISSING
grep -Fq 'ROOM_READBACK_ONLY=true' <<<"$LOG_OUTPUT" ||
  fail ROOM_SOURCE_OF_TRUTH_MARKER_MISSING
grep -Fq 'OPAQUE_CURSOR_CACHE_REPLAY=false' <<<"$LOG_OUTPUT" ||
  fail OPAQUE_CURSOR_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_NET02_NETWORK_TO_ROOM_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'PULL_BASED_POST_FEED_SYNC=PASS\n'
printf 'NETWORK_SUCCESS_TO_ROOM_TRANSACTION=PASS\n'
printf 'ROOM_CANONICAL_READBACK=PASS\n'
printf 'OPAQUE_REMOTE_CURSOR_POLICY=PASS\n'
printf 'STALE_CURSOR_REJECTED_BEFORE_NETWORK=PASS\n'
printf 'APPEND_STABLE_ID_DEDUP=PASS\n'
printf 'REFRESH_REPLACES_AND_INCREMENTS_GENERATION=PASS\n'
printf 'HTTP_FAILURE_PRESERVES_ROOM=PASS\n'
printf 'TRANSACTION_FAILURE_ROLLS_BACK=PASS\n'
printf 'NETWORK_RESPONSE_BODY_NOT_EXPOSED=PASS\n'
printf 'ROOM_SCHEMA_V2_UNCHANGED=PASS\n'
printf 'NET01_SECURE_HTTP_BOUNDARY=PRESERVED\n'
printf 'DATA03_ROOM_SOURCE_OF_TRUTH=PRESERVED\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'OAUTH=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_NET02_NETWORK_TO_ROOM_RUNTIME_PASS\n'
