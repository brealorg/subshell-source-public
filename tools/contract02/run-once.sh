#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_CONTRACT02_RUN_ONCE_RESULT_CACHE_RUNNER_V1

usage() {
    cat <<'EOF'
Usage:
  tools/contract02/run-once.sh --manifest <path> --output <dir>

Manifest format (TSV, no header required):
  id<TAB>path<TAB>block_mode<TAB>predecessors

block_mode:
  primary  - block when this contract's own assertions fail
  any      - block when own assertions or any predecessor status fails
  none     - report failures but do not block the release gate

predecessors:
  -         - none
  id1,id2   - previously declared manifest ids

Contract02 protocol:
  - each contract is executed exactly once by this runner
  - contract must emit exactly one:
      CONTRACT02_PRIMARY_RESULT=PASS
    or
      CONTRACT02_PRIMARY_RESULT=FAIL
  - PASS must exit 0; FAIL must exit non-zero
  - contracts must contain own assertions only; predecessors are runner-owned
EOF
}

MANIFEST=''
OUTPUT=''

while [[ $# -gt 0 ]]; do
    case "$1" in
        --manifest)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            MANIFEST="$2"
            shift 2
            ;;
        --output)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            OUTPUT="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR=UNKNOWN_ARGUMENT:$1" >&2
            usage >&2
            exit 64
            ;;
    esac
done

