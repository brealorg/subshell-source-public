#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CONTRACTS=(
  tools/check-source-contract.sh
  tools/check-auth04-contract.sh
  tools/check-auth05b-contract.sh
  tools/check-auth05c-contract.sh
  tools/check-auth05d2-contract.sh
  tools/check-auth05e2-contract.sh
  tools/check-quality-gates-contract.sh
)

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-all-contracts.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

# SUBSHELL_WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME_V1
REAL_BASH="$(command -v bash)"
MEMO_WRAPPER="$ROOT/tools/contract-runtime/bash"
MEMO_DIR="$TMP/memo"
MEMO_EVENTS="$TMP/memo-events.tsv"

test -x "$MEMO_WRAPPER" || {
  printf 'FAIL=SUBSHELL_CONTRACT_MEMO_WRAPPER_MISSING FILE=%s\n' "$MEMO_WRAPPER"
  exit 1
}

mkdir -p "$MEMO_DIR"
: > "$MEMO_EVENTS"
export SUBSHELL_CONTRACT_REAL_BASH="$REAL_BASH"
export SUBSHELL_CONTRACT_MEMO_ROOT="$ROOT"
export SUBSHELL_CONTRACT_MEMO_DIR="$MEMO_DIR"
export SUBSHELL_CONTRACT_MEMO_EVENTS="$MEMO_EVENTS"
export PATH="$ROOT/tools/contract-runtime:$PATH"

for contract in "${CONTRACTS[@]}"; do
  test -f "$contract" || {
    printf 'FAIL=SUBSHELL_CONTRACT_MISSING FILE=%s\n' "$contract"
    exit 1
  }

  printf 'RUNNING_SUBSHELL_CONTRACT=%s\n' "$contract"
  log="$TMP/$(basename "$contract").log"
  if bash "$contract" >"$log" 2>&1; then
    rc=0
  else
    rc=$?
  fi

  if [[ "$rc" -ne 0 ]]; then
    cat "$log"
    printf 'FAIL=SUBSHELL_CONTRACT_EXIT FILE=%s EXIT_CODE=%s\n' "$contract" "$rc"
    exit "$rc"
  fi

  if grep -Eq '^FAIL=|^RESULT=.*_FAIL$' "$log"; then
    cat "$log"
    printf 'FAIL=SUBSHELL_CONTRACT_OUTPUT_FAILURE FILE=%s\n' "$contract"
    exit 1
  fi

  result="$(grep -E '^RESULT=.*_PASS$' "$log" | tail -1 || true)"
  if [[ -n "$result" ]]; then
    printf '%s\n' "$result"
  else
    printf 'PASS=SUBSHELL_CONTRACT_CLEAN FILE=%s\n' "$contract"
  fi
done

MEMO_HITS="$(awk -F '\t' '$1 == "HIT" {n++} END {print n+0}' "$MEMO_EVENTS")"
MEMO_MISSES="$(awk -F '\t' '$1 == "MISS" {n++} END {print n+0}' "$MEMO_EVENTS")"
MEMO_FAILURES_NOT_CACHED="$(awk -F '\t' '$1 == "FAIL_NOT_CACHED" {n++} END {print n+0}' "$MEMO_EVENTS")"

echo "CONTRACT_MEMO_HITS=$MEMO_HITS"
echo "CONTRACT_MEMO_MISSES=$MEMO_MISSES"
echo "CONTRACT_MEMO_FAILURES_NOT_CACHED=$MEMO_FAILURES_NOT_CACHED"
echo 'CONTRACT_MEMO_POLICY=SUCCESS_ONLY_PER_SUITE'
echo "CONTRACT_COUNT=${#CONTRACTS[@]}"
echo 'RESULT=SUBSHELL_ALL_CONTRACTS_PASS'
