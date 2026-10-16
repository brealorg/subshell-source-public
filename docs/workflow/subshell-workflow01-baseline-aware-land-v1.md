# WORKFLOW01 — baseline-aware repository landing

The repository has accumulated historical source contracts that can remain red
after successor features legitimately replace older static assumptions. Those
baseline failures must not make ordinary debug compilation or every feature
landing impossible.

WORKFLOW01 separates three concerns:

1. ordinary debug compilation is fast and does not automatically execute the
   entire historical contract suite;
2. feature landing compares canonical contract failures on current `main`
   against the synchronized candidate and blocks newly introduced failure
   signatures; failure identity is derived only from semantic `FAIL=` and
   failing `RESULT=` markers, never incidental diagnostic file paths; baseline
   and candidate contract suites run sequentially with isolated temporary roots
   so historical contracts cannot race each other;
3. strict milestone and `check` retain the complete legacy contract suite
   during migration, while release qualification uses the Contract02 delta
   gate and the release build consumes its verified qualification record.

The release build consumes a prior Contract02 qualification record for the exact
source snapshot and does not rerun the complete legacy contract suite through
`preReleaseBuild`. This release boundary is separate from lane landing.

`tools/subshell land` remains responsible for unit tests, exact lane build,
zero Kotlin warnings, the land lint profile (new findings for the rules in
`app/build.gradle.kts` STEP3_LAND_LINT_GATE_V1, today's in
`app/lint-land-baseline.xml`), the auth policy-matrix tasks, APK identity,
and FF-only promotion.

No direct-main commit path or hook bypass is introduced.

<!-- SUBSHELL_WORKFLOW01_BASELINE_AWARE_LAND_V1 -->

## WORKFLOW02 — bounded contract runtime

The canonical aggregate contract runner uses success-only memoization while one
`tools/check-all-contracts.sh` suite is running. The contract graph is a DAG,
but predecessor contracts are intentionally embedded in many later contracts;
without memoization, the same successful checker can be executed through many
different predecessor paths.

The runtime wrapper is scoped to the aggregate suite and preserves the existing
contract semantics:

- successful contract invocations are keyed by resolved checker path, working
  directory, arguments, and a stable environment fingerprint, then replayed
  from the suite-local temporary cache;
- Failures are never memoized, so a failing checker is executed normally each
  time it is required and continues to emit its canonical failure evidence;
- non-contract `bash` commands and `bash` option invocations bypass the wrapper;
- the memo cache lives only inside the aggregate suite temporary directory, so
  baseline and candidate runs remain isolated and sequential;
- no contract is skipped because of a previous build or previous suite.

This removes repeated successful predecessor evaluation without weakening the
baseline-aware delta policy, strict milestone, unit/build, warning, APK identity,
or FF-only landing gates.

Direct `tools/check-source-contract.sh` invocation now bootstraps the same
success-only memo runtime when it is not already running inside the aggregate
suite. When invoked by `tools/check-all-contracts.sh`, it reuses the inherited
suite-local memo context instead of creating a nested cache. A partial or foreign
memo environment fails closed rather than silently changing contract execution.
The direct gate reports memo hits, misses, and non-cached failures before its
canonical final result.

<!-- SUBSHELL_WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP_V1 -->

<!-- SUBSHELL_WORKFLOW02_SUCCESS_ONLY_CONTRACT_MEMO_RUNTIME_V1 -->
