# MESSAGES02D5D — authoritative post-send Direct Chat handoff

## Product behavior

After Reddit Chat accepts the first text, image, or GIF from **New message**, Subshell keeps
the composer in a completed/locked state (`Message sent` / `Opening Direct Chat…`) while it
waits for Reddit's normal Inbox projection of that Direct Chat.

When the authoritative Inbox row is available, the current `NEW_MESSAGE` route is replaced
by the established `Conversation` route.

## Authority boundary

D5D routes only with an existing `InboxItem` from `RedditInboxUiState.items`.

It does **not**:

- synthesize an Inbox item;
- assume a Matrix room ID is a Reddit Inbox thread ID;
- add any Matrix write;
- retry the accepted Direct Chat create/send operation;
- add Seen/read receipts;
- add presence.

The selector requires:

1. `ActivityKind.MESSAGE`;
2. typed conversation detail `DIRECT_CHAT` (`[direct chat room]`);
3. the authoritative peer username to match the participant from the successful start request.

The peer is resolved with the same policy used by the established Conversation surface:
first non-outgoing conversation sender, otherwise the Inbox item's sender.

## Refresh and fail-safe behavior

`AuthConnectViewModel` already triggers a force-refresh of Inbox immediately after an
accepted Direct Chat start.

D5D consumes that authoritative state first. If no matching row appears, D5D allows one
bounded additional **read-only Inbox refresh**. It never repeats `createRoom`, text send,
media upload, or media send.

If the bounded refresh settles without a matching authoritative Direct Chat row, Subshell
resets the completed start state and returns to Inbox rather than guessing a route identity.

## Navigation

Before successful handoff:

`... -> Main(INBOX) -> Secondary(NEW_MESSAGE)`

After successful handoff:

`... -> Main(INBOX) -> Conversation(authoritativeInboxItem.id)`

The route is replaced rather than pushed, so Back from the conversation returns to Inbox
instead of returning to a completed New message composer.

The existing Conversation surface remains the single owner of foreground Matrix sync,
typing, delete, text replies, image/GIF replies, and Inbox refresh reconciliation.

<!-- SUBSHELL_MESSAGES02D5D_RUNTIME_PROOF_20260829 -->
## Runtime verification

On 2026-08-29, DEV1 runtime verification proved the complete new-chat post-send handoff.

The first text event created a new Direct Chat and was accepted:

- `stage=directory result=accepted`
- `stage=room result=created`
- `stage=send result=accepted`

The first Inbox projection did not expose the authoritative row immediately, so D5D used
its single bounded extra read-only refresh:

- `result=refresh_requested source=authoritative_inbox`

The refreshed Inbox then exposed the matching Direct Chat row and the route replacement
completed successfully:

- `result=accepted source=authoritative_inbox`

No `fallback_inbox` path was observed. The runtime-proven DEV1 APK SHA256 was
`ecd596afd6bddf74b588a085c79a6a4852ae2b34b162c73a54edb55ada81017d`.

This closes D5D for the authoritative post-send handoff scope: accepted New Message sends
transition into the established Conversation surface using Reddit's real Inbox projection,
without synthetic Inbox identities or Matrix-room-ID routing.
