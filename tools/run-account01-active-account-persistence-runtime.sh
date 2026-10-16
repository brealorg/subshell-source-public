#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_SERIAL="${ADB_SERIAL:-19021FDF600GBN}"
PACKAGE="${PACKAGE:-io.github.brealorg.subshell.debug.dev2}"
TEST_PACKAGE="${TEST_PACKAGE:-${PACKAGE}.test}"
EXPECTED_ACCOUNT="${EXPECTED_ACCOUNT:-brealorg}"
EXPECTED_DEBUG_SLOT="${EXPECTED_DEBUG_SLOT:-dev2}"
EXPECTED_VERSION_NAME='0.65.4-account01-active-account-persistence-dev'
EXPECTED_VERSION_CODE='90'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
TEST_CLASS_FQCN='app.subshell.prototype.ui.Account01ActiveAccountPersistenceInstrumentedTest'
TEST_METHOD='repairInstalledPreferencePairFromAuthoritativeDirectory'
TEST_CLASS="$TEST_CLASS_FQCN#$TEST_METHOD"
LOG_TAG='subshell-account01'
RUNTIME_STAGE='initialization'

fail() {
    printf 'FAIL=%s\n' "$1" >&2
    exit 1
}

unhandled_error() {
    local status="$1"
    local line="$2"
    local command="$3"

    trap - ERR
    printf 'FAIL=UNHANDLED_RUNTIME_ERROR\n' >&2
    printf 'RUNTIME_STAGE=%s\n' "$RUNTIME_STAGE" >&2
    printf 'EXIT_STATUS=%s\n' "$status" >&2
    printf 'SOURCE_LINE=%s\n' "$line" >&2
    printf 'FAILED_COMMAND=%s\n' "$command" >&2
    exit "$status"
}
trap 'unhandled_error "$?" "$LINENO" "$BASH_COMMAND"' ERR

stage() {
    RUNTIME_STAGE="$1"
    printf '\n===== RUNTIME STAGE: %s =====\n' "$RUNTIME_STAGE"
}

command -v adb >/dev/null 2>&1 || fail ADB_NOT_FOUND
command -v python3 >/dev/null 2>&1 || fail PYTHON3_NOT_FOUND
command -v sha256sum >/dev/null 2>&1 || fail SHA256SUM_NOT_FOUND
command -v find >/dev/null 2>&1 || fail FIND_NOT_FOUND
command -v sort >/dev/null 2>&1 || fail SORT_NOT_FOUND
command -v tail >/dev/null 2>&1 || fail TAIL_NOT_FOUND
[[ -f "$APP_APK" ]] || fail APP_APK_NOT_FOUND
[[ -f "$TEST_APK" ]] || fail TEST_APK_NOT_FOUND

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -f "$ROOT/local.properties" ]]; then
    SDK_ROOT="$(
        sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" |
        tail -n1
    )"
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
echo "ANDROID_SDK_ROOT_RESOLVED=$SDK_ROOT"
echo "AAPT2=$AAPT2"

stage verify_built_androidtest_apk
TEST_CLASS_DESCRIPTOR="L${TEST_CLASS_FQCN//./\/};"

python3 - "$TEST_APK" "$TEST_CLASS_DESCRIPTOR" <<'PY'
import re
import sys
import zipfile

apk, descriptor = sys.argv[1:]
needle = descriptor.encode("utf-8")

with zipfile.ZipFile(apk) as archive:
    dex_entries = sorted(
        name
        for name in archive.namelist()
        if re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)
    )
    if not dex_entries:
        print("FAIL=TEST_APK_DEX_MISSING")
        raise SystemExit(2)

    matches = [
        name
        for name in dex_entries
        if needle in archive.read(name)
    ]

print("TEST_APK_DEX_ENTRIES=" + ",".join(dex_entries))
print("TEST_CLASS_DESCRIPTOR=" + descriptor)
print(
    "TEST_CLASS_DEX_ENTRY="
    + (matches[0] if matches else "MISSING")
)

