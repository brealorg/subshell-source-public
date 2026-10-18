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

## Stage C: public source snapshot (RELEASETOOL02E)

Hard rule 7: private history never goes public. After Stage B is COMPLETE,
`tools/release02/publish-source-snapshot.py` publishes the source of the
release to `brealorg/subshell-source-public` as one snapshot commit:

```bash
python3 tools/release02/publish-source-snapshot.py \
  --release-state "$PUB/release-beta-state.env" \
  --qualified-source-root "$SRC_MAIN" \
  --source-remote https://github.com/brealorg/subshell-source-public.git \
  --source-main-before "$SOURCE_PUBLIC_MAIN_BEFORE" \
  --gitleaks /path/to/gitleaks \
  --output "$PUB-source"
```

- It reads `release-beta-state.env` (STATE=COMPLETE) and the same remote-less
  qualified clone as Stage A/B. It refuses a dirty clone, a clone at another
  commit or tree, a tracked file the repository ignores, and a missing
  `.gitleaks.toml`.
- It replaces the tree of a fresh clone of the public repository with
  `git archive SOURCE_COMMIT`. It commits as
  `brealorg <6815997+brealorg@users.noreply.github.com>` on top of
  `--source-main-before` and tags the commit `v<VERSION_NAME>`.
- Fences: the snapshot's tree id equals `SOURCE_TREE` (the public source is
  byte for byte the source of the APK); the private `SOURCE_COMMIT` is not
  reachable from the public repository, checked before the commit, after it
  and after the push; gitleaks finds nothing with the repository's
  `.gitleaks.toml`; the push is `--atomic` and never forced.
- Resume: run it again with a new `--output`. If the tag already points at a
  snapshot with the same tree on main, it only verifies it
  (`SNAPSHOT_REUSED=YES`). A tag on another tree stops it.
- It writes `source-snapshot-state.env` (STATE=COMPLETE, SNAPSHOT_COMMIT, TAG,
  SNAPSHOT_TREE_IDENTITY=PASS, PRIVATE_APP_SOURCE_PUBLIC=NO).
- `tools/release02/selftest-source-snapshot.py` runs it against local
  repositories on every land (check-contract02-selftests).
- Stage B's README fence requires the sentence "The Android application source
  code is published separately in [brealorg/subshell-source-public]" on one line.
  The old "not published" sentence was accepted until the first snapshot release
  (0.82-beta) and is refused now.

## Failure classification

- **Primary**: the current RELEASETOOL02C orchestration/state assertion fails.
- **Predecessor**: RELEASETOOL02B, RELEASETOOL02A, or an older release-tool
  contract is red while the current orchestration layer itself is valid.
- **Release-blocking**: the release cannot safely reach `STATE=COMPLETE`.
- **Wrapper/infrastructure**: command plumbing, authentication, network, or
  other operational failure without evidence of a product defect.

Already verified public artifacts are not automatically rolled back.
