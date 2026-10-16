#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH06C_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug'
TEST_PACKAGE='io.github.brealorg.subshell.debug.test'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.data.auth.Auth06cLiveReadOnlyInstrumentedTest'
TEST_METHOD='localhostOAuthIdentityAndBestFeedAreRegisteredAtomicallyInRoom'
CALLBACK_URI='redreader://rr_oauth_redir'
CALLBACK_ALIAS='app.subshell.prototype.auth.AuthRedReaderCompatibilityCallback'
LOG_TAG='subshell-auth06c'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH06C_LOCALHOST_READONLY_RUNTIME_V1\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_TEST_INSTALL_AND_LOCALHOST_HTTPS\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'CALLBACK_URI=%s\n' "$CALLBACK_URI"
printf 'CLIENT_ID_SOURCE=FIXTURE_ONLY_INSIDE_ANDROID_TEST\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'JKS_OR_PASSWORD_ACCESS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'

for cmd in adb sha256sum grep sed tr; do
  command -v "$cmd" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${cmd}"
done

if [[ "${AUTH06C_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:testDebugUnitTest \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline --no-daemon --no-configuration-cache
else
  printf 'AUTH06C_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == device ]] ||
  fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

install_apk() {
  local label="$1" apk="$2" log
  log="$(mktemp "${TMPDIR:-/tmp}/subshell-auth06c-install.XXXXXX")"
  set +e
  adb -s "$SERIAL" install -r -t "$apk" >"$log" 2>&1
  local status=$?
  set -e
  printf '%s_INSTALL_STATUS=%s\n' "$label" "$status"
  sed "s/^/${label}_INSTALL_OUTPUT=/" "$log"
  rm -f "$log"
  [[ "$status" -eq 0 ]] || fail "${label}_APK_INSTALL_FAILED"
  printf '%s_APK_INSTALL=PASS\n' "$label"
}
install_apk APP "$APP_APK"
install_apk TEST "$TEST_APK"

set +e
RESOLVER="$({
  adb -s "$SERIAL" shell cmd package resolve-activity --brief \
    --user current \
    -a android.intent.action.VIEW \
    -c android.intent.category.DEFAULT \
    -c android.intent.category.BROWSABLE \
    -d "$CALLBACK_URI" \
    -p "$APP_PACKAGE"
} 2>&1 | tr -d '\r')"
RESOLVER_STATUS=$?
set -e
printf 'AUTH06C_CALLBACK_RESOLVER_STATUS=%s\n' "$RESOLVER_STATUS"
printf 'AUTH06C_CALLBACK_RESOLVER=%s\n' "$RESOLVER"
[[ "$RESOLVER_STATUS" -eq 0 ]] || fail CALLBACK_RESOLVER_FAILED
grep -Fq "$APP_PACKAGE/" <<<"$RESOLVER" || fail CALLBACK_PACKAGE_MISMATCH
grep -Fq "$CALLBACK_ALIAS" <<<"$RESOLVER" || fail CALLBACK_ALIAS_MISMATCH
printf 'REDREADER_CUSTOM_SCHEME_ALIAS_RESOLUTION=PASS\n'

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#$TEST_METHOD" \
  "$RUNNER"

RUNTIME_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S' | tr -d '\r')"
printf '%s\n' "$RUNTIME_LOG"

for marker in \
  'READ_ONLY_SCOPES_EXACT=true' \
  'TOKEN_EXCHANGE=true' \
  'IDENTITY_RESOLVED=true' \
  'BEST_LIMIT_10=true' \
  'ROOM_ITEMS=2' \
  'ACCOUNT_REGISTERED=true' \
  'PROCESS_REOPEN=true' \
  'USER_AGENT_EXACT=true' \
  'HTTPS_REQUESTS=3' \
  'RESULT=SUBSHELL_AUTH06C_LOCALHOST_READONLY_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$RUNTIME_LOG" ||
    fail "RUNTIME_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'AUTH06C_EXACT_IDENTITY_READ_SCOPES=PASS\n'
printf 'AUTH06C_TOKEN_IDENTITY_AND_BEST_SEQUENCE=PASS\n'
printf 'AUTH06C_BEST_LIMIT_TEN=PASS\n'
printf 'AUTH06C_ROOM_WRITE_BEFORE_ACCOUNT_REGISTRATION=PASS\n'
printf 'AUTH06C_PROCESS_REOPEN=PASS\n'
printf 'AUTH06C_LOCALHOST_ONLY=PASS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'RESULT=SUBSHELL_AUTH06C_LOCALHOST_READONLY_RUNTIME_PASS\n'
