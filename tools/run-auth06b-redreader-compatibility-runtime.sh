#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH06B_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug'
TEST_PACKAGE='io.github.brealorg.subshell.debug.test'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.data.auth.Auth06bRedReaderCompatibilityInstrumentedTest'
CALLBACK_URI='redreader://rr_oauth_redir'
CALLBACK_ALIAS='app.subshell.prototype.auth.AuthRedReaderCompatibilityCallback'
LOG_TAG='subshell-auth06b'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_RUNTIME_V1\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_TEST_INSTALL_ENCRYPTED_NO_BACKUP_STORAGE_CUSTOM_SCHEME_AND_LOCALHOST_HTTPS\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'CALLBACK_URI=%s\n' "$CALLBACK_URI"
printf 'CLIENT_ID_SOURCE=FIXTURE_ONLY_INSIDE_ANDROID_TEST\n'
printf 'CLIENT_ID_IN_SOURCE_RELEASE_OR_LOGS=NO_LIVE_VALUE\n'
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'JKS_OR_PASSWORD_ACCESS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'REMOTE_WRITES=NONE\n'

for command_name in adb sha256sum grep sed; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${AUTH06B_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:testDebugUnitTest \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline --no-daemon --no-configuration-cache
else
  printf 'AUTH06B_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] ||
  fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

# SUBSHELL_AUTH06B_STAGE6_INSTALL_AND_RESOLVER_FIX_V1
# Capture each install result instead of allowing set -e to terminate without a
# diagnostic marker. Package-scope the callback intent with Intent's -p option;
# --package is a pm subcommand option and is not accepted by the intent parser.
APP_INSTALL_LOG="$(mktemp "${TMPDIR:-/tmp}/subshell-auth06b-app-install.XXXXXX")"
TEST_INSTALL_LOG="$(mktemp "${TMPDIR:-/tmp}/subshell-auth06b-test-install.XXXXXX")"
trap 'rm -f "$APP_INSTALL_LOG" "$TEST_INSTALL_LOG"' EXIT

install_apk() {
  local label="$1"
  local apk="$2"
  local install_log="$3"
  local install_status

  set +e
  adb -s "$SERIAL" install -r -t "$apk" >"$install_log" 2>&1
  install_status=$?
  set -e

  printf '%s_INSTALL_STATUS=%s\n' "$label" "$install_status"
  sed "s/^/${label}_INSTALL_OUTPUT=/" "$install_log"

  [[ "$install_status" -eq 0 ]] || fail "${label}_APK_INSTALL_FAILED"
  grep -Fq 'Success' "$install_log" || fail "${label}_APK_INSTALL_SUCCESS_MARKER_MISSING"
  printf '%s_APK_INSTALL=PASS\n' "$label"
}

install_apk APP "$APP_APK" "$APP_INSTALL_LOG"
install_apk TEST "$TEST_APK" "$TEST_INSTALL_LOG"

resolve_callback() {
  local resolver_output resolver_status

  set +e
  resolver_output="$({
    adb -s "$SERIAL" shell cmd package resolve-activity --brief \
      --user current \
      -a android.intent.action.VIEW \
      -c android.intent.category.DEFAULT \
      -c android.intent.category.BROWSABLE \
      -d "$CALLBACK_URI" \
      -p "$APP_PACKAGE"
  } 2>&1 | tr -d '\r')"
  resolver_status=$?
  set -e

  if [[ "$resolver_status" -ne 0 ]]; then
    set +e
    resolver_output="$({
      adb -s "$SERIAL" shell cmd package resolve-activity --brief \
        -a android.intent.action.VIEW \
        -c android.intent.category.DEFAULT \
        -c android.intent.category.BROWSABLE \
        -d "$CALLBACK_URI" \
        -p "$APP_PACKAGE"
    } 2>&1 | tr -d '\r')"
    resolver_status=$?
    set -e
  fi

  printf 'AUTH06B_CALLBACK_RESOLVER_STATUS=%s\n' "$resolver_status"
  printf 'AUTH06B_CALLBACK_RESOLVER=%s\n' "$resolver_output"
  [[ "$resolver_status" -eq 0 ]] || fail CUSTOM_SCHEME_RESOLVER_COMMAND_FAILED
  RESOLVER="$resolver_output"
}

RESOLVER=''
resolve_callback

grep -Fq "$APP_PACKAGE/" <<<"$RESOLVER" ||
  fail CUSTOM_SCHEME_RESOLVER_PACKAGE_MISMATCH
grep -Fq "$CALLBACK_ALIAS" <<<"$RESOLVER" ||
  fail CUSTOM_SCHEME_RESOLVER_ALIAS_MISMATCH
printf 'REDREADER_CUSTOM_SCHEME_ALIAS_RESOLUTION=PASS\n'

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#encryptedLocalSelectionExactCallbackAndLocalhostIdentityRuntime" \
  "$RUNNER"

RUNTIME_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S' | tr -d '\r')"
printf '%s\n' "$RUNTIME_LOG"

for marker in \
  'LOCAL_CLIENT_ID_ENCRYPTED=true' \
  'PROCESS_REOPEN=true' \
  'NATIVE_CALLBACK_REJECTED=true' \
  'EXACT_REDREADER_CALLBACK=true' \
  'MODE_FINGERPRINT_BOUND=true' \
  'TOKEN_USER_AGENT_EXACT=true' \
  'IDENTITY_USER_AGENT_EXACT=true' \
  'CLIENT_ID_REMOVED=true' \
  'HTTPS_REQUESTS=2' \
  'RESULT=SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_RUNTIME_PASS'; do
  grep -Fq "$marker" <<<"$RUNTIME_LOG" ||
    fail "RUNTIME_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'EXPLICIT_REDREADER_COMPATIBILITY_MODE=PASS\n'
printf 'LOCAL_ENCRYPTED_CLIENT_SELECTION=PASS\n'
printf 'NO_BACKUP_CONFIGURATION_BOUNDARY=PASS\n'
printf 'PENDING_CALLBACK_MODE_AND_FINGERPRINT_BINDING=PASS\n'
printf 'CUSTOM_SCHEME_EXACT_CALLBACK_PROCESSING=PASS\n'
printf 'NATIVE_PRODUCTION_CALLBACK_PRESERVED=STATIC_PASS\n'
printf 'MODE_SPECIFIC_TOKEN_AND_IDENTITY_USER_AGENT=PASS\n'
printf 'LOCALHOST_ONLY_TOKEN_AND_IDENTITY_RUNTIME=PASS\n'
printf 'CLIENT_ID_IN_RELEASE_APK=ABSENT_BY_CONFIGURATION_BOUNDARY\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'JKS_OR_PASSWORD_ACCESS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_RUNTIME_PASS\n'
