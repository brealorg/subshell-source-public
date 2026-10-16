#!/usr/bin/env bash
set -u
set -o pipefail

SLOT="${1:-}"
ADB_SERIAL="${ADB_SERIAL:-}"

case "$SLOT" in
  dev1)
    EXPECTED_PACKAGE_ID="io.github.brealorg.subshell.debug.dev1"
    ;;
  dev2)
    EXPECTED_PACKAGE_ID="io.github.brealorg.subshell.debug.dev2"
    ;;
  *)
    echo "USAGE=$0 dev1|dev2"
    echo "STOP=DEBUG_SLOT_REQUIRED"
    exit 64
    ;;
esac

if [ -z "$ADB_SERIAL" ]; then
  echo "STOP=ADB_SERIAL_REQUIRED"
  echo "USAGE=ADB_SERIAL=<serial> $0 $SLOT"
  exit 65
fi

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO" || exit 66

GRADLE=(
  ./gradlew
  --no-daemon
  "-Psubshell.debugSlot=$SLOT"
  -Pkotlin.incremental=false
  -Pkotlin.incremental.useClasspathSnapshot=false
  -x :app:subshellContracts
)

echo "DEBUG_SLOT=$SLOT"
echo "EXPECTED_PACKAGE_ID=$EXPECTED_PACKAGE_ID"
echo "ADB_SERIAL=$ADB_SERIAL"
echo "INFO=EXPLICIT_SUBSHELL_DEBUG_SLOT_REQUIRED"
echo "INFO=SKIPPING_APP_SUBSHELLCONTRACTS_FULL_GLOBAL_GATE_IN_DEPLOY_HELPER"

"${GRADLE[@]}" :app:assembleDebug || exit $?

APK="app/build/outputs/apk/debug/app-debug.apk"
META="app/build/outputs/apk/debug/output-metadata.json"
[ -f "$APK" ] || { echo "STOP=DEBUG_APK_NOT_FOUND"; exit 67; }
[ -f "$META" ] || { echo "STOP=OUTPUT_METADATA_NOT_FOUND"; exit 68; }

python3 - "$META" "$EXPECTED_PACKAGE_ID" <<'PY'
import json, sys
meta, expected = sys.argv[1], sys.argv[2]
with open(meta, "r", encoding="utf-8") as f:
    data = json.load(f)
actual = data.get("applicationId")
print(f"OUTPUT_METADATA_APPLICATION_ID={actual}")
if actual != expected:
    print(f"STOP=WRONG_OUTPUT_APPLICATION_ID expected={expected} actual={actual}")
    raise SystemExit(69)
PY
[ "$?" -eq 0 ] || exit $?

APK_ANALYZER="${ANDROID_HOME:-$HOME/Android/Sdk}/cmdline-tools/latest/bin/apkanalyzer"
[ -x "$APK_ANALYZER" ] || { echo "STOP=APK_ANALYZER_NOT_FOUND"; exit 70; }

APK_ID="$("$APK_ANALYZER" manifest application-id "$APK" | tr -d '\r\n')"
echo "APK_APPLICATION_ID=$APK_ID"
[ "$APK_ID" = "$EXPECTED_PACKAGE_ID" ] || {
  echo "STOP=WRONG_APK_APPLICATION_ID expected=$EXPECTED_PACKAGE_ID actual=$APK_ID"
  exit 71
}

LOCAL_SHA="$(sha256sum "$APK" | awk '{print $1}')"
echo "LOCAL_APK_SHA256=$LOCAL_SHA"

adb -s "$ADB_SERIAL" get-state || exit $?
adb -s "$ADB_SERIAL" install -r "$APK" || exit $?

DEVICE_APK="$(
  adb -s "$ADB_SERIAL" shell pm path "$EXPECTED_PACKAGE_ID" |
  head -n1 |
  sed 's/^package://' |
  tr -d '\r'
)"
[ -n "$DEVICE_APK" ] || { echo "STOP=INSTALLED_PACKAGE_PATH_EMPTY"; exit 72; }
echo "DEVICE_APK=$DEVICE_APK"

PULLED_APK="$(mktemp --suffix=.apk)"
trap 'rm -f "$PULLED_APK"' EXIT
adb -s "$ADB_SERIAL" pull "$DEVICE_APK" "$PULLED_APK" >/dev/null || exit $?

DEVICE_SHA="$(sha256sum "$PULLED_APK" | awk '{print $1}')"
echo "DEVICE_APK_SHA256=$DEVICE_SHA"

[ "$LOCAL_SHA" = "$DEVICE_SHA" ] || {
  echo "STOP=INSTALLED_APK_HASH_MISMATCH"
  exit 73
}

echo "PASS=DEBUG_SLOT_APPLICATION_ID_VERIFIED"
echo "PASS=LOCAL_AND_INSTALLED_APK_HASH_IDENTICAL"
echo "RESULT=SUBSHELL_DEBUG_SLOT_INSTALL_VERIFIED"
