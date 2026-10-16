# MESSAGES02C6R5 — Optimistic Direct Chat media echo

C6R3/C6R4 runtime measurements show the remaining warm Direct Chat media latency is
dominated by Reddit Matrix media upload (roughly 2.5–4 seconds), while cached identity/room
overhead is negligible.

C6R5 moves perceived latency ahead of that network boundary without changing transport:

1. When a media-only Direct Chat send begins, the selected local `content://` URI is published
   immediately in `MessageReplyUiState`.
2. The conversation renders that local image/GIF as an outgoing bubble marked `Sending…` and
   follows the tail immediately.
3. The composer keeps its attachment internally for failure recovery but hides the duplicate
   preview while the optimistic bubble is in flight.
4. On Matrix HTTP 200 + event_id, the local optimistic bubble is replaced by the existing
   server-confirmed Matrix media echo.
5. Reddit Inbox projection later reconciles that echo by unique Matrix media ID as before.
6. On failure/outcome-unknown the optimistic URI is cleared; the composer attachment remains
   available under the existing error policy.

No Matrix upload/send endpoint, cache lifetime, auth behavior, room resolution, or retry policy
changes. Text + media remains fail-closed because Reddit Web caption semantics are not proven.
