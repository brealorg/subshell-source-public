#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH04_ADB_SERIAL:-19021FDF600GBN}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${AUTH04_OUTPUT_DIR:-${HOME}/Downloads/subshell-auth04-config-runtime-${STAMP}}"
POLICY_LOG="${OUTPUT}/configuration-policy.log"
UNCONFIGURED_LOG="${OUTPUT}/manifest-unconfigured-gradle.log"
FIXTURE_LOG="${OUTPUT}/manifest-fixture-gradle.log"
RESTORE_LOG="${OUTPUT}/manifest-restore-gradle.log"
APP_APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
ASSETLINKS="${OUTPUT}/.well-known/assetlinks.json"
MANIFEST_VERIFIER="${ROOT}/tools/verify-auth04-app-link-manifest.py"
ASSETLINKS_TOOL="${ROOT}/tools/generate-auth04-assetlinks.py"
EXPECTED_DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'
EXPECTED_COMPONENT_NAMESPACE='app.subshell.prototype'

mkdir -p "$OUTPUT"

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'REMOTE_WRITES=NONE\n' >&2
  printf 'DEVICE_NETWORK_MUTATIONS=NONE\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH04_PRODUCTION_CONFIG_RUNTIME_FAILED\n' >&2
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

find_merged_manifest() {
  local candidate
  candidate="$({
    find "$ROOT/app/build/intermediates" -type f -name AndroidManifest.xml -print0 2>/dev/null |
      while IFS= read -r -d '' file; do
        if grep -Fq 'AuthProductionCallback' "$file" 2>/dev/null; then
          printf '%s %s\n' "$(stat -c '%Y' "$file")" "$file"
        fi
      done
  } | sort -nr | head -n 1 | cut -d' ' -f2-)"
  [[ -n "$candidate" && -f "$candidate" ]] || return 1
  printf '%s\n' "$candidate"
}

