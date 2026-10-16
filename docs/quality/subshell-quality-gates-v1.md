# subshell quality gates v1

This file defines the verification policy built into the Gradle task graph and
the local feature-lane workflow.

## Ordinary development builds

Ordinary debug builds, including Android Studio Run and `assembleDebug`, do not
execute the complete historical `subshellContracts` suite automatically.

Debug compilation must remain independently usable even when the canonical
repository has known legacy contract debt. Compile errors, unit-test failures,
APK identity failures, and Kotlin warnings remain real build failures.

## Baseline-aware lane landing

`./tools/subshell land dev1` and `./tools/subshell land dev2` own the normal
feature-promotion gate.

Before unit tests and the lane build, `land` runs the complete contract suite on
both current `main` and the synchronized candidate lane. The candidate may
preserve or reduce failures that already exist on `main`, but any new contract
failure signature blocks landing.

This is a baseline-aware regression gate, not a blanket contract bypass.

Landing still requires:

- clean/synchronized lane state;
- `git diff --check`;
- no new contract failures relative to current `main`;
- all debug unit tests;
- the exact lane debug APK build;
- zero Kotlin compiler warnings;
- APK package identity;
- an FF-only promotion to `main`.

## Full milestone gate

Run:

```bash
./gradlew :app:subshellQualityCheck --offline --no-daemon
```

The milestone task remains strict and requires:

- all subshell contracts;
- `lintDebug`;
- `testDebugUnitTest`;
- `assembleDebug`;
- lint result: `0 errors / 0 warnings / 0 hints`.

The standard Gradle `check` task also depends on this full gate.

## Release gate

Release qualification is now separate from the strict milestone gate.

Before a signed release build, the canonical Contract02 delta qualification
records the exact source delta, executes the selected native manifest once,
runs the full debug unit-test suite, and emits an external qualification record.

`preReleaseBuild` verifies that qualification record against the exact clean
source commit/tree and manifest identity, and independently requires AUTH05D2
signing-identity verification. The release path does not rerun
`subshellQualityCheck` or `subshellContracts` after qualification has passed.

The standard Gradle `check` task continues to use the strict full milestone
gate during incremental Contract02 migration.

## Intended workflow

- Normal development: build or Run normally.
- Feature completion: `checkpoint`, then `land`; landing remains baseline-aware.
- Contract-debt cleanup / locked milestone: run `subshellQualityCheck`.
- Release qualification: run the Contract02 delta qualification for the actual
  release scope.
- Release build: consume the verified qualification record and perform the
  separate signing/package gates.

<!-- SUBSHELL_WORKFLOW01_BASELINE_AWARE_LAND_V1 -->
