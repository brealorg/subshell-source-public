# subshell AUTH02 account session integration contract V1

AUTH02 connects the secure OAuth session foundation from AUTH01 to a typed account directory without
moving credentials into UI state, Room, settings backup, or process snapshots. The implementation
marker is `SUBSHELL_AUTH02_ACCOUNT_SESSION_INTEGRATION_V1`.

## Identity and account IDs

Authenticated identity is resolved only after a successful OAuth code exchange through a bounded
HTTPS identity client. Provider account ID and username are normalized inside the auth boundary.
The stable authenticated account key is provider-qualified (`reddit:<provider-account-id>`), while
local fixtures use the explicit `local:<username>` namespace. Local and authenticated usernames
must be unique case-insensitively; a collision is rejected rather than silently merging state.

## Metadata-only registry and multiple sessions

The encrypted no-backup auth directory contains:

- one metadata-only account registry with account ID, provider, provider account ID, username, and
  creation/last-used timestamps;
- one independently encrypted OAuth session envelope per authenticated account;
- no token fields in the registry model.

Account-specific session filenames use SHA-256-derived opaque suffixes. Access and refresh tokens
remain inside AES-256-GCM envelopes owned by Android Keystore. AUTH01's temporary unbound session is
moved into the account-specific envelope only after identity succeeds.

## Active account and bearer routing

`AuthAccountSessionCoordinator` owns account selection. The active authenticated account determines
which encrypted session backs `NetworkBearerTokenProvider`. Selecting a local fixture always yields
no bearer token. Expiry refresh is serialized across callers and updates only the active account's
session. Account switching and the active stable ID survive an external process restart.

Logout revokes and deletes only the active authenticated session, removes only that account's
metadata record, and preserves unrelated authenticated and local accounts. The fallback after
scoped logout is the first explicit local fixture account.

## Sanitized UI boundary

UI code sees only `AccountSessionSummary`: stable ID, username, source, sanitized connection state,
and an optional provider label. It cannot import `data.auth`, token models, access tokens, or refresh
tokens. Existing per-account local preferences remain keyed by the unique username, while active
account persistence uses the stable account ID with legacy username fallback.

Room remains the canonical and exclusive content source established by NET02. Auth identity is
session metadata, not cached Reddit content, and does not change the Room schema.

## Runtime proof

The two-phase localhost HTTPS runtime registers two authenticated accounts, verifies identity bearer
scoping, rejects a local-fixture username collision, encrypts all private auth files, force-stops the
app process, restores the active account, serializes an expired-token refresh to one request,
switches bearer ownership, performs scoped revocation/logout, and proves the unrelated account
survives.

No request is sent to Reddit. No live OAuth client ID is present. Production verified App Links and
real Reddit login remain deferred.
