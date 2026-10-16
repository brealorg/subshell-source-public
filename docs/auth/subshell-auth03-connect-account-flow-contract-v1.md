# subshell AUTH03 connect-account flow contract V1

AUTH03 connects the secure OAuth session boundary from AUTH01 to the
multi-account directory from AUTH02 without exposing authorization
responses, tokens, or storage objects to Compose.

## Build configuration boundary

A normal source checkout contains no Reddit OAuth client identifier and no
owned callback URI. AUTH04 now owns the successor production configuration
boundary and accepts a paired client ID and one canonical HTTPS redirect URI
through Gradle properties or environment. The production callback alias
remains disabled when those values are absent.

AUTH03 still owns the connect-account state machine, native Auth Tab launch,
mode-scoped compatibility login, dedicated callback ingestion, encrypted completion and AUTH02
registration.
Supplying a structurally valid redirect URI does not prove Android App Link
ownership. Production deployment must also publish the matching Digital Asset
Links statement on the owned HTTPS host using the final installed signing
certificate.

## Authorization and callback ownership

- Native production authorization opens in Android Auth Tab.
- RedReader compatibility authorization opens in a dedicated WebView because the legacy
  `redreader://rr_oauth_redir` client contract is not captured by browsers without Auth Tab
  support. The WebView has no JavaScript bridge, blocks cleartext and non-HTTPS navigation, and
  consumes only the exact custom-scheme callback.
- `AuthCallbackActivity` is the dedicated callback target.
- The production HTTPS callback is an `autoVerify` activity alias gated by
  external build configuration.
- Debug retains the isolated `subshell-auth-test://oauth/callback` route.
- The callback activity stores the validated response in the encrypted
  no-backup vault and sends only a non-sensitive result token to
  `MainActivity`.
- Compose never receives an authorization response, access token, refresh
  token, redirect URI, OAuth state, or authorization URL.

## Sanitized UI state machine

The UI observes only `ConnectAccountUiState`:

- unavailable
- idle
- launching
- awaiting callback
- exchanging
- resolving identity
- connected
- cancelled
- recoverable or terminal error

A connected result contains only provider label, username, and a monotonic
account-directory revision. Failure states contain a sanitized failure
category and retryability flag.

## Process and failure policy

Pending authorization and validated callback records remain encrypted in
`noBackupFilesDir`. A process started without a callback result extra checks
for a stored callback and resumes completion exactly once. Duplicate non-secret
result tokens after successful completion are idempotent and cannot replace the
connected UI state with a missing-callback error. A validated encrypted callback
also takes precedence over a later stale or malformed non-secret activity result.

Cancellation, callback mismatch, provider failure, token exchange failure,
identity failure, browser launch failure, and retry do not remove unrelated
AUTH02 accounts or their sessions. Temporary unbound session data is deleted
on cancellation or failed identity resolution.

Successful completion:

1. exchanges the callback behind the auth boundary;
2. resolves provider identity;
3. registers the account through AUTH02;
4. activates the new stable account ID;
5. increments the sanitized directory revision;
6. synchronizes the UI account catalogue without exposing credentials.

## Data and network isolation

AUTH03 does not change the Room schema. Room remains the canonical and
exclusive content read source established by NET02. Auth storage remains
separate under the encrypted no-backup vault.

Instrumentation uses the existing localhost TLS fixture. No request is sent
to Reddit, no live client ID is used, and no remote content write occurs.
