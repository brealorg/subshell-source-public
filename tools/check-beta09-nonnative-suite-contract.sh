#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_BETA09_NONNATIVE_CONTRACT02_ADAPTER_V1

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
    # followpost01a1a and followpost01a1b were retired on 2026-10-14 (step 3,
    # owner approved).
    # settings02 and theme01-system-bar were retired on 2026-10-14 (step 3,
    # owner approved).
    # media-audio-r27 and a11y01a411 were retired on 2026-10-13 (step 3,
    # owner approved).
    # profile01a7a4 was retired on 2026-10-13 (step 3, owner approved); its
    # behaviour is covered by Step3RemoteHistorySyncComposeTest.
    # auth08a1, quality01d-usektx and auth08a2 were retired on 2026-10-12
    # (step 3, owner approved); their behaviour is covered by Step3* tests.
    # crosspost01a1a-a1e and crosspost01a2 were retired on 2026-10-13 (step 3,
    # owner approved); their behaviour is covered by Step3Crosspost* tests.
    # messages02d5b, d5b2, d5c and d5d were retired on 2026-10-13 (step 3,
    # owner approved); their behaviour is covered by Step3NewMessage* tests.
)

TMP="$(mktemp -d "${TMPDIR:-/tmp}/subshell-beta09-contract02-suite.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT INT TERM

for spec in "${CHILDREN[@]}"; do
    id="${spec%%|*}"
    path="${spec#*|}"
    log="$TMP/$id.log"

    if [[ ! -f "$path" ]]; then
        fail "BETA09_SUITE_MISSING_${id}"
        continue
    fi

    set +e
    bash "$path" >"$log" 2>&1
    rc=$?
    set -e

    if [[ "$rc" -eq 0 ]]; then
        pass "BETA09_SUITE_${id}"
    else
        fail "BETA09_SUITE_${id}_RC_${rc}"
    fi
done

if [[ "$FAIL" -eq 0 ]]; then
    echo 'CONTRACT02_PRIMARY_RESULT=PASS'
    echo 'RESULT=SUBSHELL_BETA09_NONNATIVE_SUITE_CONTRACT_PASS'
    exit 0
fi

echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
echo 'RESULT=SUBSHELL_BETA09_NONNATIVE_SUITE_CONTRACT_FAIL'
exit 1
