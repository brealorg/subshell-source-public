#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAIL=%s\n' "$1"; exit 1; }
pass() { printf 'PASS=%s\n' "$1"; }

APP_BUILD='app/build.gradle.kts'
MAIN_MANIFEST='app/src/main/AndroidManifest.xml'
DEBUG_MANIFEST='app/src/debug/AndroidManifest.xml'
RELEASE_MANIFEST='app/src/release/AndroidManifest.xml'
CALLBACK='app/src/main/java/app/subshell/prototype/auth/AuthCallbackActivity.kt'
DOC='docs/auth/subshell-auth05e2-production-app-link-contract-v1.md'
VERIFIER='tools/verify-auth05e2-production-app-link.py'
RUNTIME='tools/run-auth05e2-production-app-link-runtime.sh'
AUTH04_DOC='docs/auth/subshell-auth04-production-oauth-config-contract-v1.md'
AUTH05D2_DOC='docs/auth/subshell-auth05d2-release-signing-wiring-contract-v1.md'
EXPECTED_FINGERPRINT='52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84'

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-auth05e2-contract.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
SECRET_EVIDENCE="$TMP/secret-evidence.txt"
REDDIT_EVIDENCE="$TMP/reddit-evidence.txt"

for file in \
  "$APP_BUILD" "$MAIN_MANIFEST" "$DEBUG_MANIFEST" "$RELEASE_MANIFEST" \
  "$CALLBACK" "$DOC" "$VERIFIER" "$RUNTIME" "$AUTH04_DOC" "$AUTH05D2_DOC"; do
  [[ -f "$file" ]] ||
    fail "AUTH05E2_FILE_MISSING_${file//[^A-Za-z0-9]/_}"
done

[[ -x "$VERIFIER" ]] || fail AUTH05E2_VERIFIER_NOT_EXECUTABLE
[[ -x "$RUNTIME" ]] || fail AUTH05E2_RUNTIME_NOT_EXECUTABLE

VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' "$APP_BUILD"
)"
if [[ "$VERSION_CODE" =~ ^[0-9]+$ ]] &&
   (( VERSION_CODE >= 57 )) &&
   rg -Fq 'SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_V1' "$APP_BUILD"; then
  pass AUTH05E2_VERSION_AND_IMPLEMENTATION_MARKER
else
  fail AUTH05E2_VERSION_AND_IMPLEMENTATION_MARKER
fi

for token in \
  'AUTH05E2_PRODUCTION_CALLBACK_HOST = "brealorg.github.io"' \
  'AUTH05E2_PRODUCTION_CALLBACK_PATH = "/oauth/callback"' \
  'AUTH05E2_PRODUCTION_CALLBACK_URI' \
  '"https://$AUTH05E2_PRODUCTION_CALLBACK_HOST$AUTH05E2_PRODUCTION_CALLBACK_PATH"' \
  'redirect URI must match the published AUTH05E2 production callback' \
  'WRONG_PUBLISHED_CALLBACK' \
  'PASS=AUTH05E2_EXACT_PUBLISHED_CALLBACK_LOCK'; do
  rg -Fq "$token" "$APP_BUILD" ||
    fail "AUTH05E2_CALLBACK_LOCK_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH05E2_EXACT_PUBLISHED_CALLBACK_CONFIGURATION_LOCK

if rg -Fq 'android:name="app.subshell.prototype.auth.AuthProductionCallback"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:enabled="true"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:exported="true"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:targetActivity="app.subshell.prototype.auth.AuthCallbackActivity"' "$RELEASE_MANIFEST" &&
   rg -Fq 'tools:node="replace"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:autoVerify="true"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:scheme="https"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:host="brealorg.github.io"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:path="/oauth/callback"' "$RELEASE_MANIFEST" &&
   ! rg -Fq 'pathPrefix' "$RELEASE_MANIFEST" &&
   ! rg -Fq 'pathPattern' "$RELEASE_MANIFEST"; then
  pass AUTH05E2_RELEASE_ONLY_EXACT_AUTOVERIFY_ALIAS
else
  fail AUTH05E2_RELEASE_ONLY_EXACT_AUTOVERIFY_ALIAS
