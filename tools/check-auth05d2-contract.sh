#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAIL=%s\n' "$1"; exit 1; }
pass() { printf 'PASS=%s\n' "$1"; }

APP_BUILD='app/build.gradle.kts'
DOC='docs/auth/subshell-auth05d2-release-signing-wiring-contract-v1.md'
CERT='docs/auth/subshell-app-signing-cert-v1.pem'
VERIFIER='tools/verify-auth05d2-release-signing.py'
RUNTIME='tools/run-auth05d2-release-signing-runtime.sh'
AUTH05_RUNTIME='tools/run-auth05-production-identity-runtime.sh'
AUTH05C_RUNTIME='tools/run-auth05c-application-id-runtime.sh'
EXPECTED_CERT_FILE_SHA256='3f3daf38830c065621dd244324f5960a0b2751cb7fcd56210d1c1b5bfff2045a'
EXPECTED_KEYSTORE_SHA256='7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7'
EXPECTED_FINGERPRINT='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-auth05d2-contract.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
SECRET_EVIDENCE="$TMP/secret-evidence.txt"
NETWORK_EVIDENCE="$TMP/network-evidence.txt"

for file in \
  "$APP_BUILD" "$DOC" "$CERT" "$VERIFIER" "$RUNTIME" \
  "$AUTH05_RUNTIME" "$AUTH05C_RUNTIME"; do
  [[ -f "$file" ]] || fail "AUTH05D2_FILE_MISSING_${file//[^A-Za-z0-9]/_}"
done
[[ -x "$VERIFIER" ]] || fail AUTH05D2_VERIFIER_NOT_EXECUTABLE
[[ -x "$RUNTIME" ]] || fail AUTH05D2_RUNTIME_NOT_EXECUTABLE

VERSION_CODE="$(awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' "$APP_BUILD")"
if [[ "$VERSION_CODE" =~ ^[0-9]+$ ]] &&
   (( VERSION_CODE >= 56 )) &&
   rg -Fq 'SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_V1' "$APP_BUILD"; then
  pass AUTH05D2_VERSION_AND_IMPLEMENTATION_MARKER
else
  fail AUTH05D2_VERSION_AND_IMPLEMENTATION_MARKER
fi

if rg -Fq 'AUTH05D2_RELEASE_KEY_ALIAS = "subshell-app-signing"' "$APP_BUILD" &&
   rg -Fq "$EXPECTED_FINGERPRINT" "$DOC" &&
   rg -Fq "$EXPECTED_KEYSTORE_SHA256" "$APP_BUILD" &&
   [[ "$(sha256sum "$CERT" | awk '{print $1}')" == "$EXPECTED_CERT_FILE_SHA256" ]] &&
   openssl x509 -in "$CERT" -noout -fingerprint -sha256 |
     grep -Fq "$EXPECTED_FINGERPRINT"; then
  pass AUTH05D2_LOCKED_PUBLIC_CERTIFICATE_AND_KEYSTORE_IDENTITY
else
  fail AUTH05D2_LOCKED_PUBLIC_CERTIFICATE_AND_KEYSTORE_IDENTITY
fi

for token in \
  'environmentVariable("SUBSHELL_RELEASE_KEYSTORE_PATH")' \
  'environmentVariable("SUBSHELL_RELEASE_STORE_PASSWORD_FILE")' \
  'environmentVariable("SUBSHELL_RELEASE_KEY_PASSWORD_FILE")' \
  'keystore path, store-password file, and key-password file must be supplied together' \
  'store and private-key passwords require separate files' \
  'store and private-key passwords must differ' \
  'must remain outside the source tree' \
  'must not grant group or other permissions' \
  'must contain at least 24 characters'; do
  rg -Fq "$token" "$APP_BUILD" ||
    fail "AUTH05D2_EXTERNAL_INPUT_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH05D2_FAIL_CLOSED_EXTERNAL_SECRET_FILE_BOUNDARY

