#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${WRITE01A1_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug.dev2'
TEST_PACKAGE='io.github.brealorg.subshell.debug.dev2.test'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.data.auth.Write01aCommentWriteAuthInstrumentedTest'
LOG_TAG='subshell-write01a1'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_WRITE01A1_COMMENT_WRITE_AUTH_LOCALHOST_RUNTIME\n'
printf 'RISK=R1_TEST_ONLY_CACHE_STORAGE_KEYSTORE_LOCALHOST_HTTPS_AND_DEBUG_APK_INSTALL\n'
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'TEST_PACKAGE=%s\n' "$TEST_PACKAGE"
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_COMMENT_WRITES=NONE\n'
printf 'TARGET_APP_AUTH_DATA_MUTATIONS=NONE\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${WRITE01A1_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew"     :app:assembleDebug     :app:assembleDebugAndroidTest     -Psubshell.debugSlot=dev2     --offline --no-daemon --no-configuration-cache
else
  printf 'WRITE01A1_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] || fail DEVICE_NOT_READY

cleanup_test_cache() {
  adb -s "$SERIAL" shell run-as "$APP_PACKAGE" rm -rf     cache/subshell-write01a1-auth-test-positive     cache/subshell-write01a1-auth-test-negative     >/dev/null 2>&1 || true
}
trap cleanup_test_cache EXIT

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null
cleanup_test_cache
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r -e class "$TEST_CLASS" "$RUNNER"

LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG"
for marker in   'WRITE01A1_SCOPE=identity_read_submit'   'DEFAULT_READ_ONLY_PRESERVED=true'   'SCOPE_BOUND_CALLBACK=true'   'ACCOUNT_PINNED=true'   'UPGRADE_ATOMIC=true'   'TARGET_APP_AUTH_VAULT_TOUCHED=false'   'TEST_CACHE_STORAGE_CLEANED=true'   'LIVE_REDDIT_NETWORK=false'   'RESULT=SUBSHELL_WRITE01A1_COMMENT_WRITE_AUTH_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$LOG" || fail "RUNTIME_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'EXPLICIT_COMMENT_WRITE_GRANT=PASS\n'
printf 'READ_ONLY_DEFAULT_GRANT=PRESERVED\n'
printf 'SCOPE_AND_ACCOUNT_BOUND_CALLBACK=PASS\n'
printf 'PINNED_ACCOUNT_TOKEN_RESOLUTION=PASS\n'
cleanup_test_cache
trap - EXIT
printf 'ATOMIC_BOUND_SESSION_UPGRADE=PASS\n'
printf 'TARGET_APP_AUTH_VAULT_TOUCHED=NO\n'
printf 'ISOLATED_TEST_CACHE_STORAGE=PASS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'REMOTE_COMMENT_WRITES=NONE\n'
printf 'RESULT=SUBSHELL_WRITE01A1_COMMENT_WRITE_AUTH_RUNTIME_PASS\n'