fi

if rg -Fq 'android:enabled="${auth04ProductionCallbackEnabled}"' "$MAIN_MANIFEST" &&
   rg -Fq 'android:host="${auth04ProductionCallbackHost}"' "$MAIN_MANIFEST" &&
   rg -Fq 'android:path="${auth04ProductionCallbackPath}"' "$MAIN_MANIFEST" &&
   rg -Fq 'android:scheme="subshell-auth-test"' "$DEBUG_MANIFEST" &&
   rg -Fq 'android:host="oauth"' "$DEBUG_MANIFEST" &&
   rg -Fq 'android:path="/callback"' "$DEBUG_MANIFEST" &&
   ! rg -Fq 'brealorg.github.io' "$DEBUG_MANIFEST"; then
  pass AUTH05E2_MAIN_FAIL_CLOSED_AND_DEBUG_CUSTOM_SCHEME_ISOLATION
else
  fail AUTH05E2_MAIN_FAIL_CLOSED_AND_DEBUG_CUSTOM_SCHEME_ISOLATION
fi

if rg -Fq 'android:exported="false"' "$MAIN_MANIFEST" &&
   rg -Fq 'android:noHistory="true"' "$MAIN_MANIFEST" &&
   rg -Fq 'OAuthCallbackProcessor' "$CALLBACK" &&
   rg -Fq 'AuthCallbackFailureKind.NO_PENDING_AUTHORIZATION' \
      app/src/main/java/app/subshell/prototype/data/auth/OAuthSessionManager.kt; then
  pass AUTH05E2_DEDICATED_NON_EXPORTED_LOCAL_CALLBACK_PROCESSING
else
  fail AUTH05E2_DEDICATED_NON_EXPORTED_LOCAL_CALLBACK_PROCESSING
fi

for token in \
  'RESULT=SUBSHELL_AUTH05E2_RELEASE_MANIFEST_VERIFIED' \
  'RESULT=SUBSHELL_AUTH05E2_ASSETLINKS_VERIFIED' \
  'RESULT=SUBSHELL_AUTH05E2_DOMAIN_STATE_VERIFIED' \
  'RESULT=SUBSHELL_AUTH05E2_RESOLVER_VERIFIED' \
  'PRODUCTION_DATA_EXACT_MATCH_FAILED' \
  'DEBUG_CUSTOM_SCHEME_LEAKED_INTO_RELEASE' \
  'ASSETLINKS_EXACT_IDENTITY_MISMATCH' \
  'DOMAIN_STATE_NOT_VERIFIED_' \
  'UNFORCED_CALLBACK_RESOLVER_NOT_SUBSHELL'; do
  rg -Fq "$token" "$VERIFIER" ||
    fail "AUTH05E2_VERIFIER_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH05E2_MANIFEST_ASSETLINKS_DOMAIN_AND_RESOLVER_VERIFIER

for token in \
  'curl' \
  '--max-redirs 0' \
  'application/json' \
  'read -r -s -p' \
  'SUBSHELL_RELEASE_STORE_PASSWORD_FILE' \
  'SUBSHELL_RELEASE_KEY_PASSWORD_FILE' \
  ':app:assembleRelease' \
  'RESULT=SUBSHELL_ALL_CONTRACTS_PASS' \
  'CONTRACT_COUNT=[0-9]+' \
  'AUTH05_OUTPUT_DIR="$OUTPUT/auth05-runtime"' \
  'AUTH05C_OUTPUT_DIR="$OUTPUT/auth05c-runtime"' \
  'RESULT=SUBSHELL_AUTH05_PRODUCTION_IDENTITY_RUNTIME_PASS' \
  'RESULT=SUBSHELL_AUTH05C_APPLICATION_ID_RUNTIME_PASS' \
  'trap on_error ERR' \
  'adb -s "$SERIAL" install -r "$RELEASE_APK"' \
  'pm set-app-links' \
  '--package "$FINAL_APPLICATION_ID" 0 all' \
  'pm verify-app-links' \
  '--re-verify "$FINAL_APPLICATION_ID"' \
  'pm get-app-links "$FINAL_APPLICATION_ID"' \
  'cmd package resolve-activity' \
  'SUBSHELL_AUTH05E2_STAGE12_DEFAULT_CATEGORY_FIX_V1' \
  'android.intent.category.DEFAULT' \
  'android.intent.category.BROWSABLE' \
  'am start -W' \
  'RESULT=SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_AND_DOMAIN_RUNTIME_PASS'; do
  rg -Fq -- "$token" "$RUNTIME" ||
    fail "AUTH05E2_RUNTIME_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done

