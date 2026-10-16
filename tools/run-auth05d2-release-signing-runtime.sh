#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${AUTH05D2_OUTPUT_DIR:-${HOME}/Downloads/subshell-auth05d2-release-signing-${STAMP}}"
SERIAL="${AUTH05D2_ADB_SERIAL:-19021FDF600GBN}"
VERIFIER="${ROOT}/tools/verify-auth05d2-release-signing.py"
KEYSTORE_ARTIFACT="${OUTPUT}/release-signing-inputs.json"
APK_ARTIFACT="${OUTPUT}/release-apk-verification.json"
BUILD_LOG="${OUTPUT}/release-build.log"
TEST_BUILD_LOG="${OUTPUT}/android-test-build.log"
AUTH05_LOG="${OUTPUT}/auth05-runtime.log"
AUTH05C_LOG="${OUTPUT}/auth05c-runtime.log"
LAUNCH_LOG="${OUTPUT}/release-launch.log"
POLL_LOG="${OUTPUT}/release-foreground-poll.log"

FINAL_APPLICATION_ID='io.github.brealorg.subshell'
DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'
TEST_APPLICATION_ID='io.github.brealorg.subshell.debug.test'
COMPONENT_NAMESPACE='app.subshell.prototype'
MAIN_ACTIVITY='app.subshell.prototype.MainActivity'
KEY_ALIAS='subshell-app-signing'
EXPECTED_RELEASE_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'
EXPECTED_KEYSTORE_SHA256='7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7'
RETIRED_PROTOTYPE_APPLICATION_ID='app.subshell.prototype'

RELEASE_APK="${ROOT}/app/build/outputs/apk/release/app-release.apk"
DEBUG_APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="${ROOT}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

TMP_ROOT=''
STORE_PASSWORD=''
KEY_PASSWORD=''
KEYSTORE_PATH="${SUBSHELL_RELEASE_KEYSTORE_PATH:-}"
RELEASE_PID=''

mkdir -p "$OUTPUT"

cleanup() {
  STORE_PASSWORD=''
  KEY_PASSWORD=''
  unset SUBSHELL_RELEASE_KEYSTORE_PATH
  unset SUBSHELL_RELEASE_STORE_PASSWORD_FILE
  unset SUBSHELL_RELEASE_KEY_PASSWORD_FILE
  if [[ -n "$TMP_ROOT" && -d "$TMP_ROOT" ]]; then
    rm -rf -- "$TMP_ROOT"
  fi
}
trap cleanup EXIT

fail() {
  local reason="$1"
  printf 'FAIL=%s\n' "$reason" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'PASSWORDS_LOGGED=NO\n' >&2
  printf 'PRIVATE_KEY_COPIED=NO\n' >&2
  printf 'AUTH05D2_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n' >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'REMOTE_WRITES=NONE\n' >&2
  printf 'DEVICE_NETWORK_MUTATIONS=NONE\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_RUNTIME_FAILED\n' >&2
  exit 1
}

find_android_build_tool() {
  local tool="$1"
  local direct
  direct="$(command -v "$tool" 2>/dev/null || true)"
  if [[ -n "$direct" ]]; then
    printf '%s\n' "$direct"
    return 0
  fi

  local sdk
  for sdk in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "${HOME}/Android/Sdk"; do
    [[ -n "$sdk" && -d "$sdk/build-tools" ]] || continue
    find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$tool" -print 2>/dev/null
  done | sort -V | tail -n 1
}

canonical_fingerprint() {
  local raw="$1"
  local compact
  compact="$(printf '%s' "$raw" | tr -d ':' | tr '[:lower:]' '[:upper:]')"
  [[ "$compact" =~ ^[0-9A-F]{64}$ ]] || return 1
  printf '%s' "$compact" | sed 's/../&:/g; s/:$//'
}

package_present() {
  local package_name="$1"
  adb -s "$SERIAL" shell pm list packages "$package_name" 2>/dev/null |
    tr -d '\r' |
    grep -Fxq "package:${package_name}"
}

package_uid() {
  local package_name="$1"
  adb -s "$SERIAL" shell cmd package list packages -U "$package_name" 2>/dev/null |
    tr -d '\r' |
    sed -nE "s/^package:${package_name}[[:space:]]+uid:([0-9]+)$/\1/p" |
    head -n 1
}

