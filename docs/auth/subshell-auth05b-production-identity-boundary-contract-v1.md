# subshell AUTH05B production identity boundary contract V1

## Scope

AUTH05B introduced fail-closed validation for a candidate final Android
application ID and the public SHA-256 fingerprint of a release signing
certificate. AUTH05C subsequently applies the selected application ID
`io.github.brealorg.subshell` while preserving the AUTH05B certificate boundary.

AUTH05D1 subsequently creates the owner-managed app-signing identity outside
source control, and AUTH05D2 wires that identity into release builds through
external file inputs. AUTH05B itself remains the public identity-validation
boundary and still does not register a Reddit client, deploy an App Link, or
perform live OAuth.

## External public inputs

Candidate release identity metadata remains available as an external pair:

- Gradle property `subshell.identity.applicationId`, or environment variable
  `SUBSHELL_FINAL_APPLICATION_ID`.
- Gradle property `subshell.identity.signingCertSha256`, or environment
  variable `SUBSHELL_FINAL_SIGNING_CERT_SHA256`.

Neither value is a private signing key. Both are public deployment identity
metadata. Partial configuration is rejected, and external values never
override the application ID selected by AUTH05C.

## Validation

A candidate application ID must contain at least three lowercase package
segments, use only canonical package characters, and not be the retired
prototype ID, an Android namespace, an example namespace, or a development,
test, prototype, example, or invalid suffix.

The signing certificate fingerprint must contain exactly 32 uppercase
colon-separated hexadecimal octets. Host-side validation rejects the
fingerprint extracted from the debug APK.

## Decision artifact

`tools/validate-auth05-production-identity.py` can validate a candidate
application-ID and public-certificate pair and create a deterministic local
JSON decision artifact. The artifact is validation-only: it contains no
private key, OAuth client, callback URI, or publication operation.

AUTH05C has a separate migration artifact recording that the selected
application ID was applied before AUTH05D2. The successor marker records that
release-signing wiring is now configured without changing the AUTH05B decision
artifact.

## Release-signing successor boundary

AUTH05D2 adds a Gradle `signingConfig`, but no keystore path, password, private
key, or local secret file is committed. Release builds require three external
environment variables containing an external JKS path and two temporary
password-file paths. The locked public certificate fingerprint and exact JKS
file SHA-256 are non-secret source constants. Release tasks fail closed unless
the external JKS and private-key access verify before packaging.

## Isolation

No request is sent to Reddit. No live client ID, callback URI, token, signing
key, domain deployment, remote write, device network mutation, or GitHub
mutation occurs. AUTH01 through AUTH04, NET01, NET02, and Room source-of-truth
boundaries remain unchanged.