raise SystemExit(0 if matches else 3)
PY
echo 'BUILT_ANDROIDTEST_APK_CLASS=PASS'

stage verify_built_apk_identity
APP_BADGING=''
if ! APP_BADGING="$(
    "$AAPT2" dump badging "$APP_APK" 2>&1
)"; then
    printf '%s\n' "$APP_BADGING"
    fail APP_APK_BADGING_FAILED
fi
APP_PACKAGE_LINE="$(
    grep -m1 '^package: ' <<<"$APP_BADGING" || true
)"
printf 'APP_PACKAGE_LINE=%s\n' "${APP_PACKAGE_LINE:-MISSING}"
grep -Fq "name='$PACKAGE'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_PACKAGE_ID_MISMATCH
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_VERSION_CODE_MISMATCH
grep -Fq "versionName='$EXPECTED_VERSION_NAME'" <<<"$APP_PACKAGE_LINE" ||
    fail BUILT_APP_VERSION_NAME_MISMATCH
echo 'BUILT_APP_APK_IDENTITY=PASS'

TEST_BADGING=''
if ! TEST_BADGING="$(
    "$AAPT2" dump badging "$TEST_APK" 2>&1
)"; then
    printf '%s\n' "$TEST_BADGING"
    fail TEST_APK_BADGING_FAILED
fi
TEST_PACKAGE_LINE="$(
    grep -m1 '^package: ' <<<"$TEST_BADGING" || true
)"
printf 'TEST_PACKAGE_LINE=%s\n' "${TEST_PACKAGE_LINE:-MISSING}"
grep -Fq "name='$TEST_PACKAGE'" <<<"$TEST_PACKAGE_LINE" ||
    fail BUILT_TEST_PACKAGE_ID_MISMATCH
echo 'BUILT_TEST_APK_IDENTITY=PASS'
echo "EXPLICIT_DEBUG_SLOT=$EXPECTED_DEBUG_SLOT"

echo
echo 'APPROVED_OPERATION=RUN_ACCOUNT01_ACTIVE_ACCOUNT_PERSISTENCE_RUNTIME'
echo 'RISK=R1_LOCAL_APP_PREFERENCE_REPAIR_AND_ANDROIDTEST'
echo "ADB_SERIAL=$ADB_SERIAL"
echo "PACKAGE=$PACKAGE"
echo "TEST_PACKAGE=$TEST_PACKAGE"
echo "EXPECTED_ACCOUNT=$EXPECTED_ACCOUNT"
echo 'APP_LAUNCH=NONE'
echo 'LIVE_REDDIT_NETWORK=NONE'
echo 'REMOTE_WRITES=NONE'

stage verify_adb_device
ADB_STATE_OUTPUT=''
if ! ADB_STATE_OUTPUT="$(adb -s "$ADB_SERIAL" get-state </dev/null 2>&1)"; then
    printf '%s\n' "$ADB_STATE_OUTPUT"
    fail ADB_GET_STATE_FAILED
fi
ADB_STATE_OUTPUT="${ADB_STATE_OUTPUT//$'\r'/}"
echo "ADB_STATE=$ADB_STATE_OUTPUT"
[[ "$ADB_STATE_OUTPUT" == 'device' ]] || fail ADB_DEVICE_NOT_READY
echo 'ADB_DEVICE=PASS'

stage inspect_preinstall_packages
PREINSTALL_PACKAGE_LIST=''
if ! PREINSTALL_PACKAGE_LIST="$(
    adb -s "$ADB_SERIAL" shell pm list packages --user 0 "$PACKAGE" \
        </dev/null 2>&1
)"; then
    printf '%s\n' "$PREINSTALL_PACKAGE_LIST"
    fail PREINSTALL_PACKAGE_LIST_QUERY_FAILED
fi
PREINSTALL_PACKAGE_LIST="${PREINSTALL_PACKAGE_LIST//$'\r'/}"
printf 'PREINSTALL_PACKAGE_LIST_BEGIN\n%s\nPREINSTALL_PACKAGE_LIST_END\n' \
    "${PREINSTALL_PACKAGE_LIST:-NONE}"

