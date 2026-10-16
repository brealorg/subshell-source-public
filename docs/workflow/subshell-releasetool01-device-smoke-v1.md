# RELEASETOOL01B — exact preview device smoke

RELEASETOOL01B is Stage C of the canonical release workflow.

It consumes the `release-record.env` and exact preview APK produced by
RELEASETOOL01A. Stage C never invokes Gradle, never signs an APK, and performs
no Git or GitHub operation.

## Shared release-record verification

`tools/release01/verify-release-record.py` verifies:

- exact release-record schema;
- release gate is PASS;
- current clean source tree matches the recorded source tree;
- preview APK path is external and exists;
- APK SHA-256 and byte size;
- application ID;
- version name and version code;
- exactly one APK signer;
- locked signer certificate fingerprint.

The verifier is intentionally reusable by later promotion tooling.

## Device smoke

`tools/release01/smoke-preview-device.sh` accepts an absolute release-record
path, an optional explicit adb serial, and an optional
`--qualified-source-root`.

The qualified source root is the clean Git worktree whose tree is recorded in
the release record. By default this is the repository containing the smoke
driver. The explicit override exists so Stage C tooling can be newer than the
APK source without weakening provenance: the record is still checked against
the exact qualified source tree that produced the preview.

Before installation it verifies the complete release record and exact preview
APK bytes. It then records any existing package version, UID and
`firstInstallTime`.

The APK is installed with `adb install -r`.

For an existing installation, the smoke requires UID and firstInstallTime to be
preserved, proving an in-place package update rather than an uninstall/reinstall.
For a fresh installation it records that separate case.

After installation the device package must report the release-record version
name/code. The canonical main activity is launched and must become the resumed
foreground activity with a live process.

Finally the external APK is hashed again to prove the exact preview APK bytes
were not changed during device smoke.

Stage C performs a device package install/update and application launch. It
does not claim that launching the application prevents the application itself
from performing its normal network behavior.

## Separation from Stage B and Stage D

Stage B owns qualification, signing, release build, APK verification and
immutable preview creation.

Stage C owns only exact-preview device installation and launch smoke.

Stage D will own public promotion of those same exact preview bytes. It must not
rebuild or resign the APK.
