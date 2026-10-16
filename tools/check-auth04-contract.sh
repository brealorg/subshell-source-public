#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { printf 'FAIL=%s\n' "$1"; exit 1; }
pass() { printf 'PASS=%s\n' "$1"; }

APP_BUILD='app/build.gradle.kts'
MANIFEST='app/src/main/AndroidManifest.xml'
DEBUG_MANIFEST='app/src/debug/AndroidManifest.xml'
RELEASE_MANIFEST='app/src/release/AndroidManifest.xml'
APP_RUNTIME='app/src/main/java/app/subshell/prototype/auth/AuthApplicationRuntime.kt'
CALLBACK='app/src/main/java/app/subshell/prototype/auth/AuthCallbackActivity.kt'
BROWSER='app/src/main/java/app/subshell/prototype/auth/AuthBrowserLauncher.kt'
BACKUP='app/src/main/java/app/subshell/prototype/backup/SettingsBackup.kt'
PROCESS_STATE='app/src/main/java/app/subshell/prototype/ui/SubshellProcessState.kt'
ROOM='app/src/main/java/app/subshell/prototype/data/cache/SubshellCacheDatabase.kt'
DOC='docs/auth/subshell-auth04-production-oauth-config-contract-v1.md'
MANIFEST_VERIFY='tools/verify-auth04-app-link-manifest.py'
ASSETLINKS='tools/generate-auth04-assetlinks.py'
RUNTIME='tools/run-auth04-production-config-runtime.sh'

AUTH04_CONTRACT_TMP="$(
  mktemp -d "${TMPDIR:-/tmp}/subshell-auth04-contract.XXXXXX"
)"
trap 'rm -rf "$AUTH04_CONTRACT_TMP"' EXIT
UI_ROOM_CONFIG_EVIDENCE="${AUTH04_CONTRACT_TMP}/ui-room-config.txt"
HARDCODED_VALUE_EVIDENCE="${AUTH04_CONTRACT_TMP}/hardcoded-values.txt"
STORAGE_LEAK_EVIDENCE="${AUTH04_CONTRACT_TMP}/storage-leak.txt"

for file in \
  "$APP_BUILD" "$MANIFEST" "$DEBUG_MANIFEST" "$RELEASE_MANIFEST" "$APP_RUNTIME" \
  "$CALLBACK" "$BROWSER" "$BACKUP" "$PROCESS_STATE" "$ROOM" \
  "$DOC" "$MANIFEST_VERIFY" "$ASSETLINKS" "$RUNTIME"; do
  test -f "$file" || fail "AUTH04_FILE_MISSING_$(basename "$file")"
done

AUTH04_VERSION_CODE="$(
  awk -F'= ' '/^[[:space:]]*versionCode = / {print $2; exit}' "$APP_BUILD"
)"
if [[ "$AUTH04_VERSION_CODE" =~ ^[0-9]+$ ]] &&
   (( AUTH04_VERSION_CODE >= 53 )) &&
   rg -Fq 'SUBSHELL_AUTH04_PRODUCTION_OAUTH_CONFIGURATION_V1' "$APP_BUILD"; then
  pass AUTH04_VERSION_AND_IMPLEMENTATION_MARKER
else
  fail AUTH04_VERSION_AND_IMPLEMENTATION_MARKER
fi

