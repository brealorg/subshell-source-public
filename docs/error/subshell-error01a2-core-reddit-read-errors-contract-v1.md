# subshell ERROR01A2 — Core Reddit read error presentation contract v1

ERROR01A2 applies the ERROR01A1 failure-detail and Material presentation foundation to the primary authenticated Reddit read surfaces without replacing their feature/domain `*FailureKind` types.

## Scope

The migrated surfaces are Home/feed, post comments, Inbox, joined communities/subscriptions, Explore/community discovery including typeahead fallback, and profile comments.

## Failure-detail boundary

Authenticated read failures carry optional `FailureDetails` from the network/session/storage boundary into the corresponding UI state. HTTP status and retry-after metadata, transport kind, protocol kind, and safe local failure categories remain available to presentation instead of being flattened to a generic failure string.

The existing domain failure enums remain authoritative. `FailureDetails` augments those enums; it does not replace them.

## Cached and degraded content

Cached or previously loaded content must not erase the cause of a failed refresh. When usable content remains visible, the shared presentation kind is `DEGRADED` and the message states both the failure reason and which saved content remains available.

Degraded notices use a compact Material treatment. On scrollable core-read surfaces they remain visible as sticky list headers once scrolled past, rather than becoming hidden above the current viewport. They remain in normal list flow before that point and therefore do not overlay search fields or other screen controls. Append failures remain at their paging boundary.

When no usable content is available, the shared presentation kind is `BLOCKING`. Community suggestion/typeahead failures may use `INLINE`; fallback suggestions use `DEGRADED`.

## Authorization and session semantics

A missing feature scope remains an authorization flow owned by the feature. A rejected, expired, or otherwise unusable account session remains a session/reauthentication concern. ERROR01A2 does not relabel session failures as permission failures.

## Material presentation

The migrated surfaces use the shared ERROR01A1 `ErrorNotice` family. They do not maintain parallel local failure-card implementations or local generic error-string mappers.

Retry remains feature-owned and callback-free in `ErrorPresentation`: the presentation carries `ErrorActionKind.RETRY`, while the screen maps that action to its existing refresh or append operation.

Post comments support both pull-to-refresh and an explicit `Refresh comments` action in the post-level overflow menu. The per-comment overflow remains comment-scoped and does not own thread refresh. Cached comment refresh failures stay with the sticky comment controls so the failure remains visible while the user reads the thread.

## Security

All technical detail remains subject to the ERROR01A1 sanitization boundary. ERROR01A2 does not expose access tokens, refresh tokens, authorization codes, OAuth state, cookies, callback payloads, raw sensitive URLs, response bodies, or stack traces.

## Deferred work

ERROR01A2 does not migrate media/backup errors or write/mutation failures. Those remain ERROR01A3 and ERROR01A4 scope respectively.
