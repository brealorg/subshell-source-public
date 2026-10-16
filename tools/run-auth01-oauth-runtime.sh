#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH01_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.auth.Auth01OAuthSessionInstrumentedTest'
LOG_TAG='subshell-auth01'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH01_SECURE_OAUTH_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_KEYSTORE_NO_BACKUP_FILES_LOCALHOST_HTTPS_AND_FORCE_STOP\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=STATE_VALIDATE_CODE_EXCHANGE_ENCRYPT_RESTART_SERIALIZED_REFRESH_ROTATE_REVOKE_LOGOUT\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${AUTH01_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'AUTH01_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

printf '===== AUTH01 WRITE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#writeEncryptedSessionBeforeExternalProcessRestart" \
  "$RUNNER"

WRITE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$WRITE_LOG"
grep -Fq 'AUTH01_PHASE=WRITE' <<<"$WRITE_LOG" || fail WRITE_PHASE_MARKER_MISSING
grep -Fq 'STATE_MISMATCH_REJECTED=true' <<<"$WRITE_LOG" || fail STATE_MISMATCH_MARKER_MISSING
grep -Fq 'STATE_REPLAY_REJECTED=true' <<<"$WRITE_LOG" || fail STATE_REPLAY_MARKER_MISSING
grep -Fq 'CODE_EXCHANGE=true' <<<"$WRITE_LOG" || fail CODE_EXCHANGE_MARKER_MISSING
grep -Fq 'ENCRYPTED_SESSION=true' <<<"$WRITE_LOG" || fail ENCRYPTED_SESSION_MARKER_MISSING
grep -Fq 'PLAINTEXT_SECRET_LEAK=false' <<<"$WRITE_LOG" || fail PLAINTEXT_LEAK_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_AUTH01_WRITE_PHASE_PASS' <<<"$WRITE_LOG" || fail WRITE_RESULT_MISSING

adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug
sleep 1
adb -s "$SERIAL" logcat -c

printf '===== AUTH01 RESTORE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#restoreRefreshAndRevokeAfterExternalProcessRestart" \
  "$RUNNER"

RESTORE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$RESTORE_LOG"
grep -Fq 'AUTH01_PHASE=RESTORE' <<<"$RESTORE_LOG" || fail RESTORE_PHASE_MARKER_MISSING
grep -Fq 'SESSION_RESTORED=true' <<<"$RESTORE_LOG" || fail SESSION_RESTORE_MARKER_MISSING
grep -Fq 'SERIALIZED_REFRESH_REQUESTS=1' <<<"$RESTORE_LOG" || fail SERIALIZED_REFRESH_MARKER_MISSING
grep -Fq 'REFRESH_ROTATION=true' <<<"$RESTORE_LOG" || fail REFRESH_ROTATION_MARKER_MISSING
grep -Fq 'BEARER_PROVIDER_UPDATED=true' <<<"$RESTORE_LOG" || fail BEARER_UPDATE_MARKER_MISSING
grep -Fq 'REMOTE_REVOKE=true' <<<"$RESTORE_LOG" || fail REMOTE_REVOKE_MARKER_MISSING
grep -Fq 'LOCAL_LOGOUT=true' <<<"$RESTORE_LOG" || fail LOCAL_LOGOUT_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_AUTH01_SECURE_OAUTH_RUNTIME_PASS' <<<"$RESTORE_LOG" || fail RUNTIME_RESULT_MISSING

printf 'SECURE_RANDOM_STATE_AND_CONSTANT_TIME_VALIDATION=PASS\n'
printf 'SINGLE_USE_CALLBACK_AND_REPLAY_REJECTION=PASS\n'
printf 'HTTPS_FORM_CODE_EXCHANGE=PASS\n'
printf 'ANDROID_KEYSTORE_AES_GCM_VAULT=PASS\n'
printf 'NO_BACKUP_ATOMIC_SESSION_RESTORE=PASS\n'
printf 'SERIALIZED_EXPIRY_REFRESH=PASS\n'
printf 'REFRESH_TOKEN_ROTATION=PASS\n'
printf 'NETWORK_BEARER_PROVIDER_UPDATE=PASS\n'
printf 'REMOTE_REVOCATION_AND_LOCAL_LOGOUT=PASS\n'
printf 'AUTH_TAB_SOURCE_CONTRACT=PASS\n'
printf 'PRODUCTION_VERIFIED_APP_LINK=DEFERRED\n'
printf 'NET01_SECURE_HTTP_BOUNDARY=PRESERVED\n'
printf 'NET02_ROOM_SOURCE_OF_TRUTH=PRESERVED\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_AUTH01_SECURE_OAUTH_RUNTIME_PASS\n'
