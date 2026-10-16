# subshell AUTH01 secure OAuth session contract V1

AUTH01 introduces the first authenticated session boundary on top of NET01 and NET02. It does not
connect live Reddit credentials, client registration, production callback ownership, background
synchronization, or remote writes.

Implementation marker: `SUBSHELL_AUTH01_SECURE_OAUTH_SESSION_V1`.

## Ownership

- `data.auth` owns authorization state, callback validation, token exchange, refresh, revocation,
  encrypted persistence, and optional provider account identity.
- `AuthCallbackActivity` is dedicated to callback ingestion. `MainActivity` remains launcher-only and
  never receives an authorization code or token.
- `NetworkBearerTokenProvider` remains the only boundary through which NET01 can obtain an access
  token. UI, Room, settings backups, process snapshots, and repository DTOs do not expose tokens.
- Production callback registration is deliberately absent until an owned HTTPS domain and final app
  signing identity can publish a verified Android App Link association.
- Debug builds expose only `subshell-auth-test://oauth/callback` for deterministic local runtime proof.

## Authorization and callback policy

- Authorization uses the code flow, permanent duration, least-privilege scopes, and a freshly
  generated 256-bit URL-safe state value.
- Pending state is encrypted, expires after ten minutes, and is consumed exactly once after a valid
  callback.
- Scheme, authority, path, state, and callback shape are validated before a code or denial is stored.
- Callback codes are written only to encrypted app-private storage and are removed before exchange.
- Native production authentication launches through AndroidX Browser Auth Tab with the verified
  HTTPS App Link as callback. RedReader compatibility is the only exception: its legacy client
  identity uses a dedicated app-private WebView that intercepts only the exact registered
  `redreader://rr_oauth_redir` callback. No WebView is used for token exchange or authenticated API
  traffic.

## Token transport and persistence

- Authorization-code exchange, refresh, and revocation use HTTPS POST form bodies and HTTP Basic
  client authentication. Installed Reddit applications use an empty client secret.
- Access and refresh tokens are bounded, strictly parsed, never placed in URLs, and never logged.
- Access-token expiry applies a one-minute refresh skew.
- Refresh is serialized by a mutex so concurrent callers produce one network refresh and one atomic
  encrypted session update.
- Encryption uses a non-exportable Android Keystore AES-256 key with `AES/GCM/NoPadding`.
- Ciphertext is atomically stored under `noBackupFilesDir/subshell-auth`; tokens never enter Room,
  SharedPreferences, cloud backup, device transfer, or the settings snapshot allowlist.
- Logout revokes the refresh token when present and always clears local encrypted session material.

## Runtime proof

The AUTH01 runtime uses the existing NET01 localhost TLS certificate and server implementation. It
runs in two instrumentation invocations separated by an external force-stop:

1. create state, reject mismatched state, accept one callback, reject callback replay, exchange a
   one-time code, persist an encrypted session, and prove plaintext secrets are absent;
2. restore the session, expire it, issue two concurrent access requests, prove one refresh request,
   rotate the refresh token, update the bearer provider, revoke, and clear the local session.

No request is sent to Reddit. No OAuth client ID or secret for production is included. Room remains
the canonical content source established by NET02.