if rg -Fq 'gradleProperty("subshell.auth.redditClientId")' "$APP_BUILD" &&
   rg -Fq 'environmentVariable("SUBSHELL_REDDIT_CLIENT_ID")' "$APP_BUILD" &&
   rg -Fq 'gradleProperty("subshell.auth.redirectUri")' "$APP_BUILD" &&
   rg -Fq 'environmentVariable("SUBSHELL_AUTH_REDIRECT_URI")' "$APP_BUILD" &&
   rg -Fq 'validateAuth04ProductionConfiguration' "$APP_BUILD" &&
   rg -Fq 'SUBSHELL_AUTH04_GRADLE_KTS_COMPILE_FIX_V1' "$APP_BUILD" &&
   rg -Fq 'import java.net.URI' "$APP_BUILD" &&
   rg -Fq 'import java.net.URISyntaxException' "$APP_BUILD" &&
   rg -Fq 'URI(redirectUriText)' "$APP_BUILD" &&
   rg -Fq 'catch (_: URISyntaxException)' "$APP_BUILD" &&
   ! rg -Fq 'java.net.URI(redirectUriText)' "$APP_BUILD" &&
   ! rg -Fq 'private const val AUTH04_' "$APP_BUILD" &&
   rg -Fq 'client ID and redirect URI must be supplied together' "$APP_BUILD" &&
   rg -Fq 'AUTH04_REDDIT_OAUTH_CONFIGURED' "$APP_BUILD" &&
   rg -Fq 'AUTH04_REDDIT_CLIENT_ID' "$APP_BUILD" &&
   rg -Fq 'AUTH04_REDDIT_REDIRECT_URI' "$APP_BUILD" &&
   ! rg -Fq 'subshell.auth.redirectHost' "$APP_BUILD" &&
   ! rg -Fq 'SUBSHELL_AUTH_REDIRECT_HOST' "$APP_BUILD" &&
   ! rg -Fq 'subshell.auth.redirectPath' "$APP_BUILD"; then
  pass AUTH04_SINGLE_CANONICAL_EXTERNAL_CONFIGURATION_BOUNDARY
else
  fail AUTH04_SINGLE_CANONICAL_EXTERNAL_CONFIGURATION_BOUNDARY
fi

for token in \
  'clientId != clientId.trim()' \
  'redirectUriText != redirectUriText.trim()' \
  'redirectUri.scheme != "https"' \
  'redirectUri.rawUserInfo != null || redirectUri.port != -1' \
  'redirectUri.rawQuery != null || redirectUri.rawFragment != null' \
  'redirect URI must contain an ASCII DNS hostname' \
  'isReservedAuth04CallbackHost' \
  'host.endsWith(".localhost")' \
  'host.endsWith(".local")' \
  'host.endsWith(".test")' \
  'host.endsWith(".example")' \
  'host.endsWith(".invalid")' \
  'path.contains("//")' \
  'it == "." || it == ".."' \
  'redirectUriText != canonicalRedirectUri' \
  'redirect URI must match the published AUTH05E2 production callback'; do
  grep -Fq "$token" "$APP_BUILD" ||
    fail "AUTH04_VALIDATION_TOKEN_MISSING_${token//[^A-Za-z0-9]/_}"
done
pass AUTH04_FAIL_CLOSED_CONFIGURATION_VALIDATION

if rg -Fq 'verifyAuth04ConfigurationPolicy' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH04_UNCONFIGURED_FAIL_CLOSED' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH04_STRUCTURALLY_VALID_CONFIGURATION' "$APP_BUILD" &&
   rg -Fq 'PARTIAL_CLIENT_ONLY' "$APP_BUILD" &&
   rg -Fq 'NON_HTTPS' "$APP_BUILD" &&
   rg -Fq 'LOCALHOST' "$APP_BUILD" &&
   rg -Fq 'IP_LITERAL' "$APP_BUILD" &&
   rg -Fq 'RESERVED_DOMAIN' "$APP_BUILD" &&
   rg -Fq 'EXPLICIT_PORT' "$APP_BUILD" &&
   rg -Fq 'QUERY' "$APP_BUILD" &&
   rg -Fq 'FRAGMENT' "$APP_BUILD" &&
   rg -Fq 'PATH_TRAVERSAL' "$APP_BUILD" &&
   rg -Fq 'ENCODED_PATH' "$APP_BUILD" &&
   rg -Fq 'WRONG_PUBLISHED_CALLBACK' "$APP_BUILD" &&
   rg -Fq 'PASS=AUTH05E2_EXACT_PUBLISHED_CALLBACK_LOCK' "$APP_BUILD" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH04_CONFIGURATION_POLICY_PASS' "$APP_BUILD"; then
  pass AUTH04_SHARED_POLICY_MATRIX
else
  fail AUTH04_SHARED_POLICY_MATRIX
fi

