# RELEASETOOL01C — minimal GitHub prerelease publish

Stage D is intentionally small.

It accepts the existing Stage-B `release-record.env`, verifies the exact APK
with the existing shared verifier, and derives the tag as `v<VERSION_NAME>`.

The publication flow is:

1. verify release record and APK locally;
2. reject proof versions;
3. require the recorded source commit to already exist in the explicit GitHub
   repository;
4. reject an existing tag;
5. one release create command publishes the exact APK as a prerelease;
6. one download fetches that public APK;
7. compare downloaded SHA-256 with the Stage-B APK SHA-256.

That is the whole binary-promotion workflow.

There is no draft workflow, no custom REST release client, no numeric release
or asset IDs, no curl upload transport, no token handling, no automatic public
rollback, no rebuild, no resigning, no device install, and no Git source push.

If publication succeeds but the downloaded hash unexpectedly differs, the tool
stops and the public release is handled manually rather than adding automated
rollback machinery.

Source promotion to GitHub is a separate operation. Stage D simply stops when
the recorded source commit is not already present remotely.
