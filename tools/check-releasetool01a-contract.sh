#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_RELEASETOOL01A_CONTRACT02_NATIVE_PRIMARY_V1
releasetool01a_contract02_primary_exit() {
    local rc="$1"

    if [[ "${SUBSHELL_CONTRACT02_RUNNER_ACTIVE:-0}" == '1' ]]; then
        if [[ "$rc" -eq 0 ]]; then
            echo 'CONTRACT02_PRIMARY_RESULT=PASS'
        else
            echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
        fi
    fi

    return "$rc"
}
trap 'releasetool01a_contract02_primary_exit "$?"' EXIT


# SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_FOUNDATION_CONTRACT_V1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

PREPARE='tools/release01/prepare-signed-preview.sh'
VERIFY='tools/release01/verify-preview.py'
DOC='docs/workflow/subshell-releasetool01-signed-preview-v1.md'

FAIL=0
pass() { echo "PASS=$1"; }
fail() { echo "FAIL=$1"; FAIL=1; }

for f in "$PREPARE" "$VERIFY" "$DOC"; do
    [[ -f "$f" ]] || fail RELEASETOOL01A_REQUIRED_FILE_MISSING
done

if grep -Fq 'SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_DRIVER_V1' "$PREPARE" &&
   grep -Fq -- '--qualification-record' "$PREPARE" &&
   grep -Fq -- '--version-name' "$PREPARE" &&
   grep -Fq -- '--version-code' "$PREPARE" &&
   grep -Fq -- '--output' "$PREPARE"; then
    pass RELEASETOOL01A_DRIVER_INPUT_SURFACE
else
    fail RELEASETOOL01A_DRIVER_INPUT_SURFACE
fi

if [[ "$(grep -Fc ':app:assembleRelease' "$PREPARE")" -eq 1 ]] &&
   grep -Fq -- '--no-configuration-cache' "$PREPARE" &&
   grep -Fq 'subshell.release.qualificationRecord' "$PREPARE" &&
   grep -Fq 'subshell.release.versionName' "$PREPARE" &&
   grep -Fq 'subshell.release.versionCode' "$PREPARE"; then
    pass RELEASETOOL01A_BUILD_ONCE_RELEASE_INPUTS
else
    fail RELEASETOOL01A_BUILD_ONCE_RELEASE_INPUTS
fi

for forbidden in \
    'assembleDebugAndroidTest' \
    'RESULT=SUBSHELL_ALL_CONTRACTS_PASS' \
    'RESULT=SUBSHELL_QUALITY_GATE_PASS' \
    'adb ' \
    'gh release' \
    'git push'
do
    if grep -Fq "$forbidden" "$PREPARE"; then
        fail "RELEASETOOL01A_FORBIDDEN_STAGE_B_${forbidden//[^A-Za-z0-9]/_}"
    fi
done

if grep -Fq 'LEGACY_QUALITY_REENTRY_DETECTED' "$PREPARE" &&
   grep -Fq 'PREVIEW_COPY_NOT_BIT_IDENTICAL' "$PREPARE" &&
   grep -Fq 'mv -- "$STAGE" "$OUTPUT"' "$PREPARE"; then
    pass RELEASETOOL01A_FAIL_CLOSED_ATOMIC_PREVIEW
else
    fail RELEASETOOL01A_FAIL_CLOSED_ATOMIC_PREVIEW
fi

if grep -Fq 'SUBSHELL_RELEASETOOL01A_PREVIEW_VERIFIER_V1' "$VERIFY" &&
   grep -Fq 'apk_signer_count_not_one_' "$VERIFY" &&
   grep -Fq 'version_name_mismatch' "$VERIFY" &&
   grep -Fq 'version_code_mismatch' "$VERIFY" &&
   grep -Fq 'qualification_source_commit_mismatch' "$VERIFY" &&
   grep -Fq 'legacy_quality_reentry_present' "$VERIFY"; then
    pass RELEASETOOL01A_PREVIEW_VERIFIER_FAIL_CLOSED
else
    fail RELEASETOOL01A_PREVIEW_VERIFIER_FAIL_CLOSED
fi

if grep -Fq 'QUALIFY ONCE' "$DOC" &&
   grep -Fq 'BUILD ONCE' "$DOC" &&
   grep -Fq 'PROMOTE EXACT BYTES' "$DOC" &&
   grep -Fq 'Stage B performs no device mutation' "$DOC"; then
    pass RELEASETOOL01A_WORKFLOW_DOCUMENTED
else
    fail RELEASETOOL01A_WORKFLOW_DOCUMENTED
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'RESULT=SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_FOUNDATION_CONTRACT_PASS'
else
    echo 'RESULT=SUBSHELL_RELEASETOOL01A_SIGNED_PREVIEW_FOUNDATION_CONTRACT_FAIL'
fi
exit "$FAIL"
