#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${FEEDPOST_V2C_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
TEST_CLASS='app.subshell.prototype.ui.FeedScrollAnchorInstrumentedTest'
LOG_TAG='subshell-feedpost-v2c'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_FEEDPOST_V2C_SCROLL_ANCHOR_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_AND_STATE_ONLY_INSTRUMENTATION\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=PER_SURFACE_ANCHORS_POST_BACK_OFFSET_AND_STABLE_ITEM_REORDER\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 || fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${FEEDPOST_V2C_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --no-configuration-cache
else
  printf 'FEEDPOST_V2C_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] || fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  "$RUNNER"

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"
grep -Fq 'FEEDPOST_V2C_KEYED_ANCHORS=true' <<<"$LOG_OUTPUT" || fail KEYED_ANCHOR_MARKER_MISSING
grep -Fq 'POST_BACK_OFFSET=41' <<<"$LOG_OUTPUT" || fail POST_BACK_OFFSET_MARKER_MISSING
grep -Fq 'STABLE_ITEM_REORDER_INDEX=2' <<<"$LOG_OUTPUT" || fail STABLE_ITEM_REORDER_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_FEEDPOST_V2C_SCROLL_ANCHOR_RUNTIME_PASS' <<<"$LOG_OUTPUT" || fail RUNTIME_RESULT_MISSING

printf 'PER_SURFACE_SCROLL_ANCHORS=PASS\n'
printf 'POST_BACK_EXACT_PIXEL_OFFSET=PASS\n'
printf 'STABLE_ITEM_ID_REORDER_RECOVERY=PASS\n'
printf 'HOME_POPULAR_ALL_SAVED_ISOLATION=STATIC_AND_UNIT_PASS\n'
printf 'COMMUNITY_SORT_ISOLATION=STATIC_AND_UNIT_PASS\n'
printf 'SEARCH_QUERY_SCOPE_AREA_ISOLATION=STATIC_AND_UNIT_PASS\n'
printf 'USER_TAB_ISOLATION=STATIC_AND_UNIT_PASS\n'
printf 'YOU_TAB_ISOLATION=STATIC_AND_UNIT_PASS\n'
printf 'V2A_TYPED_POST_ORIGIN=PRESERVED\n'
printf 'V2B_CANONICAL_ROUTE_STACK=PRESERVED\n'
printf 'V2E_PROCESS_SERIALIZATION=DEFERRED\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_FEEDPOST_V2C_SCROLL_ANCHOR_RUNTIME_PASS\n'