if rg -Fq 'android:name="app.subshell.prototype.auth.AuthProductionCallback"' "$MANIFEST" &&
   rg -Fq 'android:enabled="${auth04ProductionCallbackEnabled}"' "$MANIFEST" &&
   rg -Fq 'android:targetActivity="app.subshell.prototype.auth.AuthCallbackActivity"' "$MANIFEST" &&
   rg -Fq 'android:autoVerify="true"' "$MANIFEST" &&
   rg -Fq 'android:scheme="https"' "$MANIFEST" &&
   rg -Fq 'android:host="${auth04ProductionCallbackHost}"' "$MANIFEST" &&
   rg -Fq 'android:path="${auth04ProductionCallbackPath}"' "$MANIFEST" &&
   rg -Fq 'auth04ManifestCallbackEnabled' "$APP_BUILD" &&
   rg -Fq 'AUTH04_DISABLED_CALLBACK_HOST = "auth-callback.invalid"' "$APP_BUILD" &&
   rg -Fq 'AUTH04_VALIDATION_CALLBACK_HOST = "auth04-fixture.invalid"' "$APP_BUILD" &&
   rg -Fq 'auth04AppLinkValidationFixture && auth04ProductionConfiguration.configured' "$APP_BUILD"; then
  pass AUTH04_CONFIGURATION_GATED_EXACT_PRODUCTION_APP_LINK
else
  fail AUTH04_CONFIGURATION_GATED_EXACT_PRODUCTION_APP_LINK
fi

if rg -Fq 'android:scheme="subshell-auth-test"' "$DEBUG_MANIFEST" &&
   rg -Fq 'android:host="oauth"' "$DEBUG_MANIFEST" &&
   rg -Fq 'android:path="/callback"' "$DEBUG_MANIFEST" &&
   ! rg -Fq 'subshell-auth-test' "$MANIFEST"; then
  pass AUTH04_DEBUG_CALLBACK_REMAINS_TEST_ONLY
else
  fail AUTH04_DEBUG_CALLBACK_REMAINS_TEST_ONLY
fi

if rg -Fq 'android:name="app.subshell.prototype.auth.AuthProductionCallback"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:enabled="true"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:autoVerify="true"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:host="brealorg.github.io"' "$RELEASE_MANIFEST" &&
   rg -Fq 'android:path="/oauth/callback"' "$RELEASE_MANIFEST" &&
   rg -Fq 'AUTH05E2 successor binding' "$DOC"; then
  pass AUTH04_PRODUCTION_APP_LINK_SUCCESSOR_AUTH05E2
else
  fail AUTH04_PRODUCTION_APP_LINK_SUCCESSOR_AUTH05E2
fi

if rg -Fq 'BuildConfig.AUTH04_REDDIT_OAUTH_CONFIGURED' "app/src/main/java" &&
   rg -Fq 'BuildConfig.AUTH04_REDDIT_CLIENT_ID' "app/src/main/java" &&
   rg -Fq 'BuildConfig.AUTH04_REDDIT_REDIRECT_URI' "app/src/main/java" &&
   rg -Fq 'RedditOAuth.selectRuntimeConfiguration' "app/src/main/java" &&
   rg -Fq 'selected.configuration' "app/src/main/java" &&
   rg -Fq 'AuthTabIntent.Builder()' "$BROWSER" &&
   rg -Fq 'class AuthCallbackActivity' "$CALLBACK" &&
   ! rg -n 'BuildConfig\.AUTH04_REDDIT|AUTH04_REDDIT_CLIENT_ID|AUTH04_REDDIT_REDIRECT_URI' \
      app/src/main/java/app/subshell/prototype/ui \
      app/src/main/java/app/subshell/prototype/data/cache \
      >"$UI_ROOM_CONFIG_EVIDENCE"; then
  pass AUTH04_RUNTIME_CONFIGURATION_AND_UI_ROOM_ISOLATION
else
  fail AUTH04_RUNTIME_CONFIGURATION_AND_UI_ROOM_ISOLATION
  sed -n '1,40p' "$UI_ROOM_CONFIG_EVIDENCE" 2>/dev/null || true
fi

if rg -Fq 'PRODUCTION_CALLBACK_ALIAS_COUNT_INVALID' "$MANIFEST_VERIFY" &&
   rg -Fq 'PRODUCTION_CALLBACK_AUTOVERIFY_MISSING' "$MANIFEST_VERIFY" &&
   rg -Fq 'PRODUCTION_CALLBACK_FORBIDDEN_' "$MANIFEST_VERIFY" &&
   rg -Fq 'AUTH04_EXACT_CALLBACK_INTENT_RESOLUTION=PASS' "$MANIFEST_VERIFY" &&
   rg -Fq 'AUTH04_NON_MATCHING_CALLBACK_REJECTION=PASS' "$MANIFEST_VERIFY" &&
   rg -Fq 'parser.add_argument("--expected-component-namespace", required=True)' "$MANIFEST_VERIFY" &&
   rg -Fq 'AUTH04_COMPONENT_NAMESPACE=' "$MANIFEST_VERIFY" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH04_MERGED_MANIFEST_VERIFIED' "$MANIFEST_VERIFY"; then
  pass AUTH04_MERGED_MANIFEST_AND_INTENT_RESOLUTION_VERIFIER
