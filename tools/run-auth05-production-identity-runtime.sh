#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${AUTH05_OUTPUT_DIR:-${HOME}/Downloads/subshell-auth05-production-identity-${STAMP}}"
POLICY_LOG="${OUTPUT}/identity-policy.log"
CONFIGURED_POLICY_LOG="${OUTPUT}/identity-policy-configured-fixture.log"
APP_APK="${ROOT}/app/build/outputs/apk/debug/app-debug.apk"
DECISION="${OUTPUT}/identity-decision-fixture.json"
VALIDATOR="${ROOT}/tools/validate-auth05-production-identity.py"
FIXTURE_APPLICATION_ID='org.subshell.validationfixture'
FIXTURE_FINGERPRINT='10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F:20:21:22:23:24:25:26:27:28:29:2A:2B:2C:2D:2E:2F'
FINAL_APPLICATION_ID='io.github.brealorg.subshell'
DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'
RETIRED_PROTOTYPE_APPLICATION_ID='app.subshell.prototype'
# SUBSHELL_AUTH05D2_FINAL_RELEASE_CERT_RUNTIME_MIGRATION_V1
FINAL_RELEASE_SIGNING_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'

mkdir -p "$OUTPUT"

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  printf 'OUTPUT=%s\n' "$OUTPUT" >&2
  printf 'LIVE_REDDIT_NETWORK=NONE\n' >&2
  printf 'LIVE_OAUTH_CLIENT_ID=NONE\n' >&2
  printf 'SIGNING_KEYS_COLLECTED=NONE\n' >&2
  printf 'REMOTE_WRITES=NONE\n' >&2
  printf 'DEVICE_NETWORK_MUTATIONS=NONE\n' >&2
  printf 'GITHUB_MUTATIONS=NONE\n' >&2
  printf 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_FAILED\n' >&2
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

expect_rejected() {
  local label="$1"
  shift
  if python3 "$VALIDATOR" validate "$@" >/dev/null 2>&1; then
    fail "AUTH05_EXPECTED_REJECTION_MISSING_${label}"
  fi
  printf 'PASS=AUTH05_REJECTED_%s\n' "$label"
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_BUILD_INTERMEDIATES_AND_LOCAL_PUBLIC_IDENTITY_ARTIFACT_ONLY\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'TEST_SEQUENCE=FAIL_CLOSED_POLICY_CURRENT_DEBUG_IDENTITY_REJECTION_FIXTURE_DECISION_AND_NON_APPLICATION\n'
printf 'PRODUCTION_IDENTITY_APPLIED=APPLICATION_ID_AND_RELEASE_SIGNING_WIRING\n'
printf 'AUTH05_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'SIGNING_KEYS_COLLECTED=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'

cd "$ROOT"
[[ -x "$VALIDATOR" ]] || fail VALIDATOR_NOT_EXECUTABLE
[[ -f "$APP_APK" ]] || fail DEBUG_APK_MISSING

unset SUBSHELL_FINAL_APPLICATION_ID
unset SUBSHELL_FINAL_SIGNING_CERT_SHA256
unset SUBSHELL_REDDIT_CLIENT_ID
unset SUBSHELL_AUTH_REDIRECT_URI

printf '\n===== AUTH05 FAIL-CLOSED GRADLE POLICY =====\n'
./gradlew :app:verifyAuth05ProductionIdentityPolicy \
  '-Psubshell.identity.applicationId=' \
  '-Psubshell.identity.signingCertSha256=' \
  '-Psubshell.auth.redditClientId=' \
  '-Psubshell.auth.redirectUri=' \
  --offline --no-daemon --no-configuration-cache --rerun-tasks \
  > >(tee "$POLICY_LOG") 2>&1

grep -Fq 'PASS=AUTH05_UNCONFIGURED_IDENTITY_NOT_APPLIED' "$POLICY_LOG" ||
  fail UNCONFIGURED_POLICY_MARKER_MISSING
grep -Fq 'PASS=AUTH05_APPLICATION_ID_MIGRATION_APPLIED' "$POLICY_LOG" ||
  fail APPLICATION_ID_MIGRATION_MARKER_MISSING
grep -Fq "AUTH05_SELECTED_APPLICATION_ID=${FINAL_APPLICATION_ID}" "$POLICY_LOG" ||
  fail SELECTED_APPLICATION_ID_MARKER_MISSING
grep -Fq 'AUTH05_EXTERNAL_PRODUCTION_IDENTITY_CONFIGURED=false' "$POLICY_LOG" ||
  fail UNCONFIGURED_EXTERNAL_STATE_MISMATCH
grep -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_POLICY_PASS' "$POLICY_LOG" ||
  fail IDENTITY_POLICY_RESULT_MISSING
printf 'AUTH05_UNCONFIGURED_POLICY=PASS\n'

printf '\n===== AUTH05 EXTERNAL FIXTURE VALIDATION ONLY =====\n'
./gradlew :app:verifyAuth05ProductionIdentityPolicy \
  "-Psubshell.identity.applicationId=${FIXTURE_APPLICATION_ID}" \
  "-Psubshell.identity.signingCertSha256=${FIXTURE_FINGERPRINT}" \
  '-Psubshell.auth.redditClientId=' \
  '-Psubshell.auth.redirectUri=' \
  --offline --no-daemon --no-configuration-cache --rerun-tasks \
  > >(tee "$CONFIGURED_POLICY_LOG") 2>&1

grep -Fq 'AUTH05_EXTERNAL_PRODUCTION_IDENTITY_CONFIGURED=true' "$CONFIGURED_POLICY_LOG" ||
  fail CONFIGURED_FIXTURE_STATE_MISMATCH
grep -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_POLICY_PASS' "$CONFIGURED_POLICY_LOG" ||
  fail CONFIGURED_FIXTURE_POLICY_RESULT_MISSING
printf 'AUTH05_FIXTURE_VALIDATION_ONLY=PASS\n'
printf 'AUTH05_FIXTURE_APPLICATION_ID_APPLIED=NO\n'
printf 'AUTH05_FIXTURE_SIGNING_CONFIGURATION_APPLIED=NO\n'

printf '\n===== AUTH05 CURRENT DEBUG IDENTITY =====\n'
AAPT2="$(find_android_build_tool aapt2)"
APKSIGNER="$(find_android_build_tool apksigner)"
[[ -x "$AAPT2" ]] || fail AAPT2_NOT_FOUND
[[ -x "$APKSIGNER" ]] || fail APKSIGNER_NOT_FOUND

CURRENT_APPLICATION_ID="$($AAPT2 dump badging "$APP_APK" |
  sed -n "s/^package: name='\([^']*\)'.*/\1/p" |
  head -n 1)"
