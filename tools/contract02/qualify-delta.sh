#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_CONTRACT02F_CANONICAL_DELTA_QUALIFIER_V1

usage() {
    cat <<'USAGE'
Usage:
  tools/contract02/qualify-delta.sh \
    --base <known-good-source-commit> \
    --manifest <contract02-native-manifest> \
    --output <external-output-dir>
USAGE
}

BASE=''
MANIFEST=''
OUTPUT=''

while [[ $# -gt 0 ]]; do
    case "$1" in
        --base)
            [[ $# -ge 2 ]] || { usage >&2; exit 64; }
            BASE="$2"
            shift 2
            ;;
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

[[ -n "$BASE" ]] || { echo 'ERROR=BASE_REQUIRED' >&2; exit 64; }
[[ -n "$MANIFEST" ]] || { echo 'ERROR=MANIFEST_REQUIRED' >&2; exit 64; }
[[ -n "$OUTPUT" ]] || { echo 'ERROR=OUTPUT_REQUIRED' >&2; exit 64; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

canonical_path() {
    python3 - "$1" <<'PY'
from pathlib import Path
import sys
print(Path(sys.argv[1]).expanduser().resolve())
PY
}

ROOT_CANON="$(canonical_path "$ROOT")"

if [[ "$MANIFEST" != /* ]]; then
    MANIFEST="$ROOT/$MANIFEST"
fi
MANIFEST="$(canonical_path "$MANIFEST")"
OUTPUT="$(canonical_path "$OUTPUT")"

[[ -f "$MANIFEST" ]] || {
    echo "ERROR=MANIFEST_NOT_FOUND:$MANIFEST" >&2
    exit 66
}

case "$OUTPUT/" in
    "$ROOT_CANON/"*)
        echo "ERROR=OUTPUT_MUST_BE_OUTSIDE_SOURCE_TREE:$OUTPUT" >&2
        exit 65
        ;;
esac

[[ -z "$(git status --porcelain)" ]] || {
    echo 'ERROR=SOURCE_WORKTREE_NOT_CLEAN' >&2
    exit 65
}

HEAD="$(git rev-parse HEAD)" || exit 65
TREE="$(git rev-parse 'HEAD^{tree}')" || exit 65
BASE_RESOLVED="$(git rev-parse "$BASE^{commit}")" || {
    echo "ERROR=BASE_COMMIT_NOT_FOUND:$BASE" >&2
    exit 65
}

git merge-base --is-ancestor "$BASE_RESOLVED" "$HEAD" || {
    echo "ERROR=BASE_NOT_ANCESTOR:$BASE_RESOLVED:$HEAD" >&2
    exit 65
}

case "$MANIFEST/" in
    "$ROOT_CANON/"*) ;;
    *)
        echo "ERROR=MANIFEST_OUTSIDE_SOURCE_TREE:$MANIFEST" >&2
        exit 65
        ;;
esac

MANIFEST_REL="${MANIFEST#"$ROOT_CANON/"}"
[[ "$MANIFEST_REL" != "$MANIFEST" ]] || {
    echo 'ERROR=MANIFEST_RELATIVE_PATH_RESOLUTION_FAILED' >&2
    exit 65
}

MANIFEST_SHA256="$(sha256sum "$MANIFEST" | awk '{print $1}')"

mkdir -p "$OUTPUT" || exit 73
CONTRACT_OUT="$OUTPUT/contracts"
COMMITS="$OUTPUT/commits.txt"
CHANGED="$OUTPUT/changed-paths.txt"
UNIT_LOG="$OUTPUT/testDebugUnitTest.log"
RECORD="$OUTPUT/qualification-record.env"

git log --reverse --format='%H %s' "$BASE_RESOLVED..$HEAD" >"$COMMITS"
git diff --name-only "$BASE_RESOLVED" "$HEAD" | sort >"$CHANGED"

COMMIT_COUNT="$(git rev-list --count "$BASE_RESOLVED..$HEAD")"
CHANGED_PATH_COUNT="$(wc -l <"$CHANGED" | tr -d ' ')"

echo 'CONTRACT02_QUALIFIER_VERSION=1'
echo "SOURCE_COMMIT=$HEAD"
echo "SOURCE_TREE=$TREE"
echo "BASE_COMMIT=$BASE_RESOLVED"
echo "COMMIT_COUNT=$COMMIT_COUNT"
echo "CHANGED_PATH_COUNT=$CHANGED_PATH_COUNT"
echo "MANIFEST_PATH=$MANIFEST_REL"
echo "MANIFEST_SHA256=$MANIFEST_SHA256"

set +e
bash tools/contract02/run-once.sh \
    --manifest "$MANIFEST_REL" \
    --output "$CONTRACT_OUT"
CONTRACT_RC=$?
set -e

[[ -f "$CONTRACT_OUT/summary.txt" ]] || {
    echo "ERROR=CONTRACT02_RUNNER_INFRA_FAILURE rc=$CONTRACT_RC" >&2
    exit 70
}

summary_value() {
    local key="$1"
    local value
    value="$(sed -n "s/^${key}=//p" "$CONTRACT_OUT/summary.txt")"
    [[ "$(printf '%s\n' "$value" | sed '/^$/d' | wc -l | tr -d ' ')" -eq 1 ]] || {
        echo "ERROR=SUMMARY_KEY_INVALID:$key" >&2
        exit 70
    }
    printf '%s' "$value"
}

PRIMARY_FAILURES="$(summary_value PRIMARY_FAILURES)"
PREDECESSOR_FAILURES="$(summary_value PREDECESSOR_FAILURES)"
RELEASE_BLOCKING_FAILURES="$(summary_value RELEASE_BLOCKING_FAILURES)"
CONTRACT_GATE="$(summary_value RELEASE_GATE)"

set +e
./gradlew \
    --offline \
    --no-daemon \
    --no-configuration-cache \
    :app:testDebugUnitTest \
    >"$UNIT_LOG" 2>&1
UNIT_RC=$?
set -e

UNIT_KOTLIN_WARNING_COUNT="$(grep -Ec '^w: ' "$UNIT_LOG" || true)"

UNIT_TESTS='FAIL'
if [[ "$UNIT_RC" -eq 0 ]]; then
    UNIT_TESTS='PASS'
fi

# SECRETSCAN01_QUALIFICATION_SCANS_THE_WHOLE_TREE_V1: the tree is scanned
# with gitleaks and its own .gitleaks.toml exactly as Stage C will scan the
# public snapshot, so a finding stops the release before it is signed (0.85
# was signed and published before Stage C found two false positives).
GITLEAKS="${SUBSHELL_GITLEAKS:-$HOME/.local/bin/gitleaks}"
SCAN_OUT="$OUTPUT/secret-scan"
SECRET_SCAN='MISSING'
if [[ -x "$GITLEAKS" ]]; then
    mkdir -p "$SCAN_OUT"
    SCAN_TREE="$(mktemp -d)"
    set +e
    SCAN_RC=2
    if git archive "$HEAD" | tar -x -C "$SCAN_TREE" &&
        [[ -f "$SCAN_TREE/.gitleaks.toml" ]]; then
        (cd "$SCAN_TREE" && "$GITLEAKS" dir . \
            --config .gitleaks.toml \
            --redact=100 \
            --no-banner \
            --exit-code 1 \
            --report-format json \
            --report-path "$SCAN_OUT/gitleaks-report.json") \
            >"$SCAN_OUT/gitleaks.log" 2>&1
        SCAN_RC=$?
    fi
    set -e
    rm -rf "$SCAN_TREE"
    case "$SCAN_RC" in
        0) SECRET_SCAN='PASS' ;;
        1) SECRET_SCAN='FINDINGS' ;;
        *) SECRET_SCAN='FAILED' ;;
    esac
fi

RELEASE_GATE='BLOCKED'
if [[ "$CONTRACT_GATE" == 'PASS' &&
      "$RELEASE_BLOCKING_FAILURES" == '0' &&
      "$UNIT_TESTS" == 'PASS' &&
      "$UNIT_KOTLIN_WARNING_COUNT" == '0' &&
      "$SECRET_SCAN" == 'PASS' ]]; then
    RELEASE_GATE='PASS'
fi

cat >"$RECORD" <<EOF_RECORD
CONTRACT02_QUALIFICATION_RECORD_VERSION=1
SOURCE_COMMIT=$HEAD
SOURCE_TREE=$TREE
BASE_COMMIT=$BASE_RESOLVED
COMMIT_COUNT=$COMMIT_COUNT
CHANGED_PATH_COUNT=$CHANGED_PATH_COUNT
MANIFEST_PATH=$MANIFEST_REL
MANIFEST_SHA256=$MANIFEST_SHA256
PRIMARY_FAILURES=$PRIMARY_FAILURES
PREDECESSOR_FAILURES=$PREDECESSOR_FAILURES
RELEASE_BLOCKING_FAILURES=$RELEASE_BLOCKING_FAILURES
CONTRACT_GATE=$CONTRACT_GATE
UNIT_TESTS=$UNIT_TESTS
UNIT_TEST_RC=$UNIT_RC
UNIT_KOTLIN_WARNING_COUNT=$UNIT_KOTLIN_WARNING_COUNT
SECRET_SCAN=$SECRET_SCAN
RELEASE_GATE=$RELEASE_GATE
EOF_RECORD

POST_HEAD="$(git rev-parse HEAD)"
POST_TREE="$(git rev-parse 'HEAD^{tree}')"

if [[ "$POST_HEAD" != "$HEAD" ||
      "$POST_TREE" != "$TREE" ||
      -n "$(git status --porcelain)" ]]; then
    echo 'ERROR=SOURCE_INTEGRITY_CHANGED_DURING_QUALIFICATION' >&2
    exit 70
fi

echo "PRIMARY_FAILURES=$PRIMARY_FAILURES"
echo "PREDECESSOR_FAILURES=$PREDECESSOR_FAILURES"
echo "RELEASE_BLOCKING_FAILURES=$RELEASE_BLOCKING_FAILURES"
echo "CONTRACT_GATE=$CONTRACT_GATE"
echo "UNIT_TESTS=$UNIT_TESTS"
echo "UNIT_TEST_RC=$UNIT_RC"
echo "UNIT_KOTLIN_WARNING_COUNT=$UNIT_KOTLIN_WARNING_COUNT"
echo "SECRET_SCAN=$SECRET_SCAN"
echo "RELEASE_GATE=$RELEASE_GATE"
echo "QUALIFICATION_RECORD=$RECORD"

if [[ "$RELEASE_GATE" == 'PASS' ]]; then
    echo 'RESULT=SUBSHELL_CONTRACT02_DELTA_QUALIFICATION_PASS'
    exit 0
fi

echo 'RESULT=SUBSHELL_CONTRACT02_DELTA_QUALIFICATION_BLOCKED'
exit 1