[[ -n "$MANIFEST" ]] || { echo 'ERROR=MANIFEST_REQUIRED' >&2; exit 64; }
[[ -n "$OUTPUT" ]] || { echo 'ERROR=OUTPUT_REQUIRED' >&2; exit 64; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ "$MANIFEST" != /* ]]; then
    MANIFEST="$ROOT/$MANIFEST"
fi
if [[ "$OUTPUT" != /* ]]; then
    OUTPUT="$ROOT/$OUTPUT"
fi

[[ -f "$MANIFEST" ]] || {
    echo "ERROR=MANIFEST_NOT_FOUND:$MANIFEST" >&2
    exit 66
}

mkdir -p "$OUTPUT/logs" || exit 73
CACHE="$OUTPUT/results.tsv"
SUMMARY="$OUTPUT/summary.txt"

declare -a IDS=()
declare -a PATHS=()
declare -a BLOCKS=()
declare -a PREDS=()
declare -A SEEN=()
declare -A PRIMARY=()
declare -A PRED_STATUS=()
declare -A OVERALL=()
declare -A RELEASE_BLOCK=()

manifest_error() {
    echo "ERROR=MANIFEST:$1" >&2
    exit 65
}

line_no=0
while IFS=$'\t' read -r id path block predecessors extra || [[ -n "${id:-}" ]]; do
    line_no=$((line_no + 1))

    [[ -z "${id:-}" ]] && continue
    [[ "$id" == \#* ]] && continue

    [[ -z "${extra:-}" ]] || manifest_error "LINE_${line_no}_TOO_MANY_COLUMNS"
    [[ "$id" =~ ^[a-z0-9][a-z0-9._-]*$ ]] ||
        manifest_error "LINE_${line_no}_INVALID_ID:$id"
    [[ -z "${SEEN[$id]+x}" ]] ||
        manifest_error "LINE_${line_no}_DUPLICATE_ID:$id"

    [[ -n "${path:-}" ]] ||
        manifest_error "LINE_${line_no}_PATH_REQUIRED:$id"
    [[ "$path" != /* ]] ||
        manifest_error "LINE_${line_no}_ABSOLUTE_PATH_FORBIDDEN:$id"
    case "/$path/" in
        */../*|*/./*)
            manifest_error "LINE_${line_no}_NON_CANONICAL_PATH:$id"
            ;;
    esac
    [[ -f "$ROOT/$path" ]] ||
        manifest_error "LINE_${line_no}_CONTRACT_NOT_FOUND:$id:$path"

    case "${block:-}" in
        primary|any|none) ;;
        *) manifest_error "LINE_${line_no}_INVALID_BLOCK_MODE:$id:${block:-EMPTY}" ;;
    esac

    predecessors="${predecessors:--}"
    if [[ "$predecessors" != '-' ]]; then
        IFS=',' read -ra pred_ids <<<"$predecessors"
        declare -A pred_seen=()
        for pred in "${pred_ids[@]}"; do
            [[ "$pred" =~ ^[a-z0-9][a-z0-9._-]*$ ]] ||
                manifest_error "LINE_${line_no}_INVALID_PREDECESSOR:$id:$pred"
            [[ -n "${SEEN[$pred]+x}" ]] ||
                manifest_error "LINE_${line_no}_PREDECESSOR_MUST_APPEAR_EARLIER:$id:$pred"
            [[ -z "${pred_seen[$pred]+x}" ]] ||
                manifest_error "LINE_${line_no}_DUPLICATE_PREDECESSOR:$id:$pred"
            pred_seen["$pred"]=1
        done
        unset pred_seen
    fi

    SEEN["$id"]=1
    IDS+=("$id")
    PATHS+=("$path")
    BLOCKS+=("$block")
    PREDS+=("$predecessors")
done <"$MANIFEST"

[[ "${#IDS[@]}" -gt 0 ]] || manifest_error EMPTY

printf 'id\tpath\texecution_count\tprimary\tpredecessor\toverall\tblock_mode\trelease_blocking\texit_code\tpredecessor_failures\tlog\n' >"$CACHE"

primary_failures=0
predecessor_failures=0
release_blocking_failures=0

echo 'CONTRACT02_RUNNER_VERSION=1'
echo "CONTRACT02_MANIFEST=$MANIFEST"
echo "CONTRACT02_CONTRACT_COUNT=${#IDS[@]}"
echo "CONTRACT02_RESULT_CACHE=$CACHE"

for i in "${!IDS[@]}"; do
    id="${IDS[$i]}"
    path="${PATHS[$i]}"
    block="${BLOCKS[$i]}"
    predecessors="${PREDS[$i]}"
    log="$OUTPUT/logs/$id.log"

    pred_status='PASS'
    pred_fail_ids='-'
    if [[ "$predecessors" != '-' ]]; then
        IFS=',' read -ra pred_ids <<<"$predecessors"
        failed=()
        for pred in "${pred_ids[@]}"; do
            if [[ "${OVERALL[$pred]:-MISSING}" != 'PASS' ]]; then
                pred_status='FAIL'
                failed+=("$pred")
            fi
        done
        if [[ "${#failed[@]}" -gt 0 ]]; then
            pred_fail_ids="$(IFS=,; echo "${failed[*]}")"
        fi
    fi

    echo "RUNNING_CONTRACT02=$id PATH=$path"

    start_ns="$(date +%s%N 2>/dev/null || date +%s000000000)"
    set +e
    (
        export SUBSHELL_CONTRACT02_RUNNER_ACTIVE=1
        export SUBSHELL_CONTRACT02_ID="$id"
        export SUBSHELL_CONTRACT02_RESULT_CACHE="$CACHE"
        export SUBSHELL_CONTRACT02_PREDECESSORS="$predecessors"
        export SUBSHELL_CONTRACT02_PREDECESSOR_STATUS="$pred_status"
        bash "$ROOT/$path"
    ) >"$log" 2>&1
    rc=$?
    set -e
    end_ns="$(date +%s%N 2>/dev/null || date +%s000000000)"

    mapfile -t markers < <(
        grep -E '^CONTRACT02_PRIMARY_RESULT=(PASS|FAIL)$' "$log" 2>/dev/null || true
    )

    primary='FAIL'
    protocol_error=''
    if [[ "${#markers[@]}" -ne 1 ]]; then
        protocol_error="PRIMARY_RESULT_MARKER_COUNT_${#markers[@]}"
    else
        marker="${markers[0]#CONTRACT02_PRIMARY_RESULT=}"
        case "$marker:$rc" in
            PASS:0)
                primary='PASS'
                ;;
            FAIL:0)
                protocol_error='FAIL_MARKER_WITH_ZERO_EXIT'
                ;;
            PASS:*)
                protocol_error="PASS_MARKER_WITH_NONZERO_EXIT_$rc"
                ;;
            FAIL:*)
                primary='FAIL'
                ;;
        esac
    fi

    if [[ -n "$protocol_error" ]]; then
        primary='FAIL'
        echo "CONTRACT02_PROTOCOL_ERROR=$id:$protocol_error"
    fi

    [[ "$primary" == 'FAIL' ]] &&
        primary_failures=$((primary_failures + 1))
    [[ "$pred_status" == 'FAIL' ]] &&
        predecessor_failures=$((predecessor_failures + 1))

    overall='PASS'
    if [[ "$primary" == 'FAIL' || "$pred_status" == 'FAIL' ]]; then
        overall='FAIL'
    fi

    blocking='NO'
    case "$block" in
        primary)
            [[ "$primary" == 'FAIL' ]] && blocking='YES'
            ;;
        any)
            [[ "$overall" == 'FAIL' ]] && blocking='YES'
            ;;
        none)
            ;;
    esac

    [[ "$blocking" == 'YES' ]] &&
        release_blocking_failures=$((release_blocking_failures + 1))

    PRIMARY["$id"]="$primary"
    PRED_STATUS["$id"]="$pred_status"
    OVERALL["$id"]="$overall"
    RELEASE_BLOCK["$id"]="$blocking"

    if [[ "$start_ns" =~ ^[0-9]+$ && "$end_ns" =~ ^[0-9]+$ ]]; then
        duration_ms=$(( (end_ns - start_ns) / 1000000 ))
    else
        duration_ms=0
    fi

    printf '%s\t%s\t1\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$id" "$path" "$primary" "$pred_status" "$overall" "$block" \
        "$blocking" "$rc" "$pred_fail_ids" "$log" >>"$CACHE"

    echo "CONTRACT02_RESULT=$id PRIMARY=$primary PREDECESSOR=$pred_status OVERALL=$overall RELEASE_BLOCKING=$blocking EXIT_CODE=$rc DURATION_MS=$duration_ms"
done

release_gate='PASS'
runner_rc=0
if [[ "$release_blocking_failures" -gt 0 ]]; then
    release_gate='BLOCKED'
    runner_rc=1
fi

cat >"$SUMMARY" <<EOF
SUBSHELL_CONTRACT02_RUN_ONCE_SUMMARY_V1
CONTRACTS_EXECUTED=${#IDS[@]}
EXECUTION_COUNT_PER_CONTRACT=1
PRIMARY_FAILURES=$primary_failures
PREDECESSOR_FAILURES=$predecessor_failures
RELEASE_BLOCKING_FAILURES=$release_blocking_failures
RELEASE_GATE=$release_gate
RESULT_CACHE=$CACHE
EOF

cat "$SUMMARY"

if [[ "$release_gate" == 'PASS' ]]; then
    echo 'RESULT=SUBSHELL_CONTRACT02_RUNNER_PASS'
else
    echo 'RESULT=SUBSHELL_CONTRACT02_RUNNER_BLOCKED'
fi

exit "$runner_rc"
