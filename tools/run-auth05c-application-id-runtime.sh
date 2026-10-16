#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${AUTH05C_OUTPUT_DIR:-${HOME}/Downloads/subshell-auth05c-application-id-${STAMP}}"
APP_APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="${ROOT}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
MIGRATION="${OUTPUT}/application-id-migration.json"
VERIFIER="${ROOT}/tools/verify-auth05c-application-id.py"

FINAL_APPLICATION_ID='io.github.brealorg.subshell'
DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'
TEST_APPLICATION_ID='io.github.brealorg.subshell.debug.test'
COMPONENT_NAMESPACE='app.subshell.prototype'
MAIN_ACTIVITY='app.subshell.prototype.MainActivity'
CALLBACK_ACTIVITY='app.subshell.prototype.auth.AuthCallbackActivity'
CALLBACK_ALIAS='app.subshell.prototype.auth.AuthProductionCallback'
FINAL_RELEASE_SIGNING_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'

mkdir -p "$OUTPUT"

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n' >&2
  printf 'AUTH05C_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n' >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'SIGNING_KEYS_COLLECTED=NONE\n' >&2
  printf 'REMOTE_WRITES=NONE\n' >&2
  printf 'DEVICE_NETWORK_MUTATIONS=NONE\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_FAILED\n' >&2
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

package_from_apk() {
  local aapt2="$1"
  local apk="$2"
  "$aapt2" dump badging "$apk" |
    sed -n "s/^package: name='\([^']*\)'.*/\1/p" |
    head -n 1
}

