# RELEASETOOL01A — canonical signed preview foundation

RELEASETOOL01 separates qualification, signed artifact creation, device smoke,
and public promotion into distinct stages.

The invariant is:

```text
QUALIFY ONCE
→ BUILD ONCE
→ VERIFY EXACT APK
→ PROMOTE EXACT BYTES
```

## Stage A — Contract02 qualification

`tools/contract02/qualify-delta.sh` owns release qualification. It produces an
external `qualification-record.env` bound to the exact clean source commit/tree
and the explicit Contract02 manifest.

RELEASETOOL01A itself is a native Contract02 contract and its foundation proof
uses `tools/contract02/manifests/releasetool01a.tsv`. This keeps the proof
manifest aligned with the actual release-tool delta rather than reusing the
historical beta.2 reference manifest.

Stage B does not rerun Contract02 or the legacy global quality suite.

## Stage B — signed preview

`tools/release01/prepare-signed-preview.sh` consumes:

- an absolute external qualification-record path;
- an explicit release version name;
- an explicit positive version code;
- an absolute external final preview directory.

Signing material remains external through the existing AUTH05D2 variables:

```text
SUBSHELL_RELEASE_KEYSTORE_PATH
SUBSHELL_RELEASE_STORE_PASSWORD_FILE
SUBSHELL_RELEASE_KEY_PASSWORD_FILE
```

The driver independently validates the qualification record, pre-verifies the
locked JKS identity, invokes `:app:assembleRelease` exactly once with
`--no-configuration-cache`, and supplies both the qualification record and
release version overrides to Gradle.

`preReleaseBuild` therefore verifies the prior Contract02 qualification and
AUTH05D2 signing identity without re-entering `subshellQualityCheck` or
`subshellContracts`.

The generated APK is verified with the existing AUTH05D2 cryptographic verifier
and independently checked for exact application ID, version name, version code,
single signer, and locked signer certificate.

The exact built APK is copied byte-for-byte to a private staging directory. A
release record binds the source identity, qualification identity, version,
application ID, signer, APK SHA-256, APK byte size, build-log hash, and preview
APK path.

Only after every check passes is the staging directory atomically renamed to
the requested final preview directory. A failed run removes staging output and
never creates a final qualified preview directory.

Stage B performs no device mutation and no Git/GitHub mutation.

## Stage C — device preview

Device install and launch smoke are a separate explicit operation. They consume
the exact Stage-B preview APK and do not rebuild it.

## Stage D — public promotion

Public GitHub prerelease promotion is also separate. It consumes the Stage-B
release record and exact preview APK, performs no Gradle build and no signing,
and must prove the downloaded remote asset is byte-identical.

The public site/version-copy change remains separately reviewable source work.

## Historical AUTH05D2 runtime

`tools/run-auth05d2-release-signing-runtime.sh` remains useful as historical
integration proof, but it is not the canonical RELEASETOOL01 driver. Its old
combined responsibilities include legacy global-quality expectations,
androidTest build, host regressions, device install, and launch.
