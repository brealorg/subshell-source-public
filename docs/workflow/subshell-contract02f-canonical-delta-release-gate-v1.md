# CONTRACT02F — canonical delta release qualification gate

CONTRACT02F establishes the release workflow that replaces recursive
legacy contract execution during release builds.

## Canonical qualification command

Future release qualification uses:

```text
tools/contract02/qualify-delta.sh \
  --base <known-good-release-source-commit> \
  --manifest <release-specific-contract02-manifest> \
  --output <external-output-directory>
```

The qualifier requires a clean source worktree, verifies the known-good
base as an ancestor of `HEAD`, records exact commits and changed paths,
hashes the selected manifest, executes the Contract02 manifest once, runs
the full `:app:testDebugUnitTest`, and emits an external
`qualification-record.env`.

The record keeps Primary, Predecessor, and Release-blocking counts
separate and emits `RELEASE_GATE=PASS|BLOCKED`.

## Qualification record

The record pins source commit/tree, base commit, delta counts, manifest
path/SHA-256, Contract02 classification, unit-test status, warning count,
and final release-gate state.

`tools/contract02/verify-release-qualification.py` independently verifies
that the record still describes the exact clean source snapshot being
built. A stale record or changed manifest is rejected.

## Gradle release boundary

Standard Gradle `check` continues to depend on the existing
`subshellQualityCheck` during incremental migration.

`preReleaseBuild` no longer depends on `subshellQualityCheck`. Instead it
depends on:

- `verifyContract02ReleaseQualification`
- `verifyAuth05d2ReleaseSigningIdentity`

The release path therefore does not execute `subshellContracts` after a
delta qualification has already passed.

Qualification and release build are separate stages. A release build
consumes the prior qualification record; it does not silently rerun
Contract02.

## Configuration cache

Until the existing AUTH verification tasks are separately made compatible
with Gradle configuration-cache serialization, the canonical release
driver invokes release builds with configuration cache disabled:

```text
--no-configuration-cache
```

The qualification record is supplied through either:

```text
-Psubshell.release.qualificationRecord=/absolute/path/qualification-record.env
```

or:

```text
SUBSHELL_RELEASE_QUALIFICATION_RECORD=/absolute/path/qualification-record.env
```

Signing inputs and release version overrides remain separate concerns.

## Manifest policy

`beta02-reference.tsv` remains a historical reference manifest only.
Future releases use an explicit Contract02-native manifest for the actual
delta being qualified.

CONTRACT02F does not attempt to migrate all legacy contracts.