DEFAULT_CATEGORY_COUNT="$(grep -Fc 'android.intent.category.DEFAULT' "$RUNTIME")"
BROWSABLE_CATEGORY_COUNT="$(grep -Fc 'android.intent.category.BROWSABLE' "$RUNTIME")"
if [[ "$DEFAULT_CATEGORY_COUNT" == '3' &&
      "$BROWSABLE_CATEGORY_COUNT" == '3' ]]; then
  pass AUTH05E2_RESOLVER_AND_LAUNCH_EXPLICIT_DEFAULT_BROWSABLE_PAIRING
else
  fail AUTH05E2_RESOLVER_AND_LAUNCH_EXPLICIT_DEFAULT_BROWSABLE_PAIRING
fi

for token in \
  'SUBSHELL_AUTH05E2_STAGE12_FOREGROUND_OBSERVER_FIX_V1' \
  'KEYCODE_WAKEUP' \
  'wm dismiss-keyguard' \
  'callback-activity-attempt-' \
  'callback-window-attempt-' \
  'topResumedActivity=' \
  'mResumedActivity:' \
  'ResumedActivity:' \
  'mCurrentFocus=' \
  'mFocusedApp=' \
  "FOREGROUND_EVIDENCE='RESUMED_ACTIVITY'" \
  "FOREGROUND_EVIDENCE='WINDOW_FOCUS'" \
  'AUTH05E2_STAGE12_FOREGROUND_OBSERVER=ANDROID17_RESUMED_OR_WINDOW_FOCUS'; do
  rg -Fq -- "$token" "$RUNTIME" ||
    fail "AUTH05E2_FOREGROUND_OBSERVER_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done

for token in \
  'SUBSHELL_AUTH05E2_STAGE6_LAUNCH_RESULT_FIX_V1' \
  'CALLBACK_LAUNCH_REPORTED_ACTIVITY' \
  "CALLBACK_LAUNCH_RESULT='CALLBACK_ACTIVITY_ENTRY'" \
  "CALLBACK_LAUNCH_RESULT='MAIN_ACTIVITY_FINAL'" \
  "FOREGROUND_EVIDENCE='AM_START_W_FINAL_MAIN_ACTIVITY'" \
  'CALLBACK_VIEW_INTENT_UNEXPECTED_REPORTED_ACTIVITY' \
  'CALLBACK_ROUTE_ALIAS_RESOLVED=PASS' \
  'CALLBACK_LAUNCH_ACTIVITY_RESULT=PASS'; do
  rg -Fq -- "$token" "$RUNTIME" ||
    fail "AUTH05E2_LAUNCH_RESULT_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done

if rg -Fq '> >(tee "$CALLBACK_LAUNCH_LOG")' "$RUNTIME"; then
  fail AUTH05E2_ASYNCHRONOUS_CALLBACK_LAUNCH_CAPTURE_REMAINS
fi
if rg -Fq "grep -m1 'topResumedActivity='" "$RUNTIME"; then
  fail AUTH05E2_EARLY_CLOSING_TOP_RESUMED_PIPE_REMAINS
fi
pass AUTH05E2_AM_START_W_FINAL_MAIN_ACTIVITY_IS_COMPLETION_PROOF
pass AUTH05E2_CALLBACK_ALIAS_TO_TRANSIENT_OR_FINAL_ACTIVITY_RESULT
pass AUTH05E2_INTERACTIVE_DEVICE_AND_ANDROID17_FOREGROUND_OBSERVER
pass AUTH05E2_SIGNED_BUILD_INSTALL_DOMAIN_AND_UNFORCED_INTENT_RUNTIME

