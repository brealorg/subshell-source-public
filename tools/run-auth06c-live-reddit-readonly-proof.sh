#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${AUTH06C_ADB_SERIAL:-19021FDF600GBN}"
APP_PACKAGE='io.github.brealorg.subshell.debug.dev1'
TEST_PACKAGE='io.github.brealorg.subshell.debug.dev1.test'
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
EXPECTED_RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS='app.subshell.prototype.data.auth.Auth06cLiveReadOnlyProofInstrumentedTest'
TEST_METHOD='inspectPersistedLiveReadOnlyProofWithoutNetwork'
LOG_TAG='subshell-auth06c-live'

fail() { printf 'FAIL=%s\n' "$1" >&2; exit 1; }

printf 'APPROVED_OPERATION=RUN_SUBSHELL_AUTH06C_USER_INITIATED_LIVE_REDDIT_READONLY_PROOF_V1\n'
printf 'RISK=R3_USER_INITIATED_LIVE_REDDIT_OAUTH_TOKEN_IDENTITY_AND_ONE_BOUNDED_FEED_PAGE\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'SERIAL=%s\n' "$SERIAL"
printf 'APP_PACKAGE=%s\n' "$APP_PACKAGE"
printf 'CLIENT_ID_INPUT_CHANNEL=APP_UI_ONLY\n'
printf 'CLIENT_ID_SHELL_ENV_ADB_CHAT_OR_LOG=PROHIBITED\n'
printf 'REQUESTED_SCOPES=identity_read\n'
printf 'WRITE_SCOPES=NONE\n'
printf 'FEED_PAGE_LIMIT=25\n'
printf 'BACKGROUND_POLLING=NONE\n'
printf 'AUTOMATIC_PAGING=BOUNDED_VIEWPORT_AND_FILTER_GOVERNOR\n'
printf 'AUTO_APPEND_BUDGET_PAGES=2\n'
printf 'REDDIT_PASSWORD_HANDLING_BY_SUBSHELL=NONE\n'
printf 'JKS_OR_PASSWORD_ACCESS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'

for cmd in adb grep sed tr; do
  command -v "$cmd" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${cmd}"
done

"$ROOT/gradlew" \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  -Psubshell.debugSlot=dev1 \
  --offline \
  --no-daemon

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == device ]] ||
  fail ADB_USB_TARGET_NOT_READY

adb -s "$SERIAL" install -r -t "$APP_APK" | sed 's/^/APP_INSTALL=/'
adb -s "$SERIAL" install -r -t "$TEST_APK" | sed 's/^/TEST_INSTALL=/'

RUNNER="$(
  adb -s "$SERIAL" shell pm list instrumentation |
    tr -d '\r' |
    sed -n "/(target=${APP_PACKAGE})/s/^instrumentation:\([^ ]*\).*/\1/p" |
    head -n 1
)"
[[ "$RUNNER" == "$EXPECTED_RUNNER" ]] ||
  fail DEV1_INSTRUMENTATION_TARGET_MISMATCH
printf 'TEST_RUNNER=%s\n' "$RUNNER"

adb -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
adb -s "$SERIAL" shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 \
  >/dev/null

cat <<'STEPS'
AUTH06C_MANUAL_STEP_A:
1. Open subshell Settings -> Accounts -> Reddit compatibility.
2. Enter the client ID directly in the app and save it.
3. Do not paste the client ID into this terminal, chat, ADB, or logs.
4. Return to this terminal and press Enter.
STEPS
read -r

adb -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
adb -s "$SERIAL" shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 \
  >/dev/null

cat <<'STEPS'
AUTH06C_MANUAL_STEP_B:
1. In subshell, start Connect Reddit account.
2. Authorize only identity + read in subshell's isolated Reddit sign-in view.
3. Wait for the sign-in view to close and subshell to report the account connected.
4. Confirm that Home · Best shows the first read-only Reddit page.
5. Only then return to this terminal and press Enter.
STEPS
read -r

# Prove process persistence before the no-network inspector runs.
adb -s "$SERIAL" shell am force-stop "$APP_PACKAGE"
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS#$TEST_METHOD" \
  "$RUNNER"

PROOF_LOG="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S' | tr -d '\r')"
printf '%s\n' "$PROOF_LOG"
for marker in \
  'CLIENT_MODE_REDREADER=true' \
  'ACCOUNT_PRESENT=true' \
  'TOKEN_PRESENT=true' \
  'SCOPES_IDENTITY_READ=true' \
  'ROOM_FEED_PRESENT=true' \
  'PROCESS_REOPEN=true' \
  'RESULT=SUBSHELL_AUTH06C_LIVE_REDDIT_READONLY_PROOF_PASS'; do
  grep -Fq "$marker" <<<"$PROOF_LOG" ||
    fail "LIVE_PROOF_MARKER_MISSING_${marker//[^A-Za-z0-9]/_}"
done

printf 'AUTH06C_LIVE_OAUTH=PASS\n'
printf 'AUTH06C_LIVE_ACCOUNT_IDENTITY=PASS\n'
printf 'AUTH06C_LIVE_FIRST_BEST_PAGE_IN_ROOM=PASS\n'
printf 'AUTH06C_LIVE_PROCESS_REOPEN=PASS\n'
printf 'AUTH06C_LIVE_PROOF_INSPECTOR_NETWORK=NONE\n'
printf 'CLIENT_ID_OR_TOKEN_DISCLOSED=NO\n'
printf 'WRITE_SCOPES=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_AUTH06C_LIVE_REDDIT_READONLY_PROOF_PASS\n'
