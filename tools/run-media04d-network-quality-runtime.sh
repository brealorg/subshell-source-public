#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE='io.github.brealorg.subshell.debug'
TEST_PACKAGE='io.github.brealorg.subshell.debug.test'
RUNNER='androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.media.VideoNetworkQualityProfilesInstrumentedTest'
LOG_TAG='subshell-network'

fail() {
  printf 'FAIL=%s\n' "$1"
  printf 'SOURCE_MUTATIONS=NONE\n'
  printf 'GITHUB_MUTATIONS=NONE\n'
  printf 'RESULT=SUBSHELL_MEDIA04D_NETWORK_QUALITY_RUNTIME_FAILED\n'
  exit 1
}

cd "$ROOT"

grep -Fq \
  'SUBSHELL_MEDIA04D_NETWORK_QUALITY_PROFILES_V1' \
  app/build.gradle.kts ||
  fail 'MEDIA04D_IMPLEMENTATION_MARKER_MISSING'

echo 'APPROVED_OPERATION=RUN_SUBSHELL_MEDIA04D_NETWORK_QUALITY_RUNTIME'
echo 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL'
echo "SOURCE_ROOT=$ROOT"
echo 'NETWORK_OBSERVER=ANDROID_DEFAULT_NETWORK_CALLBACK'
echo 'QUALITY_PROFILES=UNMETERED_AND_METERED'
echo 'TEST_SEQUENCE=REAL_INITIAL_SNAPSHOT_AND_DETERMINISTIC_PROFILE_MATRIX'
echo 'DEVICE_NETWORK_MUTATIONS=NONE'

if [[ "${MEDIA04D_SKIP_BUILD:-0}" != '1' ]]; then
  ./gradlew \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon
else
  echo 'MEDIA04D_BUILD=SKIPPED_BY_VERIFIED_CALLER'
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
[[ "$LOGCAT_OUTPUT" == *'RESULT=SUBSHELL_MEDIA04D_NETWORK_QUALITY_RUNTIME_PASS'* ]] ||
  fail 'MEDIA04D_RUNTIME_PASS_MARKER_MISSING'

METRICS_LINE="$(
  printf '%s\n' "$LOGCAT_OUTPUT" |
    grep -F 'MEDIA04D_NETWORK_CLASS=' |
    tail -n 1 || true
)"
[[ -n "$METRICS_LINE" ]] || fail 'MEDIA04D_RUNTIME_METRICS_MISSING'

NETWORK_CLASS="$(sed -nE 's/.*MEDIA04D_NETWORK_CLASS=([A-Z]+).*/\1/p' <<<"$METRICS_LINE")"
UNMETERED_QUALITY="$(sed -nE 's/.*UNMETERED_QUALITY=([A-Z]+).*/\1/p' <<<"$METRICS_LINE")"
METERED_QUALITY="$(sed -nE 's/.*METERED_QUALITY=([A-Z]+).*/\1/p' <<<"$METRICS_LINE")"
EFFECTIVE_QUALITY="$(sed -nE 's/.*EFFECTIVE_QUALITY=([A-Z]+).*/\1/p' <<<"$METRICS_LINE")"

[[ "$NETWORK_CLASS" =~ ^(OFFLINE|METERED|UNMETERED)$ ]] ||
  fail 'NETWORK_CLASS_INVALID'
[[ "$UNMETERED_QUALITY" =~ ^(LOW|MEDIUM|HIGH)$ ]] ||
  fail 'UNMETERED_QUALITY_INVALID'
[[ "$METERED_QUALITY" =~ ^(LOW|MEDIUM|HIGH)$ ]] ||
  fail 'METERED_QUALITY_INVALID'
[[ "$EFFECTIVE_QUALITY" =~ ^(LOW|MEDIUM|HIGH)$ ]] ||
  fail 'EFFECTIVE_QUALITY_INVALID'

echo "DEVICE_NETWORK_CLASS=$NETWORK_CLASS"
echo "UNMETERED_PROFILE=$UNMETERED_QUALITY"
echo "METERED_PROFILE=$METERED_QUALITY"
echo "DEVICE_EFFECTIVE_PROFILE=$EFFECTIVE_QUALITY"
echo 'OFFLINE_PRESERVES_ACTIVE_PROFILE=PASS'
echo 'LOCAL_MEDIA_NETWORK_INDEPENDENCE=PASS'
echo 'MANUAL_TRACK_OVERRIDE_PRECEDENCE=SOURCE_CONTRACT_PASS'
echo 'DEVICE_NETWORK_MUTATIONS=NONE'
echo 'SOURCE_MUTATIONS=NONE'
echo 'GITHUB_MUTATIONS=NONE'
echo 'RESULT=SUBSHELL_MEDIA04D_NETWORK_QUALITY_RUNTIME_PASS'
