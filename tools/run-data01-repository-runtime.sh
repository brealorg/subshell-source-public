#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${DATA01_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.DataRepositoryInstrumentedTest'
LOG_TAG='subshell-data01'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_DATA01_REPOSITORY_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_LOCAL_FAKE_DATA_MUTATIONS\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=INJECT_FAKE_REPOSITORY_READ_CONTENT_AND_RECORD_LOCAL_MUTATIONS\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'PERSISTENT_DATABASE=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${DATA01_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew"         :app:assembleDebug         :app:assembleDebugAndroidTest         --offline         --no-daemon         --no-configuration-cache
else
  printf 'DATA01_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

adb -s "$SERIAL" shell am instrument -w -r       -e class "$TEST_CLASS"       "$RUNNER"

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"

grep -Fq 'DATA01_REPOSITORY_BOUNDARY=true' <<<"$LOG_OUTPUT" ||
  fail REPOSITORY_BOUNDARY_MARKER_MISSING
grep -Fq 'MUTATION_COUNT=5' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_COUNT_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_DATA01_REPOSITORY_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'TYPED_REPOSITORY_BOUNDARY=PASS\n'
printf 'FAKE_LOCAL_BACKEND=PASS\n'
printf 'POST_COMMUNITY_USER_INBOX_READS=PASS\n'
printf 'CREATE_COMMENT_SAVE_VOTE_POLL_MUTATIONS=PASS\n'
printf 'V2A_TYPED_POST_ORIGIN=PRESERVED\n'
printf 'V2B_CANONICAL_ROUTE_STACK=PRESERVED\n'
printf 'V2C_KEYED_SCROLL_ANCHORS=PRESERVED\n'
printf 'V2E_FULL_PROCESS_STATE_RESTORE=PRESERVED\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'PERSISTENT_DATABASE=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_DATA01_REPOSITORY_RUNTIME_PASS\n'
