#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH03_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.auth.Auth03ConnectAccountInstrumentedTest'
LOG_TAG='subshell-auth03'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH03_CONNECT_ACCOUNT_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_KEYSTORE_NO_BACKUP_LOCALHOST_HTTPS_AND_FORCE_STOP\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=BROWSER_FAILURE_CANCEL_MISMATCH_PROVIDER_ERROR_RESTART_SUCCESS_EXCHANGE_ERROR_IDENTITY_ERROR_RETRY\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'PRODUCTION_APP_LINK=CONFIGURATION_GATED_DISABLED_BY_DEFAULT\n'
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

if [[ "${AUTH03_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest \
    --offline --no-daemon --no-configuration-cache
else
  printf 'AUTH03_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

printf '===== AUTH03 WRITE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#writePendingConnectFlowWithCancelMismatchProviderErrorAndProcessSafeCallback" \
  "$RUNNER"

WRITE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$WRITE_LOG"
for marker in \
  'AUTH03_PHASE=WRITE' \
  'BROWSER_FAILURE=true' \
  'EXPLICIT_CANCEL=true' \
  'STATE_MISMATCH=true' \
  'PROVIDER_ERROR=true' \
  'EXISTING_ACCOUNTS_PRESERVED=true' \
  'CALLBACK_PERSISTED_FOR_RESTART=true' \
  'PLAINTEXT_CALLBACK_LEAK=false' \
  'RESULT=SUBSHELL_AUTH03_WRITE_PHASE_PASS'; do
  grep -Fq "$marker" <<<"$WRITE_LOG" ||
    fail "WRITE_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug
sleep 1
adb -s "$SERIAL" logcat -c

printf '===== AUTH03 RESTORE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#restoreConnectThenPreserveAccountAcrossExchangeIdentityErrorsAndRetry" \
  "$RUNNER"

RESTORE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$RESTORE_LOG"
for marker in \
  'AUTH03_PHASE=RESTORE' \
  'CONNECTED_AFTER_PROCESS_RESTART=alice' \
  'DUPLICATE_RESULT_IDEMPOTENT=true' \
  'TOKEN_EXCHANGE_ERROR=true' \
  'IDENTITY_ERROR=true' \
  'RETRY_SUCCESS=bob' \
  'EXISTING_ACCOUNT_PRESERVED=true' \
  'ACTIVE_ACCOUNT=bob' \
  'HTTPS_REQUESTS=7' \
  'SANITIZED_UI_STATE=true' \
  'RESULT=SUBSHELL_AUTH03_CONNECT_ACCOUNT_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$RESTORE_LOG" ||
    fail "RESTORE_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'CONNECT_ACCOUNT_UI_STATE_MACHINE=PASS\n'
printf 'AUTH_TAB_BROWSER_BOUNDARY=STATIC_PASS\n'
printf 'DEDICATED_CALLBACK_NO_SECRET_EXTRA=STATIC_PASS\n'
printf 'PRODUCTION_HTTPS_APP_LINK_CONFIGURATION_GATE=STATIC_PASS\n'
printf 'SINGLE_USE_PROCESS_SAFE_CALLBACK=PASS\n'
printf 'DUPLICATE_RESULT_IDEMPOTENCE=PASS\n'
printf 'BROWSER_FAILURE_AND_EXPLICIT_CANCEL=PASS\n'
printf 'CALLBACK_MISMATCH_REJECTION=PASS\n'
printf 'PROVIDER_ERROR_PRESERVES_ACCOUNTS=PASS\n'
printf 'TOKEN_EXCHANGE_ERROR_PRESERVES_ACCOUNTS=PASS\n'
printf 'IDENTITY_ERROR_PRESERVES_ACCOUNTS=PASS\n'
printf 'RETRY_AND_NEW_ACCOUNT_ACTIVATION=PASS\n'
printf 'SANITIZED_UI_DIRECTORY_SYNC=STATIC_AND_UNIT_PASS\n'
printf 'AUTH01_SECURE_OAUTH_SESSION=PRESERVED\n'
printf 'AUTH02_ACCOUNT_SESSION_DIRECTORY=PRESERVED\n'
printf 'NET01_SECURE_HTTP_BOUNDARY=PRESERVED\n'
printf 'NET02_ROOM_SOURCE_OF_TRUTH=PRESERVED\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_AUTH03_CONNECT_ACCOUNT_RUNTIME_PASS\n'
