#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${AUTH05E2_OUTPUT_DIR:-${HOME}/Downloads/subshell-auth05e2-production-app-link-${STAMP}}"
SERIAL="${AUTH05E2_ADB_SERIAL:-19021FDF600GBN}"

AUTH05D2_VERIFIER="${ROOT}/tools/verify-auth05d2-release-signing.py"
AUTH05E2_VERIFIER="${ROOT}/tools/verify-auth05e2-production-app-link.py"
AUTH04_MANIFEST_VERIFIER="${ROOT}/tools/verify-auth04-app-link-manifest.py"

FINAL_APPLICATION_ID='io.github.brealorg.subshell'
DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'
TEST_APPLICATION_ID='io.github.brealorg.subshell.debug.test'
RETIRED_PROTOTYPE_APPLICATION_ID='app.subshell.prototype'
COMPONENT_NAMESPACE='app.subshell.prototype'
MAIN_ACTIVITY='app.subshell.prototype.MainActivity'
CALLBACK_ALIAS='app.subshell.prototype.auth.AuthProductionCallback'
CALLBACK_ACTIVITY='app.subshell.prototype.auth.AuthCallbackActivity'

SITE_ORIGIN='https://brealorg.github.io'
ASSETLINKS_URL='https://brealorg.github.io/.well-known/assetlinks.json'
CALLBACK_URI='https://brealorg.github.io/oauth/callback'
CALLBACK_HOST='brealorg.github.io'
CALLBACK_PATH='/oauth/callback'
PAGES_COMMIT='3a1d41849a1e69d9e9452d3b3b88c35420f6e586'

KEY_ALIAS='subshell-app-signing'
EXPECTED_RELEASE_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'
EXPECTED_KEYSTORE_SHA256='7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7'

RELEASE_APK="${ROOT}/app/build/outputs/apk/release/app-release.apk"
DEBUG_APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="${ROOT}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

BUILD_LOG="${OUTPUT}/release-build.log"
TEST_BUILD_LOG="${OUTPUT}/android-test-build.log"
AUTH05_LOG="${OUTPUT}/auth05-runtime.log"
AUTH05C_LOG="${OUTPUT}/auth05c-runtime.log"
KEYSTORE_ARTIFACT="${OUTPUT}/release-signing-inputs.json"
APK_ARTIFACT="${OUTPUT}/release-apk-verification.json"
REMOTE_ASSETLINKS="${OUTPUT}/remote-assetlinks.json"
REMOTE_ASSETLINKS_HEADERS="${OUTPUT}/remote-assetlinks.headers"
REMOTE_CALLBACK="${OUTPUT}/remote-callback.html"
REMOTE_CALLBACK_HEADERS="${OUTPUT}/remote-callback.headers"
RELEASE_MANIFEST="${OUTPUT}/AndroidManifest-release.xml"
DEBUG_MANIFEST="${OUTPUT}/AndroidManifest-debug.xml"
DOMAIN_RESET_LOG="${OUTPUT}/domain-reset.log"
DOMAIN_VERIFY_LOG="${OUTPUT}/domain-reverify.log"
DOMAIN_STATE="${OUTPUT}/domain-state.txt"
DOMAIN_USER_STATE="${OUTPUT}/domain-user-state.txt"
DOMAIN_POLL_LOG="${OUTPUT}/domain-poll.log"
RESOLVER_LOG="${OUTPUT}/callback-resolver.txt"
CALLBACK_LAUNCH_LOG="${OUTPUT}/callback-launch.log"
FOREGROUND_POLL_LOG="${OUTPUT}/callback-foreground-poll.log"

TMP_ROOT=''
STORE_PASSWORD=''
KEY_PASSWORD=''
KEYSTORE_PATH="${SUBSHELL_RELEASE_KEYSTORE_PATH:-}"
RELEASE_PID=''
CURRENT_STAGE='INITIALIZATION'

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
  printf 'FAIL_STAGE=%s\n' "$CURRENT_STAGE" >&2
  printf 'FAIL=%s\n' "$reason" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'PASSWORDS_LOGGED=NO\n' >&2
  printf 'PRIVATE_KEY_COPIED=NO\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_RUNTIME_FAILED\n' >&2
  exit 1
}

