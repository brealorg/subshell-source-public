#!/usr/bin/env bash
# SUBSHELL_CHECKER_RUNNER_V1: run contract checkers one at a time, in the
# foreground, each with its own memo runtime.
#
# Many checkers re-run their predecessors, and those re-run theirs; without
# memoization the same checkers start again and again and a run of
# check-a11y01a1 or check-safety01a3 never ends within the 120 s cap. Each
# checker here gets a fresh tools/contract-runtime memo, so a predecessor
# that passed once is not run again inside that checker (failures are never
# cached). SUBSHELL_CONTRACT_REAL_BASH is set explicitly, so the wrapper
# never resolves `bash` to itself (the 2026-09-19 fork exhaustion).
# check-all-contracts.sh and check-source-contract.sh bootstrap their own
# memo and run without this setup.
#
# Usage:
#   tools/run-checkers.sh --out FILE [--budget SECONDS] (--list FILE | CHECKER...)
#
# FILE (outside every repository) gets one line per checker:
#   <exit code> <seconds>s <checker> <first RESULT= line>
# A second call with the same --out continues after the checkers already
# listed there. No new checker starts once --budget seconds (default 480)
# have passed, so one call fits a 600 s tool limit.
# Exit: 0 all listed checkers passed, 1 a checker failed, 3 paused (call
# again), 2 usage.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REAL_BASH=/usr/bin/bash
[[ -x "$REAL_BASH" ]] || REAL_BASH=/bin/bash
CAP_SECONDS=120

usage() {
    echo 'STOP=USAGE: tools/run-checkers.sh --out FILE [--budget SECONDS] (--list FILE | CHECKER...)' >&2
    exit 2
}

out=''
list=''
budget=480
checkers=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --out) [[ $# -ge 2 ]] || usage; out="$2"; shift 2 ;;
        --budget) [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || usage; budget="$2"; shift 2 ;;
        --list) [[ $# -ge 2 && -f "$2" ]] || usage; list="$2"; shift 2 ;;
        -*) usage ;;
        *) checkers+=("$1"); shift ;;
    esac
done
[[ -n "$out" ]] || usage
[[ -n "$list" || ${#checkers[@]} -gt 0 ]] || usage
if [[ -n "$list" ]]; then
    while IFS= read -r line; do
        [[ -n "$line" && "$line" != \#* ]] && checkers+=("$line")
    done <"$list"
fi

out_dir="$(cd "$(dirname "$out")" 2>/dev/null && pwd)" || { echo 'STOP=OUT_DIR_MISSING' >&2; exit 2; }
out="$out_dir/$(basename "$out")"
case "$out_dir/" in
    "$ROOT"/*) echo 'STOP=OUT_INSIDE_REPOSITORY' >&2; exit 2 ;;
esac
touch "$out"

memo_base="$(mktemp -d "${TMPDIR:-/tmp}/subshell-run-checkers.XXXXXX")"
trap 'rm -rf -- "$memo_base"' EXIT INT TERM

cd "$ROOT" || exit 2
started="$(date +%s)"
paused=NO
for spec in "${checkers[@]}"; do
    name="$(basename "$spec")"
    path="tools/$name"
    if grep -Fq " $name " "$out"; then
        continue
    fi
    if (( $(date +%s) - started >= budget )); then
        paused=YES
        echo "PAUSED_BEFORE=$name"
        break
    fi
    if [[ ! -f "$path" ]]; then
        echo "127 0s $name MISSING" >>"$out"
        continue
    fi
    log="$memo_base/last.log"
    begin="$(date +%s)"
    case "$name" in
        check-all-contracts.sh|check-source-contract.sh)
            timeout --kill-after=10 "$CAP_SECONDS" "$REAL_BASH" "$path" >"$log" 2>&1
            ;;
        *)
            memo="$(mktemp -d "$memo_base/memo.XXXXXX")"
            SUBSHELL_CONTRACT_REAL_BASH="$REAL_BASH" \
            SUBSHELL_CONTRACT_MEMO_ROOT="$ROOT" \
            SUBSHELL_CONTRACT_MEMO_DIR="$memo/memo" \
            SUBSHELL_CONTRACT_MEMO_EVENTS="$memo/events.tsv" \
            PATH="$ROOT/tools/contract-runtime:$PATH" \
                timeout --kill-after=10 "$CAP_SECONDS" "$REAL_BASH" "$path" >"$log" 2>&1
            ;;
    esac
    rc=$?
    echo "$rc $(( $(date +%s) - begin ))s $name $(grep -m1 '^RESULT=' "$log")" >>"$out"
done

total="${#checkers[@]}"
done_count=0
failed=0
for spec in "${checkers[@]}"; do
    name="$(basename "$spec")"
    line="$(grep -F " $name " "$out" | tail -1)"
    [[ -n "$line" ]] || continue
    done_count=$((done_count + 1))
    [[ "${line%% *}" == 0 ]] || failed=$((failed + 1))
done
echo "PROGRESS=$done_count/$total FAILED=$failed"
awk '$1 != 0' "$out"
if [[ "$failed" -gt 0 ]]; then exit 1; fi
if [[ "$paused" == YES ]]; then exit 3; fi
exit 0