[[ -n "$CURRENT_APPLICATION_ID" ]] || fail CURRENT_APPLICATION_ID_MISSING

RAW_CERT_SHA256="$($APKSIGNER verify --print-certs "$APP_APK" 2>/dev/null |
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
  head -n 1)"
CURRENT_CERT_SHA256="$(canonical_fingerprint "$RAW_CERT_SHA256")" ||
  fail CURRENT_SIGNING_CERT_SHA256_INVALID

printf 'CURRENT_APPLICATION_ID=%s\n' "$CURRENT_APPLICATION_ID"
printf 'CURRENT_DEBUG_SIGNING_CERT_SHA256=%s\n' "$CURRENT_CERT_SHA256"
[[ "$CURRENT_APPLICATION_ID" == "$DEBUG_APPLICATION_ID" ]] ||
  fail CURRENT_DEBUG_APPLICATION_ID_MISMATCH

expect_rejected CURRENT_DEBUG_APPLICATION_ID \
  --application-id "$CURRENT_APPLICATION_ID" \
  --sha256-cert-fingerprint "$FIXTURE_FINGERPRINT"
expect_rejected RETIRED_PROTOTYPE_APPLICATION_ID \
  --application-id "$RETIRED_PROTOTYPE_APPLICATION_ID" \
  --sha256-cert-fingerprint "$FIXTURE_FINGERPRINT"
expect_rejected CURRENT_DEBUG_SIGNING_CERTIFICATE \
  --application-id "$FIXTURE_APPLICATION_ID" \
  --sha256-cert-fingerprint "$CURRENT_CERT_SHA256" \
  --forbid-sha256-cert-fingerprint "$CURRENT_CERT_SHA256"
printf 'AUTH05_CURRENT_DEBUG_IDENTITY_REJECTED=PASS\n'
printf 'AUTH05_RETIRED_PROTOTYPE_IDENTITY_REJECTED=PASS\n'

printf '\n===== AUTH05 DETERMINISTIC LOCAL DECISION ARTIFACT =====\n'
python3 "$VALIDATOR" generate \
  --application-id "$FIXTURE_APPLICATION_ID" \
  --sha256-cert-fingerprint "$FIXTURE_FINGERPRINT" \
  --forbid-application-id "$CURRENT_APPLICATION_ID" \
  --forbid-sha256-cert-fingerprint "$CURRENT_CERT_SHA256" \
  --output "$DECISION"