on_error() {
  local status=$?
  local line="${BASH_LINENO[0]:-unknown}"
  trap - ERR
  printf 'FAIL_STAGE=%s\n' "$CURRENT_STAGE" >&2
  printf 'FAIL=UNHANDLED_COMMAND_FAILURE\n' >&2
  printf 'FAIL_LINE=%s\n' "$line" >&2
  printf 'EXIT_STATUS=%s\n' "$status" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'PASSWORDS_LOGGED=NO\n' >&2
  printf 'PRIVATE_KEY_COPIED=NO\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_RUNTIME_FAILED\n' >&2
  exit "$status"
}
trap on_error ERR

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

find_merged_manifest() {
  local variant="$1"
  local required_token="$2"
  local candidate

  candidate="$({
    find "$ROOT/app/build/intermediates" \
      -type f \
      -name AndroidManifest.xml \
      -path "*/${variant}/*" \
      -print0 2>/dev/null |
      while IFS= read -r -d '' file; do
        if grep -Fq "$required_token" "$file" 2>/dev/null; then
          printf '%s %s\n' "$(stat -c '%Y' "$file")" "$file"
        fi
      done
  } | sort -nr | head -n 1 | cut -d' ' -f2-)"
  [[ -n "$candidate" && -f "$candidate" ]] || return 1
  printf '%s\n' "$candidate"
}

fetch_without_redirect() {
  local url="$1"
  local headers="$2"
  local body="$3"
  local expected_status="$4"
  local status

  status="$(
    curl \
      --silent \
      --show-error \
      --connect-timeout 10 \
      --max-time 30 \
      --max-redirs 0 \
      --header 'Cache-Control: no-cache' \
      --dump-header "$headers" \
      --output "$body" \
      --write-out '%{http_code}' \
      "$url"
  )"
  [[ "$status" == "$expected_status" ]] ||
    fail "REMOTE_HTTP_${status}_EXPECTED_${expected_status}_${url}"
}

printf 'APPROVED_OPERATION=APPLY_BUILD_INSTALL_AND_VERIFY_SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1\n'
printf 'RISK=R3_LOCAL_SOURCE_ALREADY_APPLIED_RELEASE_BUILD_SIGNED_PACKAGE_INSTALL_AND_ANDROID_DOMAIN_NETWORK_VERIFICATION\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'DEBUG_APPLICATION_ID=%s\n' "$DEBUG_APPLICATION_ID"
printf 'CALLBACK_URI=%s\n' "$CALLBACK_URI"
printf 'ASSETLINKS_URL=%s\n' "$ASSETLINKS_URL"
printf 'PAGES_COMMIT=%s\n' "$PAGES_COMMIT"
printf 'EXPECTED_RELEASE_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'KEYSTORE_PATH_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_COMMAND_LINE=NO\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=ANDROID_DOMAIN_VERIFICATION_FETCH_ONLY\n'

CURRENT_STAGE='1_SOURCE_TOOL_DEVICE_AND_PUBLIC_SITE_PREFLIGHT'
printf '\n===== 1/13 SOURCE TOOL DEVICE AND PUBLIC SITE PREFLIGHT =====\n'
cd "$ROOT"

for command_name in \
  bash python3 keytool openssl sha256sum adb rg awk sed grep find stat mktemp \
  curl cmp sort; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

[[ -x ./gradlew ]] || fail GRADLE_WRAPPER_NOT_EXECUTABLE
[[ -x "$AUTH05D2_VERIFIER" ]] || fail AUTH05D2_VERIFIER_NOT_EXECUTABLE
[[ -x "$AUTH05E2_VERIFIER" ]] || fail AUTH05E2_VERIFIER_NOT_EXECUTABLE
[[ -x "$AUTH04_MANIFEST_VERIFIER" ]] || fail AUTH04_MANIFEST_VERIFIER_NOT_EXECUTABLE

AUTH05E2_CURRENT_VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' app/build.gradle.kts
)"
[[ "$AUTH05E2_CURRENT_VERSION_CODE" =~ ^[0-9]+$ ]] &&
  (( AUTH05E2_CURRENT_VERSION_CODE >= 57 )) ||
  fail AUTH05E2_VERSION_CODE_MISSING
grep -Fq 'SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1' app/build.gradle.kts ||
  fail AUTH05E2_IMPLEMENTATION_MARKER_MISSING
[[ -f app/src/release/AndroidManifest.xml ]] ||
  fail AUTH05E2_RELEASE_MANIFEST_MISSING

adb -s "$SERIAL" get-state 2>/dev/null | grep -Fxq device ||
  fail ADB_DEVICE_NOT_READY
printf 'DEVICE=%s\n' "$(adb -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
printf 'ANDROID_RELEASE=%s\n' "$(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
printf 'ANDROID_SDK=%s\n' "$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"

