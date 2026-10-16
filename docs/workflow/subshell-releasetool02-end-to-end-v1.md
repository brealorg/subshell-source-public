# RELEASETOOL02C — end-to-end beta-release orchestrator

## Purpose

RELEASETOOL02C is the canonical operator-facing entry point for public beta
promotion after the signed preview/device qualification stages are complete.

It composes the two mutation-aware RELEASETOOL02 stages:

```text
RELEASETOOL02A
  signed release record
  -> GitHub prerelease
  -> canonical three-asset set
  -> remote APK byte verification
  -> STATE=REMOTE_APK_VERIFIED

RELEASETOOL02B
  STATE=REMOTE_APK_VERIFIED
  -> public README/site promotion
  -> root update-manifest promotion
  -> live manifest verification
  -> STATE=COMPLETE
```

The orchestrator itself performs no build, signing, or Android device mutation.

## Full mode

Full mode starts from a RELEASETOOL01A release record and calls
RELEASETOOL02A followed by RELEASETOOL02B.

```text
--release-record ABS
--qualified-source-root ABS
--public-repo OWNER/REPO
--public-target PUBLIC_INFRA_COMMIT_SHA
--title "Subshell X"
--notes-file ABS
--public-main-before SHA
--pages-repo OWNER/REPO
--pages-main-before SHA
--previous-version VERSION
--manifest-url URL
--site-url URL
--output ABS
```

The private Android `SOURCE_COMMIT` is never used as the public GitHub Release
target. `--public-target` remains an explicit public-infrastructure identity.

## Resume mode

If RELEASETOOL02A has already completed, pass its promotion state instead of a
release record:

```text
--promotion-state ABS/promotion-state.env
```

The state must prove:

```text
STATE=REMOTE_APK_VERIFIED
REMOTE_APK_VERIFIED=YES
PRIVATE_APP_SOURCE_PUBLIC=NO
NEXT_STAGE=PUBLIC_SITE_PROMOTION
```

RELEASETOOL02C then skips Stage A and invokes RELEASETOOL02B directly.

## Existing-only replay

`--existing-only` is the historical/post-release verification mode.

In resume mode it checks, before RELEASETOOL02B starts, that:

- the GitHub Release already exists;
- public project `main` is already different from `--public-main-before`;
- Pages `main` is already different from `--pages-main-before`.

If either repository is still at the supplied base, the run stops rather than
entering a push path.

RELEASETOOL02B then applies its stronger one-child/content-equivalence resume
fences. Therefore an existing-only replay can validate an already completed
release but cannot create the site or manifest commits.

Full-mode existing-only is also conservative: the release must already exist
before RELEASETOOL02A is invoked.

## Beta.3 replay

0.77.0-beta.3 predates the canonical RELEASETOOL02A public provenance-record
format. The public release itself and APK bytes are already independently
verified.

The safe beta.3 replay therefore begins from a reconstructed, independently
verified `REMOTE_APK_VERIFIED` promotion state and runs RELEASETOOL02C in:

```text
--promotion-state ... --existing-only
```

A temporary local clone may place the historical beta.3 private source commit
on a clean local `main` so RELEASETOOL02B can re-read the exact bundled beta.3
changelog. No public branch, release asset, APK, device, or private source is
mutated during that replay.

This replay exercises the production resume path that was needed during the
actual beta.3 release: already-published GitHub Release, already-pushed public
site commit, already-pushed Pages manifest commit, live manifest, and final
private-source fence.

## Final state

After successful Stage B completion, RELEASETOOL02C writes
`release-beta-state.env`:

```text
RELEASETOOL02_ORCHESTRATOR_RECORD_VERSION=1
STATE=COMPLETE
MODE=FULL|RESUME
EXISTING_ONLY=YES|NO
PROMOTION_STATE_SHA256=...
PUBLICATION_STATE_SHA256=...
SOURCE_COMMIT=...
VERSION_NAME=...
VERSION_CODE=...
APK_SHA256=...
PRIVATE_APP_SOURCE_PUBLIC=NO
PRIMARY_FAILURE=NO
PREDECESSOR_FAILURE=NO
RELEASE_BLOCKING_FAILURE=NO
```

## Failure classification

- **Primary**: the current RELEASETOOL02C orchestration/state assertion fails.
- **Predecessor**: RELEASETOOL02B, RELEASETOOL02A, or an older release-tool
  contract is red while the current orchestration layer itself is valid.
- **Release-blocking**: the release cannot safely reach `STATE=COMPLETE`.
- **Wrapper/infrastructure**: command plumbing, authentication, network, or
  other operational failure without evidence of a product defect.

Already verified public artifacts are not automatically rolled back.
