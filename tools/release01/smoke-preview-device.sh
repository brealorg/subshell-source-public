#!/usr/bin/env bash
set -euo pipefail

# SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_V1

RECORD_VERIFIER='tools/release01/verify-release-record.py'
MAIN_ACTIVITY='app.subshell.prototype.MainActivity'

usage() {
    cat <<'USAGE'
Usage:
  tools/release01/smoke-preview-device.sh \
    --release-record /absolute/path/release-record.env \
    [--qualified-source-root /absolute/path/to/qualified/source] \
    [--serial ADB_SERIAL]

If --qualified-source-root is omitted, the repository containing this driver is
used. If --serial is omitted, SUBSHELL_ADB_SERIAL is used when set; otherwise
exactly one connected adb device is required.
USAGE
}

RELEASE_RECORD=''
QUALIFIED_SOURCE_ROOT=''
SERIAL="${SUBSHELL_ADB_SERIAL:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --release-record)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            RELEASE_RECORD="$2"
            shift 2
            ;;
        --qualified-source-root)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            QUALIFIED_SOURCE_ROOT="$2"
            shift 2
            ;;
        --serial)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            SERIAL="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR=UNKNOWN_ARGUMENT:$1" >&2
            usage >&2
            exit 64
            ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

if [[ -z "$QUALIFIED_SOURCE_ROOT" ]]; then
    QUALIFIED_SOURCE_ROOT="$ROOT"
fi
QUALIFIED_SOURCE_ROOT="$(
    python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).expanduser().resolve())' "$QUALIFIED_SOURCE_ROOT"
)"

fail() {
    echo "STOP=RELEASETOOL01B_DEVICE_SMOKE_FAILED reason=$1" >&2
    echo 'BUILD_RUN=NO' >&2
    echo 'SIGNING=NO' >&2
    echo 'REMOTE_OPERATIONS=NONE' >&2
    echo 'RESULT=SUBSHELL_RELEASETOOL01B_DEVICE_SMOKE_FAILED' >&2
    exit 1
}

find_android_build_tool() {
    local tool="$1"
    local direct sdk_root build_tools

    direct="$(command -v "$tool" 2>/dev/null || true)"
    if [[ -n "$direct" ]]; then
        printf '%s\n' "$direct"
        return 0
    fi

    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
    build_tools="$(
        find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null |
            sort -V |
            tail -n 1
    )"
    [[ -x "$build_tools/$tool" ]] || return 1
    printf '%s\n' "$build_tools/$tool"
}

package_present() {
    adb -s "$SERIAL" shell pm list packages "$1" 2>/dev/null |
        tr -d '\r' |
        grep -Fxq "package:$1"
}

package_uid() {
    adb -s "$SERIAL" shell cmd package list packages -U "$1" 2>/dev/null |
        tr -d '\r' |
        sed -nE "s/^package:$1[[:space:]]+uid:([0-9]+)$/\1/p" |
        head -n 1
}

package_field() {
    local package="$1"
    local field="$2"
    adb -s "$SERIAL" shell dumpsys package "$package" 2>/dev/null |
        tr -d '\r' |
        sed -n "s/^[[:space:]]*${field}=//p" |
        head -n 1
}