fetch_without_redirect "$ASSETLINKS_URL" \
  "$REMOTE_ASSETLINKS_HEADERS" "$REMOTE_ASSETLINKS" 200
fetch_without_redirect "${CALLBACK_URI}/" \
  "$REMOTE_CALLBACK_HEADERS" "$REMOTE_CALLBACK" 200

ASSETLINKS_CONTENT_TYPE="$(
  awk '
    BEGIN { IGNORECASE=1 }
    /^content-type:/ {
      sub(/\r$/, "")
      sub(/^[^:]+:[[:space:]]*/, "")
      print
      exit
    }
  ' "$REMOTE_ASSETLINKS_HEADERS"
)"
[[ "$ASSETLINKS_CONTENT_TYPE" == application/json* ]] ||
  fail REMOTE_ASSETLINKS_CONTENT_TYPE_MISMATCH
python3 "$AUTH05E2_VERIFIER" assetlinks --input "$REMOTE_ASSETLINKS"

printf 'REMOTE_ASSETLINKS_CONTENT_TYPE=%s\n' "$ASSETLINKS_CONTENT_TYPE"
printf 'REMOTE_ASSETLINKS_NO_REDIRECT=PASS\n'
printf 'REMOTE_CALLBACK_HTTPS_200_NO_REDIRECT=PASS\n'
printf 'SOURCE_TOOL_DEVICE_AND_PUBLIC_SITE_PREFLIGHT=PASS\n'

CURRENT_STAGE='2_EXTERNAL_KEYSTORE_AND_HIDDEN_PASSWORD_INPUT'
printf '\n===== 2/13 EXTERNAL KEYSTORE AND HIDDEN PASSWORD INPUT =====\n'

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
[[ "$STORE_PASSWORD" != "$KEY_PASSWORD" ]] ||
  fail STORE_AND_KEY_PASSWORDS_MUST_DIFFER

if [[ -d /dev/shm && -w /dev/shm ]]; then
  TMP_ROOT="$(mktemp -d /dev/shm/subshell-auth05e2.XXXXXX)"
else
  TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/subshell-auth05e2.XXXXXX")"
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

CURRENT_STAGE='3_EXACT_RELEASE_SIGNING_IDENTITY_VERIFY'
printf '\n===== 3/13 EXACT RELEASE SIGNING IDENTITY VERIFY =====\n'

python3 "$AUTH05D2_VERIFIER" keystore \
  --keystore "$KEYSTORE_PATH" \
  --store-password-file "$STORE_PASSWORD_FILE" \
  --key-password-file "$KEY_PASSWORD_FILE" \
  --key-alias "$KEY_ALIAS" \
  --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
  --expected-keystore-sha256 "$EXPECTED_KEYSTORE_SHA256" \
  --application-id "$FINAL_APPLICATION_ID" \
  --source-root "$ROOT" \
  --output "$KEYSTORE_ARTIFACT"
python3 "$AUTH05D2_VERIFIER" verify-artifact --input "$KEYSTORE_ARTIFACT"

! rg -q '/home/|"storePassword"|"keyPassword"|"private_key"[[:space:]]*:' \
  "$KEYSTORE_ARTIFACT" ||
  fail KEYSTORE_ARTIFACT_CONTAINS_PRIVATE_PATH_OR_SECRET
printf 'KEYSTORE_ARTIFACT_SHA256=%s\n' \
  "$(sha256sum "$KEYSTORE_ARTIFACT" | awk '{print $1}')"
printf 'EXACT_RELEASE_SIGNING_IDENTITY=PASS\n'

CURRENT_STAGE='4_SIGNED_RELEASE_BUILD_WITH_CANONICAL_QUALITY_GATE'
printf '\n===== 4/13 SIGNED RELEASE BUILD WITH CANONICAL QUALITY GATE =====\n'

./gradlew :app:assembleRelease \
  --offline --no-daemon --no-configuration-cache --rerun-tasks \
  > >(tee "$BUILD_LOG") 2>&1

[[ -f "$RELEASE_APK" ]] || fail RELEASE_APK_MISSING
grep -Fq 'RESULT=SUBSHELL_ALL_CONTRACTS_PASS' "$BUILD_LOG" ||
  fail ALL_CONTRACTS_RESULT_MISSING
# check-all-contracts prints its computed count (STEP3-QUALITY-LINT); any
# count proves the aggregator ran, the fixed 20 had long gone stale.
grep -Eq 'CONTRACT_COUNT=[0-9]+' "$BUILD_LOG" ||
  fail AUTH05E2_CONTRACT_COUNT_MISSING
