# RELEASETOOL02D — full beta operator pipeline

## Purpose

RELEASETOOL02D is the single operator-facing entry point for a future Subshell
public beta after release metadata (version, notes, public base commits, and
qualification base) has been chosen.

It composes existing qualified tools instead of duplicating their mutation
logic.

```text
INIT
  |
  | tools/contract02/qualify-delta.sh
  v
QUALIFIED
  |
  | tools/release01/prepare-signed-preview.sh
  v
SIGNED
  |
  | tools/release01/smoke-preview-device.sh
  v
DEVICE_SMOKED
  |
  | tools/release02/release-beta.py  (RELEASETOOL02C)
  v
COMPLETE
```

## Why the boundaries remain separate

Qualification establishes the exact private source commit/tree and release
gate. Signed-preview performs the one release build and records the APK hash,
signer, version, package, and source identity. Device smoke installs that exact
recorded APK and checks the resulting package/version/runtime launch.
RELEASETOOL02C then promotes the same release record to the public release,
site, and update manifest.

RELEASETOOL02D never directly calls Gradle, apksigner, adb, gh, git push, or a
GitHub mutation endpoint. It owns orchestration and machine-readable state.

## Required inputs

```text
--qualified-source-root ABS
--base KNOWN_GOOD_PRIVATE_SOURCE_COMMIT
--qualification-manifest PATH_INSIDE_SOURCE
--version-name VERSION
--version-code POSITIVE_INTEGER
[--serial ADB_SERIAL]

--public-repo OWNER/REPO
--public-target 40_HEX_PUBLIC_INFRA_COMMIT
--title TEXT
--notes-file ABS
--public-main-before 40_HEX_SHA

--pages-repo OWNER/REPO
--pages-main-before 40_HEX_SHA
--previous-version VERSION
--manifest-url HTTPS_URL
--site-url HTTPS_URL

--output ABS
[--resume]
```

The qualification manifest is explicit because release qualification is a
release-specific product scope. RELEASETOOL02D must not silently replace it
with a release-tool-only manifest.

## State file

`operator-state.env` is written atomically after each successful boundary.

It records immutable operator inputs plus hashes and paths for the successful
stage outputs.

### INIT

Source root, commit/tree, qualification base, manifest SHA, version, requested
device serial, public repository identities/base commits, notes SHA, and live
URLs are frozen.

### QUALIFIED

Adds the exact `qualification-record.env` path and SHA-256.

The record must identify the same source commit/tree and contain:

```text
RELEASE_GATE=PASS
```

### SIGNED

Adds the exact `release-record.env`, signed preview APK, and SHA-256 values.

The release record must match the frozen source commit/tree, version name, and
version code. Its APK SHA must equal the preview APK bytes.

### DEVICE_SMOKED

Adds the device-smoke log, its SHA-256, and the actual serial reported by
RELEASETOOL01B.

The log must contain:

```text
RESULT=SUBSHELL_RELEASETOOL01B_EXACT_PREVIEW_DEVICE_SMOKE_PASS
```

### COMPLETE

Adds RELEASETOOL02C `release-beta-state.env` and its SHA-256.

The final state must prove:

```text
STATE=COMPLETE
PRIVATE_APP_SOURCE_PUBLIC=NO
RELEASE_BLOCKING_FAILURE=NO
```

Its source commit and APK SHA must equal the earlier operator checkpoints.

## Resume

Use the same arguments and output directory with `--resume`.

Every immutable input is compared against `operator-state.env`. Previously
successful stage files are re-hashed and their identities are revalidated
before any later stage starts.

Resume behavior:

```text
INIT           -> rerun qualification
QUALIFIED      -> continue at signing
SIGNED         -> continue at device smoke
DEVICE_SMOKED  -> continue at RELEASETOOL02C
COMPLETE       -> verify state and exit successfully
```

Changing source commit/tree, base, manifest/hash, version, notes, device
selection, public base commits, URLs, or other frozen inputs fails closed.

## Non-destructive attempts

Each stage uses a new attempt path, for example:

```text
qualification-attempt-001/
qualification-attempt-002/

signed-preview-attempt-001/
publication-attempt-001/

device-smoke-attempt-001.log
```

A failed attempt is preserved for forensics. Resume does not delete it and
does not automatically roll back public state.

For stages whose output directory may not exist after an early failure
(notably signed preview), the sibling `*-attempt-NNN.log` also reserves that
attempt number. A resume therefore advances to the next number instead of
truncating or replacing the earlier failure log.

For public-stage failures, a later RELEASETOOL02C attempt relies on the
already-qualified RELEASETOOL02A/B reconciliation rules to reuse correct
remote state and fail closed on unexpected state.

## Failure classification

RELEASETOOL02D distinguishes orchestration defects from predecessor failures.

- Qualification failure: classification details from the qualification record
  are surfaced when available.
- Signed preview failure: predecessor `RELEASETOOL01A`.
- Device smoke failure: predecessor `RELEASETOOL01B`.
- Public promotion failure: predecessor `RELEASETOOL02C`.
- Invalid/mismatched operator state or wiring: RELEASETOOL02D primary.
- Any incomplete required stage is release-blocking for a future release.

This does not retroactively make a historical already-published beta
release-blocking.

## Hermetic process-level final lock

The RELEASETOOL02D contract also runs
`tools/release02/integration-test-release-beta-operator.py`.

That test executes the real operator against a temporary clean git repository
whose qualification, signing, device-smoke, and public-promotion tools are
controlled local fakes. It performs no real build, signing, device, network,
GitHub, release, site, or Pages mutation.

The simulation proves these process-level behaviors:

```text
fresh run:
  qualification PASS
  signed preview FAIL
  state remains QUALIFIED
  signed-preview-attempt-001.log preserved

resume:
  signed preview uses attempt-002 and PASS
  device smoke PASS
  publication FAIL
  state remains DEVICE_SMOKED
  publication-attempt-001.log preserved

resume:
  publication uses attempt-002
  same release-record used by smoke and publication
  state reaches COMPLETE

complete resume:
  no new attempt artifacts

changed immutable input:
  fail closed before any new stage
```

This is the final non-mutating integration lock for the operator state machine.

## Operator result

Successful fresh or resumed execution ends with:

```text
STATE=COMPLETE
PRIMARY_FAILURE=NO
PREDECESSOR_FAILURE=NO
RELEASE_BLOCKING_FAILURE=NO
RESULT=SUBSHELL_RELEASETOOL02D_FULL_OPERATOR_PASS
```