[[ "$RELEASE_RECORD" == /* ]] || fail RELEASE_RECORD_MUST_BE_ABSOLUTE
[[ -f "$RELEASE_RECORD" ]] || fail RELEASE_RECORD_NOT_FOUND
[[ -d "$QUALIFIED_SOURCE_ROOT/.git" || -f "$QUALIFIED_SOURCE_ROOT/.git" ]] ||
    fail QUALIFIED_SOURCE_ROOT_NOT_GIT_WORKTREE

AAPT2="$(find_android_build_tool aapt2)" || fail AAPT2_NOT_FOUND
APKSIGNER="$(find_android_build_tool apksigner)" || fail APKSIGNER_NOT_FOUND

if [[ -z "$SERIAL" ]]; then
    mapfile -t DEVICES < <(
        adb devices |
            awk 'NR > 1 && $2 == "device" {print $1}'
    )
    case "${#DEVICES[@]}" in
        1) SERIAL="${DEVICES[0]}" ;;
        0) fail NO_ADB_DEVICE ;;
        *) fail MULTIPLE_ADB_DEVICES_REQUIRE_SERIAL ;;
    esac
fi

adb -s "$SERIAL" get-state 2>/dev/null | grep -Fxq device ||
    fail ADB_DEVICE_NOT_READY

verify_field() {
    local field="$1"
    python3 "$RECORD_VERIFIER" \
        --record "$RELEASE_RECORD" \
        --source-root "$QUALIFIED_SOURCE_ROOT" \
        --aapt2 "$AAPT2" \
        --apksigner "$APKSIGNER" \
        --field "$field" |
        sed -n 's/^FIELD_VALUE=//p' |
        tail -n 1
}

# Full verification first; field reads below deliberately repeat only local
# read-only artifact verification, never a build/sign/install action.
python3 "$RECORD_VERIFIER" \
    --record "$RELEASE_RECORD" \
    --source-root "$QUALIFIED_SOURCE_ROOT" \
    --aapt2 "$AAPT2" \
    --apksigner "$APKSIGNER" ||
    fail RELEASE_RECORD_VERIFICATION_FAILED

PREVIEW_APK="$(verify_field PREVIEW_APK)"
APPLICATION_ID="$(verify_field APPLICATION_ID)"
VERSION_NAME="$(verify_field VERSION_NAME)"
VERSION_CODE="$(verify_field VERSION_CODE)"
APK_SHA256="$(verify_field APK_SHA256)"

[[ -f "$PREVIEW_APK" ]] || fail VERIFIED_PREVIEW_APK_DISAPPEARED

echo "QUALIFIED_SOURCE_ROOT=$QUALIFIED_SOURCE_ROOT"
echo "SERIAL=$SERIAL"
echo "DEVICE=$(adb -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
echo "APPLICATION_ID=$APPLICATION_ID"
echo "TARGET_VERSION_NAME=$VERSION_NAME"
echo "TARGET_VERSION_CODE=$VERSION_CODE"
echo "APK_SHA256=$APK_SHA256"

PRESENT_BEFORE=NO
UID_BEFORE=''
VERSION_NAME_BEFORE=''
VERSION_CODE_BEFORE=''
FIRST_INSTALL_TIME_BEFORE=''

if package_present "$APPLICATION_ID"; then
    PRESENT_BEFORE=YES
    UID_BEFORE="$(package_uid "$APPLICATION_ID")"
    VERSION_NAME_BEFORE="$(package_field "$APPLICATION_ID" versionName)"
    VERSION_CODE_BEFORE_RAW="$(package_field "$APPLICATION_ID" versionCode)"
    VERSION_CODE_BEFORE="${VERSION_CODE_BEFORE_RAW%% *}"
    FIRST_INSTALL_TIME_BEFORE="$(package_field "$APPLICATION_ID" firstInstallTime)"
fi

echo "PACKAGE_PRESENT_BEFORE=$PRESENT_BEFORE"
echo "UID_BEFORE=${UID_BEFORE:-NONE}"
echo "VERSION_NAME_BEFORE=${VERSION_NAME_BEFORE:-NONE}"
echo "VERSION_CODE_BEFORE=${VERSION_CODE_BEFORE:-NONE}"
echo "FIRST_INSTALL_TIME_BEFORE=${FIRST_INSTALL_TIME_BEFORE:-NONE}"

INSTALL_OUTPUT="$(adb -s "$SERIAL" install -r "$PREVIEW_APK" 2>&1)"
printf '%s\n' "$INSTALL_OUTPUT"
grep -Fq 'Success' <<<"$INSTALL_OUTPUT" || fail ADB_INSTALL_FAILED

package_present "$APPLICATION_ID" || fail PACKAGE_NOT_PRESENT_AFTER_INSTALL

UID_AFTER="$(package_uid "$APPLICATION_ID")"
VERSION_NAME_AFTER="$(package_field "$APPLICATION_ID" versionName)"
VERSION_CODE_AFTER_RAW="$(package_field "$APPLICATION_ID" versionCode)"
VERSION_CODE_AFTER="${VERSION_CODE_AFTER_RAW%% *}"
FIRST_INSTALL_TIME_AFTER="$(package_field "$APPLICATION_ID" firstInstallTime)"

echo "UID_AFTER=$UID_AFTER"
echo "VERSION_NAME_AFTER=$VERSION_NAME_AFTER"
echo "VERSION_CODE_AFTER=$VERSION_CODE_AFTER"
echo "FIRST_INSTALL_TIME_AFTER=$FIRST_INSTALL_TIME_AFTER"

[[ "$VERSION_NAME_AFTER" == "$VERSION_NAME" ]] || fail INSTALLED_VERSION_NAME_MISMATCH
[[ "$VERSION_CODE_AFTER" == "$VERSION_CODE" ]] || fail INSTALLED_VERSION_CODE_MISMATCH

if [[ "$PRESENT_BEFORE" == YES ]]; then
    [[ -n "$UID_BEFORE" && "$UID_AFTER" == "$UID_BEFORE" ]] ||
        fail IN_PLACE_UID_NOT_PRESERVED
    [[ -n "$FIRST_INSTALL_TIME_BEFORE" &&
       "$FIRST_INSTALL_TIME_AFTER" == "$FIRST_INSTALL_TIME_BEFORE" ]] ||
        fail IN_PLACE_FIRST_INSTALL_TIME_NOT_PRESERVED
    echo 'IN_PLACE_UPDATE_IDENTITY=PASS'
else
    echo 'FRESH_INSTALL_IDENTITY=PASS'
fi

adb -s "$SERIAL" shell am force-stop "$APPLICATION_ID"

LAUNCH_OUTPUT="$(
    adb -s "$SERIAL" shell am start -W \
        -n "$APPLICATION_ID/$MAIN_ACTIVITY" 2>&1
)"
printf '%s\n' "$LAUNCH_OUTPUT"
grep -Fq 'Status: ok' <<<"$LAUNCH_OUTPUT" || fail MAIN_ACTIVITY_LAUNCH_FAILED

FOREGROUND=NO
for attempt in $(seq 1 12); do
    TOP="$(
        adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null |
            grep -m1 'topResumedActivity=' || true
    )"
    if grep -Fq "$APPLICATION_ID/$MAIN_ACTIVITY" <<<"$TOP"; then
        FOREGROUND=YES
        echo "FOREGROUND_MATCH_ATTEMPT=$attempt"
        break
    fi
    sleep 1
done
[[ "$FOREGROUND" == YES ]] || fail MAIN_ACTIVITY_NOT_FOREGROUND

PID="$(adb -s "$SERIAL" shell pidof "$APPLICATION_ID" | tr -d '\r')"
[[ -n "$PID" ]] || fail APP_PROCESS_NOT_RUNNING

# Re-hash after install to prove the external preview bytes were not mutated.
[[ "$(sha256sum "$PREVIEW_APK" | awk '{print $1}')" == "$APK_SHA256" ]] ||
    fail PREVIEW_APK_BYTES_CHANGED_DURING_SMOKE

echo "PID=$PID"
echo 'EXACT_PREVIEW_BYTES=PASS'
echo 'INSTALLED_PACKAGE_VERSION=PASS'
echo 'MAIN_ACTIVITY_FOREGROUND=PASS'
echo 'BUILD_RUN=NO'
echo 'SIGNING=NO'
echo 'REMOTE_OPERATIONS=NONE'
echo 'DEVICE_PACKAGE_MUTATION=INSTALL_OR_UPDATE_AND_LAUNCH'
echo 'RESULT=SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_PASS'