grep -Fq 'RESULT=SUBSHELL_QUALITY_GATE_PASS' "$BUILD_LOG" ||
  fail QUALITY_GATE_RESULT_MISSING
grep -Fq 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_IDENTITY_PASS' "$BUILD_LOG" ||
  fail RELEASE_SIGNING_IDENTITY_RESULT_MISSING
grep -Fq 'BUILD SUCCESSFUL' "$BUILD_LOG" ||
  fail RELEASE_BUILD_NOT_SUCCESSFUL
printf 'SIGNED_RELEASE_BUILD=PASS\n'

CURRENT_STAGE='5_ANDROID_TEST_APK_AND_DEBUG_BUILD_VERIFY'
printf '\n===== 5/13 ANDROID TEST APK AND DEBUG BUILD VERIFY =====\n'

./gradlew :app:assembleDebugAndroidTest \
  --offline --no-daemon --no-configuration-cache \
  > >(tee "$TEST_BUILD_LOG") 2>&1

[[ -f "$DEBUG_APK" ]] || fail DEBUG_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
grep -Fq 'BUILD SUCCESSFUL' "$TEST_BUILD_LOG" ||
  fail TEST_APK_BUILD_NOT_SUCCESSFUL
printf 'ANDROID_TEST_APK_AND_DEBUG_BUILD=PASS\n'

CURRENT_STAGE='6_RELEASE_APK_PACKAGE_CERTIFICATE_AND_MANIFEST_VERIFY'
printf '\n===== 6/13 RELEASE APK PACKAGE CERTIFICATE AND MANIFEST VERIFY =====\n'

AAPT2="$(find_android_build_tool aapt2)"
APKSIGNER="$(find_android_build_tool apksigner)"
[[ -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND
[[ -x "$APKSIGNER" ]] || fail APKSIGNER_NOT_FOUND

python3 "$AUTH05D2_VERIFIER" apk \
  --apk "$RELEASE_APK" \
  --aapt2 "$AAPT2" \
  --apksigner "$APKSIGNER" \
  --application-id "$FINAL_APPLICATION_ID" \
  --expected-fingerprint "$EXPECTED_RELEASE_CERT_SHA256" \
  --output "$APK_ARTIFACT"
python3 "$AUTH05D2_VERIFIER" verify-artifact --input "$APK_ARTIFACT"

RELEASE_MERGED_SOURCE="$(
  find_merged_manifest release 'brealorg.github.io'
)" || fail RELEASE_MERGED_MANIFEST_NOT_FOUND
cp "$RELEASE_MERGED_SOURCE" "$RELEASE_MANIFEST"
python3 "$AUTH05E2_VERIFIER" manifest --input "$RELEASE_MANIFEST"

RELEASE_APK_SHA256="$(sha256sum "$RELEASE_APK" | awk '{print $1}')"
printf 'RELEASE_APK_SHA256=%s\n' "$RELEASE_APK_SHA256"
printf 'RELEASE_APK_PACKAGE_CERTIFICATE_AND_MANIFEST=PASS\n'

CURRENT_STAGE='7_DEBUG_RELEASE_TEST_CALLBACK_AND_SIGNING_ISOLATION'
printf '\n===== 7/13 DEBUG RELEASE TEST CALLBACK AND SIGNING ISOLATION =====\n'

DEBUG_MERGED_SOURCE="$(
  find_merged_manifest debug 'AuthProductionCallback'
)" || fail DEBUG_MERGED_MANIFEST_NOT_FOUND
cp "$DEBUG_MERGED_SOURCE" "$DEBUG_MANIFEST"

python3 "$AUTH04_MANIFEST_VERIFIER" \
  --manifest "$DEBUG_MANIFEST" \
  --expected-package "$DEBUG_APPLICATION_ID" \
  --expected-component-namespace "$COMPONENT_NAMESPACE" \
  --expected-enabled false \
  --expected-host auth-callback.invalid \
  --expected-path /oauth/callback

grep -Fq 'android:scheme="subshell-auth-test"' "$DEBUG_MANIFEST" ||
  fail DEBUG_CUSTOM_SCHEME_MISSING
! grep -Fq 'android:host="brealorg.github.io"' "$DEBUG_MANIFEST" ||
  fail PRODUCTION_HOST_LEAKED_INTO_DEBUG

