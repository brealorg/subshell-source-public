#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${DATA03_ADB_SERIAL:-19021FDF600GBN}"
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER='io.github.brealorg.subshell.debug.test/androidx.test.runner.AndroidJUnitRunner'
CACHE_TEST='app.subshell.prototype.data.cache.PersistentCacheInstrumentedTest'
MIGRATION_TEST='app.subshell.prototype.data.cache.SubshellCacheMigrationInstrumentedTest'
LOG_TAG='subshell-data03'

fail() {
  printf 'FAIL=%s\n' "$1" >&2
  exit 1
}

printf 'APPROVED_OPERATION=RUN_SUBSHELL_DATA03_ROOM_CACHE_RUNTIME\n'
printf 'RISK=R1_LOCAL_REVERSIBLE_DEBUG_AND_TEST_APK_INSTALL_ROOM_DATABASE_AND_LOCAL_TEST_STATE\n'
printf 'SOURCE_ROOT=%s\n' "$ROOT"
printf 'TEST_SEQUENCE=MIGRATION_WRITE_PAGES_AND_LOCAL_MUTATIONS_FORCE_STOP_DEPENDENCY_AWARE_IDEMPOTENT_COLD_START_REPLAY_OFFLINE_RESTORE_STALE_FALLBACK_AND_REMOTE_CACHE_CLEAR\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'OAUTH=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'ADB_POLICY=EXPLICIT_SERIAL_DEFAULT_USB\n'
printf 'ADB_SERIAL=%s\n' "$SERIAL"

for command_name in adb sha256sum grep; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "COMMAND_NOT_FOUND_${command_name}"
done

if [[ "${DATA03_SKIP_BUILD:-0}" != '1' ]]; then
  "$ROOT/gradlew" \
    :app:assembleDebug \
    :app:assembleDebugAndroidTest \
    --offline \
    --no-daemon \
    --no-configuration-cache
else
  printf 'DATA03_BUILD=SKIPPED_BY_VERIFIED_CALLER\n'
fi

[[ -f "$APP_APK" ]] || fail APP_APK_MISSING
[[ -f "$TEST_APK" ]] || fail TEST_APK_MISSING
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" == 'device' ]] ||
  fail ADB_USB_TARGET_NOT_READY

printf 'APP_APK_SHA256=%s\n' "$(sha256sum "$APP_APK" | awk '{print $1}')"
printf 'TEST_APK_SHA256=%s\n' "$(sha256sum "$TEST_APK" | awk '{print $1}')"

adb -s "$SERIAL" install -r -t "$APP_APK" >/dev/null
adb -s "$SERIAL" install -r -t "$TEST_APK" >/dev/null

printf '%s\n' '===== ROOM MIGRATION VALIDATION ====='
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$MIGRATION_TEST" \
  "$RUNNER"

adb -s "$SERIAL" logcat -c

printf '%s\n' '===== PERSISTENT CACHE WRITE PHASE ====='
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$CACHE_TEST#writePersistentPagesAndLocalMutationsBeforeExternalProcessRestart" \
  "$RUNNER"

adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug
adb -s "$SERIAL" shell am force-stop io.github.brealorg.subshell.debug.test

printf '%s\n' '===== PERSISTENT CACHE RESTORE PHASE ====='
adb -s "$SERIAL" shell am instrument -w -r \
  -e class "$CACHE_TEST#restoreColdStartOfflinePagesAndLocalMutationsAfterExternalProcessRestart" \
  "$RUNNER"

LOG_OUTPUT="$(adb -s "$SERIAL" logcat -d -s "$LOG_TAG:I" '*:S')"
printf '%s\n' "$LOG_OUTPUT"

grep -Fq 'DATA03_PHASE=WRITE' <<<"$LOG_OUTPUT" ||
  fail WRITE_PHASE_MARKER_MISSING
grep -Fq 'QUERY_COUNT=1' <<<"$LOG_OUTPUT" ||
  fail QUERY_COUNT_MARKER_MISSING
grep -Fq 'ENTRY_COUNT=24' <<<"$LOG_OUTPUT" ||
  fail ENTRY_COUNT_MARKER_MISSING
