#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAIL=%s\n' "$1"; exit 1; }
pass() { printf 'PASS=%s\n' "$1"; }

APP_BUILD='app/build.gradle.kts'
DOC='docs/auth/subshell-auth05b-production-identity-boundary-contract-v1.md'
VALIDATOR='tools/validate-auth05-production-identity.py'
RUNTIME='tools/run-auth05-production-identity-runtime.sh'
BACKUP='app/src/main/java/app/subshell/prototype/backup/SettingsBackup.kt'
PROCESS_STATE='app/src/main/java/app/subshell/prototype/ui/SubshellProcessState.kt'
ROOM='app/src/main/java/app/subshell/prototype/data/cache/SubshellCacheDatabase.kt'

AUTH05_CONTRACT_TMP="$(
  mktemp -d "${TMPDIR:-/tmp}/subshell-auth05-contract.XXXXXX"
)"
trap 'rm -rf "$AUTH05_CONTRACT_TMP"' EXIT
STORAGE_EVIDENCE="${AUTH05_CONTRACT_TMP}/storage.txt"
SOURCE_VALUE_EVIDENCE="${AUTH05_CONTRACT_TMP}/source-values.txt"

for file in \
  "$APP_BUILD" "$DOC" "$VALIDATOR" "$RUNTIME" \
  "$BACKUP" "$PROCESS_STATE" "$ROOM"; do
  test -f "$file" || fail "AUTH05_FILE_MISSING_$(basename "$file")"
done

AUTH05_VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' "$APP_BUILD"
)"
if [[ "$AUTH05_VERSION_CODE" =~ ^[0-9]+$ ]] &&
   (( AUTH05_VERSION_CODE >= 56 )) &&
   rg -Fq 'SUBSHELL_AUTH05B_PRODUCTION_IDENTITY_BOUNDARY_V1' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_V1' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1' "$APP_BUILD"; then
  pass AUTH05_VERSION_AND_IMPLEMENTATION_MARKER
else
  fail AUTH05_VERSION_AND_IMPLEMENTATION_MARKER
fi

if rg -Fq 'gradleProperty("subshell.identity.applicationId")' "$APP_BUILD" &&
   rg -Fq 'environmentVariable("SUBSHELL_FINAL_APPLICATION_ID")' "$APP_BUILD" &&
   rg -Fq 'gradleProperty("subshell.identity.signingCertSha256")' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_FINAL_SIGNING_CERT_SHA256' "$APP_BUILD" &&
   rg -Fq 'validateAuth05ProductionIdentity' "$APP_BUILD" &&
   rg -Fq 'final application ID and release signing certificate SHA-256 must be supplied together' "$APP_BUILD"; then
  pass AUTH05_SINGLE_EXTERNAL_PUBLIC_IDENTITY_BOUNDARY
else
  fail AUTH05_SINGLE_EXTERNAL_PUBLIC_IDENTITY_BOUNDARY
fi

for token in \
  'segments.size < 3' \
  '[a-z][a-z0-9_]{0,62}' \
  'applicationId == "app.subshell.prototype"' \
  'applicationId.startsWith("android.")' \
  'applicationId.startsWith("com.example.")' \
  'applicationId.endsWith(".prototype")' \
  'applicationId.endsWith(".debug")' \
  'applicationId.endsWith(".dev")' \
  '(?:[0-9A-F]{2}:){31}[0-9A-F]{2}' \
  'canonical uppercase colon-separated hex'; do
  grep -Fq "$token" "$APP_BUILD" ||
    fail "AUTH05_VALIDATION_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH05_FAIL_CLOSED_IDENTITY_VALIDATION

if rg -Fq 'verifyAuth05ProductionIdentityPolicy' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05_UNCONFIGURED_IDENTITY_NOT_APPLIED' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05_STRUCTURALLY_VALID_IDENTITY' "$APP_BUILD" &&
   rg -Fq 'PARTIAL_APPLICATION_ID_ONLY' "$APP_BUILD" &&
   rg -Fq 'CURRENT_PROTOTYPE_IDENTITY' "$APP_BUILD" &&
   rg -Fq 'FINGERPRINT_LOWERCASE' "$APP_BUILD" &&
   rg -Fq 'FINGERPRINT_NO_COLONS' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05_APPLICATION_ID_MIGRATION_APPLIED' "$APP_BUILD" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_POLICY_PASS' "$APP_BUILD"; then
  pass AUTH05_SHARED_POLICY_MATRIX
else
  fail AUTH05_SHARED_POLICY_MATRIX
fi

if rg -Fq 'AUTH05C_FINAL_APPLICATION_ID = "io.github.brealorg.subshell"' "$APP_BUILD" &&
   rg -Fq 'applicationId = AUTH05C_FINAL_APPLICATION_ID' "$APP_BUILD" &&
   rg -Fq 'auth05ApplicationIdMigrationApplied = true' "$APP_BUILD" &&
   rg -Fq 'create("subshellRelease")' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_RELEASE_KEYSTORE_PATH' "$APP_BUILD" &&
   rg -Fq 'AUTH05D2_EXPECTED_RELEASE_CERT_SHA256' "$APP_BUILD"; then
  pass AUTH05_APPLICATION_ID_AND_RELEASE_SIGNING_SUCCESSOR_APPLIED
