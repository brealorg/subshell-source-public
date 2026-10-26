# RELEASETOOL02B — public site and update-manifest promotion

## Purpose

RELEASETOOL02B starts only after RELEASETOOL02A has produced a promotion state
with:

```text
STATE=REMOTE_APK_VERIFIED
REMOTE_APK_VERIFIED=YES
PRIVATE_APP_SOURCE_PUBLIC=NO
NEXT_STAGE=PUBLIC_SITE_PROMOTION
```

At this point the signed APK is already public and has been downloaded again
and SHA-256 verified. RELEASETOOL02B performs the remaining public metadata
promotion without rebuilding, resigning, reinstalling, recreating the GitHub
Release, or overwriting release assets.

## Inputs

```text
--promotion-state ABSOLUTE_RELEASETOOL02A_STATE
--qualified-source-root ABSOLUTE_PRIVATE_SOURCE_ROOT
--public-repo OWNER/REPO
--public-main-before 40_HEX_SHA
--pages-repo OWNER/REPO
--pages-main-before 40_HEX_SHA
--previous-version VERSION
--manifest-url HTTPS_URL
--site-url HTTPS_URL
--output ABSOLUTE_EMPTY_DIRECTORY
```

Both `*-main-before` arguments are immutable expected pre-promotion commits.
They make remote mutation explicit and prevent a stale release process from
silently pushing on top of unrelated public changes.

## Public project/site promotion

Only these public-project paths may change:

```text
README.md
site/index.html
```

At the expected base commit, every public reference to the previous beta
version must be confined to those files. RELEASETOOL02B replaces that exact
version with the new version and preserves the README statement that the
private Android source is not published in the public project repository.

If the current public `main` is already one commit ahead of
`--public-main-before`, resume is allowed only when:

- that commit has exactly the supplied base as its single parent;
- exactly `README.md` and `site/index.html` changed;
- both files are byte-for-byte equal to the transformation RELEASETOOL02B
  would have produced from the supplied base.

Otherwise it fails closed.

Resume content equivalence is byte-sensitive. Git object contents are read
without trimming stdout; in particular, a trailing newline in README/site
content is preserved. Scalar git metadata such as hashes and status output
may still use normalized/trimmed command output.

## Update manifest

The manifest is rendered from:

1. the qualified bundled changelog entry for the promoted version;
2. the existing verified GitHub prerelease;
3. the APK SHA-256 carried forward from RELEASETOOL02A.

Schema:

```json
{
  "schemaVersion": 1,
  "channel": "beta",
  "versionCode": 300,
  "versionName": "0.80.0-beta.1",
  "publishedAt": "2026-10-01T12:00:00Z",
  "releaseUrl": "https://github.com/brealorg/subshell/releases/tag/v0.80.0-beta.1",
  "apkSha256": "...",
  "headline": "...",
  "highlights": []
}
```

The Pages repository may change only `subshell-update-beta.json`.

Resume is allowed when Pages `main` is exactly one commit ahead of the
supplied Pages base, that commit changes only the manifest, and its contents
match the expected rendered manifest byte-for-byte.

## Propagation

The update manifest is release-critical. RELEASETOOL02B waits for the public
manifest endpoint to return HTTP 200 with JSON exactly equal to the expected
manifest.

The project website is also probed for the new version. If the repository
push is verified but CDN propagation remains pending after the bounded wait,
that status is recorded as `PENDING_NONBLOCKING`; it does not invalidate the
already verified repository state or update manifest.

## Final state

Success writes `publication-state.env` with:

```text
STATE=COMPLETE
MANIFEST_LIVE=YES
PUBLIC_SITE_LIVE=YES|PENDING_NONBLOCKING
PRIVATE_APP_SOURCE_PUBLIC=NO
PRIMARY_FAILURE=NO
PREDECESSOR_FAILURE=NO
RELEASE_BLOCKING_FAILURE=NO
```

The private Android source commit is checked against the public project
repository again at the end.

Both public commits (site and manifest) are made as `brealorg`
`<6815997+brealorg@users.noreply.github.com>`, the same identity as Stage C's
source snapshot, whatever the machine's git identity is; a commit with any
other author or committer stops with `PUBLIC_COMMIT_AUTHOR_NOT_BREALORG`
before it is pushed (SECAUDIT16_PUBLIC_COMMITS_ARE_BREALORG_V1). Commits
from before 0.85 keep the identity they were made with; a resumed commit is
reused as it is.

## Resume and rollback policy

Already verified public state is reconciled, never blindly recreated.

RELEASETOOL02B performs no automatic destructive rollback. If a verified
public-site commit or manifest commit exists, a retry must validate and reuse
it. Unexpected commits, changed path sets, manifest contents, release assets,
or private-source publication fail closed.

## Scope boundary

RELEASETOOL02B does not:

- build or sign an APK;
- mutate an Android device;
- create a GitHub Release;
- upload, overwrite, or delete release assets;
- publish private Android source;
- alter any public project path other than the two approved copy files;
- alter any Pages path other than the root beta update manifest.

## Contract02 failure classification

RELEASETOOL02B has predecessor `releasetool02a`; RELEASETOOL02A already carries
RELEASETOOL01A/B/C as predecessors.

- **Primary**: RELEASETOOL02B's own site, manifest, remote identity, resume, or
  privacy assertion fails.
- **Predecessor**: RELEASETOOL02A or its older release-tool predecessors are
  red while RELEASETOOL02B's direct assertions are otherwise valid.
- **Release-blocking**: the public metadata/update stage is incomplete in a way
  that prevents a safely completed beta release.
- **Wrapper/infrastructure**: transient network/authentication/propagation or
  orchestration plumbing is reported separately from product failure.