if grep -Fxq "package:$PACKAGE" <<<"$PREINSTALL_PACKAGE_LIST"; then
    echo 'PREINSTALL_APP_PRESENT=YES'
else
    echo 'PREINSTALL_APP_PRESENT=NO'
fi

if grep -Fxq "package:$TEST_PACKAGE" <<<"$PREINSTALL_PACKAGE_LIST"; then
    echo 'PREINSTALL_TEST_PACKAGE_PRESENT=YES'
else
    echo 'PREINSTALL_TEST_PACKAGE_PRESENT=NO'
fi

echo 'PREINSTALL_PACKAGE_PRESENCE_QUERY=PASS'

stage install_account01_app
APP_INSTALL_OUTPUT=''
if ! APP_INSTALL_OUTPUT="$(
    adb -s "$ADB_SERIAL" install -r -t "$APP_APK" </dev/null 2>&1
)"; then
    printf '%s\n' "$APP_INSTALL_OUTPUT"
    fail APP_INSTALL_FAILED
fi
printf '%s\n' "$APP_INSTALL_OUTPUT"
grep -Fq 'Success' <<<"$APP_INSTALL_OUTPUT" || fail APP_INSTALL_SUCCESS_MARKER_MISSING
echo 'ACCOUNT01_APP_INSTALL=PASS'

stage verify_installed_app_identity
PACKAGE_DUMP="$(
    adb -s "$ADB_SERIAL" shell dumpsys package "$PACKAGE" </dev/null 2>&1
)" || {
    printf '%s\n' "$PACKAGE_DUMP"
    fail INSTALLED_APP_DUMPSYS_FAILED
}
VERSION_NAME="$(
    awk -F= '/versionName=/{print $2; exit}' <<<"$PACKAGE_DUMP" |
    tr -d '\r'
)"
VERSION_CODE="$(
    sed -nE 's/.*versionCode=([0-9]+).*/\1/p' <<<"$PACKAGE_DUMP" |
    head -n1 |
    tr -d '\r'
)"
echo "INSTALLED_VERSION_NAME=$VERSION_NAME"
echo "INSTALLED_VERSION_CODE=$VERSION_CODE"
[[ "$VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] ||
    fail INSTALLED_VERSION_NAME_MISMATCH
[[ "$VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] ||
    fail INSTALLED_VERSION_CODE_MISMATCH
echo 'INSTALLED_ACCOUNT01_APP_IDENTITY=PASS'

stage remove_stale_test_package
FORCE_STOP_TEST_OUTPUT="$(
    adb -s "$ADB_SERIAL" shell am force-stop "$TEST_PACKAGE" </dev/null 2>&1
)" || {
    printf '%s\n' "$FORCE_STOP_TEST_OUTPUT"
    fail TEST_PACKAGE_FORCE_STOP_FAILED
}

UNINSTALL_OUTPUT=''
UNINSTALL_STATUS=0
UNINSTALL_OUTPUT="$(
    adb -s "$ADB_SERIAL" uninstall "$TEST_PACKAGE" </dev/null 2>&1
)" || UNINSTALL_STATUS=$?
echo "TEST_PACKAGE_UNINSTALL_STATUS=$UNINSTALL_STATUS"
printf '%s\n' "${UNINSTALL_OUTPUT:-NO_OUTPUT}"

POST_UNINSTALL_PACKAGE_LIST=''
if ! POST_UNINSTALL_PACKAGE_LIST="$(
    adb -s "$ADB_SERIAL" shell pm list packages --user 0 "$TEST_PACKAGE" \
        </dev/null 2>&1
)"; then
    printf '%s\n' "$POST_UNINSTALL_PACKAGE_LIST"
    fail POST_UNINSTALL_PACKAGE_LIST_QUERY_FAILED
fi
POST_UNINSTALL_PACKAGE_LIST="${POST_UNINSTALL_PACKAGE_LIST//$'\r'/}"
printf 'POST_UNINSTALL_PACKAGE_LIST_BEGIN\n%s\nPOST_UNINSTALL_PACKAGE_LIST_END\n' \
    "${POST_UNINSTALL_PACKAGE_LIST:-NONE}"