printf 'APPROVED_OPERATION=BUILD_VERIFY_INSTALL_AND_LAUNCH_SUBSHELL_AUTH05D2_RELEASE_SIGNED_APK_V1\n'
printf 'RISK=R2_LOCAL_REVERSIBLE_SOURCE_BUILD_PERMANENT_RELEASE_SIGNED_PACKAGE_INSTALL_AND_APP_LAUNCH\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'DEBUG_APPLICATION_ID=%s\n' "$DEBUG_APPLICATION_ID"
printf 'TEST_APPLICATION_ID=%s\n' "$TEST_APPLICATION_ID"
printf 'COMPONENT_NAMESPACE=%s\n' "$COMPONENT_NAMESPACE"
printf 'KEY_ALIAS=%s\n' "$KEY_ALIAS"
printf 'EXPECTED_RELEASE_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'EXPECTED_KEYSTORE_SHA256=%s\n' "$EXPECTED_KEYSTORE_SHA256"
printf 'KEYSTORE_PATH_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_COMMAND_LINE=NO\n'
printf 'AUTH05D2_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'

printf '\n===== 1/10 SOURCE TOOL AND DEVICE PREFLIGHT =====\n'
cd "$ROOT"
for command_name in bash python3 keytool openssl sha256sum adb rg awk sed grep find stat mktemp; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done
[[ -x ./gradlew ]] || fail GRADLE_WRAPPER_NOT_EXECUTABLE
[[ -x "$VERIFIER" ]] || fail AUTH05D2_VERIFIER_NOT_EXECUTABLE
AUTH05D2_CURRENT_VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' app/build.gradle.kts
)"
[[ "$AUTH05D2_CURRENT_VERSION_CODE" =~ ^[0-9]+$ ]] &&
  (( AUTH05D2_CURRENT_VERSION_CODE >= 56 )) ||
  fail AUTH05D2_VERSION_CODE_MISSING
grep -Fq 'SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1' app/build.gradle.kts ||
  fail AUTH05D2_IMPLEMENTATION_MARKER_MISSING
[[ "$(sha256sum docs/auth/subshell-app-signing-cert-v1.pem | awk '{print $1}')" == \
   '3f3daf38830c065621dd244324f5960a0b2751cb7fcd56210d1c1b5bfff2045a' ]] ||
  fail AUTH05D2_PUBLIC_CERTIFICATE_SOURCE_MISMATCH

adb -s "$SERIAL" get-state 2>/dev/null | grep -Fxq device ||
  fail ADB_DEVICE_NOT_READY
printf 'DEVICE=%s\n' "$(adb -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
printf 'SOURCE_TOOL_AND_DEVICE_PREFLIGHT=PASS\n'

printf '\n===== 2/10 EXTERNAL KEYSTORE AND HIDDEN PASSWORD INPUT =====\n'
if [[ -z "$KEYSTORE_PATH" ]]; then
  read -r -p 'Absolute path to subshell app-signing JKS: ' KEYSTORE_PATH