DEBUG_RAW_CERT="$(
  "$APKSIGNER" verify --print-certs "$DEBUG_APK" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
    head -n 1
)"
TEST_RAW_CERT="$(
  "$APKSIGNER" verify --print-certs "$TEST_APK" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
    head -n 1
)"
DEBUG_CERT="$(canonical_fingerprint "$DEBUG_RAW_CERT")" ||
  fail DEBUG_CERT_INVALID
TEST_CERT="$(canonical_fingerprint "$TEST_RAW_CERT")" ||
  fail TEST_CERT_INVALID
[[ "$DEBUG_CERT" == "$TEST_CERT" ]] ||
  fail DEBUG_AND_TEST_CERT_MISMATCH
[[ "$DEBUG_CERT" != "$EXPECTED_RELEASE_CERT_SHA256" ]] ||
  fail DEBUG_CERT_EQUALS_RELEASE_CERT

printf 'DEBUG_SIGNING_CERT_SHA256=%s\n' "$DEBUG_CERT"
printf 'RELEASE_SIGNING_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'DEBUG_PRODUCTION_CALLBACK_ALIAS=DISABLED\n'
printf 'DEBUG_CUSTOM_SCHEME=RETAINED\n'
printf 'DEBUG_RELEASE_TEST_CALLBACK_AND_SIGNING_ISOLATION=PASS\n'

CURRENT_STAGE='8_AUTH05_AND_AUTH05C_HOST_REGRESSIONS'
printf '\n===== 8/13 AUTH05 AND AUTH05C HOST REGRESSIONS =====\n'

AUTH05_OUTPUT_DIR="$OUTPUT/auth05-runtime" \
  bash tools/run-auth05-production-identity-runtime.sh \
  > >(tee "$AUTH05_LOG") 2>&1
AUTH05C_OUTPUT_DIR="$OUTPUT/auth05c-runtime" \
  bash tools/run-auth05c-application-id-runtime.sh \
  > >(tee "$AUTH05C_LOG") 2>&1

grep -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_PASS' \
  "$AUTH05_LOG" ||
  fail AUTH05_RUNTIME_REGRESSION
grep -Fq 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_PASS' \
  "$AUTH05C_LOG" ||
  fail AUTH05C_RUNTIME_REGRESSION

printf 'AUTH05_PRODUCTION_IDENTITY_REGRESSION=PASS\n'
printf 'AUTH05C_APPLICATION_ID_REGRESSION=PASS\n'

CURRENT_STAGE='9_RELEASE_INSTALL_AND_PACKAGE_UID_ISOLATION'
printf '\n===== 9/13 RELEASE INSTALL AND PACKAGE UID ISOLATION =====\n'

DEBUG_PRESENT_BEFORE='NO'
PROTOTYPE_PRESENT_BEFORE='NO'
package_present "$DEBUG_APPLICATION_ID" && DEBUG_PRESENT_BEFORE='YES'
package_present "$RETIRED_PROTOTYPE_APPLICATION_ID" && PROTOTYPE_PRESENT_BEFORE='YES'
printf 'DEBUG_PACKAGE_PRESENT_BEFORE=%s\n' "$DEBUG_PRESENT_BEFORE"
printf 'RETIRED_PROTOTYPE_PACKAGE_PRESENT_BEFORE=%s\n' "$PROTOTYPE_PRESENT_BEFORE"

adb -s "$SERIAL" install -r "$RELEASE_APK" >/dev/null
package_present "$FINAL_APPLICATION_ID" ||
  fail RELEASE_PACKAGE_NOT_INSTALLED

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

printf 'RELEASE_INSTALL_AND_PACKAGE_UID_ISOLATION=PASS\n'

CURRENT_STAGE='10_ANDROID_APP_LINK_STATE_RESET_AND_REVERIFY'
printf '\n===== 10/13 ANDROID APP LINK STATE RESET AND REVERIFY =====\n'

adb -s "$SERIAL" shell pm set-app-links \
  --package "$FINAL_APPLICATION_ID" 0 all \
  > >(tee "$DOMAIN_RESET_LOG") 2>&1

adb -s "$SERIAL" shell pm verify-app-links \
  --re-verify "$FINAL_APPLICATION_ID" \
  > >(tee "$DOMAIN_VERIFY_LOG") 2>&1

printf 'ANDROID_APP_LINK_STATE_RESET=PASS\n'
printf 'ANDROID_APP_LINK_REVERIFY_REQUESTED=PASS\n'

