#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA18_NONNATIVE_CONTRACT02_ADAPTER_V1

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

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-beta18-contract02-suite.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT INT TERM

for spec in "${CHILDREN[@]}"; do
    id="${spec%%|*}"
    path="${spec#*|}"
    log="$TMP/$id.log"

    if [[ ! -f "$path" ]]; then
        fail "BETA18_SUITE_MISSING_${id}"
        continue
    fi

    set +e
    bash "$path" >"$log" 2>&1
    rc=$?
    set -e

    if [[ "$rc" -eq 0 ]]; then
        pass "BETA18_SUITE_${id}"
    else
        fail "BETA18_SUITE_${id}_RC_${rc}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA18_NONNATIVE_SUITE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA18_NONNATIVE_SUITE_CONTRACT_FAIL'
exit 1