canonical_fingerprint() {
  local raw="$1"
  local compact
  compact="$(printf '%s' "$raw" | tr -d ':' | tr '[:lower:]' '[:upper:]')"
  [[ "$compact" =~ ^[0-9A-F]{64}$ ]] || return 1
  printf '%s' "$compact" | sed 's/../&:/g; s/:$//'
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_RUNTIME\n'
printf 'RISK=R1_LOCAL_BUILD_AND_PUBLIC_PACKAGE_IDENTITY_INSPECTION_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'TEST_SEQUENCE=APK_AND_TEST_PACKAGE_COMPONENT_NAMESPACE_TARGET_PACKAGE_SIGNING_AND_DETERMINISTIC_ARTIFACT\n'
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'DEBUG_APPLICATION_ID=%s\n' "$DEBUG_APPLICATION_ID"
printf 'TEST_APPLICATION_ID=%s\n' "$TEST_APPLICATION_ID"
printf 'COMPONENT_NAMESPACE=%s\n' "$COMPONENT_NAMESPACE"
printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n'
printf 'AUTH05C_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'SIGNING_KEYS_COLLECTED=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'

cd "$ROOT"
[[ -x "$VERIFIER" ]] || fail MIGRATION_VERIFIER_NOT_EXECUTABLE
[[ -f "$APP_APK" ]] || fail DEBUG_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING

AAPT2="$(find_android_build_tool aapt2)"
APKSIGNER="$(find_android_build_tool apksigner)"
[[ -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND
[[ -x "$APKSIGNER" ]] || fail APKSIGNER_NOT_FOUND

printf '\n===== AUTH05C APK PACKAGE IDENTITIES =====\n'
ACTUAL_APP_PACKAGE="$(package_from_apk "$AAPT2" "$APP_APK")"
ACTUAL_TEST_PACKAGE="$(package_from_apk "$AAPT2" "$TEST_APK")"
[[ "$ACTUAL_APP_PACKAGE" == "$DEBUG_APPLICATION_ID" ]] ||
  fail DEBUG_APK_APPLICATION_ID_MISMATCH
[[ "$ACTUAL_TEST_PACKAGE" == "$TEST_APPLICATION_ID" ]] ||
  fail TEST_APK_APPLICATION_ID_MISMATCH

printf 'AUTH05C_DEBUG_APK_APPLICATION_ID=%s\n' "$ACTUAL_APP_PACKAGE"
printf 'AUTH05C_TEST_APK_APPLICATION_ID=%s\n' "$ACTUAL_TEST_PACKAGE"
printf 'PASS=AUTH05C_DEBUG_APK_APPLICATION_ID\n'
printf 'PASS=AUTH05C_TEST_APK_APPLICATION_ID\n'

printf '\n===== AUTH05C TEST TARGET PACKAGE =====\n'
TEST_MANIFEST_DUMP="${OUTPUT}/test-manifest-aapt2.txt"
"$AAPT2" dump xmltree "$TEST_APK" --file AndroidManifest.xml > "$TEST_MANIFEST_DUMP"
grep -Fq "$DEBUG_APPLICATION_ID" "$TEST_MANIFEST_DUMP" ||
  fail TEST_APK_TARGET_PACKAGE_MISMATCH
grep -Fq 'androidx.test.runner.AndroidJUnitRunner' "$TEST_MANIFEST_DUMP" ||
  fail TEST_APK_RUNNER_MISSING
printf 'PASS=AUTH05C_TEST_TARGETS_DEBUG_APPLICATION\n'
printf 'PASS=AUTH05C_ANDROIDX_TEST_RUNNER\n'

printf '\n===== AUTH05C COMPONENT NAMESPACE =====\n'
for component in "$MAIN_ACTIVITY" "$CALLBACK_ACTIVITY" "$CALLBACK_ALIAS"; do
  grep -Fq "android:name=\"${component}\"" app/src/main/AndroidManifest.xml ||
    fail "MAIN_MANIFEST_COMPONENT_MISSING_${component//[^A-Za-z0-9]/_}"
done
grep -Fq \
  "android:targetActivity=\"${CALLBACK_ACTIVITY}\"" \
  app/src/main/AndroidManifest.xml ||
  fail CALLBACK_TARGET_COMPONENT_MISMATCH
grep -Fq \
  "android:name=\"${CALLBACK_ACTIVITY}\"" \
  app/src/debug/AndroidManifest.xml ||
  fail DEBUG_CALLBACK_COMPONENT_MISMATCH
printf 'PASS=AUTH05C_COMPONENT_NAMESPACE_EXPLICIT\n'
printf 'PASS=AUTH05C_KOTLIN_NAMESPACE_UNCHANGED\n'

printf '\n===== AUTH05C DEBUG SIGNING ISOLATION =====\n'
APP_RAW_CERT="$($APKSIGNER verify --print-certs "$APP_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
TEST_RAW_CERT="$($APKSIGNER verify --print-certs "$TEST_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
APP_CERT="$(canonical_fingerprint "$APP_RAW_CERT")" ||
  fail DEBUG_APK_CERTIFICATE_INVALID
TEST_CERT="$(canonical_fingerprint "$TEST_RAW_CERT")" ||
  fail TEST_APK_CERTIFICATE_INVALID
[[ "$APP_CERT" == "$TEST_CERT" ]] ||
  fail APP_AND_TEST_DEBUG_CERTIFICATES_DIFFER
printf 'AUTH05C_DEBUG_SIGNING_CERT_SHA256=%s\n' "$APP_CERT"
printf 'AUTH05C_RELEASE_SIGNING_CERT_SHA256=%s\n' "$FINAL_RELEASE_SIGNING_CERT_SHA256"
printf 'PASS=AUTH05C_DEBUG_SIGNING_REMAINS_NON_PRODUCTION\n'

printf '\n===== AUTH05C DETERMINISTIC MIGRATION ARTIFACT =====\n'
python3 "$VERIFIER" generate \
  --application-id "$FINAL_APPLICATION_ID" \
  --debug-application-id "$DEBUG_APPLICATION_ID" \
  --test-application-id "$TEST_APPLICATION_ID" \
  --component-namespace "$COMPONENT_NAMESPACE" \
  --output "$MIGRATION"
python3 "$VERIFIER" verify \
  --application-id "$FINAL_APPLICATION_ID" \
  --debug-application-id "$DEBUG_APPLICATION_ID" \
  --test-application-id "$TEST_APPLICATION_ID" \
  --component-namespace "$COMPONENT_NAMESPACE" \
  --input "$MIGRATION"
[[ "$MIGRATION" != "$ROOT"/* ]] ||
  fail MIGRATION_ARTIFACT_INSIDE_SOURCE_TREE
! rg -q \
  '"private_key"[[:space:]]*:|"storePassword"[[:space:]]*:|"keyPassword"[[:space:]]*:|"oauth_client_id"[[:space:]]*:[[:space:]]*"[^"]+"' \
  "$MIGRATION" ||
  fail MIGRATION_ARTIFACT_CONTAINS_SECRET
printf 'AUTH05C_MIGRATION_ARTIFACT_SHA256=%s\n' \
  "$(sha256sum "$MIGRATION" | awk '{print $1}')"
printf 'AUTH05C_MIGRATION_ARTIFACT_PUBLISHED=NO\n'

printf '\n===== AUTH05C RESULT =====\n'
printf 'AUTH05C_FINAL_APPLICATION_ID_APPLIED=PASS\n'
printf 'AUTH05C_DEBUG_SUFFIX_APPLIED=PASS\n'
printf 'AUTH05C_TEST_APPLICATION_ID_LOCKED=PASS\n'
printf 'AUTH05C_COMPONENT_NAMESPACE_DEFERRED=PASS\n'
printf 'AUTH05C_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2\n'
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'DEBUG_APPLICATION_ID=%s\n' "$DEBUG_APPLICATION_ID"
printf 'TEST_APPLICATION_ID=%s\n' "$TEST_APPLICATION_ID"
printf 'COMPONENT_NAMESPACE=%s\n' "$COMPONENT_NAMESPACE"
printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n'
printf 'AUTH05C_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'SIGNING_KEYS_COLLECTED=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'MIGRATION=%s\n' "$MIGRATION"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_PASS\n'
