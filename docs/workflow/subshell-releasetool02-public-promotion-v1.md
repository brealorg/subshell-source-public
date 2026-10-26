# RELEASETOOL02A — resumable public GitHub beta promotion

## Purpose

RELEASETOOL02A promotes the exact signed APK produced by RELEASETOOL01A into
the public GitHub beta release without rebuilding or signing anything.

The private Android source repository and the public project/release
repository are intentionally different topologies. The private Android source
commit must **not** be pushed to the public project repository.

RELEASETOOL02A therefore takes two separate identities:

- `SOURCE_COMMIT` / `SOURCE_TREE` from the signed release record identify the
  qualified private application source.
- `--public-target` is an explicit immutable commit SHA in the public project
  repository and is the GitHub Release target.

The public target is never inferred from `SOURCE_COMMIT`.

## Inputs

```text
--release-record ABSOLUTE_RELEASETOOL01A_RECORD
--qualified-source-root ABSOLUTE_PRIVATE_SOURCE_ROOT
--repo OWNER/REPO
--public-target 40_HEX_PUBLIC_COMMIT
--title RELEASE_TITLE
--notes-file ABSOLUTE_NOTES_FILE
--output ABSOLUTE_EMPTY_OUTPUT_DIRECTORY
```

The qualified source root must be clean `main` and match the source commit and
tree recorded by RELEASETOOL01A.

Before any remote mutation, the existing RELEASETOOL01 release-record verifier
is run and the public target is verified remotely.

## Public assets

The canonical public beta asset set is exactly:

```text
Subshell-<version>.apk
Subshell-<version>.apk.sha256
release-record.txt
```

The public APK is a byte-identical copy of the already-qualified signed
preview. No release build or signing operation is allowed in this stage.

The public provenance record retains the private source commit/tree and
names the public source snapshot Stage C publishes for the same release:
`app_source_public_repository=brealorg/subshell-source-public`,
`app_source_public_tag=<tag>` and `app_source_public_tree=<SOURCE_TREE>`
(Stage C stops unless the snapshot's tree is exactly that tree), with
`app_source_private_commit_public=NO`: the private commit and its history are
never public (rule 7). Before 0.85 the record said
`app_source_publicly_pushed=NO` and
`public_repository_source_scope=PROJECT_INFRA_SITE_RELEASES_ONLY`, which read
as if the source were unpublished (SECAUDIT26).

## Create and resume semantics

If the GitHub release does not exist, RELEASETOOL02A creates one prerelease
targeted at the explicit public target commit.

If it already exists, RELEASETOOL02A enters resume mode:

1. tag, title, prerelease state and public target must match;
2. every existing expected asset must have the expected SHA-256 and size;
3. unexpected assets fail closed;
4. expected missing assets may be uploaded;
5. no existing asset is overwritten or deleted;
6. the final asset set must contain exactly the three canonical assets;
7. the APK is downloaded again and SHA-256 verified against the qualified
   Stage-B artifact.

Set membership is used for asset comparison. Locale-dependent ordering is not
part of the contract.

An already verified public release is never automatically rolled back.

## State record

Successful completion writes `promotion-state.env` with:

```text
STATE=REMOTE_APK_VERIFIED
RELEASE_CREATED=YES|NO
RELEASE_REUSED=YES|NO
REMOTE_APK_VERIFIED=YES
PRIVATE_APP_SOURCE_PUBLIC=NO
NEXT_STAGE=PUBLIC_SITE_PROMOTION
```

This is the handoff boundary for the next RELEASETOOL02 stage.

## Scope boundary

RELEASETOOL02A does **not**:

- publish private Android source;
- modify either repository's `main`;
- update the public README or website;
- publish `subshell-update-beta.json`;
- build or sign an APK;
- mutate an Android device.

Public site and update manifest are later stages and run only after
`REMOTE_APK_VERIFIED`.

## Contract02 predecessor wiring

RELEASETOOL01A, RELEASETOOL01B, and RELEASETOOL01C are declared first as
nonblocking predecessor records. RELEASETOOL02A lists all three as explicit
predecessors and uses `block_mode=primary`. This preserves the requested
separation: an older red release-tool contract is reported as predecessor debt
without turning a passing RELEASETOOL02A primary assertion into a release
blocker.

## Failure classification

- **Primary**: a RELEASETOOL02A assertion about release identity, asset
  integrity, topology, or byte identity is genuinely false.
- **Predecessor**: an older release contract is red while RELEASETOOL02A's own
  assertions pass.
- **Release-blocking**: the failed/incomplete stage prevents safe continuation
  of the public release.
- **Wrapper/infrastructure**: authentication, connectivity, stale orchestration
  assumptions, credentials, or execution plumbing; these are not product
  failures.

## Release notes on the public site (RELEASETOOL02C, 2026-10-13)

The promotion also renders the public release notes from the qualified bundled
changelog (`app/src/main/assets/changelog/releases.json`) with
`tools/release02/public_release_notes.py`:

- `site/index.html` and `README.md`: the region between
  `<!-- RELEASETOOL02_WHATS_NEW_START -->` and `<!-- RELEASETOOL02_WHATS_NEW_END -->`
  shows the newest release's headline and first four highlights. The version
  copy in these two files is still rewritten from the previous to the current
  version, as before.
- `site/changelog/index.html`: the region between
  `<!-- RELEASETOOL02_CHANGELOG_START -->` and `<!-- RELEASETOOL02_CHANGELOG_END -->`
  lists every release, newest first. This page names every earlier version, so it
  is the one file outside the two copy files allowed to contain the previous
  version; it is re-rendered, never version-rewritten.

The scope fence stays: the previous version may only appear in the two copy
files and the release-notes page, and the commit may only change those files.
A file whose markers are missing or duplicated stops the promotion
(`RELEASE_NOTES_MARKERS_INVALID_*`). Resume compares each file with the same
rendering of its base content.
