#!/usr/bin/env bash
set -u

SERIAL="${ADB_SERIAL:-19021FDF600GBN}"
PACKAGE="io.github.brealorg.subshell.debug.dev1"
ACTIVITY="app.subshell.prototype.MainActivity"
POST="t3_1vmwvx7"
TARGET="t1_p3j1gm4"
USERNAME="brealorg"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${OUT_DIR:-$HOME/Downloads}"
LOG="$OUT_DIR/subshell-profile01a5-v227-r3-duplicate-identity-repro-$STAMP.logcat.txt"
RC=0
LOGCAT_PID=""

wait_log() {
    local pattern="$1"
    local timeout_seconds="${2:-15}"
    local deadline=$((SECONDS + timeout_seconds))
    while (( SECONDS < deadline )); do
        if grep -E "$pattern" "$LOG" >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.10
    done
    return 1
}

if ! command -v adb >/dev/null 2>&1; then
    echo "FAIL=ADB_NOT_FOUND"
    RC=90
elif [[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" != "device" ]]; then
    echo "FAIL=DEVICE_NOT_AVAILABLE"
    RC=91
fi

if [[ "$RC" -eq 0 ]]; then
    DUMP="$(adb -s "$SERIAL" shell dumpsys package "$PACKAGE" 2>/dev/null || true)"
    CODE="$(sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' <<<"$DUMP" | head -1)"
    NAME="$(sed -n 's/^[[:space:]]*versionName=//p' <<<"$DUMP" | head -1 | tr -d '\r')"
    echo "VERSION_CODE=$CODE"
    echo "VERSION_NAME=$NAME"
    if [[ "$CODE" != "227" || "$NAME" != "0.76.70-profile01a5-canonical-target-continuity-r3-dev" ]]; then
        echo "FAIL=WRONG_DUPLICATE_IDENTITY_REPRO_VERSION"
        RC=92
    fi
fi

if [[ "$RC" -eq 0 ]]; then
    : > "$LOG"
    adb -s "$SERIAL" logcat -c
    adb -s "$SERIAL" shell am force-stop "$PACKAGE"
    stdbuf -oL adb -s "$SERIAL" logcat -v time -s SubshellCommentNav:V AndroidRuntime:E '*:S' > "$LOG" 2>&1 &
    LOGCAT_PID=$!
    sleep 0.20
    echo "STEP=OPEN_KNOWN_DUPLICATE_CACHE_TARGET post=$POST target=$TARGET"
    adb -s "$SERIAL" shell am start -W \
        -f 0x20000000 \
        -n "$PACKAGE/$ACTIVITY" \
        --es subshell.debug.focused_post_id "$POST" \
        --es subshell.debug.focused_comment_id "$TARGET" \
        --es subshell.debug.focused_username "$USERNAME"
    START_RC=$?
    if [[ "$START_RC" -ne 0 ]]; then
        echo "FAIL=DUPLICATE_IDENTITY_TARGET_LAUNCH_FAILED"
        RC="$START_RC"
    fi
fi

if [[ "$RC" -eq 0 ]]; then
    if ! wait_log "phase=landing_verified .*target=$TARGET( |$)" 15; then
        echo "FAIL=DUPLICATE_IDENTITY_TARGET_LANDING_NOT_VERIFIED"
        RC=93
    fi
fi

if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
fi

if grep -Fq "Key \"comment:$TARGET\" was already used" "$LOG"; then
    echo "FAIL=DUPLICATE_LAZY_KEY_CRASH_REPRODUCED"
    RC=94
fi

if grep -Fq "FATAL EXCEPTION" "$LOG"; then
    echo "FAIL=ANDROID_RUNTIME_FATAL_DURING_DUPLICATE_IDENTITY_REPRO"
    RC=96
fi

if grep -Fq "phase=duplicate_comment_identity post=$POST" "$LOG"; then
    echo "DUPLICATE_IDENTITY_DIAGNOSTIC=OBSERVED_AND_CONTAINED"
else
    echo "DUPLICATE_IDENTITY_DIAGNOSTIC=NOT_OBSERVED_CACHE_MAY_ALREADY_BE_HEALED"
fi

PID="$(adb -s "$SERIAL" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
echo "PROCESS_AFTER=${PID:-NONE}"
if [[ "$RC" -eq 0 && -z "$PID" ]]; then
    echo "FAIL=DEV1_PROCESS_NOT_ALIVE_AFTER_DUPLICATE_IDENTITY_REPRO"
    RC=95
fi

echo "LOGCAT=$LOG"
if [[ "$RC" -eq 0 ]]; then
    echo "RESULT=SUBSHELL_PROFILE01A5_V227_R3_DUPLICATE_IDENTITY_REPRO_PASS"
else
    echo "RESULT=SUBSHELL_PROFILE01A5_V227_R3_DUPLICATE_IDENTITY_REPRO_STOP"
fi
echo "TERMINAL_STILL_ALIVE=YES"
exit "$RC"
