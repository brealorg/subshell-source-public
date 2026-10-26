#!/usr/bin/env bash
# EMU01_OLD_ANDROID_SMOKE_V1 (owner 2026-10-16): boots each emulator headless
# and read-only, installs one APK, cold-starts it and checks that MainActivity
# is the resumed activity with no crash or ANR for the app in the log. A
# screenshot per Android version goes to the output folder. Every adb call
# names the emulator's own serial, so a connected phone is never touched.
#
#   tools/emulator-smoke.sh --apk FILE --out DIR [--avd NAME]...
#
# Without --avd it runs subshell-api26, subshell-api30 and subshell-api36.
# Create them once with:
#   avdmanager create avd -n subshell-api26 -d pixel_6 \
#     -k "system-images;android-26;google_apis;x86_64"
#   avdmanager create avd -n subshell-api30 -d pixel_6 \
#     -k "system-images;android-30;google_apis;x86_64"
#   avdmanager create avd -n subshell-api36 -d pixel_6 \
#     -k "system-images;android-36;google_apis_ps16k;x86_64"
# Exit 0 when every emulator passes, 1 when one fails, 2 on wrong usage.
#
# EMU02_EMULATORS_BOOT_WITH_4_GB_V1 (owner 2026-10-21): every emulator boots
# with 4 GB RAM, whatever its AVD says. With its AVD's 2 GB (2.5 GB after the
# emulator's own bump) the API 36 image's post-boot work let the low-memory
# killer kill the app in the foreground (0.84-beta smoke; the 0.83 APK that
# passed on 10-19 failed the same way). The AVDs are not changed.
#
# EMU03_API36_IS_SUBSHELLS_OWN_AVD_V1 (owner 2026-10-24): API 36 ran on
# Morphe_16KB_API36, which belonged to another project and was deleted in a
# disk cleanup there. subshell-api36 is Subshell's own, with the same 16 KB
# page-size Google APIs image.
set -u -o pipefail

APK=""
OUT=""
AVDS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk) APK="${2:-}"; shift 2 ;;
        --out) OUT="${2:-}"; shift 2 ;;
        --avd) AVDS+=("${2:-}"); shift 2 ;;
        *) echo "STOP=USAGE: $0 --apk FILE --out DIR [--avd NAME]..." >&2; exit 2 ;;
    esac
done
[[ -f "$APK" && -n "$OUT" ]] || { echo "STOP=USAGE: $0 --apk FILE --out DIR [--avd NAME]..." >&2; exit 2; }
[[ ${#AVDS[@]} -gt 0 ]] || AVDS=(subshell-api26 subshell-api30 subshell-api36)
mkdir -p "$OUT" || exit 2

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="$SDK/emulator/emulator"
AAPT2="$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
[[ -x "$EMULATOR" && -x "$AAPT2" ]] || { echo "STOP=SDK_TOOLS_MISSING" >&2; exit 2; }
PACKAGE="$("$AAPT2" dump packagename "$APK" 2>/dev/null)"
[[ -n "$PACKAGE" ]] || { echo "STOP=APK_PACKAGE_UNREADABLE" >&2; exit 2; }
ACTIVITY="$PACKAGE/app.subshell.prototype.MainActivity"

FAILED=0
PORT=5580
for AVD in "${AVDS[@]}"; do
    SERIAL="emulator-$PORT"
    LOG="$OUT/$AVD-emulator.log"
    "$EMULATOR" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
        -no-snapshot -read-only -memory 4096 -gpu swiftshader_indirect >"$LOG" 2>&1 &
    EMULATOR_PID=$!
    STATUS=PASS
    REASON=ok

    booted=0
    for _ in $(seq 1 150); do
        if [[ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
            booted=1
            break
        fi
        kill -0 "$EMULATOR_PID" 2>/dev/null || break
        sleep 2
    done
    API="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"

    if [[ "$booted" != 1 ]]; then
        STATUS=FAIL; REASON=EMULATOR_DID_NOT_BOOT
    elif ! adb -s "$SERIAL" install -r -t "$APK" >"$OUT/$AVD-install.log" 2>&1; then
        STATUS=FAIL; REASON=INSTALL_FAILED
    else
        adb -s "$SERIAL" logcat -c
        adb -s "$SERIAL" shell am start-activity -W -n "$ACTIVITY" >"$OUT/$AVD-start.log" 2>&1
        sleep 10
        adb -s "$SERIAL" logcat -d -v brief >"$OUT/$AVD-logcat.txt" 2>/dev/null
        adb -s "$SERIAL" exec-out screencap -p >"$OUT/$AVD.png" 2>/dev/null
        TOP="$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|mResumedActivity')"
        if grep -qE "FATAL EXCEPTION" "$OUT/$AVD-logcat.txt" &&
            grep -qE "Process: $PACKAGE" "$OUT/$AVD-logcat.txt"; then
            STATUS=FAIL; REASON=APP_CRASHED
        elif grep -qE "ANR in $PACKAGE" "$OUT/$AVD-logcat.txt"; then
            STATUS=FAIL; REASON=APP_NOT_RESPONDING
        elif [[ "$TOP" != *"$ACTIVITY"* && "$TOP" != *"$PACKAGE/.MainActivity"* ]]; then
            STATUS=FAIL; REASON=MAIN_ACTIVITY_NOT_RESUMED
        fi
    fi

    adb -s "$SERIAL" emu kill >/dev/null 2>&1
    for _ in $(seq 1 30); do kill -0 "$EMULATOR_PID" 2>/dev/null || break; sleep 1; done
    kill "$EMULATOR_PID" 2>/dev/null
    echo "EMU01_RESULT avd=$AVD api=${API:-unknown} status=$STATUS reason=$REASON"
    [[ "$STATUS" == PASS ]] || FAILED=1
    PORT=$((PORT + 2))
done

if [[ "$FAILED" == 0 ]]; then
    echo "RESULT=SUBSHELL_EMU01_SMOKE_PASS"
else
    echo "RESULT=SUBSHELL_EMU01_SMOKE_FAIL"
fi
exit "$FAILED"
