#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAIL=%s\n' "$1"; exit 1; }
pass() { printf 'PASS=%s\n' "$1"; }

APP_BUILD='app/build.gradle.kts'
MAIN_MANIFEST='app/src/main/AndroidManifest.xml'
DEBUG_MANIFEST='app/src/debug/AndroidManifest.xml'
AUTH_RUNTIME='app/src/main/java/app/subshell/prototype/auth/AuthApplicationRuntime.kt'
DOC='docs/auth/subshell-auth05c-application-id-migration-contract-v1.md'
RUNTIME='tools/run-auth05c-application-id-runtime.sh'
VERIFIER='tools/verify-auth05c-application-id.py'
AUTH04_RUNTIME='tools/run-auth04-production-config-runtime.sh'
AUTH04_VERIFIER='tools/verify-auth04-app-link-manifest.py'
AUTH05_RUNTIME='tools/run-auth05-production-identity-runtime.sh'

AUTH05C_TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-auth05c-contract.XXXXXX")"
trap 'rm -rf "$AUTH05C_TMP"' EXIT
LEGACY_RUNTIME_EVIDENCE="${AUTH05C_TMP}/legacy-runtime.txt"
SECRET_EVIDENCE="${AUTH05C_TMP}/secret-source.txt"

for file in \
  "$APP_BUILD" "$MAIN_MANIFEST" "$DEBUG_MANIFEST" "$AUTH_RUNTIME" \
  "$DOC" "$RUNTIME" "$VERIFIER" "$AUTH04_RUNTIME" "$AUTH04_VERIFIER" \
  "$AUTH05_RUNTIME"; do
  [[ -f "$file" ]] || fail "AUTH05C_FILE_MISSING_$(basename "$file")"
done

VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' "$APP_BUILD"
)"
if [[ "$VERSION_CODE" =~ ^[0-9]+$ ]] &&
   (( VERSION_CODE >= 56 )) &&
   rg -Fq 'SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_V1' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1' "$APP_BUILD"; then
  pass AUTH05C_VERSION_AND_IMPLEMENTATION_MARKER
else
  fail AUTH05C_VERSION_AND_IMPLEMENTATION_MARKER
fi

if rg -Fq 'AUTH05C_FINAL_APPLICATION_ID = "io.github.brealorg.subshell"' "$APP_BUILD" &&
   rg -Fq 'AUTH05C_DEBUG_APPLICATION_ID_SUFFIX = ".debug"' "$APP_BUILD" &&
   rg -Fq 'AUTH05C_TEST_APPLICATION_ID =' "$APP_BUILD" &&
   rg -Fq 'applicationId = AUTH05C_FINAL_APPLICATION_ID' "$APP_BUILD" &&
   rg -Fq 'testApplicationId = AUTH05C_TEST_APPLICATION_ID' "$APP_BUILD" &&
   rg -Fq 'applicationIdSuffix = AUTH05C_DEBUG_APPLICATION_ID_SUFFIX' "$APP_BUILD"; then
  pass AUTH05C_PRODUCTION_DEBUG_AND_TEST_APPLICATION_IDS
else
  fail AUTH05C_PRODUCTION_DEBUG_AND_TEST_APPLICATION_IDS
fi

if rg -Fq 'AUTH05C_COMPONENT_NAMESPACE = "app.subshell.prototype"' "$APP_BUILD" &&
   rg -Fq 'namespace = AUTH05C_COMPONENT_NAMESPACE' "$APP_BUILD" &&
   test -d app/src/main/java/app/subshell/prototype &&
   test -d app/src/androidTest/java/app/subshell/prototype; then
  pass AUTH05C_NAMESPACE_MIGRATION_DEFERRED
else
  fail AUTH05C_NAMESPACE_MIGRATION_DEFERRED
fi

for token in \
  'android:name="app.subshell.prototype.MainActivity"' \
  'android:name="app.subshell.prototype.auth.AuthCallbackActivity"' \
  'android:name="app.subshell.prototype.auth.AuthProductionCallback"' \
  'android:targetActivity="app.subshell.prototype.auth.AuthCallbackActivity"'; do
  rg -Fq "$token" "$MAIN_MANIFEST" ||
    fail "AUTH05C_MAIN_MANIFEST_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
rg -Fq \
  'android:name="app.subshell.prototype.auth.AuthCallbackActivity"' \
  "$DEBUG_MANIFEST" ||
  fail AUTH05C_DEBUG_CALLBACK_COMPONENT_NOT_FULLY_QUALIFIED
pass AUTH05C_EXPLICIT_COMPONENT_NAMESPACE

if rg -Fq \
     '"android:${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_NAME} (subshell)"' \
     "app/src/main/java" &&
   rg -Fq 'BuildConfig.APPLICATION_ID' \
     app/src/androidTest/java/app/subshell/prototype/data/network/Net01HttpBoundaryInstrumentedTest.kt &&
   rg -Fq 'BuildConfig.APPLICATION_ID' \
     app/src/androidTest/java/app/subshell/prototype/data/sync/Net02PostFeedSyncInstrumentedTest.kt &&
   ! rg -Fq \
     '"android:app.subshell.prototype:${BuildConfig.VERSION_NAME} (subshell)"' \
     "$AUTH_RUNTIME"; then
  pass AUTH05C_RUNTIME_USER_AGENT_USES_APPLICATION_ID
