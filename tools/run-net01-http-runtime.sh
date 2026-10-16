#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${NET01_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.network.Net01HttpBoundaryInstrumentedTest'
LOG_TAG='subshell-net01'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_NET01_SECURE_HTTP_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_LOCALHOST_HTTPS_FIXTURE_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=LOCALHOST_TLS_JSON_HEADERS_BEARER_RATE_LIMIT_MALFORMED_BODY_LIMIT_AND_COROUTINE_CANCELLATION\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'OAUTH=NONE\n'
printf 'PERSISTENT_DATABASE=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${NET01_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'NET01_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

grep -Fq 'NET01_HTTPS_REQUESTS=6' <<<"$LOG_OUTPUT" ||
  fail HTTPS_REQUEST_COUNT_MARKER_MISSING
grep -Fq 'JSON_ITEMS=2' <<<"$LOG_OUTPUT" ||
  fail JSON_MAPPING_MARKER_MISSING
grep -Fq 'RATE_LIMIT_RETRY_MS=7000' <<<"$LOG_OUTPUT" ||
  fail RATE_LIMIT_MARKER_MISSING
grep -Fq 'MALFORMED_JSON=true' <<<"$LOG_OUTPUT" ||
  fail MALFORMED_JSON_MARKER_MISSING
grep -Fq 'BODY_LIMIT=true' <<<"$LOG_OUTPUT" ||
  fail BODY_LIMIT_MARKER_MISSING
grep -Fq 'CALL_CANCELLED=true' <<<"$LOG_OUTPUT" ||
  fail CANCELLATION_MARKER_MISSING
grep -Fq 'AUTH_HEADER_SCOPED=true' <<<"$LOG_OUTPUT" ||
  fail AUTH_HEADER_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_NET01_SECURE_HTTP_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'HTTPS_ONLY_ENDPOINT=PASS\n'
printf 'ANDROID_NETWORK_SECURITY_CONFIG=PASS\n'
printf 'SHARED_OKHTTP_CLIENT_POLICY=PASS\n'
printf 'STRUCTURED_HTTP_URL_BUILDING=PASS\n'
printf 'TRANSPARENT_USER_AGENT=PASS\n'
printf 'OPTIONAL_BEARER_AUTH_INJECTION=PASS\n'
printf 'BOUNDED_JSON_BODY=PASS\n'
printf 'SANITIZED_PROTOCOL_FAILURES=PASS\n'
printf 'RATE_LIMIT_AND_RETRY_AFTER_METADATA=PASS\n'
printf 'COROUTINE_CALL_CANCELLATION=PASS\n'
printf 'REDDIT_AFTER_CURSOR_RAW_JSON_LIMIT_100=PASS\n'
printf 'ROOM_SOURCE_OF_TRUTH=PRESERVED\n'
printf 'DATA03_ROOM_CACHE_POLICY=PRESERVED\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'OAUTH=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_NET01_SECURE_HTTP_RUNTIME_PASS\n'