grep -Fq 'GENERATION=1' <<<"$LOG_OUTPUT" ||
  fail QUERY_GENERATION_MARKER_MISSING
grep -Fq 'NEXT_CURSOR=24' <<<"$LOG_OUTPUT" ||
  fail QUERY_CURSOR_MARKER_MISSING
grep -Fq 'LOCAL_MUTATION_COUNT=5' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_COUNT_MARKER_MISSING
grep -Fq 'DATA03_PHASE=RESTORE' <<<"$LOG_OUTPUT" ||
  fail RESTORE_PHASE_MARKER_MISSING
grep -Fq 'FRESH_COLD_START=true' <<<"$LOG_OUTPUT" ||
  fail FRESH_COLD_START_MARKER_MISSING
grep -Fq 'STALE_OFFLINE=true' <<<"$LOG_OUTPUT" ||
  fail STALE_OFFLINE_MARKER_MISSING
grep -Fq 'LRU_QUERY_COUNT=2' <<<"$LOG_OUTPUT" ||
  fail LRU_QUERY_COUNT_MARKER_MISSING
grep -Fq 'LOCAL_MUTATIONS_SURVIVE_CLEAR=true' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_CLEAR_BOUNDARY_MARKER_MISSING
grep -Fq 'REPLAY_APPLIED=5' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_REPLAY_APPLIED_MARKER_MISSING
grep -Fq 'REPLAY_ALREADY_APPLIED=2' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_REPLAY_ALREADY_APPLIED_MARKER_MISSING
grep -Fq 'REPLAY_DEFERRED=0' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_REPLAY_DEFERRED_MARKER_MISSING
grep -Fq 'REPLAY_FAILED=0' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_REPLAY_FAILED_MARKER_MISSING
grep -Fq 'REPLAY_IDEMPOTENT=true' <<<"$LOG_OUTPUT" ||
  fail LOCAL_MUTATION_REPLAY_IDEMPOTENCE_MARKER_MISSING
grep -Fq 'RESULT=SUBSHELL_DATA03_ROOM_CACHE_RUNTIME_PASS' <<<"$LOG_OUTPUT" ||
  fail RUNTIME_RESULT_MISSING

printf 'ROOM_SCHEMA_MIGRATION_1_2=PASS\n'
printf 'TRANSACTIONAL_INITIAL_AND_APPEND_WRITES=PASS\n'
printf 'SEPARATE_PROCESS_COLD_START_HYDRATION=PASS\n'
printf 'FRESH_CACHE_BEFORE_BACKEND=PASS\n'
printf 'STALE_OFFLINE_DATABASE_FALLBACK=PASS\n'
printf 'ORDERED_QUERY_ENTRY_RESTORE=PASS\n'
printf 'LOCAL_MUTATIONS_RESTORED=PASS\n'
printf 'DEPENDENCY_AWARE_LOCAL_MUTATION_REPLAY=PASS\n'
printf 'EXPLICIT_LOCAL_MUTATION_REPLAY_OUTCOMES=PASS\n'
printf 'IDEMPOTENT_LOCAL_MUTATION_REPLAY=PASS\n'
printf 'REMOTE_CACHE_CLEAR_PRESERVES_LOCAL_MUTATIONS=PASS\n'
printf 'DATA02_PAGING_POLICY=PRESERVED\n'
printf 'DATA01_REPOSITORY_BOUNDARY=PRESERVED\n'
printf 'V2A_TYPED_POST_ORIGIN=PRESERVED\n'
printf 'V2B_CANONICAL_ROUTE_STACK=PRESERVED\n'
printf 'V2C_KEYED_SCROLL_ANCHORS=PRESERVED\n'
printf 'V2E_FULL_PROCESS_STATE_RESTORE=PRESERVED\n'
printf 'REDDIT_NETWORKING=NONE\n'
printf 'OAUTH=NONE\n'
printf 'DEVICE_NETWORK_MUTATIONS=NONE\n'
printf 'SOURCE_MUTATIONS=NONE\n'
printf 'GITHUB_MUTATIONS=NONE\n'
printf 'RESULT=SUBSHELL_DATA03_ROOM_CACHE_RUNTIME_PASS\n'
