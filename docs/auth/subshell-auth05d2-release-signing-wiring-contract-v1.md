# subshell AUTH05D2 release-signing wiring contract V1

## Locked public identity

AUTH05D2 binds production release artifacts for
`io.github.brealorg.subshell` to the owner-managed AUTH05D1 certificate:

```text
alias=subshell-app-signing
keystore_type=JKS
certificate_sha256=52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84
keystore_file_sha256=7702295c7184a73cb1baba354becaabcdc186cce76de19a470c41bd66afeb2a7
```

The matching public certificate is committed as
`docs/auth/subshell-app-signing-cert-v1.pem`. The certificate and fingerprints
are public deployment identity; the JKS and passwords are not.

## External secret boundary

Gradle accepts release-signing material only from these environment variables:

```text
SUBSHELL_RELEASE_KEYSTORE_PATH
SUBSHELL_RELEASE_STORE_PASSWORD_FILE
SUBSHELL_RELEASE_KEY_PASSWORD_FILE
```

The source tree contains no default JKS path and no password. Password values
are read from two separate private files so they do not appear in command-line
arguments, Gradle properties, or shell history. Partial configuration, relative
paths, source-tree paths, permissive file modes, short passwords, equal
passwords, wrong aliases, wrong JKS bytes, inaccessible private keys, and
certificate mismatches fail closed.

## Gradle and release artifact

Debug builds remain available without release credentials and retain the debug
application ID and debug certificate. Historical AUTH05D2 wiring required the
full Gradle quality gate before release packaging. The CONTRACT02F successor
instead requires a verified Contract02 qualification record for the exact source
snapshot while retaining mandatory cryptographic verification of the external
JKS before packaging. The release APK must have:

```text
application_id=io.github.brealorg.subshell
certificate_sha256=52:E9:62:E7:41:2E:25:DA:80:1E:0A:39:06:E0:BF:B4:39:90:0A:1A:A1:5B:A9:F9:3C:95:B4:99:55:BA:F5:84
```

The host runtime verifies APK signatures with `apksigner`, package identity
with `aapt2`, exact JKS identity with `keytool`, and private-key access through
a signed CSR. Generated verification artifacts contain public metadata only.

## Isolation

No JKS, password, private key, absolute local key path, OAuth client ID, callback
URI, token, or account data is written into source, APK resources, Room,
settings backups, process snapshots, or public verification artifacts.
Production App Links were outside AUTH05D2. AUTH05E2 now deploys and verifies
the release-only callback while preserving the AUTH05D2 signing boundary.
No request is sent to Reddit and no GitHub mutation is performed by AUTH05D2.