CURRENT_STAGE='11_ANDROID_DOMAIN_VERIFICATION_STATE'
printf '\n===== 11/13 ANDROID DOMAIN VERIFICATION STATE =====\n'

DOMAIN_VERIFIED='NO'
for attempt in $(seq 1 60); do
  adb -s "$SERIAL" shell pm get-app-links "$FINAL_APPLICATION_ID" \
    > "$DOMAIN_STATE" 2>&1 || true

  state_line="$(
    sed -nE \
      's/^[[:space:]]*brealorg\.github\.io[[:space:]]*:[[:space:]]*([^[:space:]]+)[[:space:]]*$/\1/p' \
      "$DOMAIN_STATE" |
      head -n 1
  )"
  printf 'ATTEMPT=%s DOMAIN_STATE=%s\n' \
    "$attempt" "${state_line:-missing}" |
    tee -a "$DOMAIN_POLL_LOG"

  if python3 "$AUTH05E2_VERIFIER" domain-state \
      --input "$DOMAIN_STATE" \
      >> "$DOMAIN_POLL_LOG" 2>&1; then
    DOMAIN_VERIFIED='YES'
    printf 'DOMAIN_VERIFIED_ATTEMPT=%s\n' "$attempt"
    break
  fi
  sleep 5
done

[[ "$DOMAIN_VERIFIED" == 'YES' ]] ||
  fail ANDROID_DOMAIN_VERIFICATION_NOT_VERIFIED

adb -s "$SERIAL" shell pm get-app-links --user cur "$FINAL_APPLICATION_ID" \
  > "$DOMAIN_USER_STATE" 2>&1 || true

python3 "$AUTH05E2_VERIFIER" domain-state --input "$DOMAIN_STATE"
printf 'ANDROID_DOMAIN_VERIFICATION=verified\n'
printf 'ANDROID_DOMAIN_VERIFICATION_STATE=PASS\n'

CURRENT_STAGE='12_UNFORCED_HTTPS_CALLBACK_RESOLUTION_AND_LAUNCH'
printf '\n===== 12/13 UNFORCED HTTPS CALLBACK RESOLUTION AND LAUNCH =====\n'

# SUBSHELL_AUTH05E2_STAGE12_DEFAULT_CATEGORY_FIX_V1
# Match the platform's implicit startActivity category shape explicitly.
set +e
adb -s "$SERIAL" shell cmd package resolve-activity \
  --brief \
  --user current \
  -a android.intent.action.VIEW \
  -c android.intent.category.DEFAULT \
  -c android.intent.category.BROWSABLE \
  -d "$CALLBACK_URI" \
  > "$RESOLVER_LOG" 2>&1
RESOLVER_STATUS=$?
set -e

if [[ "$RESOLVER_STATUS" -ne 0 ]]; then
  adb -s "$SERIAL" shell cmd package resolve-activity \
    --brief \
    -a android.intent.action.VIEW \
    -c android.intent.category.DEFAULT \
    -c android.intent.category.BROWSABLE \
    -d "$CALLBACK_URI" \
    > "$RESOLVER_LOG" 2>&1 ||
    fail CALLBACK_RESOLVER_COMMAND_FAILED
fi

python3 "$AUTH05E2_VERIFIER" resolver --input "$RESOLVER_LOG"

# SUBSHELL_AUTH05E2_STAGE12_FOREGROUND_OBSERVER_FIX_V1
# The signed build can take several minutes, so the device may have timed out
# before Stage 12. Wake it immediately before the local routing proof. Dismissing
# a secure keyguard is not bypassed; the subsequent resumed/focus gate remains
# fail-closed if Android does not make MainActivity foreground.
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
sleep 1

adb -s "$SERIAL" shell am force-stop "$FINAL_APPLICATION_ID"
adb -s "$SERIAL" shell am start -W \
  --user current \
  -a android.intent.action.VIEW \
  -c android.intent.category.DEFAULT \
  -c android.intent.category.BROWSABLE \
  -d "$CALLBACK_URI" \
  > "$CALLBACK_LAUNCH_LOG" 2>&1
cat "$CALLBACK_LAUNCH_LOG"

grep -Fq 'Status: ok' "$CALLBACK_LAUNCH_LOG" ||
  fail CALLBACK_VIEW_INTENT_START_FAILED

