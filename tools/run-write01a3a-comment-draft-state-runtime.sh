#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${WRITE01A3A_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug.dev2'
TEST_PACKAGE='io.github.brealorg.subshell.debug.dev2.test'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.ui.Write01a3aCommentDraftStateInstrumentedTest'
LOG_TAG='subshell-write01a3a'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_WRITE01A3A_COMMENT_DRAFT_STATE_RUNTIME\n'
printf 'RISK=R1_LOCAL_STATE_TEST_APK_AND_DEBUG_APK_INSTALL\n'
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'TEST_PACKAGE=%s\n' "$TEST_PACKAGE"
printf 'APP_UI_LANGUAGE=ENGLISH\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'REMOTE_COMMENT_WRITES=NONE\n'
printf 'TARGET_APP_AUTH_DATA_MUTATIONS=NONE\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${WRITE01A3A_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew"     :app:assembleDebug     :app:assembleDebugAndroidTest     -Psubshell.debugSlot=dev2     --offline --no-daemon --no-configuration-cache
else
  printf 'WRITE01A3A_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] ||
  fail DEVICE_NOT_READY

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument   -w -r   -e class "$TEST_CLASS"   "$RUNNER"

LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG"

for marker in   'WRITE01A3A_TYPED_TARGETS=true'   'DRAFT_OWNER=SUBSHELL_STATE'   'PROCESS_RESTORE=true'   'RETARGET_GUARD=true'   'MEDIA_PRESERVED=true'   'APP_UI_LANGUAGE=ENGLISH'   'LIVE_REDDIT_NETWORK=false'   'REMOTE_COMMENT_WRITES=false'   'RESULT=SUBSHELL_WRITE01A3A_COMMENT_DRAFT_STATE_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$LOG" ||
    fail "RUNTIME_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'TYPED_T1_T3_TARGETS=PASS\n'
printf 'COMMENT_DRAFT_OWNER=SUBSHELL_STATE\n'
printf 'CONFIGURATION_AND_PROCESS_RESTORE=PASS\n'
printf 'UNSENT_DRAFT_RETARGET_GUARD=PASS\n'
printf 'MEDIA_ATTACHMENT_METADATA_PRESERVED=PASS\n'
printf 'APP_UI_LANGUAGE=ENGLISH\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'REMOTE_COMMENT_WRITES=NONE\n'
printf 'RESULT=SUBSHELL_WRITE01A3A_COMMENT_DRAFT_STATE_RUNTIME_PASS\n'