if rg -Fq 'signingConfigs {' "$APP_BUILD" &&
   rg -Fq 'create("subshellRelease")' "$APP_BUILD" &&
   rg -Fq 'storeFile = auth05d2ReleaseSigningInputs.keystore' "$APP_BUILD" &&
   rg -Fq 'storePassword = auth05d2ReleaseSigningInputs.storePassword' "$APP_BUILD" &&
   rg -Fq 'keyAlias = AUTH05D2_RELEASE_KEY_ALIAS' "$APP_BUILD" &&
   rg -Fq 'keyPassword = auth05d2ReleaseSigningInputs.keyPassword' "$APP_BUILD" &&
   rg -Fq 'signingConfig = signingConfigs.getByName("subshellRelease")' "$APP_BUILD"; then
  pass AUTH05D2_GRADLE_RELEASE_SIGNING_WIRING
else
  fail AUTH05D2_GRADLE_RELEASE_SIGNING_WIRING
fi

if rg -Fq 'verifyAuth05d2ReleaseSigningPolicy' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05D2_UNCONFIGURED_DEBUG_BUILD_ALLOWED' "$APP_BUILD" &&
   rg -Fq '"KEYSTORE_ONLY"' "$APP_BUILD" &&
   rg -Fq '"PASSWORD_FILES_ONLY"' "$APP_BUILD" &&
   rg -Fq 'println("PASS=AUTH05D2_REJECTED_$label")' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05D2_COMPLETE_EXTERNAL_INPUT_SET' "$APP_BUILD" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_POLICY_PASS' "$APP_BUILD"; then
  pass AUTH05D2_SHARED_POLICY_MATRIX
else
  fail AUTH05D2_SHARED_POLICY_MATRIX
fi

PRE_RELEASE_BLOCK="$(
  sed -n     '/tasks.matching { it.name == "preReleaseBuild" }.configureEach {/,/^}/p'     "$APP_BUILD"
)"

AUTH05D2_LEGACY_RELEASE_GATE=NO
AUTH05D2_CONTRACT02F_RELEASE_GATE=NO

if rg -Fq 'dependsOn(subshellQualityCheck)' <<<"$PRE_RELEASE_BLOCK" &&
   rg -Fq 'dependsOn(verifyAuth05d2ReleaseSigningIdentity)' <<<"$PRE_RELEASE_BLOCK"; then
  AUTH05D2_LEGACY_RELEASE_GATE=YES
fi

if rg -Fq 'dependsOn(verifyContract02ReleaseQualification)' <<<"$PRE_RELEASE_BLOCK" &&
   rg -Fq 'dependsOn(verifyAuth05d2ReleaseSigningIdentity)' <<<"$PRE_RELEASE_BLOCK" &&
   ! rg -Fq 'subshellQualityCheck' <<<"$PRE_RELEASE_BLOCK" &&
   ! rg -Fq 'subshellContracts' <<<"$PRE_RELEASE_BLOCK" &&
   rg -Fq 'tools/contract02/verify-release-qualification.py' "$APP_BUILD" &&
   rg -Fq 'subshell.release.qualificationRecord' "$APP_BUILD"; then
  AUTH05D2_CONTRACT02F_RELEASE_GATE=YES
fi

if rg -Fq 'verifyAuth05d2ReleaseSigningIdentity' "$APP_BUILD" &&
   rg -Fq 'tools/verify-auth05d2-release-signing.py' "$APP_BUILD" &&
   [[ "$AUTH05D2_LEGACY_RELEASE_GATE" == YES ||
      "$AUTH05D2_CONTRACT02F_RELEASE_GATE" == YES ]]; then
  pass AUTH05D2_RELEASE_TASK_FAIL_CLOSED_AND_SUCCESSOR_QUALIFIED
else
  fail AUTH05D2_RELEASE_TASK_FAIL_CLOSED_AND_SUCCESSOR_QUALIFIED
fi

for token in \
  'Entry type: PrivateKeyEntry' \
  'KEYSTORE_SHA256_MISMATCH' \
  'keytool' \
  '-certreq' \
  'openssl' \
  'Public-Key: (4096 bit)' \
  'APK_SIGNER_COUNT_NOT_ONE' \
  'APK_CERTIFICATE_FINGERPRINT_MISMATCH' \
  'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_IDENTITY_PASS' \
  'RESULT=SUBSHELL_AUTH05D2_RELEASE_APK_PASS' \
  'RESULT=SUBSHELL_AUTH05D2_ARTIFACT_VERIFIED'; do
  rg -Fq -- "$token" "$VERIFIER" ||
    fail "AUTH05D2_VERIFIER_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH05D2_KEYSTORE_PRIVATE_KEY_AND_APK_CRYPTOGRAPHIC_VERIFIER

