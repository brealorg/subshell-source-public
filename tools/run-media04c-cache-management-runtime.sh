#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE='io.github.brealorg.subshell.debug'
TEST_PACKAGE='io.github.brealorg.subshell.debug.test'
RUNNER='androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.media.VideoMediaCacheClearInstrumentedTest'
LOG_TAG='subshell-cache'

fail() {
  printf 'FAIL=%s\n' "$1"
  printf 'SOURCE_MUTATIONS=NONE\n'
  printf 'GITHUB_MUTATIONS=NONE\n'
  printf 'RESULT=SUBSHELL_MEDIA04C_CACHE_MANAGEMENT_RUNTIME_FAILED\n'
  exit 1
}

cd "$ROOT"

grep -Fq \
  'SUBSHELL_MEDIA04C_CACHE_OBSERVABILITY_AND_CLEAR_V1' \
  app/build.gradle.kts ||
  fail 'MEDIA04C_IMPLEMENTATION_MARKER_MISSING'

echo 'APPROVED_OPERATION=RUN_SUBSHELL_MEDIA04C_CACHE_MANAGEMENT_RUNTIME'
echo 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_MEDIA_CACHE_RESET'
echo "SOURCE_ROOT=$ROOT"
echo 'CACHE_SCOPE=DEBUG_APP_BOUNDED_MEDIA_CACHE_ONLY'
echo 'TEST_SEQUENCE=HTTPS_SEED_STATUS_CLEAR_ZERO_AND_REFILL'
echo 'PLAYER_PRELOADER_RELEASE=ABSENT'
echo 'REMOTE_PROVIDER_CONTACT=NONE'
echo 'DEVICE_NETWORK_MUTATIONS=NONE'

if [[ "${MEDIA04C_SKIP_BUILD:-0}" != '1' ]]; then
  ./gradlew \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon
else
  echo 'MEDIA04C_BUILD=SKIPPED_BY_VERIFIED_CALLER'
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
[[ "$LOGCAT_OUTPUT" == *'RESULT=SUBSHELL_MEDIA04C_CACHE_CLEAR_RUNTIME_PASS'* ]] ||
  fail 'MEDIA04C_RUNTIME_PASS_MARKER_MISSING'

METRICS_LINE="$(
  printf '%s\n' "$LOGCAT_OUTPUT" |
    grep -F 'MEDIA04C_BEFORE_BYTES=' |
    tail -n 1 || true
)"
[[ -n "$METRICS_LINE" ]] || fail 'MEDIA04C_RUNTIME_METRICS_MISSING'

BEFORE_BYTES="$(sed -nE 's/.*MEDIA04C_BEFORE_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
AFTER_BYTES="$(sed -nE 's/.*AFTER_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
REMOVED_RESOURCES="$(sed -nE 's/.*REMOVED_RESOURCES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
REMOVED_BYTES="$(sed -nE 's/.*REMOVED_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
REFILL_BYTES="$(sed -nE 's/.*REFILL_BYTES=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"
HTTPS_REQUESTS="$(sed -nE 's/.*HTTPS_REQUESTS=([0-9]+).*/\1/p' <<<"$METRICS_LINE")"

[[ "$BEFORE_BYTES" =~ ^[0-9]+$ && "$BEFORE_BYTES" -gt 0 ]] ||
  fail 'CACHE_BEFORE_BYTES_INVALID'
[[ "$AFTER_BYTES" == '0' ]] || fail 'CACHE_AFTER_CLEAR_NOT_ZERO'
[[ "$REMOVED_RESOURCES" =~ ^[0-9]+$ && "$REMOVED_RESOURCES" -gt 0 ]] ||
  fail 'CACHE_REMOVED_RESOURCES_INVALID'
[[ "$REMOVED_BYTES" =~ ^[0-9]+$ && "$REMOVED_BYTES" -ge "$BEFORE_BYTES" ]] ||
  fail 'CACHE_REMOVED_BYTES_INVALID'
[[ "$REFILL_BYTES" =~ ^[0-9]+$ && "$REFILL_BYTES" -gt 0 ]] ||
  fail 'CACHE_REFILL_BYTES_INVALID'
[[ "$HTTPS_REQUESTS" =~ ^[0-9]+$ && "$HTTPS_REQUESTS" -ge 2 ]] ||
  fail 'CACHE_REFILL_UPSTREAM_REQUEST_INVALID'

echo "CACHE_BEFORE_BYTES=$BEFORE_BYTES"
echo "CACHE_AFTER_CLEAR_BYTES=$AFTER_BYTES"
echo "CACHE_REMOVED_RESOURCES=$REMOVED_RESOURCES"
echo "CACHE_REMOVED_BYTES=$REMOVED_BYTES"
echo "CACHE_REFILL_BYTES=$REFILL_BYTES"
echo "HTTPS_FIXTURE_REQUESTS=$HTTPS_REQUESTS"
echo 'CACHE_STATUS_OBSERVABILITY=PASS'
echo 'CACHE_CLEAR=PASS'
echo 'CACHE_ZERO_AFTER_CLEAR=PASS'
echo 'PROCESS_LOCAL_CACHE_REUSABLE_AFTER_CLEAR=PASS'
echo 'PLAYER_PRELOADER_RELEASE=ABSENT'
echo 'DEVICE_NETWORK_MUTATIONS=NONE'
echo 'SOURCE_MUTATIONS=NONE'
echo 'GITHUB_MUTATIONS=NONE'
echo 'RESULT=SUBSHELL_MEDIA04C_CACHE_MANAGEMENT_RUNTIME_PASS'
