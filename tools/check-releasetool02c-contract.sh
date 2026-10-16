#!/usr/bin/env bash
set -u -o pipefail

DRIVER='tools/release02/release-beta.py'
SELFTEST='tools/release02/selftest-release-beta.py'
DOC='docs/workflow/subshell-releasetool02-end-to-end-v1.md'
MANIFEST='tools/contract02/manifests/releasetool02c.tsv'

fail() {
    echo "FAIL=$1"
    echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
    echo 'RESULT=SUBSHELL_RELEASETOOL02C_CONTRACT_FAIL'
    exit 1
}

for f in "$DRIVER" "$SELFTEST" "$DOC" "$MANIFEST"; do
    [[ -f "$f" ]] || fail "MISSING_${f//[^A-Za-z0-9]/_}"
done

python3 - "$DRIVER" "$SELFTEST" <<'PY'
from pathlib import Path
import sys
for source_name in sys.argv[1:]:
    source = Path(source_name)
    compile(source.read_text(), str(source), "exec")
PY
[[ "$?" -eq 0 ]] || fail PYTHON_SYNTAX

PYTHONDONTWRITEBYTECODE=1 python3 "$SELFTEST" ||
    fail SELFTEST

grep -Fq 'promote-github-beta.py' "$DRIVER" &&
grep -Fq 'promote-public-site-manifest.py' "$DRIVER" &&
grep -Fq 'PROMOTION_STATE_MISSING_AFTER_STAGE_A_OR_RESUME' "$DRIVER" ||
    fail STAGE_A_TO_B_WIRING

grep -Fq 'mode.add_argument("--release-record")' "$DRIVER" &&
grep -Fq 'mode.add_argument("--promotion-state")' "$DRIVER" &&
grep -Fq 'MODE={mode}' "$DRIVER" ||
    fail FULL_AND_RESUME_MODES

grep -Fq -- '--existing-only' "$DRIVER" &&
grep -Fq 'EXISTING_ONLY_PUBLIC_SITE_NOT_ALREADY_PROMOTED' "$DRIVER" &&
grep -Fq 'EXISTING_ONLY_PAGES_MANIFEST_NOT_ALREADY_PROMOTED' "$DRIVER" ||
    fail EXISTING_ONLY_NO_PUSH_PREFLIGHT

grep -Fq 'STATE=COMPLETE' "$DRIVER" &&
grep -Fq 'FINAL_RELEASE_BLOCKING_FAILURE' "$DRIVER" &&
grep -Fq 'PRIVATE_APP_SOURCE_PUBLIC=NO' "$DRIVER" ||
    fail FINAL_STATE_FENCES

! grep -Fq 'assembleRelease' "$DRIVER" &&
! grep -Fq 'apksigner' "$DRIVER" &&
! grep -Fq 'adb ' "$DRIVER" ||
    fail NO_BUILD_SIGN_DEVICE_SCOPE

grep -Fqi 'Full mode' "$DOC" &&
grep -Fqi 'Resume mode' "$DOC" &&
grep -Fqi 'existing-only' "$DOC" &&
grep -Fqi 'beta.3 replay' "$DOC" &&
grep -Fqi 'COMPLETE' "$DOC" ||
    fail WORKFLOW_DOCUMENTED

grep -Fq $'releasetool02c\ttools/check-releasetool02c-contract.sh\tprimary\treleasetool02b' "$MANIFEST" ||
    fail NATIVE_MANIFEST_REGISTRATION

echo 'PASS=RELEASETOOL02C_STAGE_A_TO_B_ORCHESTRATION'
echo 'PASS=RELEASETOOL02C_FULL_AND_RESUME_MODES'
echo 'PASS=RELEASETOOL02C_EXISTING_ONLY_REPLAY_GUARD'
echo 'PASS=RELEASETOOL02C_COMPLETE_FINAL_STATE'
echo 'PASS=RELEASETOOL02C_NO_BUILD_SIGN_DEVICE_SCOPE'
echo 'PASS=RELEASETOOL02C_NATIVE_PREDECESSOR_WIRING'
echo 'CONTRACT02_PRIMARY_RESULT=PASS'
echo 'RESULT=SUBSHELL_RELEASETOOL02C_CONTRACT_PASS'
