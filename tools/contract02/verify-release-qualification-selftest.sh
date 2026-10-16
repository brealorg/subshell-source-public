#!/usr/bin/env bash
set -euo pipefail

# SUBSHELL_CONTRACT02F_VERIFIER_SELFTEST_V1 (step 3, 2026-10-09): the release
# qualification verifier accepts a matching record and refuses each kind of
# bad one. It runs against a throwaway git source outside this repository,
# so the real source tree is never read or changed.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="$ROOT/tools/contract02/verify-release-qualification.py"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/contract02-verifier-selftest.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

SRC="$TMP/source"
mkdir -p "$SRC/tools/contract02/manifests"
git -C "$SRC" init -q
git -C "$SRC" config user.email selftest@example.invalid
git -C "$SRC" config user.name selftest
printf 'a\ttools/check-a.sh\tprimary\t-\n' >"$SRC/tools/contract02/manifests/r.tsv"
echo base >"$SRC/file.txt"
git -C "$SRC" add -A
git -C "$SRC" commit -q -m base
BASE="$(git -C "$SRC" rev-parse HEAD)"
echo next >>"$SRC/file.txt"
git -C "$SRC" commit -q -am next
HEAD_COMMIT="$(git -C "$SRC" rev-parse HEAD)"
TREE="$(git -C "$SRC" rev-parse 'HEAD^{tree}')"
SHA="$(sha256sum "$SRC/tools/contract02/manifests/r.tsv" | cut -d' ' -f1)"

# write_record FILE [KEY=VALUE overrides...]; an override with an empty value
# removes the key.
write_record() {
    local file="$1"; shift
    declare -A rec=(
        [CONTRACT02_QUALIFICATION_RECORD_VERSION]=1
        [SOURCE_COMMIT]="$HEAD_COMMIT"
        [SOURCE_TREE]="$TREE"
        [BASE_COMMIT]="$BASE"
        [COMMIT_COUNT]=1
        [CHANGED_PATH_COUNT]=1
        [MANIFEST_PATH]=tools/contract02/manifests/r.tsv
        [MANIFEST_SHA256]="$SHA"
        [PRIMARY_FAILURES]=0
        [PREDECESSOR_FAILURES]=0
        [RELEASE_BLOCKING_FAILURES]=0
        [CONTRACT_GATE]=PASS
        [UNIT_TESTS]=PASS
        [UNIT_TEST_RC]=0
        [UNIT_KOTLIN_WARNING_COUNT]=0
        [RELEASE_GATE]=PASS
    )
    local pair
    for pair in "$@"; do
        if [[ -z "${pair#*=}" ]]; then unset "rec[${pair%%=*}]"; else rec[${pair%%=*}]="${pair#*=}"; fi
    done
    : >"$file"
    local key
    for key in "${!rec[@]}"; do printf '%s=%s\n' "$key" "${rec[$key]}" >>"$file"; done
}

verify() {
    python3 -I "$VERIFIER" --record "$1" --source-root "$SRC"
}

write_record "$TMP/good.txt"
verify "$TMP/good.txt" | grep -Fq 'RESULT=SUBSHELL_CONTRACT02_RELEASE_QUALIFICATION_VERIFICATION_PASS'
echo 'PASS=VERIFIER_ACCEPTS_A_MATCHING_RECORD'

# expect_rejected NAME REASON [overrides...]
expect_rejected() {
    local name="$1" reason="$2"; shift 2
    write_record "$TMP/$name.txt" "$@"
    local out
    if out="$(verify "$TMP/$name.txt")"; then
        echo "FAIL=VERIFIER_ACCEPTED_$name"; exit 1
    fi
    grep -Fq "reason=$reason" <<<"$out" || { echo "FAIL=VERIFIER_WRONG_REASON_$name"; echo "$out"; exit 1; }
    echo "PASS=VERIFIER_REJECTS_$name"
}

expect_rejected commit source_commit_mismatch "SOURCE_COMMIT=$BASE"
expect_rejected tree source_tree_mismatch "SOURCE_TREE=0000000000000000000000000000000000000000"
expect_rejected manifest manifest_sha256_mismatch "MANIFEST_SHA256=deadbeef"
expect_rejected manifest_path manifest_path_not_canonical_relative "MANIFEST_PATH=../r.tsv"
expect_rejected release_gate release_gate_not_pass "RELEASE_GATE=BLOCKED"
expect_rejected blocking release_blocking_failures_nonzero "RELEASE_BLOCKING_FAILURES=1"
expect_rejected contract_gate contract_gate_not_pass "CONTRACT_GATE=FAIL"
expect_rejected unit_tests unit_tests_not_pass "UNIT_TESTS=FAIL"
expect_rejected warnings unit_kotlin_warnings_nonzero "UNIT_KOTLIN_WARNING_COUNT=2"
expect_rejected missing_key record_missing_keys_RELEASE_GATE "RELEASE_GATE="

# A record inside the source tree is refused.
write_record "$SRC/inside.txt"
out="$(verify "$SRC/inside.txt" || true)"
grep -Fq 'reason=qualification_record_must_be_external_to_source_tree' <<<"$out"
rm -f "$SRC/inside.txt"
echo 'PASS=VERIFIER_REJECTS_A_RECORD_INSIDE_THE_SOURCE'

# A dirty source tree is refused.
echo dirty >>"$SRC/file.txt"
out="$(verify "$TMP/good.txt" || true)"
grep -Fq 'reason=source_worktree_not_clean' <<<"$out"
echo 'PASS=VERIFIER_REJECTS_A_DIRTY_SOURCE'

echo 'RESULT=SUBSHELL_CONTRACT02F_VERIFIER_SELFTEST_PASS'
