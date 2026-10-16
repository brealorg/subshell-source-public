#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${ADB_SERIAL:-19021FDF600GBN}"
PACKAGE="io.github.brealorg.subshell.debug.dev1"
ACTIVITY="app.subshell.prototype.MainActivity"
POST="t3_1upifrl"
CHILD="t1_ow1j26z"
PARENT="t1_ow1iwm8"
USERNAME="brealorg"
TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${OUT_DIR:-$HOME/Downloads}"
LOG="$OUT_DIR/subshell-profile01a6a2-v229-adb-repro-$TS.logcat.txt"
SHOT="$OUT_DIR/subshell-profile01a6a2-v229-adb-repro-parent-$TS.png"
UI_XML_REMOTE="/sdcard/subshell-profile01a6a2-v229-adb-repro-$TS.xml"
UI_XML="$OUT_DIR/subshell-profile01a6a2-v229-adb-repro-parent-$TS.xml"
LOGCAT_PID=""

mkdir -p "$OUT_DIR"
: > "$LOG"

cleanup() {
  local rc=$?
  set +e
  trap - EXIT INT TERM
  if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi
  adb -s "$SERIAL" shell rm -f "$UI_XML_REMOTE" >/dev/null 2>&1 || true
  echo "LOGCAT=$LOG"
  [[ -s "$SHOT" ]] && echo "SCREENSHOT=$SHOT"
  [[ -s "$UI_XML" ]] && echo "UI_DUMP=$UI_XML"
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "FAIL=$1"
  return 1
}

wait_log() {
  local pattern="$1"
  local timeout_seconds="${2:-12}"
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    if grep -E "$pattern" "$LOG" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.10
  done
  return 1
}

launch_target() {
  local comment_id="$1"
  adb -s "$SERIAL" shell am start -W \
    -f 0x20000000 \
    -n "$PACKAGE/$ACTIVITY" \
    --es subshell.debug.focused_post_id "$POST" \
    --es subshell.debug.focused_comment_id "$comment_id" \
    --es subshell.debug.focused_username "$USERNAME"
}

command -v adb >/dev/null 2>&1 || fail ADB_NOT_FOUND
[[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" == "device" ]] || fail DEVICE_NOT_AVAILABLE
DUMP="$(adb -s "$SERIAL" shell dumpsys package "$PACKAGE")"
CODE="$(sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' <<<"$DUMP" | head -1)"
NAME="$(sed -n 's/^[[:space:]]*versionName=//p' <<<"$DUMP" | head -1 | tr -d '\r')"
echo "VERSION_CODE=$CODE"
echo "VERSION_NAME=$NAME"
EXPECTED_CODE="${EXPECTED_REPRO_VERSION_CODE:-}"
EXPECTED_NAME="${EXPECTED_REPRO_VERSION_NAME:-}"
if [[ -n "$EXPECTED_CODE" || -n "$EXPECTED_NAME" ]]; then
  [[ -n "$EXPECTED_CODE" && -n "$EXPECTED_NAME" ]] || fail INCOMPLETE_EXPECTED_DEBUG_REPRO_VERSION_IDENTITY
  [[ "$CODE" == "$EXPECTED_CODE" && "$NAME" == "$EXPECTED_NAME" ]] || fail WRONG_DEBUG_REPRO_VERSION_IDENTITY
else
  case "$CODE:$NAME" in
    "229:0.76.72-profile01a6a2-comments-shared-history-dev"|\
    "230:0.76.73-profile01a6a3-library-shortcut-dev"|\
    "231:0.76.74-profile01a6a4-library-polish-system-bar-contrast-dev"|\
    "232:0.76.75-profile01a6a5-adaptive-library-destinations-dev"|\
    "233:0.76.76-profile01a6a6-inline-you-destinations-dev"|\
    "234:0.76.77-profile01a6a7-profile-content-tab-strip-dev") ;;
    *) fail WRONG_DEBUG_REPRO_VERSION_IDENTITY ;;
  esac
fi

adb -s "$SERIAL" logcat -c
stdbuf -oL adb -s "$SERIAL" logcat -v time -s SubshellCommentNav:V '*:S' > "$LOG" 2>&1 &
LOGCAT_PID=$!
sleep 0.20

