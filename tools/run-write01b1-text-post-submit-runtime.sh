#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${WRITE01B1_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug.dev2'
TEST_PACKAGE='io.github.brealorg.subshell.debug.dev2.test'
EXPECTED_VERSION_NAME='0.65.5-write01b1-text-post-submit-transport-dev'
EXPECTED_VERSION_CODE='91'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.data.auth.reddit.Write01b1TextPostSubmitTransportInstrumentedTest'
LOG_TAG='subshell-write01b1'
TEST_PACKAGE_INSTALLED=0

fail() {
    printf 'FAIL=%s\n' "$1" >&2
    exit 1
}

cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [[ "$TEST_PACKAGE_INSTALLED" -eq 1 ]]; then
        adb -s "$SERIAL" uninstall "$TEST_PACKAGE" </dev/null >/dev/null 2>&1 || true
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

printf 'APPROVED_OPERATION=RUN_WRITE01B1_TEXT_POST_SUBMIT_LOCALHOST_RUNTIME\n'
printf 'RISK=R1_LOCALHOST_HTTPS_TEST_APK_AND_DEV2_APK_INSTALL\n'
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'TEST_PACKAGE=%s\n' "$TEST_PACKAGE"
printf 'FIXTURE_TRANSPORT=LOCALHOST_HTTPS\n'
printf 'APP_ACTIVITY_LAUNCH=NONE\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_POST_WRITES=NONE\n'
printf 'TARGET_APP_AUTH_DATA_MUTATIONS=NONE\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb grep python3 find sort tail; do
    command -v "$command_name" >/dev/null 2>&1 ||
        fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${WRITE01B1_SKIP_BUILD:-0}" != '1' ]]; then
    "$ROOT/gradlew" \
        :app:assembleDebug \
        :app:assembleDebugAndroidTest \
        -Psubshell.debugSlot=dev2 \
        --offline \
        --no-daemon
else
    printf 'WRITE01B1_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -f "$ROOT/local.properties" ]]; then
    SDK_ROOT="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -n1)"
fi
if [[ -z "$SDK_ROOT" && -d "$HOME/Android/Sdk" ]]; then
    SDK_ROOT="$HOME/Android/Sdk"
fi
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT/build-tools" ]] ||
    fail ANDROID_SDK_BUILD_TOOLS_NOT_FOUND

