#!/usr/bin/env bash
# Sequential checker baseline runner (slice zero; CLAUDE.md hard rule 9).
# Usage: run-baseline-seq.sh OUT_DIR REPO [BUDGET_SECONDS]
# One checker at a time, foreground, 120 s hard limit per checker
# (SLICEZERO_CHECKER_TIMEOUT overrides, for tests). OUT_DIR must be outside
# every repo. Resumable: a checker with an existing .rc file is skipped.
# Exits with "PAUSED" when the time budget is used up; rerun to continue.
# Children run through tools/contract-runtime/bash (success-only memo, one
# shared memo dir per baseline); the top level always runs /usr/bin/bash so
# no top-level result is a memo replay. check-all-contracts and
# check-contract-delta build their own memo environment and run natively.
set -u
OUT="$1"; REPO="$2"; BUDGET="${3:-480}"
PER_CHECKER_TIMEOUT="${SLICEZERO_CHECKER_TIMEOUT:-120}"
REAL_BASH="${SLICEZERO_REAL_BASH:-/usr/bin/bash}"
START=$(date +%s)

mkdir -p "$OUT/checkers" "$OUT/memo"
cd "$REPO" || exit 3

if [[ ! -f "$OUT/HEAD" ]]; then
    git rev-parse HEAD > "$OUT/HEAD"
    git status --porcelain > "$OUT/git-status-before.txt"
    env | cut -d= -f1 | sort > "$OUT/env-names.txt"
    printf 'checker\trc\tseconds\tpass\tfail\tresult\tmemo_hit\tmemo_miss\tmemo_failnc\n' > "$OUT/progress.tsv"
    : > "$OUT/memo-events.tsv"
fi

NATIVE_PATH="$PATH"
export SUBSHELL_CONTRACT_REAL_BASH="$REAL_BASH"
export SUBSHELL_CONTRACT_MEMO_ROOT="$REPO"
export SUBSHELL_CONTRACT_MEMO_DIR="$OUT/memo"
export SUBSHELL_CONTRACT_MEMO_EVENTS="$OUT/memo-events.tsv"
export PATH="$REPO/tools/contract-runtime:$PATH"
export LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8
hash -r

for path in tools/check-*.sh; do
    name="$(basename "$path" .sh)"
    [[ -f "$OUT/checkers/$name.rc" ]] && continue
    now=$(date +%s)
    if (( now - START >= BUDGET )); then
        echo "PAUSED after $((now - START))s; next=$name"
        exit 0
    fi
    printf 'current=%s\nstarted=%s\n' "$name" "$(date -Is)" > "$OUT/state.txt"
    ev_before=$(wc -l < "$OUT/memo-events.tsv")
    t0=$(date +%s.%N)
    case "$name" in
        check-all-contracts|check-contract-delta)
            # Aggregate/tool scripts build their own memo environment; with the
            # wrapper already on PATH, `command -v bash` resolves to the wrapper
            # and recurses without bound. Run them in the native environment.
            timeout --kill-after=10 "$PER_CHECKER_TIMEOUT" \
                env -u SUBSHELL_CONTRACT_REAL_BASH -u SUBSHELL_CONTRACT_MEMO_ROOT \
                    -u SUBSHELL_CONTRACT_MEMO_DIR -u SUBSHELL_CONTRACT_MEMO_EVENTS \
                    PATH="$NATIVE_PATH" \
                    "$REAL_BASH" "$path" > "$OUT/checkers/$name.out" 2> "$OUT/checkers/$name.err" < /dev/null
            rc=$?
            echo "native-env" > "$OUT/checkers/$name.mode"
            ;;
        *)
            # /usr/bin/bash explicitly: the top-level run must be a real run,
            # never a memo replay. Children still go through the wrapper.
            timeout --kill-after=10 "$PER_CHECKER_TIMEOUT" \
                "$REAL_BASH" "$path" > "$OUT/checkers/$name.out" 2> "$OUT/checkers/$name.err" < /dev/null
            rc=$?
            ;;
    esac
    t1=$(date +%s.%N)
    echo "$rc" > "$OUT/checkers/$name.rc"
    secs=$(awk -v a="$t0" -v b="$t1" 'BEGIN{printf "%.2f", b-a}')
    npass=$(grep -c '^PASS=' "$OUT/checkers/$name.out")
    nfail=$(grep -c '^FAIL=' "$OUT/checkers/$name.out")
    result=$(grep -o '^RESULT=.*' "$OUT/checkers/$name.out" | tail -1 | cut -c8-)
    tail -n +"$((ev_before + 1))" "$OUT/memo-events.tsv" > "$OUT/checkers/$name.memo-events.tsv"
    hit=$(awk -F'\t' '$1=="HIT"{n++}END{print n+0}' "$OUT/checkers/$name.memo-events.tsv")
    miss=$(awk -F'\t' '$1=="MISS"{n++}END{print n+0}' "$OUT/checkers/$name.memo-events.tsv")
    fnc=$(awk -F'\t' '$1=="FAIL_NOT_CACHED"{n++}END{print n+0}' "$OUT/checkers/$name.memo-events.tsv")
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$name" "$rc" "$secs" "$npass" "$nfail" "${result:-}" "$hit" "$miss" "$fnc" >> "$OUT/progress.tsv"
    # stray processes from the checker tree must not survive
    strays=$(pgrep -f "$REPO/tools/check-" | wc -l)
    if (( strays > 0 )); then
        echo "STRAY_PROCESSES=$strays after $name" >> "$OUT/runner.log"
        : # log only; timeout already killed the process group
    fi
    echo "$(date -Is) $name rc=$rc ${secs}s pass=$npass fail=$nfail" >> "$OUT/runner.log"
done
git status --porcelain > "$OUT/git-status-after.txt"
printf 'current=\nfinished=%s\n' "$(date -Is)" > "$OUT/state.txt"
echo "BASELINE_DONE"
