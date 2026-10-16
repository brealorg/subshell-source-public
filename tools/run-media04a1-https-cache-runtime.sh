#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE='io.github.brealorg.subshell.debug'
TEST_PACKAGE='io.github.brealorg.subshell.debug.test'
RUNNER='androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.media.VideoMediaHttpsCacheInstrumentedTest'
LOG_TAG='subshell-cache'

fail() {
  printf 'FAIL=%s\n' "$1"
  printf 'SOURCE_MUTATIONS=NONE\n'
  printf 'GITHUB_MUTATIONS=NONE\n'
  printf 'RESULT=SUBSHELL_MEDIA04A1_HTTPS_CACHE_RUNTIME_FAILED\n'
  exit 1
}

cd "$ROOT"

grep -Fq \
  'SUBSHELL_MEDIA04A1_DETERMINISTIC_HTTPS_CACHE_RUNTIME_PROOF_V1' \
  app/build.gradle.kts ||
  fail 'MEDIA04A1_IMPLEMENTATION_MARKER_MISSING'

echo 'APPROVED_OPERATION=RUN_SUBSHELL_MEDIA04A1_DETERMINISTIC_HTTPS_CACHE_RUNTIME'
echo 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_CACHE_RESET'
echo "SOURCE_ROOT=$ROOT"
echo 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS'
echo 'CLEARTEXT_TRAFFIC=DISABLED'
echo 'TEST_SEQUENCE=COLD_UPSTREAM_WRITE_THEN_SERVER_OFFLINE_CACHE_READ'
echo 'REMOTE_PROVIDER_CONTACT=NONE'

if [[ "${MEDIA04A1_SKIP_BUILD:-0}" != '1' ]]; then
  ./gradlew \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon
else
  echo 'MEDIA04A1_BUILD=SKIPPED_BY_VERIFIED_CALLER'
fi

APP_APK='app/build/outputs/apk/debug/app-debug.apk'
TEST_APK="$(
  find app/build/outputs/apk/androidTest/debug \
    -maxdepth 1 -type f -name '*androidTest.apk' \
    -print -quit 2>/dev/null || true
)"

[[ -f "$APP_APK" ]] || fail 'DEBUG_APK_NOT_FOUND'
[[ -n "$TEST_APK" && -f "$TEST_APK" ]] || fail 'ANDROID_TEST_APK_NOT_FOUND'

unset ANDROID_SERIAL
mapfile -t ALL_DEVICES < <(
  adb devices |
    awk 'NR > 1 && $2 == "device" { print $1 }'
)
mapfile -t MDNS_DEVICES < <(
  printf '%s\n' "${ALL_DEVICES[@]}" |
    grep -E '_adb-tls-connect\._tcp$' || true
)

if [[ "${#MDNS_DEVICES[@]}" -eq 1 ]]; then
  SERIAL="${MDNS_DEVICES[0]}"
  ADB_POLICY='MDNS_FIRST'
elif [[ "${#ALL_DEVICES[@]}" -eq 1 ]]; then
  SERIAL="${ALL_DEVICES[0]}"
  ADB_POLICY='SINGLE_DEVICE_DIRECT_FALLBACK'
else
  adb devices -l
  fail 'ADB_DEVICE_SELECTION_AMBIGUOUS'
fi

echo "ADB_POLICY=$ADB_POLICY"
echo "ADB_SERIAL=$SERIAL"
echo "APP_APK_SHA256=$(sha256sum "$APP_APK" | awk '{print $1}')"
echo "TEST_APK_SHA256=$(sha256sum "$TEST_APK" | awk '{print $1}')"

adb -s "$SERIAL" install -r "$APP_APK" >/dev/null ||
  fail 'DEBUG_APK_INSTALL_FAILED'
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null ||
  fail 'ANDROID_TEST_APK_INSTALL_FAILED'

adb -s "$SERIAL" shell am force-stop "$PACKAGE"
adb -s "$SERIAL" logcat -c

set +e
INSTRUMENTATION_OUTPUT="$(
  adb -s "$SERIAL" shell am instrument -w -r \
    -e class "$TEST_CLASS" \
    "$TEST_PACKAGE/$RUNNER" 2>&1
)"
INSTRUMENTATION_STATUS=$?
set -e