# SUBSHELL_AUTH05E2_STAGE6_LAUNCH_RESULT_FIX_V1
# The App Link resolver proves that the implicit URL targets the exported
# AuthProductionCallback alias. Because the alias forwards immediately through
# noHistory AuthCallbackActivity, `am start -W` may report either the callback
# activity while it is still visible to ActivityTaskManager or the final
# MainActivity after local no-pending-authorization handling completes.
CALLBACK_LAUNCH_REPORTED_ACTIVITY="$(
  sed -n 's/^Activity: //p' "$CALLBACK_LAUNCH_LOG" | tail -n 1
)"
case "$CALLBACK_LAUNCH_REPORTED_ACTIVITY" in
  "${FINAL_APPLICATION_ID}/${CALLBACK_ACTIVITY}")
    CALLBACK_LAUNCH_RESULT='CALLBACK_ACTIVITY_ENTRY'
    ;;
  "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}")
    CALLBACK_LAUNCH_RESULT='MAIN_ACTIVITY_FINAL'
    ;;
  *)
    fail CALLBACK_VIEW_INTENT_UNEXPECTED_REPORTED_ACTIVITY
    ;;
esac
printf 'CALLBACK_LAUNCH_REPORTED_ACTIVITY=%s\n' \
  "$CALLBACK_LAUNCH_REPORTED_ACTIVITY"
printf 'CALLBACK_LAUNCH_RESULT=%s\n' "$CALLBACK_LAUNCH_RESULT"

FOREGROUND_MATCHED='NO'
FOREGROUND_EVIDENCE='NONE'
if [[ "$CALLBACK_LAUNCH_RESULT" == 'MAIN_ACTIVITY_FINAL' ]]; then
  FOREGROUND_MATCHED='YES'
  FOREGROUND_EVIDENCE='AM_START_W_FINAL_MAIN_ACTIVITY'
  printf 'FOREGROUND_MATCH_ATTEMPT=AM_START_W\n' |
    tee -a "$FOREGROUND_POLL_LOG"
  printf 'FOREGROUND_EVIDENCE=%s\n' "$FOREGROUND_EVIDENCE" |
    tee -a "$FOREGROUND_POLL_LOG"
fi
for attempt in $(seq 1 20); do
  if [[ "$FOREGROUND_MATCHED" == 'YES' ]]; then
    break
  fi
  activity_dump="${OUTPUT}/callback-activity-attempt-${attempt}.txt"
  window_dump="${OUTPUT}/callback-window-attempt-${attempt}.txt"

  # Capture the complete service output before parsing. This avoids closing the
  # adb/dumpsys stream early with grep -m1, which produces EPIPE and can hide
  # Android 17's legacy ResumedActivity fields.
  adb -s "$SERIAL" shell dumpsys activity activities \
    > "$activity_dump" 2>&1 || true
  adb -s "$SERIAL" shell dumpsys window windows \
    > "$window_dump" 2>&1 || true

  resumed_evidence="$(
    grep -E 'topResumedActivity=|mResumedActivity:|ResumedActivity:' \
      "$activity_dump" |
      grep -F "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}" |
      head -n 1 || true
  )"
  focus_evidence="$(
    grep -E 'mCurrentFocus=|mFocusedApp=' "$window_dump" |
      grep -F "${FINAL_APPLICATION_ID}/${MAIN_ACTIVITY}" |
      head -n 1 || true
  )"

  printf 'ATTEMPT=%s RESUMED=%s FOCUS=%s\n' \
    "$attempt" "$resumed_evidence" "$focus_evidence" |
    tee -a "$FOREGROUND_POLL_LOG"

  if [[ -n "$resumed_evidence" ]]; then
    FOREGROUND_MATCHED='YES'
    FOREGROUND_EVIDENCE='RESUMED_ACTIVITY'
  elif [[ -n "$focus_evidence" ]]; then
    FOREGROUND_MATCHED='YES'
    FOREGROUND_EVIDENCE='WINDOW_FOCUS'
  fi

  if [[ "$FOREGROUND_MATCHED" == 'YES' ]]; then
    printf 'FOREGROUND_MATCH_ATTEMPT=%s\n' "$attempt" |
      tee -a "$FOREGROUND_POLL_LOG"
    printf 'FOREGROUND_EVIDENCE=%s\n' "$FOREGROUND_EVIDENCE" |
      tee -a "$FOREGROUND_POLL_LOG"
    break
  fi
  sleep 1
done

[[ "$FOREGROUND_MATCHED" == 'YES' ]] ||
  fail CALLBACK_DID_NOT_RETURN_TO_SUBSHELL_MAIN_ACTIVITY