else
  fail AUTH04_MERGED_MANIFEST_AND_INTENT_RESOLUTION_VERIFIER
fi

if rg -Fq 'delegate_permission/common.handle_all_urls' "$ASSETLINKS" &&
   rg -Fq 'sha256_cert_fingerprints' "$ASSETLINKS" &&
   rg -Fq 'normalize_fingerprint' "$ASSETLINKS" &&
   rg -Fq 'os.replace(temporary, output)' "$ASSETLINKS" &&
   rg -Fq 'ASSETLINKS_PUBLISHED=NO' "$ASSETLINKS" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH04_ASSETLINKS_GENERATED' "$ASSETLINKS" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH04_ASSETLINKS_VERIFIED' "$ASSETLINKS"; then
  pass AUTH04_DETERMINISTIC_LOCAL_ASSETLINKS_ARTIFACT
else
  fail AUTH04_DETERMINISTIC_LOCAL_ASSETLINKS_ARTIFACT
fi

if rg -Fq 'AUTH04_FIXTURE_APK_BUILT=NO' "$RUNTIME" &&
   rg -Fq 'AUTH04_FIXTURE_INSTALLED=NO' "$RUNTIME" &&
   rg -Fq 'AUTH04_FIXTURE_DOMAIN_OWNERSHIP_CLAIMED=NO' "$RUNTIME" &&
   rg -Fq 'AUTH04_ASSETLINKS_SIGNING_IDENTITY=DEBUG_VALIDATION_ONLY' "$RUNTIME" &&
   rg -Fq 'AUTH04_ASSETLINKS_PUBLISHED=NO' "$RUNTIME" &&
   rg -Fq 'AUTH04_BUILD_INTERMEDIATES_RESTORED=UNCONFIGURED' "$RUNTIME" &&
   rg -Fq 'AUTH04_PRODUCTION_APP_LINK_MIGRATION=APPLIED_BY_AUTH05E2' "$RUNTIME" &&
   rg -Fq 'RESULT=SUBSHELL_AUTH04_PRODUCTION_CONFIGURATION_RUNTIME_PASS' "$RUNTIME"; then
  pass AUTH04_HOST_SIDE_CONFIGURATION_MANIFEST_AND_ASSETLINKS_RUNTIME
else
  fail AUTH04_HOST_SIDE_CONFIGURATION_MANIFEST_AND_ASSETLINKS_RUNTIME
fi

if rg -Fq 'No request is sent to Reddit' "$DOC" &&
   rg -Fq 'no live client ID is used' "$DOC" &&
   rg -Fq 'no domain verification request is made' "$DOC" &&
   ! rg -n \
      'SUBSHELL_REDDIT_CLIENT_ID[[:space:]]*=|SUBSHELL_AUTH_REDIRECT_URI[[:space:]]*=' \
      . --glob '!tools/check-auth04-contract.sh' \
      >"$HARDCODED_VALUE_EVIDENCE" &&
   ! rg -n \
      'AUTH04_REDDIT_CLIENT_ID|AUTH04_REDDIT_REDIRECT_URI|sha256_cert_fingerprints' \
      "$BACKUP" "$PROCESS_STATE" "$ROOM" \
      >"$STORAGE_LEAK_EVIDENCE"; then
  pass AUTH04_SOURCE_STORAGE_NETWORK_AND_PUBLICATION_ISOLATION
else
  fail AUTH04_SOURCE_STORAGE_NETWORK_AND_PUBLICATION_ISOLATION
  sed -n '1,40p' "$HARDCODED_VALUE_EVIDENCE" 2>/dev/null || true
  sed -n '1,40p' "$STORAGE_LEAK_EVIDENCE" 2>/dev/null || true
fi

printf 'RESULT=SUBSHELL_AUTH04_PRODUCTION_OAUTH_CONFIGURATION_CONTRACT_PASS\n'