if rg -Fq 'AUTH04_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' \
      tools/run-auth04-production-config-runtime.sh &&
   rg -Fq 'AUTH05_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' \
      tools/run-auth05-production-identity-runtime.sh &&
   rg -Fq 'AUTH05C_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' \
      tools/run-auth05c-application-id-runtime.sh &&
   rg -Fq 'AUTH05D2_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' \
      tools/run-auth05d2-release-signing-runtime.sh &&
   rg -Fq 'AUTH05E2 successor binding' "$AUTH04_DOC" &&
   rg -Fq 'AUTH05E2 now deploys and verifies' "$AUTH05D2_DOC"; then
  pass AUTH05E2_PREDECESSOR_SUCCESSOR_MIGRATION
else
  fail AUTH05E2_PREDECESSOR_SUCCESSOR_MIGRATION
fi

# AUTH06B successor binding: the v59 compatibility mode adds a separate custom
# scheme route while preserving the exact release-only HTTPS App Link alias.
if rg -Fq 'SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_V1' "$APP_BUILD" &&
   rg -Fq 'android:name="app.subshell.prototype.auth.AuthProductionCallback"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:host="brealorg.github.io"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:path="/oauth/callback"' "$RELEASE_MANIFEST" &&
   ! rg -Fq 'redreader' "$RELEASE_MANIFEST"; then
  pass AUTH05E2_PRODUCTION_APP_LINK_PRESERVED_BY_AUTH06B
else
  fail AUTH05E2_PRODUCTION_APP_LINK_PRESERVED_BY_AUTH06B
fi


if rg -n \
    '/home/[^ ]+.*\.jks|\.local/share/subshell/signing|storePassword[[:space:]]*=[[:space:]]*"[^$]|keyPassword[[:space:]]*=[[:space:]]*"[^$]|SUBSHELL_RELEASE_STORE_PASSWORD[[:space:]]*=|SUBSHELL_RELEASE_KEY_PASSWORD[[:space:]]*=' \
    "$APP_BUILD" "$RELEASE_MANIFEST" "$DOC" "$VERIFIER" "$RUNTIME" \
    > "$SECRET_EVIDENCE"; then
  fail AUTH05E2_LITERAL_PRIVATE_PATH_OR_SECRET_PRESENT
else
  pass AUTH05E2_NO_PRIVATE_KEYSTORE_PATH_OR_PASSWORD_IN_SOURCE
fi

if rg -n \
    'reddit\.com|oauth\.reddit|SUBSHELL_REDDIT_CLIENT_ID[[:space:]]*=|LIVE_OAUTH_CLIENT_ID=[^N]' \
    "$RELEASE_MANIFEST" "$DOC" "$VERIFIER" "$RUNTIME" \
    > "$REDDIT_EVIDENCE"; then
  fail AUTH05E2_REDDIT_NETWORK_OR_CLIENT_ID_PRESENT
else
  pass AUTH05E2_REDDIT_NETWORK_AND_LIVE_CLIENT_ID_ABSENT
fi

if rg -Fq 'pages_commit=3a1d41849a1e69d9e9452d3b3b88c35420f6e586' "$DOC" &&
   rg -Fq "$EXPECTED_FINGERPRINT" "$DOC" &&
   rg -Fq 'DEVICE_NETWORK_MUTATIONS=ANDROID_DOMAIN_VERIFICATION_FETCH_ONLY' "$RUNTIME" &&
   rg -Fq 'GITHUB_MUTATIONS=NONE' "$RUNTIME" &&
   rg -Fq 'LIVE_REDDIT_NETWORK=NONE' "$RUNTIME" &&
   rg -Fq 'LIVE_OAUTH_CLIENT_ID=NONE' "$RUNTIME"; then
  pass AUTH05E2_DOCUMENTED_PUBLIC_IDENTITY_AND_NETWORK_BOUNDARY
else
  fail AUTH05E2_DOCUMENTED_PUBLIC_IDENTITY_AND_NETWORK_BOUNDARY
fi

printf 'RESULT=SUBSHELL_AUTH05E2_PRODUCTION_APP_LINK_CONTRACT_PASS\n'