AAPT2="$(
    find "$SDK_ROOT/build-tools" \
        -mindepth 2 \
        -maxdepth 2 \
        -type f \
        -name aapt2 \
        -perm -u+x \
        -print |
    sort -V |
    tail -n1
)"
[[ -n "$AAPT2" && -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND

TEST_CLASS_DESCRIPTOR="L${TEST_CLASS//./\/};"
python3 - "$TEST_APK" "$TEST_CLASS_DESCRIPTOR" <<'PY'
import re
import sys
import zipfile

apk, descriptor = sys.argv[1:]
needle = descriptor.encode('utf-8')
with zipfile.ZipFile(apk) as archive:
    dex = sorted(
        name for name in archive.namelist()
        if re.fullmatch(r'classes(?:[0-9]+)?\.dex', name)
    )
    matches = [name for name in dex if needle in archive.read(name)]
print('TEST_CLASS_DEX_ENTRY=' + (matches[0] if matches else 'MISSING'))
raise SystemExit(0 if matches else 2)
PY
printf 'BUILT_ANDROIDTEST_CLASS=PASS\n'

APP_BADGING="$("$AAPT2" dump badging "$APP_APK")" || fail APP_APK_BADGING_FAILED
APP_PACKAGE_LINE="$(grep -m1 '^package: ' <<<"$APP_BADGING" || true)"
printf 'APP_PACKAGE_LINE=%s\n' "${APP_PACKAGE_LINE:-MISSING}"
grep -Fq "name='$APP_PACKAGE'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_PACKAGE_MISMATCH
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_VERSION_CODE_MISMATCH
grep -Fq "versionName='$EXPECTED_VERSION_NAME'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_VERSION_NAME_MISMATCH

TEST_BADGING="$("$AAPT2" dump badging "$TEST_APK")" || fail TEST_APK_BADGING_FAILED
TEST_PACKAGE_LINE="$(grep -m1 '^package: ' <<<"$TEST_BADGING" || true)"
printf 'TEST_PACKAGE_LINE=%s\n' "${TEST_PACKAGE_LINE:-MISSING}"
grep -Fq "name='$TEST_PACKAGE'" <<<"$TEST_PACKAGE_LINE" ||
    fail BUILT_TEST_PACKAGE_MISMATCH
printf 'BUILT_APK_IDENTITIES=PASS\n'

[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] ||
    fail DEVICE_NOT_READY

APP_INSTALL_OUTPUT="$(adb -s "$SERIAL" install -r -t "$APP_APK" </dev/null 2>&1)" || {
    printf '%s\n' "$APP_INSTALL_OUTPUT"
    fail APP_INSTALL_FAILED
}
printf '%s\n' "$APP_INSTALL_OUTPUT"
grep -Fq 'Success' <<<"$APP_INSTALL_OUTPUT" || fail APP_INSTALL_MARKER_MISSING

PACKAGE_DUMP="$(adb -s "$SERIAL" shell dumpsys package "$APP_PACKAGE" </dev/null)" ||
    fail APP_PACKAGE_DUMP_FAILED
INSTALLED_VERSION_NAME="$(awk -F= '/versionName=/{print $2; exit}' <<<"$PACKAGE_DUMP" | tr -d '\r')"
INSTALLED_VERSION_CODE="$(sed -nE 's/.*versionCode=([0-9]+).*/\1/p' <<<"$PACKAGE_DUMP" | head -n1 | tr -d '\r')"
printf 'INSTALLED_VERSION_NAME=%s\n' "$INSTALLED_VERSION_NAME"
printf 'INSTALLED_VERSION_CODE=%s\n' "$INSTALLED_VERSION_CODE"
[[ "$INSTALLED_VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] ||
    fail INSTALLED_VERSION_NAME_MISMATCH
[[ "$INSTALLED_VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] ||
    fail INSTALLED_VERSION_CODE_MISMATCH

adb -s "$SERIAL" uninstall "$TEST_PACKAGE" </dev/null >/dev/null 2>&1 || true
TEST_INSTALL_OUTPUT="$(adb -s "$SERIAL" install -t "$TEST_APK" </dev/null 2>&1)" || {
    printf '%s\n' "$TEST_INSTALL_OUTPUT"
    fail TEST_INSTALL_FAILED
}
printf '%s\n' "$TEST_INSTALL_OUTPUT"
grep -Fq 'Success' <<<"$TEST_INSTALL_OUTPUT" || fail TEST_INSTALL_MARKER_MISSING
TEST_PACKAGE_INSTALLED=1

REGISTRY="$(adb -s "$SERIAL" shell pm list instrumentation </dev/null | tr -d '\r')" ||
    fail INSTRUMENTATION_REGISTRY_FAILED
grep -Fq "$RUNNER (target=$APP_PACKAGE)" <<<"$REGISTRY" ||
    fail INSTRUMENTATION_TARGET_MISMATCH

adb -s "$SERIAL" shell am force-stop "$APP_PACKAGE" </dev/null
adb -s "$SERIAL" logcat -c </dev/null
INSTRUMENTATION_OUTPUT="$(
    adb -s "$SERIAL" shell am instrument \
        -w -r \
        -e class "$TEST_CLASS" \
        "$RUNNER" \
        </dev/null 2>&1
)" || {
    printf '%s\n' "$INSTRUMENTATION_OUTPUT"
    fail WRITE01B1_INSTRUMENTATION_FAILED
}
printf '%s\n' "$INSTRUMENTATION_OUTPUT"
grep -Fq 'OK (1 test)' <<<"$INSTRUMENTATION_OUTPUT" ||
    fail WRITE01B1_TEST_RESULT_MISSING

LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S' </dev/null | tr -d '\r')"
printf '%s\n' "$LOG"

for marker in \
    'WRITE01B1_HTTPS_REQUESTS=5' \
    'EXACT_API_SUBMIT_FORM=true' \
    'KIND_SELF=true' \
    'PINNED_ACCOUNT=true' \
    'API_RATE_LIMIT=true' \
    'HTTP_OUTCOME_UNKNOWN=true' \
    'MALFORMED_OUTCOME_UNKNOWN=true' \
    'DUPLICATE_GUARD=true' \
    'AUTOMATIC_CONNECTION_RETRY=false' \
    'TARGET_APP_AUTH_VAULT_TOUCHED=false' \
    'LIVE_REDDIT_NETWORK=false' \
    'REMOTE_POST_WRITES=false' \
    'RESULT=SUBSHELL_WRITE01B1_TEXT_POST_SUBMIT_TRANSPORT_RUNTIME_PASS'; do
    grep -Fq "$marker" <<<"$LOG" ||
        fail "RUNTIME_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

adb -s "$SERIAL" uninstall "$TEST_PACKAGE" </dev/null >/dev/null ||
    fail TEST_PACKAGE_CLEANUP_FAILED
TEST_PACKAGE_INSTALLED=0
if adb -s "$SERIAL" shell pm list packages --user 0 "$TEST_PACKAGE" </dev/null |
    tr -d '\r' |
    grep -Fxq "package:$TEST_PACKAGE"; then
    fail TEST_PACKAGE_REMAINS_INSTALLED
fi

printf 'TYPED_TEXT_POST_FORM=PASS\n'
printf 'EXACT_API_SUBMIT_FIELDS=PASS\n'
printf 'PINNED_ACCOUNT_SESSION=PASS\n'
printf 'SANITIZED_API_ERROR_MAPPING=PASS\n'
printf 'RATE_LIMIT_METADATA=PASS\n'
printf 'AMBIGUOUS_OUTCOME_AUTORETRY=DISABLED\n'
printf 'DUPLICATE_SUBMIT_GUARD=PASS\n'
printf 'TEST_PACKAGE_CLEANUP=PASS\n'
printf 'APP_ACTIVITY_LAUNCH=NONE\n'
printf 'TARGET_APP_AUTH_VAULT_TOUCHED=NO\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'REMOTE_POST_WRITES=NONE\n'
printf 'RESULT=SUBSHELL_WRITE01B1_TEXT_POST_SUBMIT_TRANSPORT_RUNTIME_PASS\n'
