# subshell AUTH06B RedReader compatibility contract v1

AUTH06B adds a separate, explicit OAuth client mode for a user-supplied installed-app
client identifier. It does not replace or weaken the locked AUTH05E2 production App
Link identity.

## Locked identities

- Version: `59` / `0.57.0-auth06b-redreader-compatibility-dev`
- Native production callback remains `https://brealorg.github.io/oauth/callback`.
- Compatibility callback is exactly `redreader://rr_oauth_redir`.
- Compatibility User-Agent is exactly `org.quantumbadger.redreader/1.25.1`.
- Compatibility mode is `OAuthClientMode.REDREADER_COMPATIBILITY`.

## Local configuration boundary

The client identifier is supplied by the user at runtime. subshell must not include a
working value in source, resources, BuildConfig, release APK metadata, logs, settings
snapshots, cloud backup, device transfer, or runtime evidence.

The selected mode and compatibility client identifier are stored as an encrypted
`StoredOAuthClientSelection` under
`noBackupFilesDir/subshell-auth-configuration`. Android Keystore AES-GCM protects the
file. Removing the compatibility configuration deletes the encrypted selection and
returns selection policy to native production mode. A process restart is required
before the application runtime adopts a changed selection.

## Callback and transaction isolation

The dedicated, non-exported `RedReaderCompatibilityLoginActivity` owns the compatibility login
surface. It permits HTTPS navigation without adding a JavaScript bridge and intercepts only the
exact custom scheme and host before Android WebView attempts to render it. The callback processor
then validates the exact redirect URI and state from the pending transaction. It accepts only
Reddit's observed empty-fragment sentinel `#_`; any other callback fragment remains invalid.

The exported alias `AuthRedReaderCompatibilityCallback` remains as a fallback for callbacks
delivered by Android or a browser. It routes into the existing non-exported, no-history
`AuthCallbackActivity` and the same callback processor.

Every pending authorization and stored callback binds:

- provider;
- OAuth client mode;
- exact redirect URI;
- single-use state;
- SHA-256 client-ID fingerprint.

Token exchange fails closed if the current configuration does not match the callback
mode and fingerprint. Stored sessions and account IDs remain isolated by client mode.

The compatibility WebView consumes only the exact `redreader://rr_oauth_redir` navigation in
process, so its normal login path does not depend on browser support or Android default-handler
selection. The exported alias remains as a fallback and does not attempt to override another
app's default-handler selection.

## Network identity

The selected mode supplies one consistent User-Agent to token exchange, account
identity, and future authenticated Reddit API clients. AUTH06B runtime proof uses only
a deterministic localhost HTTPS server. No live Reddit endpoint or live client
identifier is contacted.

## Runtime proof

`tools/run-auth06b-redreader-compatibility-runtime.sh` builds the debug and Android
test APKs, resolves the exact custom-scheme alias, installs only the debug lane, and
runs `Auth06bRedReaderCompatibilityInstrumentedTest`.

The runtime proves encrypted local persistence, process reopen, exact callback
acceptance, native-callback rejection, mode/fingerprint binding, localhost token and
identity requests, exact compatibility User-Agent, session-mode isolation, and local
configuration deletion.

## Explicit exclusions

- No client identifier is bundled or extracted.
- No live Reddit OAuth or API traffic occurs.
- No production signing material is accessed.
- No GitHub or remote write occurs.
- No central proxy or shared token service is introduced.
