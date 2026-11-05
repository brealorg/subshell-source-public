#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_RELEASE48_ONE_RELEASE_SCOPE_CHECKER_V1 (#48): the non-native suite every release from 0.92 on runs (one file, not one per release)

FAIL=0

pass() {
    echo "PASS=$1"
}

fail() {
    echo "FAIL=$1"
    FAIL=1
}

CHILDREN=(
    'source-contract|tools/check-source-contract.sh'
)

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-release-contract02-suite.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT INT TERM

for spec in "${CHILDREN[@]}"; do
    id="${spec%%|*}"
    path="${spec#*|}"
    log="$TMP/$id.log"

    if [[ ! -f "$path" ]]; then
        fail "RELEASE_SUITE_MISSING_${id}"
        continue
    fi

    set +e
    bash "$path" >"$log" 2>&1
    rc=$?
    set -e

    if [[ "$rc" -eq 0 ]]; then
        pass "RELEASE_SUITE_${id}"
    else
        fail "RELEASE_SUITE_${id}_RC_${rc}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_RELEASE_NONNATIVE_SUITE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_RELEASE_NONNATIVE_SUITE_CONTRACT_FAIL'
exit 1