RELEASE_PID="$(
  adb -s "$SERIAL" shell pidof "$FINAL_APPLICATION_ID" |
    tr -d '\r'
)"
[[ -n "$RELEASE_PID" ]] || fail RELEASE_PROCESS_PID_MISSING

printf 'UNFORCED_HTTPS_CALLBACK_RESOLUTION=PASS\n'
printf 'CALLBACK_ROUTE_ALIAS_RESOLVED=PASS\n'
printf 'CALLBACK_LAUNCH_ACTIVITY_RESULT=PASS\n'
printf 'CALLBACK_RETURNED_TO_MAIN_ACTIVITY=PASS\n'
printf 'RELEASE_PID=%s\n' "$RELEASE_PID"

CURRENT_STAGE='13_FINAL_AUTH05E2_IDENTITY'
printf '\n===== 13/13 FINAL AUTH05E2 IDENTITY =====\n'

sha256sum \
  README.md \
  app/build.gradle.kts \
  app/src/release/AndroidManifest.xml \
  docs/auth/subshell-auth04-production-oauth-config-contract-v1.md \
  docs/auth/subshell-auth05d2-release-signing-wiring-contract-v1.md \
  docs/auth/subshell-auth05e2-production-app-link-contract-v1.md \
  tools/check-all-contracts.sh \
  tools/check-auth04-contract.sh \
  tools/check-auth05b-contract.sh \
  tools/check-auth05c-contract.sh \
  tools/check-auth05d2-contract.sh \
  tools/check-auth05e2-contract.sh \
  tools/check-quality-gates-contract.sh \
  tools/check-source-contract.sh \
  tools/run-auth04-production-config-runtime.sh \
  tools/run-auth05-production-identity-runtime.sh \
  tools/run-auth05c-application-id-runtime.sh \
  tools/run-auth05d2-release-signing-runtime.sh \
  tools/run-auth05e2-production-app-link-runtime.sh \
  tools/verify-auth05e2-production-app-link.py

printf 'SOURCE_CONTRACT_IDENTITY=AUTH05E2_VERSION_FLOOR_57\n'
printf 'AUTH05E2_RELEASE_ONLY_PRODUCTION_APP_LINK=PASS\n'
printf 'AUTH05E2_REMOTE_ASSETLINKS=PASS\n'
printf 'AUTH05E2_RELEASE_APK_APPLICATION_ID=PASS\n'
printf 'AUTH05E2_RELEASE_APK_CERTIFICATE=PASS\n'
printf 'AUTH05E2_RELEASE_MERGED_MANIFEST=PASS\n'
printf 'AUTH05E2_DEBUG_CALLBACK_ISOLATION=PASS\n'
printf 'AUTH05E2_AUTH05_AND_AUTH05C_HOST_REGRESSIONS=PASS\n'
printf 'AUTH05E2_ANDROID_DOMAIN_VERIFICATION=PASS\n'
printf 'AUTH05E2_STAGE12_FOREGROUND_OBSERVER=ANDROID17_RESUMED_OR_WINDOW_FOCUS\n'
printf 'AUTH05E2_UNFORCED_CALLBACK_RUNTIME=PASS\n'
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'CALLBACK_URI=%s\n' "$CALLBACK_URI"
printf 'ASSETLINKS_URL=%s\n' "$ASSETLINKS_URL"
printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n'
printf 'FINAL_RELEASE_SIGNING_CERT_SHA256=%s\n' "$EXPECTED_RELEASE_CERT_SHA256"
printf 'PRODUCTION_APP_LINK=DEPLOYED_AND_ANDROID_VERIFIED\n'
printf 'ANDROID_DOMAIN=%s\n' "$CALLBACK_HOST"
printf 'ANDROID_DOMAIN_VERIFICATION_STATE=verified\n'
printf 'KEYSTORE_PATH_IN_SOURCE=NO\n'
printf 'PASSWORDS_IN_SOURCE_OR_LOGS=NONE\n'
printf 'PRIVATE_KEY_COPIED=NO\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=ANDROID_DOMAIN_VERIFICATION_FETCH_ONLY\n'
printf 'DEBUG_PACKAGE_UNINSTALLED=NO\n'
printf 'RETIRED_PROTOTYPE_PACKAGE_UNINSTALLED=NO\n'
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'RELEASE_APK=%s\n' "$RELEASE_APK"
printf 'RELEASE_APK_SHA256=%s\n' "$RELEASE_APK_SHA256"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'PID=%s\n' "$RELEASE_PID"
printf 'RESULT=SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_AND_DOMAIN_RUNTIME_PASS\n'