if rg -Fq "FINAL_APPLICATION_ID='io.github.brealorg.subshell'" "$RUNTIME" &&
   rg -Fq "EXPECTED_RELEASE_CERT_SHA256='$EXPECTED_FINGERPRINT'" "$RUNTIME" &&
   rg -Fq "EXPECTED_KEYSTORE_SHA256='$EXPECTED_KEYSTORE_SHA256'" "$RUNTIME" &&
   rg -Fq 'read -r -s -p' "$RUNTIME" &&
   rg -Fq 'SUBSHELL_RELEASE_STORE_PASSWORD_FILE' "$RUNTIME" &&
   rg -Fq 'SUBSHELL_RELEASE_KEY_PASSWORD_FILE' "$RUNTIME" &&
   rg -Fq ':app:assembleRelease' "$RUNTIME" &&
   rg -Fq 'adb -s "$SERIAL" install -r "$RELEASE_APK"' "$RUNTIME" &&
   rg -Fq 'AUTH05D2_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' "$RUNTIME" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_AND_RUNTIME_PASS' "$RUNTIME"; then
  pass AUTH05D2_HOST_RUNTIME_RELEASE_BUILD_INSTALL_AND_LAUNCH
else
  fail AUTH05D2_HOST_RUNTIME_RELEASE_BUILD_INSTALL_AND_LAUNCH
fi

if rg -Fq 'AUTH05_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2' "$AUTH05_RUNTIME" &&
   rg -Fq 'AUTH05C_RELEASE_SIGNING_MIGRATION=APPLIED_BY_AUTH05D2' "$AUTH05C_RUNTIME" &&
   rg -Fq 'release_signing_configured_by_auth05d2' tools/verify-auth05c-application-id.py; then
  pass AUTH05D2_AUTH05B_AND_AUTH05C_SUCCESSOR_MIGRATION
else
  fail AUTH05D2_AUTH05B_AND_AUTH05C_SUCCESSOR_MIGRATION
fi

if rg -n \
    '/home/[^ ]+.*\.jks|\.local/share/subshell/signing|storePassword[[:space:]]*=[[:space:]]*"[^$]|keyPassword[[:space:]]*=[[:space:]]*"[^$]|SUBSHELL_RELEASE_STORE_PASSWORD[[:space:]]*=|SUBSHELL_RELEASE_KEY_PASSWORD[[:space:]]*=' \
    "$APP_BUILD" "$DOC" "$VERIFIER" "$RUNTIME" >"$SECRET_EVIDENCE"; then
  fail AUTH05D2_LITERAL_LOCAL_PATH_OR_SECRET_VALUE_PRESENT
  sed -n '1,80p' "$SECRET_EVIDENCE" || true
else
  pass AUTH05D2_NO_KEYSTORE_PATH_PASSWORD_OR_PRIVATE_KEY_IN_SOURCE
fi

if rg -n \
    'curl|wget|urllib\.request|requests\.|httpx\.|github api|gh pr|gh release' \
    "$VERIFIER" "$RUNTIME" >"$NETWORK_EVIDENCE"; then
  fail AUTH05D2_NETWORK_OR_GITHUB_OPERATION_PRESENT
  sed -n '1,80p' "$NETWORK_EVIDENCE" || true
else
  pass AUTH05D2_REDDIT_NETWORK_APPLINK_AND_GITHUB_ISOLATION
fi

if rg -Fq 'No JKS, password, private key, absolute local key path' "$DOC" &&
   rg -Fq 'AUTH05E2 now deploys and verifies' "$DOC" &&
   rg -Fq 'No request is sent to Reddit' "$DOC"; then
  pass AUTH05D2_DOCUMENTED_SECURITY_BOUNDARY
else
  fail AUTH05D2_DOCUMENTED_SECURITY_BOUNDARY
fi

printf 'RESULT=SUBSHELL_AUTH05D2_RELEASE_SIGNING_WIRING_CONTRACT_PASS\n'
