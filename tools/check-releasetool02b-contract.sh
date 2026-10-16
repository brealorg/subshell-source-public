#!/usr/bin/env bash
set -u -o pipefail

DRIVER='tools/release02/promote-public-site-manifest.py'
SELFTEST='tools/release02/selftest-public-site-manifest.py'
DOC='docs/workflow/subshell-releasetool02-site-manifest-v1.md'
MANIFEST='tools/contract02/manifests/releasetool02b.tsv'

fail() {
    echo "FAIL=$1"
    echo 'CONTRACT02_PRIMARY_RESULT=FAIL'
    echo 'RESULT=SUBSHELL_RELEASETOOL02B_CONTRACT_FAIL'
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

grep -Fq 'STATE"] != "REMOTE_APK_VERIFIED"' "$DRIVER" &&
grep -Fq 'REMOTE_APK_VERIFIED"] != "YES"' "$DRIVER" &&
grep -Fq 'NEXT_STAGE"] != "PUBLIC_SITE_PROMOTION"' "$DRIVER" ||
    fail RELEASETOOL02A_HANDOFF_REQUIRED

grep -Fq 'PUBLIC_COPY_PATHS = ("README.md", "site/index.html")' "$DRIVER" &&
grep -Fq 'PUBLIC_COPY_SCOPE_CHANGED' "$DRIVER" &&
grep -Fq 'PUBLIC_SOURCE_PRIVACY_BOUNDARY_MISSING' "$DRIVER" &&
grep -Fq 'def git_raw(' "$DRIVER" &&
grep -Fq 'base_text = git_raw(root, "show", f"{base}:{path}")' "$DRIVER" &&
grep -Fq 'PASS=RELEASETOOL02B_GIT_SHOW_PRESERVES_TRAILING_NEWLINE' "$SELFTEST" ||
    fail EXACT_PUBLIC_COPY_AND_PRIVACY_BOUNDARY

# RELEASETOOL02C_SITE_RELEASE_NOTES_V1 (owner 2026-10-13): the release notes on
# the public site are rendered from the bundled changelog by the promotion.
grep -Fq 'import public_release_notes as release_notes' "$DRIVER" &&
grep -Fq 'PUBLIC_SITE_PATHS = PUBLIC_COPY_PATHS + (release_notes.CHANGELOG_PAGE_PATH,)' "$DRIVER" &&
grep -Fq 'PASS=RELEASETOOL02C_SITE_RELEASE_NOTES' "$SELFTEST" ||
    fail SITE_RELEASE_NOTES_FROM_CHANGELOG

grep -Fq '"schemaVersion": 1' "$DRIVER" &&
grep -Fq '"channel": "beta"' "$DRIVER" &&
grep -Fq '"apkSha256": state["APK_SHA256"]' "$DRIVER" &&
grep -Fq 'load_release_from_changelog' "$DRIVER" ||
    fail MANIFEST_FROM_CANONICAL_CHANGELOG

grep -Fq 'PUBLIC_SITE_RESUME_PARENT_MISMATCH' "$DRIVER" &&
grep -Fq 'PAGES_RESUME_PARENT_MISMATCH' "$DRIVER" &&
grep -Fq 'PUBLIC_SITE_RESUME_CHANGED_PATHS_MISMATCH' "$DRIVER" &&
grep -Fq 'PAGES_RESUME_CHANGED_PATHS_MISMATCH' "$DRIVER" ||
    fail ONE_COMMIT_RESUME_FENCES

grep -Fq 'wait_for_live_manifest' "$DRIVER" &&
grep -Fq 'LIVE_UPDATE_MANIFEST_NOT_READY_OR_MISMATCHED' "$DRIVER" &&
grep -Fq 'PUBLIC_SITE_LIVE=' "$DRIVER" ||
    fail LIVE_PROPAGATION_VERIFICATION

grep -Fq 'verify_private_source_absent' "$DRIVER" &&
grep -Fq 'PRIVATE_APP_SOURCE_COMMIT_IS_PUBLIC' "$DRIVER" ||
    fail PRIVATE_SOURCE_FINAL_FENCE

grep -Fq 'STATE=COMPLETE' "$DRIVER" &&
grep -Fq 'MANIFEST_LIVE=YES' "$DRIVER" &&
grep -Fq 'RELEASE_BLOCKING_FAILURE=NO' "$DRIVER" ||
    fail COMPLETE_STATE_RECORD

! grep -Fq 'assembleRelease' "$DRIVER" &&
! grep -Fq 'apksigner' "$DRIVER" &&
! grep -Fq 'adb ' "$DRIVER" &&
! grep -Fq 'gh release create' "$DRIVER" &&
! grep -Fq 'gh release upload' "$DRIVER" ||
    fail NO_BUILD_SIGN_DEVICE_OR_RELEASE_ASSET_MUTATION

grep -Fqi 'REMOTE_APK_VERIFIED' "$DOC" &&
grep -Fqi 'resume' "$DOC" &&
grep -Fqi 'update manifest' "$DOC" &&
grep -Fqi 'private Android source' "$DOC" &&
grep -Fqi 'COMPLETE' "$DOC" ||
    fail WORKFLOW_DOCUMENTED

grep -Fq $'releasetool02b\ttools/check-releasetool02b-contract.sh\tprimary\treleasetool02a' "$MANIFEST" ||
    fail NATIVE_MANIFEST_REGISTRATION

echo 'PASS=RELEASETOOL02B_REMOTE_APK_VERIFIED_HANDOFF'
echo 'PASS=RELEASETOOL02B_EXACT_PUBLIC_COPY_SCOPE'
echo 'PASS=RELEASETOOL02C_SITE_RELEASE_NOTES_FROM_CHANGELOG'
echo 'PASS=RELEASETOOL02B_RAW_GIT_FILE_CONTENT_PRESERVATION'
echo 'PASS=RELEASETOOL02B_SCHEMA1_UPDATE_MANIFEST'
echo 'PASS=RELEASETOOL02B_RESUMABLE_PUBLIC_SITE_PUSH'
echo 'PASS=RELEASETOOL02B_RESUMABLE_PAGES_MANIFEST_PUSH'
echo 'PASS=RELEASETOOL02B_LIVE_MANIFEST_VERIFICATION'
echo 'PASS=RELEASETOOL02B_PRIVATE_SOURCE_FINAL_FENCE'
echo 'PASS=RELEASETOOL02B_COMPLETE_STATE'
echo 'PASS=RELEASETOOL02B_NO_BUILD_SIGN_DEVICE_OR_RELEASE_ASSET_MUTATION'
echo 'CONTRACT02_PRIMARY_RESULT=PASS'
echo 'RESULT=SUBSHELL_RELEASETOOL02B_CONTRACT_PASS'
