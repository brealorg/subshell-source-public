#!/usr/bin/env bash
set -u -o pipefail

DRIVER='tools/release02/promote-github-beta.py'
SELFTEST='tools/release02/selftest.py'
DOC='docs/workflow/subshell-releasetool02-public-promotion-v1.md'
MANIFEST='tools/contract02/manifests/releasetool02a.tsv'

fail() {
    echo "FAIL=$1"
    echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
    echo 'RESULT=SUBSHELL_RELEASETOOL02A_CONTRACT_FAIL'
    exit 1
}

for f in "$DRIVER" "$SELFTEST" "$DOC" "$MANIFEST"; do
    [[ -f "$f" ]] || fail "MISSING_${f//[^A-Za-z0-9]/_}"
done

python3 - "$DRIVER" "$SELFTEST" <<'PYCODE' ||
from pathlib import Path
import sys

for source_name in sys.argv[1:]:
    source = Path(source_name)
    compile(source.read_text(), str(source), "exec")
PYCODE
    fail PYTHON_SYNTAX

PYTHONDONTWRITEBYTECODE=1 python3 "$SELFTEST" ||
    fail SELFTEST

grep -Fq -- '--public-target' "$DRIVER" &&
grep -Fq 'PUBLIC_TARGET_NOT_IMMUTABLE_SHA' "$DRIVER" &&
grep -Fq 'verify_public_target' "$DRIVER" ||
    fail EXPLICIT_PUBLIC_TARGET

grep -Fq 'verify_private_source_not_public' "$DRIVER" &&
grep -Fq 'PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC' "$DRIVER" &&
grep -Fq 'app_source_publicly_pushed=NO' "$DRIVER" ||
    fail PRIVATE_PUBLIC_SOURCE_BOUNDARY

grep -Fq 'Subshell-{version_name}.apk' "$DRIVER" &&
grep -Fq 'Subshell-{version_name}.apk.sha256' "$DRIVER" &&
grep -Fq '"release-record.txt"' "$DRIVER" ||
    fail CANONICAL_PUBLIC_ASSET_NAMES

grep -Fq 'allow_missing_assets=True' "$DRIVER" &&
grep -Fq 'release_upload_missing' "$DRIVER" &&
grep -Fq 'REMOTE_UNEXPECTED_ASSETS_' "$DRIVER" &&
grep -Fq 'REMOTE_ASSET_DIGEST_MISMATCH_' "$DRIVER" ||
    fail RESUMABLE_FAIL_CLOSED_ASSET_RECONCILIATION

grep -Fq 'remote_download_and_verify' "$DRIVER" &&
grep -Fq 'REMOTE_APK_BYTE_IDENTITY_MISMATCH' "$DRIVER" &&
grep -Fq 'REMOTE_APK_VERIFIED=YES' "$DRIVER" ||
    fail REMOTE_APK_BYTE_IDENTITY

grep -Fq 'qualified_apk_rebuilt=NO_BIT_IDENTICAL_PREVIEW_PROMOTION' "$DRIVER" &&
! grep -Fq 'assembleRelease' "$DRIVER" &&
! grep -Fq './gradlew' "$DRIVER" ||
    fail NO_REBUILD_DURING_PROMOTION

! grep -Fq 'SOURCE_COMMIT_NOT_ON_GITHUB' "$DRIVER" &&
! grep -Eq 'public_target[[:space:]]*=[[:space:]]*record\["SOURCE_COMMIT"\]' "$DRIVER" ||
    fail NO_RELEASE01C_PRIVATE_SOURCE_TOPOLOGY

! grep -Fq 'git push' "$DRIVER" &&
! grep -Fq 'brealorg.github.io' "$DRIVER" &&
! grep -Fq 'subshell-update-beta.json' "$DRIVER" ||
    fail RELEASETOOL02A_SCOPE_BOUNDARY

grep -Fqi 'site and update manifest are later stages' "$DOC" &&
grep -Fqi 'resume' "$DOC" &&
grep -Fqi 'private Android source' "$DOC" &&
grep -Fqi 'public target' "$DOC" ||
    fail WORKFLOW_DOCUMENTED

grep -Fq $'releasetool01a\ttools/check-releasetool01a-contract.sh\tnone\t-' "$MANIFEST" &&
grep -Fq $'releasetool01b\ttools/check-releasetool01b-contract.sh\tnone\t-' "$MANIFEST" &&
# releasetool01c and the legacy publisher were retired on 2026-10-14 (step 3, owner approved).
! grep -Fq 'releasetool01c' "$MANIFEST" &&
grep -Fq $'releasetool02a\ttools/check-releasetool02a-contract.sh\tprimary\treleasetool01a,releasetool01b' "$MANIFEST" ||
    fail NATIVE_MANIFEST_REGISTRATION

echo 'PASS=RELEASETOOL02A_EXPLICIT_PUBLIC_TARGET'
echo 'PASS=RELEASETOOL02A_PRIVATE_PUBLIC_SOURCE_BOUNDARY'
echo 'PASS=RELEASETOOL02A_CANONICAL_THREE_ASSET_PATTERN'
echo 'PASS=RELEASETOOL02A_RESUMABLE_ASSET_RECONCILIATION'
echo 'PASS=RELEASETOOL02A_REMOTE_APK_BYTE_IDENTITY'
echo 'PASS=RELEASETOOL02A_NO_REBUILD'
echo 'PASS=RELEASETOOL02A_NO_PUBLIC_SITE_OR_MANIFEST_SCOPE_CREEP'
echo 'PASS=RELEASETOOL02A_NATIVE_MANIFEST'
echo 'CONTRACT02_PRIMARY_RESULT=PASS'
echo 'RESULT=SUBSHELL_RELEASETOOL02A_CONTRACT_PASS'
