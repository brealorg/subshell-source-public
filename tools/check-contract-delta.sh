#!/usr/bin/env bash
set -u
set -o pipefail

BASELINE_ROOT="${1:-}"
CANDIDATE_ROOT="${2:-}"

if [[ -z "$BASELINE_ROOT" || -z "$CANDIDATE_ROOT" ]]; then
    echo "STOP=USAGE_CHECK_CONTRACT_DELTA_BASELINE_CANDIDATE" >&2
    exit 2
fi

for pair in \
    "BASELINE:$BASELINE_ROOT" \
    "CANDIDATE:$CANDIDATE_ROOT"
do
    label="${pair%%:*}"
    root="${pair#*:}"
    [[ -d "$root" ]] || {
        echo "STOP=${label}_ROOT_MISSING path=$root" >&2
        exit 1
    }
    [[ -f "$root/tools/check-all-contracts.sh" ]] || {
        echo "STOP=${label}_CONTRACT_AGGREGATOR_MISSING" >&2
        exit 1
    }
done

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-contract-delta.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

run_suite() {
    local root="$1"
    local log="$2"
    local rcfile="$3"
    local rc

    set +e
    (
        cd "$root" || exit 97
        bash tools/check-all-contracts.sh
    ) >"$log" 2>&1
    rc=$?
    printf '%s\n' "$rc" >"$rcfile"
}

# Contract scripts are historically not guaranteed to be process-isolated.
# Run baseline and candidate sequentially with distinct TMPDIR roots so the
# same immutable inputs produce deterministic failure fingerprints.
mkdir -p "$TMP/baseline-tmp" "$TMP/candidate-tmp"

TMPDIR="$TMP/baseline-tmp" \
    run_suite "$BASELINE_ROOT" "$TMP/baseline.log" "$TMP/baseline.rc"

TMPDIR="$TMP/candidate-tmp" \
    run_suite "$CANDIDATE_ROOT" "$TMP/candidate.log" "$TMP/candidate.rc"

BASE_RC="$(cat "$TMP/baseline.rc")"
CANDIDATE_RC="$(cat "$TMP/candidate.rc")"

python3 - \
    "$TMP/baseline.log" "$BASELINE_ROOT" "$TMP/baseline.failures" \
    "$TMP/candidate.log" "$CANDIDATE_ROOT" "$TMP/candidate.failures" <<'PY_DELTA'
from pathlib import Path
import sys

def fingerprints(log_path: str, root_path: str):
    root = str(Path(root_path).resolve()).rstrip("/") + "/"
    found = set()
    for raw in Path(log_path).read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("FAIL="):
            found.add(line)
            continue
        if line.startswith("RESULT=") and line.endswith("FAIL"):
            found.add(line)
            continue
    return sorted(found)

args = sys.argv[1:]
for log, root, out in (args[:3], args[3:]):
    Path(out).write_text(
        "\n".join(fingerprints(log, root)) + "\n",
        encoding="utf-8",
    )
PY_DELTA

LC_ALL=C sort -u "$TMP/baseline.failures" -o "$TMP/baseline.failures"
LC_ALL=C sort -u "$TMP/candidate.failures" -o "$TMP/candidate.failures"
LC_ALL=C comm -13 "$TMP/baseline.failures" "$TMP/candidate.failures" >"$TMP/new.failures"

BASE_COUNT="$(awk 'NF{n++} END{print n+0}' "$TMP/baseline.failures")"
CANDIDATE_COUNT="$(awk 'NF{n++} END{print n+0}' "$TMP/candidate.failures")"
NEW_COUNT="$(awk 'NF{n++} END{print n+0}' "$TMP/new.failures")"

BASE_HASH="$(sha256sum "$TMP/baseline.failures" | awk '{print $1}')"
CANDIDATE_HASH="$(sha256sum "$TMP/candidate.failures" | awk '{print $1}')"
NEW_HASH="$(sha256sum "$TMP/new.failures" | awk '{print $1}')"

echo "BASELINE_CONTRACT_RC=$BASE_RC"
echo "CANDIDATE_CONTRACT_RC=$CANDIDATE_RC"
echo "BASELINE_FAILURE_SIGNATURES=$BASE_COUNT"
echo "CANDIDATE_FAILURE_SIGNATURES=$CANDIDATE_COUNT"
echo "NEW_FAILURE_SIGNATURES=$NEW_COUNT"
echo "BASELINE_FAILURE_SET_SHA256=$BASE_HASH"
echo "CANDIDATE_FAILURE_SET_SHA256=$CANDIDATE_HASH"
echo "NEW_FAILURE_SET_SHA256=$NEW_HASH"

if [[ "$CANDIDATE_RC" -ne 0 && "$CANDIDATE_COUNT" -eq 0 ]]; then
    echo "STOP=CANDIDATE_CONTRACT_FAILURE_UNCLASSIFIED" >&2
    tail -n 80 "$TMP/candidate.log" >&2
    exit 1
fi

if [[ "$NEW_COUNT" -ne 0 ]]; then
    echo
    echo "===== NEW CONTRACT FAILURES ====="
    cat "$TMP/new.failures"
    echo
    echo "STOP=NEW_CONTRACT_FAILURES_INTRODUCED count=$NEW_COUNT" >&2
    exit 1
fi

if [[ "$CANDIDATE_RC" -eq 0 ]]; then
    echo "CONTRACT_DEBT_STATE=CANDIDATE_FULLY_GREEN"
elif [[ "$BASE_RC" -ne 0 ]]; then
    echo "CONTRACT_DEBT_STATE=BASELINE_DEBT_PRESERVED_OR_REDUCED"
else
    echo "STOP=CANDIDATE_RED_FROM_GREEN_BASELINE_WITHOUT_SIGNATURE" >&2
    exit 1
fi

echo "RESULT=SUBSHELL_CONTRACT_DELTA_PASS"
