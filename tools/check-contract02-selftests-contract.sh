#!/usr/bin/env bash
set -u
set -o pipefail

# STEP3_CONTRACT02_SELFTESTS_GATED_V1 (2026-10-09, owner-approved): the release
# runner and the release qualification verifier are exercised on every land,
# not only at release time. Replaces check-contract02b (which only looked for
# the selftest's case names) and check-contract02f.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

fail() { echo "FAIL=$1"; echo 'RESULT=SUBSHELL_CONTRACT02_SELFTESTS_CONTRACT_FAIL'; exit 1; }

bash tools/contract02/selftest.sh >/dev/null ||
    fail CONTRACT02_RUN_ONCE_SELFTEST
echo 'PASS=CONTRACT02_RUN_ONCE_SELFTEST'

bash tools/contract02/verify-release-qualification-selftest.sh >/dev/null ||
    fail CONTRACT02F_VERIFIER_SELFTEST
echo 'PASS=CONTRACT02F_VERIFIER_SELFTEST'

# RELEASETOOL02E_PUBLIC_SOURCE_SNAPSHOTS_V1 (2026-10-16, owner-approved): the
# public source snapshot (Stage C) against local repositories, no network.
PYTHONDONTWRITEBYTECODE=1 python3 tools/release02/selftest-source-snapshot.py >/dev/null ||
    fail RELEASETOOL02E_SOURCE_SNAPSHOT_SELFTEST
echo 'PASS=RELEASETOOL02E_SOURCE_SNAPSHOT_SELFTEST'

echo 'RESULT=SUBSHELL_CONTRACT02_SELFTESTS_CONTRACT_PASS'