fi
[[ "$KEYSTORE_PATH" == /* ]] || fail KEYSTORE_PATH_NOT_ABSOLUTE
[[ -f "$KEYSTORE_PATH" ]] || fail KEYSTORE_NOT_FOUND
[[ "$(stat -Lc '%a' "$KEYSTORE_PATH")" =~ ^[0-7]*[06]00$ ]] ||
  fail KEYSTORE_MODE_NOT_PRIVATE

read -r -s -p 'Keystore password: ' STORE_PASSWORD
printf '\n'
read -r -s -p 'Private-key password: ' KEY_PASSWORD
printf '\n'
[[ "${#STORE_PASSWORD}" -ge 24 ]] || fail STORE_PASSWORD_TOO_SHORT
[[ "${#KEY_PASSWORD}" -ge 24 ]] || fail KEY_PASSWORD_TOO_SHORT
[[ "$STORE_PASSWORD" != "$KEY_PASSWORD" ]] || fail STORE_AND_KEY_PASSWORDS_MUST_DIFFER

if [[ -d /dev/shm && -w /dev/shm ]]; then
  TMP_ROOT="$(mktemp -d /dev/shm/subshell-auth05d2.XXXXXX)"
else
  TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/subshell-auth05d2.XXXXXX")"
fi
chmod 700 "$TMP_ROOT"
STORE_PASSWORD_FILE="$TMP_ROOT/store.pass"
KEY_PASSWORD_FILE="$TMP_ROOT/key.pass"
printf '%s' "$STORE_PASSWORD" > "$STORE_PASSWORD_FILE"
printf '%s' "$KEY_PASSWORD" > "$KEY_PASSWORD_FILE"
chmod 600 "$STORE_PASSWORD_FILE" "$KEY_PASSWORD_FILE"

export SUBSHELL_RELEASE_KEYSTORE_PATH="$KEYSTORE_PATH"
export SUBSHELL_RELEASE_STORE_PASSWORD_FILE="$STORE_PASSWORD_FILE"
export SUBSHELL_RELEASE_KEY_PASSWORD_FILE="$KEY_PASSWORD_FILE"
unset SUBSHELL_REDDIT_CLIENT_ID
unset SUBSHELL_AUTH_REDIRECT_URI
unset SUBSHELL_FINAL_APPLICATION_ID
unset SUBSHELL_FINAL_SIGNING_CERT_SHA256
printf 'EXTERNAL_KEYSTORE_PATH=ACCEPTED_NOT_LOGGED\n'
printf 'PASSWORD_FILES=TMPFS_OR_PRIVATE_TEMP_600\n'
printf 'PASSWORDS_ECHOED=NO\n'
printf 'PASSWORDS_LOGGED=NO\n'

printf '\n===== 3/10 EXACT KEYSTORE PRIVATE-KEY AND PUBLIC-CERTIFICATE VERIFY =====\n'
python3 "$VERIFIER" keystore \
  --keystore "$KEYSTORE_PATH" \
  --store-password-file "$STORE_PASSWORD_FILE" \
  --key-password-file "$KEY_PASSWORD_FILE" \
  --key-alias "$KEY_ALIAS" \
  --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
  --expected-keystore-sha256 "$EXPECTED_KEYSTORE_SHA256" \
  --application-id "$FINAL_APPLICATION_ID" \
  --source-root "$ROOT" \
  --output "$KEYSTORE_ARTIFACT"
python3 "$VERIFIER" verify-artifact --input "$KEYSTORE_ARTIFACT"
! rg -q '/home/|"storePassword"|"keyPassword"|"private_key"[[:space:]]*:' "$KEYSTORE_ARTIFACT" ||
  fail KEYSTORE_ARTIFACT_CONTAINS_PRIVATE_PATH_OR_SECRET
printf 'KEYSTORE_ARTIFACT_SHA256=%s\n' "$(sha256sum "$KEYSTORE_ARTIFACT" | awk '{print $1}')"
printf 'EXACT_RELEASE_SIGNING_IDENTITY=PASS\n'

printf '\n===== 4/10 SIGNED RELEASE BUILD WITH CANONICAL QUALITY GATE =====\n'
./gradlew :app:assembleRelease \
  --offline --no-daemon --no-configuration-cache --rerun-tasks \
  > >(tee "$BUILD_LOG") 2>&1
[[ -f "$RELEASE_APK" ]] || fail RELEASE_APK_MISSING
grep -Fq 'RESULT=SUBSHELL_ALL_CONTRACTS_PASS' "$BUILD_LOG" ||
  fail ALL_CONTRACTS_RESULT_MISSING
grep -Fq 'RESULT=SUBSHELL_QUALITY_GATE_PASS' "$BUILD_LOG" ||
  fail QUALITY_GATE_RESULT_MISSING
grep -Fq 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_IDENTITY_PASS' "$BUILD_LOG" ||
  fail GRADLE_RELEASE_SIGNING_IDENTITY_RESULT_MISSING
grep -Fq 'BUILD SUCCESSFUL' "$BUILD_LOG" || fail RELEASE_BUILD_NOT_SUCCESSFUL
printf 'SIGNED_RELEASE_BUILD=PASS\n'

printf '\n===== 5/10 ANDROID TEST APK BUILD =====\n'
./gradlew :app:assembleDebugAndroidTest \
  --offline --no-daemon --no-configuration-cache \
  > >(tee "$TEST_BUILD_LOG") 2>&1
[[ -f "$DEBUG_APK" ]] || fail DEBUG_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
grep -Fq 'BUILD SUCCESSFUL' "$TEST_BUILD_LOG" || fail TEST_APK_BUILD_NOT_SUCCESSFUL
printf 'ANDROID_TEST_APK_BUILD=PASS\n'

printf '\n===== 6/10 RELEASE APK PACKAGE AND SIGNATURE VERIFY =====\n'
AAPT2="$(find_android_build_tool aapt2)"
APKSIGNER="$(find_android_build_tool apksigner)"
[[ -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND
[[ -x "$APKSIGNER" ]] || fail APKSIGNER_NOT_FOUND
python3 "$VERIFIER" apk \
  --apk "$RELEASE_APK" \
  --aapt2 "$AAPT2" \
  --apksigner "$APKSIGNER" \
  --application-id "$FINAL_APPLICATION_ID" \
  --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
  --output "$APK_ARTIFACT"
python3 "$VERIFIER" verify-artifact --input "$APK_ARTIFACT"
! rg -q '/home/|"storePassword"|"keyPassword"|"private_key"[[:space:]]*:' "$APK_ARTIFACT" ||
  fail APK_ARTIFACT_CONTAINS_PRIVATE_PATH_OR_SECRET
RELEASE_APK_SHA256="$(sha256sum "$RELEASE_APK" | awk '{print $1}')"
printf 'RELEASE_APK_SHA256=%s\n' "$RELEASE_APK_SHA256"
printf 'RELEASE_APK_PACKAGE_AND_SIGNATURE=PASS\n'

printf '\n===== 7/10 DEBUG RELEASE AND TEST SIGNING ISOLATION =====\n'
DEBUG_RAW_CERT="$($APKSIGNER verify --print-certs "$DEBUG_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
TEST_RAW_CERT="$($APKSIGNER verify --print-certs "$TEST_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
DEBUG_CERT="$(canonical_fingerprint "$DEBUG_RAW_CERT")" || fail DEBUG_CERT_INVALID
TEST_CERT="$(canonical_fingerprint "$TEST_RAW_CERT")" || fail TEST_CERT_INVALID
[[ "$DEBUG_CERT" == "$TEST_CERT" ]] || fail DEBUG_AND_TEST_CERT_MISMATCH
[[ "$DEBUG_CERT" != "$EXPECTED_RELEASE_CERT_SHA256" ]] ||
  fail DEBUG_CERT_EQUALS_RELEASE_CERT
printf 'DEBUG_SIGNING_CERT_SHA256=%s\n' "$DEBUG_CERT"
printf 'RELEASE_SIGNING_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'DEBUG_RELEASE_CERTIFICATE_ISOLATION=PASS\n'

printf '\n===== 8/10 AUTH05 AND AUTH05C HOST REGRESSIONS =====\n'
AUTH05_OUTPUT_DIR="$OUTPUT/auth05-runtime" \
  bash tools/run-auth05-production-identity-runtime.sh \
  > >(tee "$AUTH05_LOG") 2>&1
AUTH05C_OUTPUT_DIR="$OUTPUT/auth05c-runtime" \
  bash tools/run-auth05c-application-id-runtime.sh \
  > >(tee "$AUTH05C_LOG") 2>&1
grep -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_PASS' "$AUTH05_LOG" ||
  fail AUTH05_RUNTIME_REGRESSION
grep -Fq 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_PASS' "$AUTH05C_LOG" ||
  fail AUTH05C_RUNTIME_REGRESSION
printf 'AUTH05_REGRESSION=PASS\n'
printf 'AUTH05C_REGRESSION=PASS\n'

printf '\n===== 9/10 RELEASE PACKAGE INSTALL COEXISTENCE AND LAUNCH =====\n'
DEBUG_PRESENT_BEFORE='NO'
PROTOTYPE_PRESENT_BEFORE='NO'
package_present "$DEBUG_APPLICATION_ID" && DEBUG_PRESENT_BEFORE='YES'
package_present "$RETIRED_PROTOTYPE_APPLICATION_ID" && PROTOTYPE_PRESENT_BEFORE='YES'
printf 'DEBUG_PACKAGE_PRESENT_BEFORE=%s\n' "$DEBUG_PRESENT_BEFORE"
printf 'RETIRED_PROTOTYPE_PACKAGE_PRESENT_BEFORE=%s\n' "$PROTOTYPE_PRESENT_BEFORE"

adb -s "$SERIAL" install -r "$RELEASE_APK" >/dev/null
package_present "$FINAL_APPLICATION_ID" || fail RELEASE_PACKAGE_NOT_INSTALLED
RELEASE_UID="$(package_uid "$FINAL_APPLICATION_ID")"
[[ -n "$RELEASE_UID" ]] || fail RELEASE_PACKAGE_UID_MISSING
printf 'RELEASE_PACKAGE_UID=%s\n' "$RELEASE_UID"

if [[ "$DEBUG_PRESENT_BEFORE" == 'YES' ]]; then
  DEBUG_UID="$(package_uid "$DEBUG_APPLICATION_ID")"
  [[ -n "$DEBUG_UID" && "$DEBUG_UID" != "$RELEASE_UID" ]] ||
    fail RELEASE_AND_DEBUG_UID_NOT_ISOLATED
  printf 'DEBUG_PACKAGE_UID=%s\n' "$DEBUG_UID"
  printf 'RELEASE_DEBUG_UID_ISOLATION=PASS\n'
fi
if [[ "$PROTOTYPE_PRESENT_BEFORE" == 'YES' ]]; then
  PROTOTYPE_UID="$(package_uid "$RETIRED_PROTOTYPE_APPLICATION_ID")"
  [[ -n "$PROTOTYPE_UID" && "$PROTOTYPE_UID" != "$RELEASE_UID" ]] ||
    fail RELEASE_AND_PROTOTYPE_UID_NOT_ISOLATED
  printf 'RETIRED_PROTOTYPE_PACKAGE_UID=%s\n' "$PROTOTYPE_UID"
  printf 'RELEASE_PROTOTYPE_UID_ISOLATION=PASS\n'
fi

adb -s "$SERIAL" shell am force-stop "$FINAL_APPLICATION_ID"
adb -s "$SERIAL" shell am start -W \
  -n "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}" \
  > >(tee "$LAUNCH_LOG") 2>&1
for attempt in $(seq 1 12); do
  activity="$(adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null |
    grep -m1 'topResumedActivity=' || true)"
  printf 'ATTEMPT=%s ACTIVITY=%s\n' "$attempt" "$activity" | tee -a "$POLL_LOG"
  if grep -Fq "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}" <<<"$activity"; then
    printf 'FOREGROUND_MATCH_ATTEMPT=%s\n' "$attempt"
    break
  fi
  sleep 1
done
grep -Fq "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}" "$POLL_LOG" ||
  fail RELEASE_MAIN_ACTIVITY_NOT_FOREGROUND
RELEASE_PID="$(adb -s "$SERIAL" shell pidof "$FINAL_APPLICATION_ID" | tr -d '\r')"
[[ -n "$RELEASE_PID" ]] || fail RELEASE_PROCESS_PID_MISSING
printf 'RELEASE_PID=%s\n' "$RELEASE_PID"
printf 'RELEASE_MAIN_ACTIVITY_FOREGROUND=PASS\n'
printf 'DEBUG_PACKAGE_UNINSTALLED=NO\n'
printf 'RETIRED_PROTOTYPE_PACKAGE_UNINSTALLED=NO\n'

printf '\n===== 10/10 FINAL AUTH05D2 RESULT =====\n'
sha256sum \
  app/build.gradle.kts \
  docs/auth/subshell-app-signing-cert-v1.pem \
  docs/auth/subshell-auth05d2-release-signing-wiring-contract-v1.md \
  tools/check-auth05d2-contract.sh \
  tools/run-auth05d2-release-signing-runtime.sh \
  tools/verify-auth05d2-release-signing.py
printf 'SOURCE_CONTRACT_IDENTITY=AUTH05D2_VERSION_56_0_54_0\n'
printf 'AUTH05D2_EXTERNAL_SIGNING_INPUTS=PASS\n'
printf 'AUTH05D2_EXACT_KEYSTORE_SHA256=PASS\n'
printf 'AUTH05D2_PRIVATE_KEY_ACCESS=PASS\n'
printf 'AUTH05D2_RELEASE_APK_APPLICATION_ID=PASS\n'
printf 'AUTH05D2_RELEASE_APK_CERTIFICATE=PASS\n'
printf 'AUTH05D2_DEBUG_RELEASE_SIGNING_ISOLATION=PASS\n'
printf 'AUTH05D2_RELEASE_PACKAGE_INSTALL_AND_LAUNCH=PASS\n'
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'FINAL_RELEASE_SIGNING_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n'
printf 'KEYSTORE_PATH_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_SOURCE_OR_LOGS=NONE\n'
printf 'PRIVATE_KEY_COPIED=NO\n'
printf 'AUTH05D2_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'RELEASE_APK=%s\n' "$RELEASE_APK"
printf 'RELEASE_APK_SHA256=%s\n' "$RELEASE_APK_SHA256"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'PID=%s\n' "$RELEASE_PID"
printf 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_AND_RUNTIME_PASS\n'
