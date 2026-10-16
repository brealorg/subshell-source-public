#!/usr/bin/env bash
set -u -o pipefail

DRIVER='tools/release02/release-beta-operator.py'
SELFTEST='tools/release02/selftest-release-beta-operator.py'
INTEGRATION='tools/release02/integration-test-release-beta-operator.py'
DOC='docs/workflow/subshell-releasetool02-full-operator-v1.md'
MANIFEST='tools/contract02/manifests/releasetool02d.tsv'

fail() {
    echo "FAIL=$1"
    echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
    echo 'RESULT=SUBSHELL_RELEASETOOL02D_CONTRACT_FAIL'
    exit 1
}

for f in "$DRIVER" "$SELFTEST" "$INTEGRATION" "$DOC" "$MANIFEST"; do
    [[ -f "$f" ]] || fail "MISSING_${f//[^A-Za-z0-9]/_}"
done

python3 - "$DRIVER" "$SELFTEST" "$INTEGRATION" <<'PY'
from pathlib import Path
import sys
for source_name in sys.argv[1:]:
    source = Path(source_name)
    compile(source.read_text(), str(source), "exec")
PY
[[ "$?" -eq 0 ]] || fail PYTHON_SYNTAX

PYTHONDONTWRITEBYTECODE=1 python3 "$SELFTEST" ||
    fail SELFTEST

PYTHONDONTWRITEBYTECODE=1 python3 "$INTEGRATION" ||
    fail HERMETIC_INTEGRATION

grep -Fq 'STATES = ("INIT", "QUALIFIED", "SIGNED", "DEVICE_SMOKED", "COMPLETE")' "$DRIVER" &&
grep -Fq 'STATE=QUALIFIED' "$DRIVER" &&
grep -Fq 'STATE=SIGNED' "$DRIVER" &&
grep -Fq 'STATE=DEVICE_SMOKED' "$DRIVER" &&
grep -Fq 'STATE=COMPLETE' "$DRIVER" ||
    fail CHECKPOINT_STATE_MACHINE

grep -Fq 'tools/contract02/qualify-delta.sh' "$DRIVER" &&
grep -Fq '"--base"' "$DRIVER" &&
grep -Fq '"--manifest"' "$DRIVER" &&
grep -Fq '"--output"' "$DRIVER" ||
    fail QUALIFICATION_DELEGATION

grep -Fq 'tools/release01/prepare-signed-preview.sh' "$DRIVER" &&
grep -Fq '"--qualification-record"' "$DRIVER" &&
grep -Fq '"--version-name"' "$DRIVER" &&
grep -Fq '"--version-code"' "$DRIVER" ||
    fail SIGNED_PREVIEW_DELEGATION

grep -Fq 'tools/release01/smoke-preview-device.sh' "$DRIVER" &&
grep -Fq '"--release-record"' "$DRIVER" &&
grep -Fq '"--qualified-source-root"' "$DRIVER" ||
    fail DEVICE_SMOKE_DELEGATION

grep -Fq 'tools/release02/release-beta.py' "$DRIVER" &&
grep -Fq '"--public-target"' "$DRIVER" &&
grep -Fq '"--public-main-before"' "$DRIVER" &&
grep -Fq '"--pages-main-before"' "$DRIVER" &&
grep -Fq '"--previous-version"' "$DRIVER" ||
    fail RELEASETOOL02C_DELEGATION

grep -Fq 'RESUME_INPUT_MISMATCH_' "$DRIVER" &&
grep -Fq 'QUALIFICATION_RECORD_SHA256' "$DRIVER" &&
grep -Fq 'RELEASE_RECORD_SHA256' "$DRIVER" &&
grep -Fq 'PREVIEW_APK_SHA256' "$DRIVER" &&
grep -Fq 'DEVICE_SMOKE_LOG_SHA256' "$DRIVER" &&
grep -Fq 'RELEASE_BETA_STATE_SHA256' "$DRIVER" ||
    fail RESUME_INTEGRITY_FENCES

grep -Fq 'next_attempt_dir' "$DRIVER" &&
grep -Fq 'next_attempt_log' "$DRIVER" &&
grep -Fq 'candidate_log = output / f"{prefix}-attempt-{index:03d}.log"' "$DRIVER" &&
grep -Fq 'if not candidate.exists() and not candidate_log.exists():' "$DRIVER" &&
grep -Fq 'PASS=RELEASETOOL02D_FAILED_SIBLING_LOG_RESERVES_ATTEMPT_NUMBER' "$SELFTEST" ||
    fail NONDESTRUCTIVE_ATTEMPT_POLICY

grep -Fq 'PREDECESSOR_FAILURE=YES_RELEASETOOL01A' "$DRIVER" &&
grep -Fq 'PREDECESSOR_FAILURE=YES_RELEASETOOL01B' "$DRIVER" &&
grep -Fq 'PREDECESSOR_FAILURE=YES_RELEASETOOL02C' "$DRIVER" ||
    fail FAILURE_CLASSIFICATION

! grep -Fq 'gradlew' "$DRIVER" &&
! grep -Fq 'apksigner' "$DRIVER" &&
! grep -Fq '"adb"' "$DRIVER" &&
! grep -Fq '"gh"' "$DRIVER" ||
    fail OPERATOR_MUST_DELEGATE_MUTATIONS

grep -Fqi 'INIT' "$DOC" &&
grep -Fqi 'QUALIFIED' "$DOC" &&
grep -Fqi 'SIGNED' "$DOC" &&
grep -Fqi 'DEVICE_SMOKED' "$DOC" &&
grep -Fqi 'COMPLETE' "$DOC" &&
grep -Fqi 'resume' "$DOC" &&
grep -Fqi 'attempt' "$DOC" ||
    fail WORKFLOW_DOCUMENTED

grep -Fq $'releasetool02d\ttools/check-releasetool02d-contract.sh\tprimary\treleasetool02c' "$MANIFEST" ||
    fail NATIVE_MANIFEST_REGISTRATION

echo 'PASS=RELEASETOOL02D_CHECKPOINT_STATE_MACHINE'
echo 'PASS=RELEASETOOL02D_QUALIFICATION_DELEGATION'
echo 'PASS=RELEASETOOL02D_SIGNED_PREVIEW_DELEGATION'
echo 'PASS=RELEASETOOL02D_DEVICE_SMOKE_DELEGATION'
echo 'PASS=RELEASETOOL02D_RELEASETOOL02C_DELEGATION'
echo 'PASS=RELEASETOOL02D_RESUME_INTEGRITY_FENCES'
echo 'PASS=RELEASETOOL02D_NONDESTRUCTIVE_ATTEMPTS'
echo 'PASS=RELEASETOOL02D_FAILED_LOG_ATTEMPT_NUMBER_PRESERVATION'
echo 'PASS=RELEASETOOL02D_HERMETIC_PROCESS_LEVEL_RESUME_SIMULATION'
echo 'PASS=RELEASETOOL02D_FAILURE_CLASSIFICATION'
echo 'PASS=RELEASETOOL02D_NO_DIRECT_MUTATION_PRIMITIVES'
echo 'CONTRACT02_PRIMARY_RESULT=PASS'
echo 'RESULT=SUBSHELL_RELEASETOOL02D_CONTRACT_PASS'
