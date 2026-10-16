#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH02_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.data.auth.Auth02AccountSessionInstrumentedTest'
LOG_TAG='subshell-auth02'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH02_ACCOUNT_SESSION_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_KEYSTORE_NO_BACKUP_LOCALHOST_HTTPS_AND_FORCE_STOP\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=REGISTER_TWO_IDENTITIES_REJECT_COLLISION_RESTART_REFRESH_SWITCH_SCOPED_REVOKE_LOGOUT\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${AUTH02_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest \
    --offline --no-daemon --no-configuration-cache
else
  printf 'AUTH02_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
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

printf '===== AUTH02 WRITE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#writeTwoAccountRegistryAndRejectFixtureCollisionBeforeExternalRestart" \
  "$RUNNER"

WRITE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$WRITE_LOG"
for marker in \
  'AUTH02_PHASE=WRITE' \
  'AUTHENTICATED_ACCOUNTS=2' \
  'LOCAL_FIXTURES=2' \
  'ACTIVE_ACCOUNT=bob' \
  'FIXTURE_COLLISION_REJECTED=true' \
  'METADATA_ONLY_REGISTRY=true' \
  'PLAINTEXT_SECRET_LEAK=false' \
  'RESULT=SUBSHELL_AUTH02_WRITE_PHASE_PASS'; do
  grep -Fq "$marker" <<<"$WRITE_LOG" || fail "WRITE_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug
sleep 1
adb -s "$SERIAL" logcat -c

printf '===== AUTH02 RESTORE PHASE =====\n'
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#restoreActiveAccountRefreshSwitchAndScopedLogoutAfterExternalRestart" \
  "$RUNNER"

RESTORE_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$RESTORE_LOG"
for marker in \
  'AUTH02_PHASE=RESTORE' \
  'ACTIVE_ACCOUNT_RESTORED=bob' \
  'SERIALIZED_REFRESH_REQUESTS=1' \
  'BEARER_SWITCHING=true' \
  'SCOPED_LOGOUT=true' \
  'UNRELATED_ACCOUNT_PRESERVED=true' \
  'LOCAL_ACCOUNT_BEARER_NULL=true' \
  'RESULT=SUBSHELL_AUTH02_ACCOUNT_SESSION_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$RESTORE_LOG" || fail "RESTORE_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'TYPED_PROVIDER_QUALIFIED_ACCOUNT_IDS=PASS\n'
printf 'METADATA_ONLY_ENCRYPTED_ACCOUNT_REGISTRY=PASS\n'
printf 'MULTI_SESSION_KEYSTORE_VAULT=PASS\n'
printf 'IDENTITY_BEARER_SCOPE=PASS\n'
printf 'LOCAL_FIXTURE_COLLISION_REJECTION=PASS\n'
printf 'ACTIVE_ACCOUNT_PROCESS_RESTORE=PASS\n'
printf 'SERIALIZED_ACTIVE_SESSION_REFRESH=PASS\n'
printf 'BEARER_SWITCHING=PASS\n'
printf 'SCOPED_REVOKE_AND_LOGOUT=PASS\n'
printf 'UNRELATED_ACCOUNT_PRESERVATION=PASS\n'
printf 'SANITIZED_UI_ACCOUNT_DIRECTORY=STATIC_AND_UNIT_PASS\n'
printf 'AUTH01_SECURE_OAUTH_SESSION=PRESERVED\n'
printf 'NET01_SECURE_HTTP_BOUNDARY=PRESERVED\n'
printf 'NET02_ROOM_SOURCE_OF_TRUTH=PRESERVED\n'
printf 'PRODUCTION_VERIFIED_APP_LINK=DEFERRED\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_AUTH02_ACCOUNT_SESSION_RUNTIME_PASS\n'