if grep -Fxq "package:$TEST_PACKAGE" <<<"$POST_UNINSTALL_PACKAGE_LIST"; then
    fail STALE_TEST_PACKAGE_REMAINS_AFTER_UNINSTALL
fi
echo 'STALE_TEST_PACKAGE_REMOVED=PASS'

stage install_fresh_androidtest_package
TEST_INSTALL_OUTPUT=''
if ! TEST_INSTALL_OUTPUT="$(
    adb -s "$ADB_SERIAL" install -t "$TEST_APK" </dev/null 2>&1
)"; then
    printf '%s\n' "$TEST_INSTALL_OUTPUT"
    fail TEST_APK_FRESH_INSTALL_FAILED
fi
printf '%s\n' "$TEST_INSTALL_OUTPUT"
grep -Fq 'Success' <<<"$TEST_INSTALL_OUTPUT" ||
    fail TEST_APK_INSTALL_SUCCESS_MARKER_MISSING
echo 'FRESH_TEST_PACKAGE_INSTALL=PASS'

stage verify_installed_test_package
TEST_PACKAGE_PATH_OUTPUT=''
if ! TEST_PACKAGE_PATH_OUTPUT="$(
    adb -s "$ADB_SERIAL" shell pm path "$TEST_PACKAGE" </dev/null 2>&1
)"; then
    printf '%s\n' "$TEST_PACKAGE_PATH_OUTPUT"
    fail INSTALLED_TEST_PACKAGE_PATH_QUERY_FAILED
fi
TEST_PACKAGE_PATH_OUTPUT="${TEST_PACKAGE_PATH_OUTPUT//$'\r'/}"
printf '%s\n' "$TEST_PACKAGE_PATH_OUTPUT"
grep -Fq 'package:' <<<"$TEST_PACKAGE_PATH_OUTPUT" ||
    fail INSTALLED_TEST_PACKAGE_PATH_MISSING

INSTRUMENTATION_REGISTRY=''
if ! INSTRUMENTATION_REGISTRY="$(
    adb -s "$ADB_SERIAL" shell pm list instrumentation </dev/null 2>&1
)"; then
    printf '%s\n' "$INSTRUMENTATION_REGISTRY"
    fail INSTRUMENTATION_REGISTRY_QUERY_FAILED
fi
INSTRUMENTATION_REGISTRY="${INSTRUMENTATION_REGISTRY//$'\r'/}"
printf '%s\n' "$INSTRUMENTATION_REGISTRY"

EXPECTED_INSTRUMENTATION="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner (target=$PACKAGE)"
grep -Fq "$EXPECTED_INSTRUMENTATION" <<<"$INSTRUMENTATION_REGISTRY" ||
    fail INSTALLED_INSTRUMENTATION_TARGET_MISMATCH
echo 'INSTALLED_INSTRUMENTATION_TARGET=PASS'

stage run_account01_instrumentation
FORCE_STOP_APP_OUTPUT="$(
    adb -s "$ADB_SERIAL" shell am force-stop "$PACKAGE" </dev/null 2>&1
)" || {
    printf '%s\n' "$FORCE_STOP_APP_OUTPUT"
    fail APP_FORCE_STOP_FAILED
}

LOGCAT_CLEAR_OUTPUT="$(
    adb -s "$ADB_SERIAL" logcat -c </dev/null 2>&1
)" || {
    printf '%s\n' "$LOGCAT_CLEAR_OUTPUT"
    fail LOGCAT_CLEAR_FAILED
}

INSTRUMENTATION_OUTPUT=''
if ! INSTRUMENTATION_OUTPUT="$(
    adb -s "$ADB_SERIAL" shell am instrument -w -r \
        -e class "$TEST_CLASS" \
        "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" \
        </dev/null 2>&1
)"; then
    printf '%s\n' "$INSTRUMENTATION_OUTPUT"
    fail ACCOUNT01_INSTRUMENTATION_FAILED
