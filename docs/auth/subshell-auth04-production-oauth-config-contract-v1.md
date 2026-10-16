# subshell AUTH04 production OAuth configuration contract V1

AUTH04 hardens the production configuration and Android App Link boundary introduced by AUTH03. It
does not perform a live Reddit authorization, register an OAuth application, publish website files,
or select a final release signing identity.

Implementation marker: `SUBSHELL_AUTH04_PRODUCTION_OAUTH_CONFIGURATION_V1`.

## External configuration

A normal checkout and validation build contain no live Reddit client ID and no production redirect
URI. Production values are accepted only from one of these paired external inputs:

- `subshell.auth.redditClientId` / `SUBSHELL_REDDIT_CLIENT_ID`
- `subshell.auth.redirectUri` / `SUBSHELL_AUTH_REDIRECT_URI`

Both values must be absent or both must be present. Configuration fails closed during Gradle
configuration when either value is partial or malformed. Validation rejects leading or trailing
whitespace, client-ID metacharacters, non-HTTPS redirects, userinfo, explicit ports, queries,
fragments, localhost, IP literals, reserved or local-use names, root callbacks, path traversal,
encoded path delimiters, and non-canonical URI spellings.

The redirect URI is a single canonical value. The manifest host and exact path are derived from that
validated URI rather than accepted as independent values that could drift from the OAuth client
registration.

## Manifest and callback policy

`AuthProductionCallback` remains an exported activity alias targeting the non-exported dedicated
`AuthCallbackActivity`. The alias contains exactly one `VIEW` filter with `DEFAULT`, `BROWSABLE`,
HTTPS, `android:autoVerify="true"`, one host, and one exact path.

The alias is disabled in an unconfigured build and uses `auth-callback.invalid` only as a disabled,
non-routable placeholder. A manifest-only validation fixture can enable the alias with
`auth04-fixture.invalid`; this fixture never configures the application runtime, builds an APK,
installs on a device, initiates domain verification, or claims domain ownership. The debug
`subshell-auth-test://oauth/callback` route remains isolated to the debug manifest.

A syntactically correct manifest cannot prove website ownership. Production deployment still
requires the same owned HTTPS host to publish a matching Digital Asset Links statement and requires
the final installed signing certificate to match that statement.

## Digital Asset Links artifact

`tools/generate-auth04-assetlinks.py` generates a deterministic `.well-known/assetlinks.json` from
an explicitly supplied Android application ID and one or more SHA-256 signing-certificate
fingerprints. It normalizes fingerprints to uppercase colon-separated form, removes duplicates,
writes atomically, and can verify the exact generated document.

The tool never reads a keystore, uploads a file, publishes to a domain, invokes a web API, or stores
signing material in source. Runtime validation derives only the public certificate fingerprint from
the already-built debug APK and labels the result as debug-validation-only.

## Runtime proof

The host-side AUTH04 runtime:

1. executes a shared Gradle configuration-policy matrix;
2. verifies the unconfigured merged manifest has the production callback alias disabled;
3. verifies a manifest-only fixture has the exact enabled App Link structure;
4. proves exact callback matching and rejection of scheme, host, and path mismatches;
5. generates and re-verifies a local Digital Asset Links deployment artifact from the current debug
   package and signing certificate;
6. restores unconfigured build intermediates.

The normal quality build then proves empty runtime client and redirect fields. AUTH01 through AUTH03,
NET01, NET02, Room schema V2, encrypted no-backup storage, and sanitized UI boundaries remain
unchanged. No request is sent to Reddit, no live client ID is used, and no domain verification request is made.
No local deployment artifact is published.


## AUTH05E2 successor binding

AUTH05E2 keeps the AUTH04 client identifier external, but locks every configured
redirect URI to the published callback:

```text
https://brealorg.github.io/oauth/callback
```

The main manifest remains fail-closed so debug and AUTH04 manifest fixtures retain
their isolated behaviour. `app/src/release/AndroidManifest.xml` is the successor
release overlay: it replaces the disabled alias with one exact, exported,
`android:autoVerify="true"` HTTPS callback for `brealorg.github.io`. No live OAuth
client identifier or Reddit request is introduced by this successor binding.