adb -s "$SERIAL" shell am force-stop "$PACKAGE"
echo "STEP=OPEN_CHILD target=$CHILD"
launch_target "$CHILD"
wait_log "phase=landing_verified .*target=$CHILD( |$)" 15 || fail CHILD_LANDING_NOT_VERIFIED
wait_log "phase=focused_row_composed target=$CHILD( |$)" 15 || fail CHILD_ROW_NOT_COMPOSED
wait_log "phase=full_discussion_start .*target=$CHILD( |$)" 3 || fail CHILD_FULL_DISCUSSION_NOT_STARTED
echo "CHILD_FULL_DISCUSSION_STARTED=YES"
sleep 0.75
if grep -E "phase=inline_hydration_start post=$POST " "$LOG" >/dev/null 2>&1; then
  fail CHILD_INLINE_HYDRATION_RAN_BEFORE_USER_SCROLL
fi
echo "CHILD_PRE_SCROLL_HYDRATION=BLOCKED_AS_EXPECTED"

adb -s "$SERIAL" shell input keyevent KEYCODE_BACK
sleep 0.50

echo "STEP=OPEN_PARENT target=$PARENT"
launch_target "$PARENT"
wait_log "phase=canonical_target_(continuity|tree_lost|repair_failed) .*target=$PARENT( |$)" 15 || fail PARENT_CANONICAL_TARGET_CONTINUITY_NOT_VERIFIED
if grep -E "phase=canonical_target_repair_failed .*target=$PARENT( |$)" "$LOG" >/dev/null 2>&1; then
  fail PARENT_CANONICAL_TARGET_REPAIR_FAILED
fi
if grep -E "phase=canonical_target_tree_lost .*target=$PARENT( |$)" "$LOG" >/dev/null 2>&1; then
  fail PARENT_CANONICAL_TARGET_TREE_LOST
fi
wait_log "phase=canonical_target_continuity_owned .*target=$PARENT .*merged=true" 15 || fail PARENT_CANONICAL_CONTINUITY_OWNER_NOT_VERIFIED
echo "PARENT_CANONICAL_CONTINUITY_OWNER=VERIFIED"
echo "PARENT_CANONICAL_TARGET_CONTINUITY=YES"
wait_log "phase=layout_identity .*target=$PARENT .*match=true" 15 || fail PARENT_LAYOUT_IDENTITY_NOT_VERIFIED
wait_log "phase=focused_row_composed target=$PARENT author=brealorg( |$)" 15 || fail PARENT_ROW_NOT_COMPOSED_AS_BREALORG
wait_log "phase=landing_verified .*target=$PARENT( |$)" 15 || fail PARENT_LANDING_NOT_VERIFIED
wait_log "phase=full_discussion_start .*target=$PARENT( |$)" 3 || fail PARENT_FULL_DISCUSSION_NOT_STARTED
echo "PARENT_FULL_DISCUSSION_STARTED=YES"
if grep -E "phase=canonical_target_continuity .*target=$PARENT .*projectedBefore=-1( |$)" "$LOG" >/dev/null 2>&1; then
  echo "CANONICAL_CONTINUITY_REPAIR_PATH=OBSERVED_AND_LANDING_VERIFIED"
else
  echo "CANONICAL_CONTINUITY_REPAIR_PATH=NOT_OBSERVED_CURRENT_STATE_ALREADY_PROJECTED"
fi
sleep 1.00

adb -s "$SERIAL" exec-out screencap -p > "$SHOT"
[[ -s "$SHOT" ]] || fail SCREENSHOT_CAPTURE_FAILED
adb -s "$SERIAL" shell uiautomator dump "$UI_XML_REMOTE" >/dev/null 2>&1 || true
adb -s "$SERIAL" pull "$UI_XML_REMOTE" "$UI_XML" >/dev/null 2>&1 || true

echo
echo "===== TARGET TRACE ====="
grep -E "target=($CHILD|$PARENT)( |$)" "$LOG" || true

echo
echo "RESULT=SUBSHELL_PROFILE01A6A2_V229_SHARED_HISTORY_FOCUSED_REPRO_COMPLETE"
echo "EXPECTED_PARENT=t1_ow1iwm8"
echo "EXPECTED_AUTHOR=brealorg"
echo "EXPECTED_VISUAL=BREALORG_COMMENT_WITH_TWO_TENOR_MEDIA_ITEMS"
echo "LEAVE_SCREEN=YES_PARENT_TARGET_REMAINS_OPEN_FOR_VISUAL_CHECK"
