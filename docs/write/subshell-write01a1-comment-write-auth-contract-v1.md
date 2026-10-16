# subshell WRITE01-A1 comment write authorization contract V1

WRITE01-A1 introduces authorization capability only. It does not submit a Reddit comment.

## Grants

- The normal RedReader-compatible connection remains exactly `identity mysubreddits read`.
- Comment publishing requires a separate user-approved `identity mysubreddits read submit` grant.
- Existing refresh tokens are never treated as having newly requested scopes.
- Read-only loaders accept a token that contains the read grant plus an explicitly approved successor scope, but they continue to issue only their existing read endpoints.

## Callback and account binding

Every authorization request persists a fingerprint of the requested scope set. Comment-write authorization also pins the authenticated account stable ID. The callback must match provider, client mode, client ID fingerprint, scope fingerprint, and pinned account before code exchange.

The upgraded session replaces the pinned account session only after token exchange, identity resolution, exact account identity verification, and required-scope verification all succeed. The previous bound session remains untouched on failure.

## Token boundary

Callers capture the active authenticated account ID before starting work and resolve or refresh the bearer for that exact account. A later account switch does not redirect an in-flight operation to another account. Tokens and response bodies do not enter UI state or host logs.

## Runtime proof

The deterministic Android proof loads fixture assets through an explicit context for the instrumentation APK. Auth records use a test-only `ContextWrapper` rooted in dedicated target-cache directories with isolated Android Keystore aliases; the production no-backup auth vault is never opened or modified. Both test directories and aliases are removed after the proof. The proof performs authorization URL generation, callback processing, localhost HTTPS token exchange, localhost identity resolution, atomic bound-session replacement, account switch, and pinned token resolution. It performs no live Reddit request and no comment write.

## UI boundary

The capability is constructed by the production runtime but is not exposed by the composer POC in A1. The existing read-only connect flow remains the only UI consumer of stored callbacks. WRITE01-A3 must route a comment-write callback to this capability before enabling the authorization action; A1 does not claim a live UI upgrade.

## Successors

- WRITE01-A2 adds the typed authenticated `POST /api/comment` transport.
- WRITE01-A3 integrates sending, retry, draft retention, thread refresh, and controlled live proof with the composer POC.
