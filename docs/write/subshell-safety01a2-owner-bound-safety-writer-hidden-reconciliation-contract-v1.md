# SAFETY01A2 — owner-bound content-safety writer and Hidden reconciliation

SAFETY01A2 turns the typed SAFETY01A1 request boundary into an authenticated
runtime capability. It still exposes no new product action in the UI; that is
reserved for A3.

## One content-safety authorization owner

Reddit Hide, Unhide and Report are all authorized by Reddit's `report` OAuth
scope. Subshell therefore adds one `CONTENT_SAFETY` capability and one persisted
`content_safety` authorization owner instead of borrowing post moderation,
post save, or another unrelated write owner.

The capability grant is:

- the existing core-read scopes;
- `history`, so the existing authoritative Hidden listing can be refreshed;
- `report`, for `/api/hide`, `/api/unhide`, and `/api/report`.

`report` is also added to the normal daily-driver account grant. Existing
accounts are not silently reauthorized: preserved stored scopes remain intact
and the existing capability-upgrade path is used when `report` is absent.

## Non-retrying mutation transport

Runtime construction uses `SubshellNetworkTransport.nonRetryingWriteCallFactory`.
A transport library retry therefore cannot accidentally duplicate a Report.

Hide/Unhide expresses a desired visibility state and is treated as idempotent
at the product boundary. A pre-response DNS/connect failure can be retried by
the user, while the write transport itself still performs no automatic retry.

Report is duplicate-prone. TIMEOUT/IO, protocol failure, and ambiguous HTTP
responses after dispatch become `OUTCOME_UNKNOWN` and do not offer a blind
retry. Explicit rate limiting remains retryable because Reddit rejected the
request rather than ambiguously applying it.

## Existing Hidden history remains authoritative

SAFETY01A2 creates no new Hidden table, cache namespace, or history loader.
After an acknowledged post Hide/Unhide, the writer asks the existing
`AuthenticatedRedditAccountHistoryUpdater` to `REFRESH`
`RedditAccountHistoryKind.HIDDEN`.

An acknowledged post Report receives the same refresh because Reddit reporting
can hide the reported post. A successful post Report publishes `hidden=true`
to the transient content-safety completion state so A3 can reconcile the
currently visible surface immediately. Comment Report has no direct Hidden
history reconciliation because Reddit's Hidden listing is post/link history.

`HiddenHistoryReconciliation.REFRESHED` means the authoritative Hidden query
was refreshed successfully. It deliberately does not claim that a target
membership check was independently proven.

A write acknowledgement is not converted into a write failure merely because
the follow-up Hidden refresh fails or needs history authorization. The
reconciliation result is carried separately, preventing a blind second write.

## Report privacy

The full report reason and optional detail fields exist only in the in-memory
pending mutation and writer request. `ContentSafetyUiState` contains operation,
target, write status and reconciliation status, but no report reason or report
detail text. SAFETY01A2 adds no persistence for report content.

## Existing runtime contracts

Callback and stored-resume routing recognize the new `CONTENT_SAFETY` owner
explicitly. It does not fall through to legacy ownerless authorization.

The existing AUTH07B1 daily-driver contract becomes successor-aware for the
new `report` grant. SAFETY01A1, PROFILE01A6 Hidden history, COMMENTSAVE01 and
the shared ERROR01A4 write-failure pipeline remain regression gates.

## A2 exclusions

- no post/comment overflow UI yet;
- no report dialog yet;
- no local `hiddenPostIds` retirement yet;
- no Room schema migration;
- no direct comment Hide/Unhide;
- no checkpoint, landing, push, or remote operation.

SAFETY01A3 will connect the runtime to the post/comment action surfaces and
retire local-only Hide behavior.

## USERACTION01A1 successor note

USERACTION01A1 reuses the owner-bound `CONTENT_SAFETY` route and extends its scope
set with `account` and `privatemessages` for real user Block/Unblock. The original
SAFETY01A2 Hide/Unhide/Report transport and reconciliation behavior remains
unchanged.