python3 "$VALIDATOR" verify \
  --application-id "$FIXTURE_APPLICATION_ID" \
  --sha256-cert-fingerprint "$FIXTURE_FINGERPRINT" \
  --forbid-application-id "$CURRENT_APPLICATION_ID" \
  --forbid-sha256-cert-fingerprint "$CURRENT_CERT_SHA256" \
  --input "$DECISION"

[[ "$DECISION" != "$ROOT"/* ]] || fail DECISION_WRITTEN_INSIDE_SOURCE_TREE
! rg -q   '"private_key"[[:space:]]*:|"storePassword"[[:space:]]*:|"keyPassword"[[:space:]]*:|"oauth_client_id"[[:space:]]*:|"callback_uri"[[:space:]]*:'   "$DECISION" ||
  fail DECISION_ARTIFACT_CONTAINS_FORBIDDEN_SECRET_FIELD
printf 'AUTH05_DECISION_ARTIFACT_SHA256=%s\n' \
  "$(sha256sum "$DECISION" | awk '{print $1}')"
printf 'AUTH05_DECISION_ARTIFACT_PUBLISHED=NO\n'

printf '\n===== AUTH05 RELEASE-SIGNING SUCCESSOR BOUNDARY =====\n'
grep -Fq 'SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1' app/build.gradle.kts ||
  fail AUTH05D2_SIGNING_MARKER_MISSING
grep -Fq 'SUBSHELL_RELEASE_KEYSTORE_PATH' app/build.gradle.kts ||
  fail AUTH05D2_EXTERNAL_KEYSTORE_PATH_BOUNDARY_MISSING
grep -Fq 'SUBSHELL_RELEASE_STORE_PASSWORD_FILE' app/build.gradle.kts ||
  fail AUTH05D2_EXTERNAL_STORE_PASSWORD_FILE_BOUNDARY_MISSING
grep -Fq 'SUBSHELL_RELEASE_KEY_PASSWORD_FILE' app/build.gradle.kts ||
  fail AUTH05D2_EXTERNAL_KEY_PASSWORD_FILE_BOUNDARY_MISSING
if rg -n '/home/[^ ]+.*\.jks|storePassword[[:space:]]*=[[:space:]]*"|keyPassword[[:space:]]*=[[:space:]]*"' app/build.gradle.kts >/dev/null; then
  fail AUTH05D2_LITERAL_SIGNING_SECRET_OR_PATH_PRESENT
fi
printf 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2\n'
printf 'FINAL_RELEASE_SIGNING_CERT_SHA256=%s\n' "$FINAL_RELEASE_SIGNING_CERT_SHA256"
printf 'SIGNING_KEYS_COLLECTED=NONE\n'

printf '\n===== AUTH05 RESULT =====\n'
printf 'AUTH05_FAIL_CLOSED_EXTERNAL_IDENTITY=PASS\n'
printf 'AUTH05_RETIRED_PROTOTYPE_ID_REJECTED=PASS\n'
printf 'AUTH05_CURRENT_DEBUG_APPLICATION_ID_REJECTED_AS_RELEASE_ID=PASS\n'
printf 'AUTH05_CURRENT_DEBUG_CERT_REJECTED=PASS\n'
printf 'AUTH05_DETERMINISTIC_DECISION_ARTIFACT=PASS\n'
printf 'AUTH05_APPLICATION_ID_MIGRATION=APPLIED_BY_AUTH05C\n'
printf 'AUTH05_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2\n'
printf 'FINAL_APPLICATION_ID=%s\n' "$FINAL_APPLICATION_ID"
printf 'FINAL_RELEASE_SIGNING_CERT_SHA256=%s\n' "$FINAL_RELEASE_SIGNING_CERT_SHA256"
printf 'PRODUCTION_IDENTITY_APPLIED=APPLICATION_ID_AND_RELEASE_SIGNING_WIRING\n'
printf 'AUTH05_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2\n'
printf 'LIVE_REDDIT_NETWORK=NONE\n'
printf 'LIVE_OAUTH_CLIENT_ID=NONE\n'
printf 'SIGNING_KEYS_COLLECTED=NONE\n'
printf 'REMOTE_WRITES=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'DECISION=%s\n' "$DECISION"
printf 'OUTPUT=%s\n' "$OUTPUT"
printf 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_PASS\n'
