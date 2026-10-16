# MESSAGES02C7 — foreground Matrix Direct Chat live read

MESSAGES02C7 turns an open Reddit Direct Chat detail into a live read surface without changing
any write semantics proven in C6.

## Runtime scope

- Start a read-only Matrix `/sync` stream only while a Direct Chat detail is visible and the host
  lifecycle is `STARTED`.
- Stop and cancel the in-flight long poll on `ON_STOP`, navigation away, account change, or target
  change. The OkHttp `Call` is cancelled with coroutine cancellation instead of lingering until
  the server timeout.
- Reuse the same in-memory Reddit web JWT provider and the same verified Matrix identity/direct-room
  cache as the C6 media writer.
- Keep one memory-only Matrix `next_batch` cursor for the current Direct Chat target so foreground
  resume can continue from the last read position without replaying the room history.

## Read protocol

The reader performs only Matrix GET requests:

1. validate the pinned authenticated Reddit account using the existing Inbox read capability;
2. reuse or obtain the first-party Reddit web JWT;
3. reuse or verify Matrix `/account/whoami` identity;
4. reuse or resolve exactly one two-member Direct Chat room by the existing C6 participant rules;
5. establish a baseline `/sync` if no resume cursor exists;
6. long-poll `/_matrix/client/v3/sync` with a 20-second server timeout and a filter pinned to the
   exact room;
7. project new `m.room.message` events with `msgtype=m.text` or `msgtype=m.image`.

Typing, read receipts, edits, reactions and other Matrix writes/events are intentionally outside C7.

## Projection and reconciliation

- `m.text` becomes a normal `MessageBubble` using `event_id` as the live identity.
- `m.image` converts Reddit `mxc://reddit.com/<media>` to the canonical Matrix download URL already
  supported by the shared message media renderer.
- Live events are merged with the existing Reddit Inbox conversation and deduplicated by exact
  event identity, unique Matrix media ID, or a tight text/timestamp match.
- Incoming/outgoing live messages therefore appear immediately in the open thread while Reddit's
  legacy Inbox projection remains the fallback and persistence/reconciliation surface.
- C6R5R1 optimistic outbound media keeps its local `content://` visual backing until the legacy
  authoritative Inbox projection arrives; a live `m.image` never reintroduces the confirmation
  spinner/flicker.
- Live activity causes one normal Inbox refresh when leaving the Direct Chat so the Inbox list and
  later non-live detail remain authoritative.

## Tail behavior

When the user is at or near the tail, new live events follow the tail automatically. If the user has
scrolled materially upward, C7 does not force them back down. Existing C6R4 outbound tail-follow
remains unchanged.

## Safety boundaries

- No Matrix POST/PUT write is added.
- No blind retry or message resend is added.
- Direct Chat text continues through the proven `t4 /api/comment` path.
- Direct Chat image/GIF write continues through the proven C6 Matrix media path.
- Text + media remains fail-closed until caption semantics are proven.
- Reddit Inbox remains the fallback/reconciliation source if the foreground Matrix read stream
  cannot connect.