else
  fail AUTH05_APPLICATION_ID_AND_RELEASE_SIGNING_SUCCESSOR_APPLIED
fi

if rg -Fq 'subshell.auth05.production_identity_decision.v1' "$VALIDATOR" &&
   rg -Fq 'decision_only_not_applied' "$VALIDATOR" &&
   rg -Fq 'release_signing_key_included' "$VALIDATOR" &&
   rg -Fq 'oauth_client_id_included' "$VALIDATOR" &&
   rg -Fq 'callback_uri_included' "$VALIDATOR" &&
   rg -Fq 'os.replace(temporary, path)' "$VALIDATOR" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05_IDENTITY_DECISION_GENERATED' "$VALIDATOR" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05_IDENTITY_DECISION_VERIFIED' "$VALIDATOR"; then
  pass AUTH05_DETERMINISTIC_LOCAL_DECISION_ARTIFACT
else
  fail AUTH05_DETERMINISTIC_LOCAL_DECISION_ARTIFACT
fi

if rg -Fq 'CURRENT_DEBUG_APPLICATION_ID' "$RUNTIME" &&
   rg -Fq 'SUBSHELL_AUTH05D2_FINAL_RELEASE_CERT_RUNTIME_MIGRATION_V1' "$RUNTIME" &&
   rg -Fq "FINAL_RELEASE_SIGNING_CERT_SHA256='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'" "$RUNTIME" &&
   rg -Fq 'RETIRED_PROTOTYPE_APPLICATION_ID' "$RUNTIME" &&
   rg -Fq 'CURRENT_DEBUG_SIGNING_CERTIFICATE' "$RUNTIME" &&
   rg -Fq 'AUTH05_FIXTURE_APPLICATION_ID_APPLIED=NO' "$RUNTIME" &&
   rg -Fq 'AUTH05_FIXTURE_SIGNING_CONFIGURATION_APPLIED=NO' "$RUNTIME" &&
   rg -Fq 'RELEASE_SIGNING_CONFIGURATION=EXTERNAL_AUTH05D2' "$RUNTIME" &&
   rg -Fq 'AUTH05_DECISION_ARTIFACT_PUBLISHED=NO' "$RUNTIME" &&
   rg -Fq 'AUTH05_APPLICATION_ID_MIGRATION=APPLIED_BY_AUTH05C' "$RUNTIME" &&
   rg -Fq 'AUTH05_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2' "$RUNTIME" &&
   rg -Fq 'FINAL_APPLICATION_ID=%s' "$RUNTIME" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_PASS' "$RUNTIME"; then
  pass AUTH05_HOST_SIDE_MIGRATED_IDENTITY_AND_FIXTURE_RUNTIME
else
  fail AUTH05_HOST_SIDE_MIGRATED_IDENTITY_AND_FIXTURE_RUNTIME
fi

if rg -Fq 'No request is sent to Reddit' "$DOC" &&
   rg -Fq 'AUTH05C subsequently applies the selected application ID' "$DOC" &&
   rg -Fq 'AUTH05D2 adds a Gradle `signingConfig`' "$DOC" &&
   rg -Fq 'No live client ID' "$DOC" &&
   ! rg -n \
      'SUBSHELL_FINAL_APPLICATION_ID[[:space:]]*=|SUBSHELL_FINAL_SIGNING_CERT_SHA256[[:space:]]*=' \
      . --glob '!tools/check-auth05b-contract.sh' \
      >"$SOURCE_VALUE_EVIDENCE" &&
   ! rg -n \
      'SUBSHELL_FINAL_APPLICATION_ID|SUBSHELL_FINAL_SIGNING_CERT_SHA256|Auth05ProductionIdentity' \
      "$BACKUP" "$PROCESS_STATE" "$ROOM" \
      >"$STORAGE_EVIDENCE" &&
   ! rg -n \
      'curl|wget|urllib\.request|requests\.|httpx\.' \
      "$VALIDATOR" "$RUNTIME" >/dev/null; then
  pass AUTH05_SOURCE_STORAGE_NETWORK_AND_SECRET_ISOLATION
else
  fail AUTH05_SOURCE_STORAGE_NETWORK_AND_SECRET_ISOLATION
  sed -n '1,40p' "$SOURCE_VALUE_EVIDENCE" 2>/dev/null || true
  sed -n '1,40p' "$STORAGE_EVIDENCE" 2>/dev/null || true
fi

printf 'RESULT=SUBSHELL_AUTH05B_PRODUCTION_IDENTITY_BOUNDARY_CONTRACT_PASS\n'
