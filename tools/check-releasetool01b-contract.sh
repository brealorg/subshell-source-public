#!/usr/bin/env bash
set -uo pipefail

# SUBSHELL_RELEASETOOL01B_DEVICE_SMOKE_CONTRACT_V1

releasetool01b_contract02_primary_exit() {
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
trap 'releasetool01b_contract02_primary_exit "$?"' EXIT

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

VERIFY='tools/release01/verify-release-record.py'
SMOKE='tools/release01/smoke-preview-device.sh'
DOC='docs/workflow/subshell-releasetool01-device-smoke-v1.md'

FAIL=0
pass() { echo "PASS=$1"; }
fail() { echo "FAIL=$1"; FAIL=1; }

for f in "$VERIFY" "$SMOKE" "$DOC"; do
    [[ -f "$f" ]] || fail RELEASETOOL01B_REQUIRED_FILE_MISSING
done

if grep -Fq 'SUBSHELL_RELEASETOOL01B_RELEASE_RECORD_VERIFIER_V1' "$VERIFY" &&
   grep -Fq 'apk_sha256_mismatch' "$VERIFY" &&
   grep -Fq 'source_tree_mismatch' "$VERIFY" &&
   grep -Fq 'apk_signer_fingerprint_mismatch' "$VERIFY"; then
    pass RELEASETOOL01B_RELEASE_RECORD_VERIFIER_FAIL_CLOSED
else
    fail RELEASETOOL01B_RELEASE_RECORD_VERIFIER_FAIL_CLOSED
fi

if grep -Fq 'SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_V1' "$SMOKE" &&
   grep -Fq -- '--qualified-source-root' "$SMOKE" &&
   grep -Fq 'QUALIFIED_SOURCE_ROOT_NOT_GIT_WORKTREE' "$SMOKE" &&
   grep -Fq 'adb -s "$SERIAL" install -r "$PREVIEW_APK"' "$SMOKE" &&
   grep -Fq 'IN_PLACE_UPDATE_IDENTITY=PASS' "$SMOKE" &&
   grep -Fq 'MAIN_ACTIVITY_FOREGROUND=PASS' "$SMOKE" &&
   grep -Fq 'EXACT_PREVIEW_BYTES=PASS' "$SMOKE"; then
    pass RELEASETOOL01B_EXACT_DEVICE_SMOKE_SURFACE
else
    fail RELEASETOOL01B_EXACT_DEVICE_SMOKE_SURFACE
fi

for forbidden in \
    './gradlew' \
    'assembleRelease' \
    'SUBSHELL_RELEASE_KEYSTORE_PATH' \
    'SUBSHELL_RELEASE_STORE_PASSWORD_FILE' \
    'SUBSHELL_RELEASE_KEY_PASSWORD_FILE' \
    'gh release' \
    'git push'
do
    if grep -Fq "$forbidden" "$SMOKE"; then
        fail "RELEASETOOL01B_FORBIDDEN_${forbidden//[^A-Za-z0-9]/_}"
    fi
done

if grep -Fq 'Stage C never invokes Gradle' "$DOC" &&
   grep -Fq 'exact preview APK bytes' "$DOC" &&
   grep -Fq 'UID and firstInstallTime' "$DOC"; then
    pass RELEASETOOL01B_WORKFLOW_DOCUMENTED
else
    fail RELEASETOOL01B_WORKFLOW_DOCUMENTED
fi

if [[ "$FAIL" -eq 0 ]]; then
    echo 'RESULT=SUBSHELL_RELEASETOOL01B_DEVICE_SMOKE_CONTRACT_PASS'
else
    echo 'RESULT=SUBSHELL_RELEASETOOL01B_DEVICE_SMOKE_CONTRACT_FAIL'
fi
exit "$FAIL"