run_manifest_case() {
  local label="$1"
  local fixture="$2"
  local expected_enabled="$3"
  local expected_host="$4"
  local log="$5"
  local copied_manifest="${OUTPUT}/AndroidManifest-${label}.xml"

  ./gradlew :app:processDebugMainManifest \
    '-Psubshell.auth.redditClientId=' \
    '-Psubshell.auth.redirectUri=' \
    "-Psubshell.auth.appLinkValidationFixture=${fixture}" \
    --offline --no-daemon --no-configuration-cache --rerun-tasks \
    > >(tee "$log") 2>&1

  grep -Fq 'BUILD SUCCESSFUL' "$log" ||
    fail "MANIFEST_${label}_BUILD_FAILED"

  local merged
  merged="$(find_merged_manifest)" ||
    fail "MANIFEST_${label}_OUTPUT_NOT_FOUND"
  cp "$merged" "$copied_manifest"

  python3 "$MANIFEST_VERIFIER" \
    --manifest "$copied_manifest" \
    --expected-package "$EXPECTED_DEBUG_APPLICATION_ID" \
    --expected-component-namespace "$EXPECTED_COMPONENT_NAMESPACE" \
    --expected-enabled "$expected_enabled" \
    --expected-host "$expected_host" \
    --expected-path /oauth/callback
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH04_PRODUCTION_CONFIGURATION_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_BUILD_INTERMEDIATES_AND_LOCAL_DEPLOYMENT_ARTIFACT_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'TEST_SEQUENCE=FAIL_CLOSED_POLICY_UNCONFIGURED_MANIFEST_FIXTURE_MANIFEST_EXACT_RESOLUTION_ASSETLINKS_AND_RESTORE\n'
printf 'AUTH04_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

cd "$ROOT"

for file in "$MANIFEST_VERIFIER" "$ASSETLINKS_TOOL"; do
  [[ -x "$file" ]] || fail "TOOL_NOT_EXECUTABLE_$(basename "$file")"
done

# AUTH04 validation must not inherit a live configuration from the shell.
unset SUBSHELL_REDDIT_CLIENT_ID
unset SUBSHELL_AUTH_REDIRECT_URI

printf '\n===== AUTH04 FAIL-CLOSED CONFIGURATION POLICY =====\n'
./gradlew :app:verifyAuth04ConfigurationPolicy \
  '-Psubshell.auth.redditClientId=' \
  '-Psubshell.auth.redirectUri=' \
  '-Psubshell.auth.appLinkValidationFixture=false' \
  --offline --no-daemon --no-configuration-cache --rerun-tasks \
  > >(tee "$POLICY_LOG") 2>&1

grep -Fq 'RESULT=SUBSHELL_AUTH04_CONFIGURATION_POLICY_PASS' "$POLICY_LOG" ||
  fail CONFIGURATION_POLICY_MARKER_MISSING
printf 'AUTH04_CONFIGURATION_POLICY=PASS\n'

if [[ "${AUTH04_SKIP_BUILD:-0}" != '1' ]]; then
  printf '\n===== AUTH04 UNCONFIGURED DEBUG APK BUILD =====\n'
  ./gradlew :app:assembleDebug \
    '-Psubshell.auth.redditClientId=' \
    '-Psubshell.auth.redirectUri=' \
    '-Psubshell.auth.appLinkValidationFixture=false' \
    --offline --no-daemon --no-configuration-cache
fi
[[ -f "$APP_APK" ]] || fail APP_APK_MISSING

printf '\n===== AUTH04 UNCONFIGURED MERGED MANIFEST =====\n'
run_manifest_case \
  unconfigured false false auth-callback.invalid "$UNCONFIGURED_LOG"
printf 'AUTH04_UNCONFIGURED_ALIAS=DISABLED\n'

printf '\n===== AUTH04 MANIFEST-ONLY VALIDATION FIXTURE =====\n'
run_manifest_case \
  fixture true true auth04-fixture.invalid "$FIXTURE_LOG"
printf 'AUTH04_FIXTURE_ALIAS=ENABLED_FOR_MERGED_MANIFEST_PROOF_ONLY\n'
printf 'AUTH04_FIXTURE_APK_BUILT=NO\n'
printf 'AUTH04_FIXTURE_INSTALLED=NO\n'
printf 'AUTH04_FIXTURE_DOMAIN_OWNERSHIP_CLAIMED=NO\n'

printf '\n===== AUTH04 DEBUG CALLBACK ISOLATION =====\n'
grep -Fq 'android:scheme="subshell-auth-test"' app/src/debug/AndroidManifest.xml ||
  fail DEBUG_CALLBACK_SCHEME_MISSING
grep -Fq 'android:host="oauth"' app/src/debug/AndroidManifest.xml ||
  fail DEBUG_CALLBACK_HOST_MISSING
grep -Fq 'android:path="/callback"' app/src/debug/AndroidManifest.xml ||
  fail DEBUG_CALLBACK_PATH_MISSING
! grep -Fq 'subshell-auth-test' app/src/main/AndroidManifest.xml ||
  fail DEBUG_CALLBACK_LEAKED_TO_MAIN_MANIFEST
printf 'AUTH04_DEBUG_CUSTOM_SCHEME=TEST_ONLY_PASS\n'

printf '\n===== AUTH04 LOCAL ASSETLINKS DEPLOYMENT ARTIFACT =====\n'
AAPT2="$(find_android_build_tool aapt2)"
APKSIGNER="$(find_android_build_tool apksigner)"
[[ -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND
[[ -x "$APKSIGNER" ]] || fail APKSIGNER_NOT_FOUND

PACKAGE_NAME="$($AAPT2 dump badging "$APP_APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)"
[[ "$PACKAGE_NAME" == "$EXPECTED_DEBUG_APPLICATION_ID" ]] ||
  fail APK_APPLICATION_ID_MISMATCH

CERT_SHA256="$($APKSIGNER verify --print-certs "$APP_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
[[ -n "$CERT_SHA256" ]] || fail APK_CERTIFICATE_SHA256_MISSING

python3 "$ASSETLINKS_TOOL" generate \
  --package-name "$PACKAGE_NAME" \
  --sha256-cert-fingerprint "$CERT_SHA256" \
  --output "$ASSETLINKS"
python3 "$ASSETLINKS_TOOL" verify \
  --package-name "$PACKAGE_NAME" \
  --sha256-cert-fingerprint "$CERT_SHA256" \
  --input "$ASSETLINKS"

[[ "$ASSETLINKS" == "$ROOT"/* ]] &&
  fail ASSETLINKS_WRITTEN_INSIDE_SOURCE_TREE
printf 'AUTH04_ASSETLINKS_SIGNING_IDENTITY=DEBUG_VALIDATION_ONLY\n'
printf 'AUTH04_ASSETLINKS_PUBLISHED=NO\n'

printf '\n===== AUTH04 RESTORE UNCONFIGURED MERGED MANIFEST =====\n'
run_manifest_case \
  restored-unconfigured false false auth-callback.invalid "$RESTORE_LOG"
printf 'AUTH04_BUILD_INTERMEDIATES_RESTORED=UNCONFIGURED\n'

printf '\n===== AUTH04 RESULT =====\n'
printf 'AUTH04_FAIL_CLOSED_CONFIGURATION=PASS\n'
printf 'AUTH04_INVALID_CONFIGURATION_MATRIX=PASS\n'
printf 'AUTH04_UNCONFIGURED_CALLBACK_DISABLED=PASS\n'
printf 'AUTH04_FIXTURE_CONFIGURED_MERGED_MANIFEST=PASS\n'
printf 'AUTH04_EXACT_APPLICATION_ID=PASS\n'
printf 'AUTH04_EXACT_CALLBACK_PATH=PASS\n'
printf 'AUTH04_AUTOVERIFY_DECLARATION=PASS\n'
printf 'AUTH04_DETERMINISTIC_INTENT_RESOLUTION=PASS\n'
printf 'AUTH04_ASSETLINKS_GENERATION_AND_VERIFICATION=PASS\n'
printf 'AUTH04_PUBLICATION=NONE\n'
printf 'AUTH04_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'ROOM_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'APP_APK=%s\n' "$APP_APK"
printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'ASSETLINKS=%s\n' "$ASSETLINKS"
printf 'ASSETLINKS_SHA256=%s\n' "$(sha256sum "$ASSETLINKS" | awk '{print $1}')"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'RESULT=SUBSHELL_AUTH04_PRODUCTION_CONFIGURATION_RUNTIME_PASS\n'