else
  fail AUTH05C_RUNTIME_USER_AGENT_USES_APPLICATION_ID
fi

if rg -Fq "EXPECTED_DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'" "$AUTH04_RUNTIME" &&
   rg -Fq "EXPECTED_COMPONENT_NAMESPACE='app.subshell.prototype'" "$AUTH04_RUNTIME" &&
   rg -Fq -- '--expected-component-namespace "$EXPECTED_COMPONENT_NAMESPACE"' "$AUTH04_RUNTIME" &&
   rg -Fq 'parser.add_argument("--expected-component-namespace", required=True)' "$AUTH04_VERIFIER" &&
   rg -Fq 'AUTH04_COMPONENT_NAMESPACE=' "$AUTH04_VERIFIER"; then
  pass AUTH05C_AUTH04_APP_LINK_COMPONENT_SEPARATION
else
  fail AUTH05C_AUTH04_APP_LINK_COMPONENT_SEPARATION
fi

if rg -Fq 'subshell.auth05c.application_id_migration.v1' "$VERIFIER" &&
   rg -Fq '"application_id_applied": True' "$VERIFIER" &&
   rg -Fq '"namespace_migration_deferred": True' "$VERIFIER" &&
   rg -Fq '"release_signing_configured_at_auth05c": False' "$VERIFIER" &&
   rg -Fq '"release_signing_configured_by_auth05d2": True' "$VERIFIER" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05C_MIGRATION_ARTIFACT_GENERATED' "$VERIFIER" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05C_MIGRATION_ARTIFACT_VERIFIED' "$VERIFIER" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_PASS' "$RUNTIME"; then
  pass AUTH05C_DETERMINISTIC_MIGRATION_ARTIFACT_AND_RUNTIME
else
  fail AUTH05C_DETERMINISTIC_MIGRATION_ARTIFACT_AND_RUNTIME
fi

if rg -Fq "FINAL_APPLICATION_ID='io.github.brealorg.subshell'" "$RUNTIME" &&
   rg -Fq "DEBUG_APPLICATION_ID='io.github.brealorg.subshell.debug'" "$RUNTIME" &&
   rg -Fq "TEST_APPLICATION_ID='io.github.brealorg.subshell.debug.test'" "$RUNTIME" &&
   rg -Fq 'PASS=AUTH05C_TEST_TARGETS_DEBUG_APPLICATION' "$RUNTIME" &&
   rg -Fq 'PASS=AUTH05C_COMPONENT_NAMESPACE_EXPLICIT' "$RUNTIME" &&
   rg -Fq 'AUTH05C_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2' "$RUNTIME"; then
  pass AUTH05C_HOST_SIDE_APK_TEST_AND_COMPONENT_RUNTIME
else
  fail AUTH05C_HOST_SIDE_APK_TEST_AND_COMPONENT_RUNTIME
fi

if ! rg -n \
    'app\.subshell\.prototype\.test/androidx\.test\.runner\.AndroidJUnitRunner|PACKAGE='\''app\.subshell\.prototype'\''|TEST_PACKAGE='\''app\.subshell\.prototype\.test'\''|am force-stop app\.subshell\.prototype(?:[[:space:]]|$)|run-as app\.subshell\.prototype(?:[[:space:]]|$)|--expected-package app\.subshell\.prototype' \
    tools/run-*.sh >"$LEGACY_RUNTIME_EVIDENCE"; then
  pass AUTH05C_LEGACY_OPERATIONAL_PACKAGE_TARGETS_RETIRED
else
  fail AUTH05C_LEGACY_OPERATIONAL_PACKAGE_TARGETS_RETIRED
  sed -n '1,60p' "$LEGACY_RUNTIME_EVIDENCE" || true
fi

if rg -Fq 'AUTH05D1 subsequently creates the owner-managed app-signing identity' \
     docs/auth/subshell-auth05b-production-identity-boundary-contract-v1.md &&
   rg -Fq 'io.github.brealorg.subshell.debug.test' "$DOC" &&
   rg -Fq 'The previous `app.subshell.prototype` installation is not removed or modified' "$DOC" &&
   rg -Fq 'No automatic migration' "$DOC" &&
   rg -Fq 'AUTH05D2' "$DOC" &&
   rg -Fq 'SUBSHELL_RELEASE_STORE_PASSWORD_FILE' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_RELEASE_KEY_PASSWORD_FILE' "$APP_BUILD" &&
   ! rg -n \
      '/home/[^ ]+.*\.jks|storePassword[[:space:]]*=[[:space:]]*"|keyPassword[[:space:]]*=[[:space:]]*"' \
      "$APP_BUILD" >"$SECRET_EVIDENCE" &&
   ! rg -n \
      'curl|wget|urllib\.request|requests\.|httpx\.' \
      "$RUNTIME" "$VERIFIER" >/dev/null; then
  pass AUTH05C_RELEASE_SIGNING_SUCCESSOR_AND_REMOTE_ISOLATION
else
  fail AUTH05C_RELEASE_SIGNING_SUCCESSOR_AND_REMOTE_ISOLATION
  sed -n '1,40p' "$SECRET_EVIDENCE" 2>/dev/null || true
fi

printf 'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_MIGRATION_CONTRACT_PASS\n'
