#!/usr/bin/env bash
set -u

ROOT="${1:-.}"
cd "$ROOT" || exit 1

RC=0
pass() { echo "PASS=$1"; }
fail() { echo "FAIL=$1"; RC=1; }

BUILD=app/build.gradle.kts
TOOL=tools/subshell
DELTA=tools/check-contract-delta.sh
QUALITY=tools/check-quality-gates-contract.sh
DOC=docs/quality/subshell-quality-gates-v1.md
WORKFLOW_DOC=docs/workflow/subshell-workflow01-baseline-aware-land-v1.md
SOURCE=tools/check-source-contract.sh
AGGREGATOR=tools/check-all-contracts.sh
MEMO_BASH=tools/contract-runtime/bash

for f in "$BUILD" "$TOOL" "$DELTA" "$QUALITY" "$DOC" "$WORKFLOW_DOC" "$SOURCE" "$AGGREGATOR" "$MEMO_BASH"; do
    [[ -f "$f" ]] || fail "WORKFLOW01_MISSING_${f//\//_}"
done

if [[ "$RC" -eq 0 ]]; then
    CHECK_BLOCK="$(
        sed -n             '/tasks.matching { it.name == "check" }.configureEach {/,/^}/p'             "$BUILD"
    )"
    PRE_RELEASE_BLOCK="$(
        sed -n             '/tasks.matching { it.name == "preReleaseBuild" }.configureEach {/,/^}/p'             "$BUILD"
    )"

    ! grep -Fq 'tasks.matching { it.name == "preDebugBuild" }.configureEach' "$BUILD" &&
    grep -Fq 'dependsOn(subshellContracts)' "$BUILD" &&
    grep -Fq 'dependsOn(subshellQualityCheck)' <<<"$CHECK_BLOCK" &&
    grep -Fq 'dependsOn(verifyContract02ReleaseQualification)' <<<"$PRE_RELEASE_BLOCK" &&
    grep -Fq 'dependsOn(verifyAuth05d2ReleaseSigningIdentity)' <<<"$PRE_RELEASE_BLOCK" &&
    ! grep -Fq 'subshellQualityCheck' <<<"$PRE_RELEASE_BLOCK" &&
    ! grep -Fq 'subshellContracts' <<<"$PRE_RELEASE_BLOCK" &&
    pass WORKFLOW01_DEBUG_CHECK_AND_RELEASE_BOUNDARIES_PRESERVED ||
        fail WORKFLOW01_DEBUG_CHECK_AND_RELEASE_BOUNDARIES_PRESERVED
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'CONTRACT_DELTA_REL="tools/check-contract-delta.sh"' "$TOOL" &&
    grep -Fq 'bash "$repo/$CONTRACT_DELTA_REL" "$MAIN" "$repo"' "$TOOL" &&
    grep -Fq 'LAND_CONTRACT_DELTA_FAILED' "$TOOL" &&
    grep -Fq 'LAND_KOTLIN_WARNINGS' "$TOOL" &&
    grep -Fq 'git -C "$MAIN" merge --ff-only "$branch"' "$TOOL" &&
    pass WORKFLOW01_LAND_OWNS_DELTA_TEST_BUILD_WARNING_FF_GATES ||
        fail WORKFLOW01_LAND_OWNS_DELTA_TEST_BUILD_WARNING_FF_GATES
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'bash tools/check-all-contracts.sh' "$DELTA" &&
    grep -Fq 'LC_ALL=C comm -13 "$TMP/baseline.failures" "$TMP/candidate.failures"' "$DELTA" &&
    grep -Fq 'TMPDIR="$TMP/baseline-tmp"' "$DELTA" &&
    grep -Fq 'TMPDIR="$TMP/candidate-tmp"' "$DELTA" &&
    ! grep -Fq 'BASE_PID=' "$DELTA" &&
    ! grep -Fq 'CANDIDATE_PID=' "$DELTA" &&
    ! grep -Fq 'DIAGNOSTIC_FILE=' "$DELTA" &&
    grep -Fq 'line.startswith("FAIL=")' "$DELTA" &&
    grep -Fq 'line.startswith("RESULT=") and line.endswith("FAIL")' "$DELTA" &&
    grep -Fq 'NEW_FAILURE_SIGNATURES=' "$DELTA" &&
    grep -Fq 'BASELINE_FAILURE_SET_SHA256=' "$DELTA" &&
    grep -Fq 'CANDIDATE_FAILURE_SET_SHA256=' "$DELTA" &&
    grep -Fq 'RESULT=SUBSHELL_CONTRACT_DELTA_PASS' "$DELTA" &&
    pass WORKFLOW01_BASELINE_DELTA_FAILS_ONLY_ON_NEW_SIGNATURES ||
        fail WORKFLOW01_BASELINE_DELTA_FAILS_ONLY_ON_NEW_SIGNATURES
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'Ordinary debug builds' "$DOC" &&
    grep -Fq 'baseline-aware' "$DOC" &&
    grep -Fq 'Contract02 delta qualification' "$DOC" &&
    grep -Fq 'release build consumes' "$WORKFLOW_DOC" &&
    grep -Fq 'SUBSHELL_WORKFLOW01_BASELINE_AWARE_LAND_V1' "$WORKFLOW_DOC" &&
    pass WORKFLOW01_POLICY_DOCUMENTED ||
        fail WORKFLOW01_POLICY_DOCUMENTED
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'SUBSHELL_WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME_V1' "$AGGREGATOR" &&
    grep -Fq 'SUBSHELL_CONTRACT_REAL_BASH=' "$AGGREGATOR" &&
    grep -Fq 'SUBSHELL_CONTRACT_MEMO_ROOT=' "$AGGREGATOR" &&
    grep -Fq 'SUBSHELL_CONTRACT_MEMO_DIR=' "$AGGREGATOR" &&
    grep -Fq 'CONTRACT_MEMO_POLICY=SUCCESS_ONLY_PER_SUITE' "$AGGREGATOR" &&
    grep -Fq 'SUBSHELL_WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME_V1' "$MEMO_BASH" &&
    grep -Fq 'FAIL_NOT_CACHED' "$MEMO_BASH" &&
    grep -Fq 'check-*-contract.sh' "$MEMO_BASH" &&
    grep -Fq 'exec "$REAL_BASH" "$@"' "$MEMO_BASH" &&
    grep -Fq 'WORKFLOW02' "$WORKFLOW_DOC" &&
    grep -Fq 'successful contract invocations' "$WORKFLOW_DOC" &&
    grep -Fq 'Failures are never memoized' "$WORKFLOW_DOC" &&
    pass WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME ||
        fail WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'SUBSHELL_WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP_V1' "$SOURCE" &&
    grep -Fq 'SOURCE_CONTRACT_MEMO_POLICY=SUCCESS_ONLY_PER_DIRECT_GATE' "$SOURCE" &&
    grep -Fq 'SOURCE_CONTRACT_MEMO_ENV_INCOMPLETE_OR_FOREIGN' "$SOURCE" &&
    grep -Fq 'export PATH="$ROOT/tools/contract-runtime:$PATH"' "$SOURCE" &&
    grep -Fq 'hash -r' "$SOURCE" &&
    grep -Fq 'source_contract_memo_report' "$SOURCE" &&
    grep -Fq 'aggregate suite and direct source-contract bootstrap' "$MEMO_BASH" &&
    grep -Fq 'SUBSHELL_WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP_V1' "$WORKFLOW_DOC" &&
    grep -Fq 'Direct `tools/check-source-contract.sh` invocation now bootstraps the same' "$WORKFLOW_DOC" &&
    grep -Fq 'it reuses the inherited' "$WORKFLOW_DOC" &&
    grep -Fq 'suite-local memo context instead of creating a nested cache.' "$WORKFLOW_DOC" &&
    grep -Fq 'A partial or foreign' "$WORKFLOW_DOC" &&
    grep -Fq 'memo environment fails closed rather than silently changing contract execution.' "$WORKFLOW_DOC" &&
    pass WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP ||
        fail WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP
fi

if [[ "$RC" -eq 0 ]]; then
    bash -n "$TOOL" &&
    bash -n "$DELTA" &&
    bash -n "$QUALITY" &&
    bash -n "$AGGREGATOR" &&
    bash -n "$MEMO_BASH" &&
    pass WORKFLOW01_SHELL_SYNTAX ||
        fail WORKFLOW01_SHELL_SYNTAX
fi

if [[ "$RC" -eq 0 ]]; then
    grep -Fq 'run_required_contract tools/check-workflow01-contract.sh WORKFLOW01_BASELINE_AWARE_LAND' "$SOURCE" &&
    pass WORKFLOW01_GLOBAL_SOURCE_CONTRACT_WIRED ||
        fail WORKFLOW01_GLOBAL_SOURCE_CONTRACT_WIRED
fi

if [[ "$RC" -eq 0 ]]; then
    echo "RESULT=SUBSHELL_WORKFLOW01_BASELINE_AWARE_LAND_CONTRACT_PASS"
else
    echo "RESULT=SUBSHELL_WORKFLOW01_BASELINE_AWARE_LAND_CONTRACT_FAIL"
fi
exit "$RC"