fi
printf '%s\n' "$INSTRUMENTATION_OUTPUT"
grep -Fq 'OK (1 test)' <<<"$INSTRUMENTATION_OUTPUT" ||
    fail ACCOUNT01_TEST_RESULT_MISSING
echo 'ACCOUNT01_INSTRUMENTATION=PASS'

stage verify_account01_runtime_log
LOG_OUTPUT=''
if ! LOG_OUTPUT="$(
    adb -s "$ADB_SERIAL" logcat -d -s "$LOG_TAG:I" '*:S' </dev/null 2>&1
)"; then
    printf '%s\n' "$LOG_OUTPUT"
    fail ACCOUNT01_LOGCAT_READ_FAILED
fi
LOG_OUTPUT="${LOG_OUTPUT//$'\r'/}"
printf '%s\n' "$LOG_OUTPUT"
grep -Fq 'ACCOUNT01_ACTIVE_ID_AUTHORITATIVE=true' <<<"$LOG_OUTPUT" ||
    fail AUTHORITATIVE_ID_RUNTIME_MARKER_MISSING
grep -Fq 'PERSISTED_PAIR_MATCH=true' <<<"$LOG_OUTPUT" ||
    fail PERSISTED_PAIR_RUNTIME_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_ACCOUNT01_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
    fail ACCOUNT01_RUNTIME_RESULT_MISSING
echo 'ACCOUNT01_RUNTIME_LOG=PASS'

stage verify_installed_preference_pair
PREFERENCE_XML=''
if ! PREFERENCE_XML="$(
    adb -s "$ADB_SERIAL" exec-out \
        run-as "$PACKAGE" \
        cat shared_prefs/subshell_preferences.xml \
        </dev/null 2>&1
)"; then
    printf '%s\n' "$PREFERENCE_XML"
    fail INSTALLED_PREFERENCE_XML_READ_FAILED
fi

PREFERENCE_RESULT=''
if ! PREFERENCE_RESULT="$(
    printf '%s' "$PREFERENCE_XML" |
    python3 -c '
import sys
import xml.etree.ElementTree as ET

expected = sys.argv[1]
root = ET.fromstring(sys.stdin.buffer.read())
values = {
    node.attrib.get("name"): node.text or ""
    for node in root
    if node.tag == "string"
}
active_id = values.get("active_account_id", "")
username = values.get("active_account_username", "")
print(f"ACTIVE_ACCOUNT_ID={active_id}")
print(f"ACTIVE_ACCOUNT_USERNAME={username}")
if not active_id or active_id.startswith("local:"):
    raise SystemExit(2)
if username != expected:
    raise SystemExit(3)
' "$EXPECTED_ACCOUNT"
)"; then
    printf '%s\n' "$PREFERENCE_RESULT"
    fail INSTALLED_PREFERENCE_PAIR_MISMATCH
fi
printf '%s\n' "$PREFERENCE_RESULT"

echo 'BUILT_ANDROIDTEST_APK_CLASS=PASS'
echo 'BUILT_APP_APK_IDENTITY=PASS'
echo 'BUILT_TEST_APK_IDENTITY=PASS'
echo "EXPLICIT_DEBUG_SLOT=$EXPECTED_DEBUG_SLOT"
echo 'FRESH_TEST_PACKAGE_INSTALL=PASS'
echo 'INSTALLED_INSTRUMENTATION_TARGET=PASS'
echo 'AUTHORITATIVE_ACTIVE_ACCOUNT_ID=PASS'
echo 'LEGACY_ACTIVE_ACCOUNT_USERNAME_REPAIRED=PASS'
echo 'AUTHENTICATED_ACCOUNT_SELECTION_PAIR=PASS'
echo 'ADB_RUNTIME_STAGE_TRACE=PASS'
echo 'APP_LAUNCH=NONE'
echo 'LIVE_REDDIT_NETWORK=NONE'
echo 'REMOTE_WRITES=NONE'
echo 'RESULT=SUBSHELL_ACCOUNT01_ACTIVE_ACCOUNT_PERSISTENCE_RUNTIME_PASS'