printf '%s\n' "$INSTRUMENTATION_OUTPUT"

LOGCAT_OUTPUT="$(
  adb -s "$SERIAL" logcat -d -v brief \
    -s "$LOG_TAG:I" '*:S' 2>/dev/null || true
)"
printf '%s\n' "$LOGCAT_OUTPUT"

[[ "$INSTRUMENTATION_STATUS" -eq 0 ]] ||
  fail 'ANDROID_INSTRUMENTATION_COMMAND_FAILED'
[[ "$INSTRUMENTATION_OUTPUT" != *'FAILURES!!!'* ]] ||
  fail 'ANDROID_INSTRUMENTATION_ASSERTION_FAILED'
[[ "$INSTRUMENTATION_OUTPUT" != *'INSTRUMENTATION_FAILED'* ]] ||
  fail 'ANDROID_INSTRUMENTATION_RUNNER_FAILED'
[[ "$LOGCAT_OUTPUT" == *'RESULT=SUBSHELL_MEDIA04A1_HTTPS_CACHE_RUNTIME_PASS'* ]] ||
  fail 'MEDIA04A1_RUNTIME_PASS_MARKER_MISSING'

METRICS_LINE="$(
  printf '%s\n' "$LOGCAT_OUTPUT" |
    grep -F 'MEDIA04A1_HTTPS_REQUESTS=' |
    tail -n 1 || true
)"
[[ -n "$METRICS_LINE" ]] || fail 'MEDIA04A1_RUNTIME_METRICS_MISSING'

HTTPS_REQUESTS="$(sed -nE 's/.*MEDIA04A1_HTTPS_REQUESTS=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
FIXTURE_BYTES="$(sed -nE 's/.*FIXTURE_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
CACHE_FILES_BYTES="$(sed -nE 's/.*CACHE_FILES_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
UPSTREAM_BYTES="$(sed -nE 's/.*UPSTREAM_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
CACHED_BYTES="$(sed -nE 's/.*CACHED_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"

[[ "$HTTPS_REQUESTS" =~ ^[0-9]+$ && "$HTTPS_REQUESTS" -gt 0 ]] ||
  fail 'HTTPS_FIXTURE_REQUEST_COUNT_INVALID'
[[ "$FIXTURE_BYTES" =~ ^[0-9]+$ && "$FIXTURE_BYTES" -gt 0 ]] ||
  fail 'HTTPS_FIXTURE_BYTES_INVALID'
[[ "$CACHE_FILES_BYTES" =~ ^[0-9]+$ && "$CACHE_FILES_BYTES" -gt 0 ]] ||
  fail 'CACHE_FILE_BYTES_INVALID'
[[ "$UPSTREAM_BYTES" =~ ^[0-9]+$ && "$UPSTREAM_BYTES" -ge "$FIXTURE_BYTES" ]] ||
  fail 'UPSTREAM_BYTES_PROOF_INVALID'
[[ "$CACHED_BYTES" =~ ^[0-9]+$ && "$CACHED_BYTES" -ge "$FIXTURE_BYTES" ]] ||
  fail 'CACHED_BYTES_PROOF_INVALID'

echo "HTTPS_FIXTURE_REQUESTS=$HTTPS_REQUESTS"
echo "FIXTURE_BYTES=$FIXTURE_BYTES"
echo "CACHE_FILES_BYTES=$CACHE_FILES_BYTES"
echo "UPSTREAM_BYTES=$UPSTREAM_BYTES"
echo "CACHED_BYTES=$CACHED_BYTES"
echo 'HTTPS_COLD_REQUEST=PASS'
echo 'CACHE_WRITE=PASS'
echo 'HTTPS_SERVER_OFFLINE_BEFORE_SECOND_READ=PASS'
echo 'OFFLINE_CACHE_READ=PASS'
echo 'CACHE_HIT_WITHOUT_NEW_UPSTREAM_BYTES=PASS'
echo 'SOURCE_MUTATIONS=NONE'
echo 'GITHUB_MUTATIONS=NONE'
echo 'RESULT=SUBSHELL_MEDIA04A1_HTTPS_CACHE_RUNTIME_PASS'
