#!/usr/bin/env bash
# A11Y01_RELEASE_ACCESSIBILITY_CHECK_V1 (#246, owner 2026-10-25: non-blocking):
# runs Google's Accessibility Test Framework over the app's main surfaces
# (Issue246AccessibilityChecksInstrumentedTest) on the API 36 emulator for the
# exact source of a release, and reports; it never stops a release.
#
#   tools/a11y-emulator-check.sh --source DIR --commit SHA --out DIR [--avd NAME]
#
# --source is a git checkout that holds SHA (the qualified release source);
# it is only read: `git archive SHA` is unpacked under --out and built there
# (offline, debug + androidTest APKs), because the qualified source must stay
# untouched for Stage C. The emulator boots as tools/emulator-smoke.sh does
# (-memory 4096, read-only, no window) and every adb call names its serial, so
# the phone is never used. ATF does not evaluate Compose under Robolectric,
# hence the emulator.
#
# Writes $OUT/a11y-result.env and prints
#   A11Y_RESULT=PASS      every surface had no accessibility error
#   A11Y_RESULT=FINDINGS  the tests ran and at least one surface had errors
#   A11Y_RESULT=FAILED    the check could not run (build, boot, install)
# Exit 0 for all three; 2 only for usage errors.
set -u -o pipefail

SOURCE=""
COMMIT=""
OUT=""
AVD="subshell-api36"
PORT=5590
TEST_CLASS="app.subshell.prototype.ui.Issue246AccessibilityChecksInstrumentedTest"
usage() { echo "STOP=USAGE: $0 --source DIR --commit SHA --out DIR [--avd NAME]" >&2; exit 2; }
while [[ $# -gt 0 ]]; do
    case "$1" in
        --source) SOURCE="${2:-}"; shift 2 ;;
        --commit) COMMIT="${2:-}"; shift 2 ;;
        --out) OUT="${2:-}"; shift 2 ;;
        --avd) AVD="${2:-}"; shift 2 ;;
        *) usage ;;
    esac
done
[[ -d "$SOURCE" && -n "$COMMIT" && -n "$OUT" ]] || usage
[[ ! -e "$OUT" ]] || { echo "STOP=OUT_EXISTS" >&2; exit 2; }
mkdir -p "$OUT" || exit 2

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="$SDK/emulator/emulator"
AAPT2="$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
SERIAL="emulator-$PORT"

finish() {
    local result="$1" reason="$2"
    {
        echo "A11Y_RESULT=$result"
        echo "A11Y_REASON=$reason"
        echo "A11Y_COMMIT=$COMMIT"
        echo "A11Y_AVD=$AVD"
    } >"$OUT/a11y-result.env"
    echo "A11Y_RESULT=$result reason=$reason"
    exit 0
}

[[ -x "$EMULATOR" && -x "$AAPT2" ]] || finish FAILED SDK_TOOLS_MISSING

BUILD="$OUT/source"
mkdir -p "$BUILD"
git -C "$SOURCE" archive "$COMMIT" | tar -x -C "$BUILD" || finish FAILED SOURCE_ARCHIVE_FAILED
(
    cd "$BUILD" &&
        ANDROID_HOME="$SDK" ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
            --offline --no-daemon --no-configuration-cache
) >"$OUT/build.log" 2>&1 || finish FAILED BUILD_FAILED
APP_APK="$BUILD/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$BUILD/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APP_APK" && -f "$TEST_APK" ]] || finish FAILED APKS_MISSING
TEST_PACKAGE="$("$AAPT2" dump packagename "$TEST_APK" 2>/dev/null)"
[[ -n "$TEST_PACKAGE" ]] || finish FAILED TEST_PACKAGE_UNREADABLE

"$EMULATOR" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
    -no-snapshot -read-only -memory 4096 -gpu swiftshader_indirect >"$OUT/emulator.log" 2>&1 &
EMULATOR_PID=$!
stop_emulator() {
    adb -s "$SERIAL" emu kill >/dev/null 2>&1
    for _ in $(seq 1 30); do kill -0 "$EMULATOR_PID" 2>/dev/null || break; sleep 1; done
    kill "$EMULATOR_PID" 2>/dev/null
}
trap stop_emulator EXIT

booted=0
for _ in $(seq 1 150); do
    if [[ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        booted=1
        break
    fi
    kill -0 "$EMULATOR_PID" 2>/dev/null || break
    sleep 2
done
[[ "$booted" == 1 ]] || finish FAILED EMULATOR_DID_NOT_BOOT
adb -s "$SERIAL" install -r -t "$APP_APK" >"$OUT/install.log" 2>&1 || finish FAILED INSTALL_FAILED
adb -s "$SERIAL" install -r -t "$TEST_APK" >>"$OUT/install.log" 2>&1 || finish FAILED INSTALL_FAILED

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r -e class "$TEST_CLASS" \
    "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" >"$OUT/instrument.txt" 2>&1
adb -s "$SERIAL" logcat -d -s SubshellA11y:I >"$OUT/warnings.txt" 2>/dev/null

if grep -qE '^OK \([0-9]+ tests?\)' "$OUT/instrument.txt"; then
    finish PASS "$(grep -oE '^OK \([0-9]+ tests?\)' "$OUT/instrument.txt")"
elif grep -qE '^Tests run: [0-9]+,  Failures: [0-9]+' "$OUT/instrument.txt"; then
    finish FINDINGS "$(grep -oE '^Tests run: [0-9]+,  Failures: [0-9]+' "$OUT/instrument.txt")"
else
    finish FAILED INSTRUMENTATION_DID_NOT_REPORT
fi
