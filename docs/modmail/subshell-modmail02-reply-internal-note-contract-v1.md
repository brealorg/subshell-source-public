# MODMAIL02 — Reply, internal note and moderator thread presentation

MODMAIL02 builds on the runtime-verified New Modmail read path at `3bad4be`.

## Product boundary

The open Modmail thread uses a fixed bottom Markdown editor rather than a chat pill. It has two explicit modes:

- **Reply** — visible to the participant.
- **Internal note** — moderator-only content with explicit warning copy.

Reply mode can send as the moderator username or hide the individual moderator identity so Reddit presents the reply as the subreddit. Media attachments are deliberately not exposed in this slice.

## Transport

Existing incremental `modmail` authorization is reused. Existing conversation replies and internal notes are sent with one POST to `/api/mod/conversations/{conversationId}` carrying `body`, `isAuthorHidden`, and `isInternal`.

The writer performs no automatic retry. A transport or protocol failure after dispatch is treated as an unknown outcome; the draft remains visible and the UI requires a thread refresh before another send.

After confirmed success the authoritative read path reloads the same conversation. No optimistic fabricated Modmail message is inserted.

## Presentation

Raw Reddit `participatingAs` strings are not presented directly. User, moderator/subreddit and internal-note semantics are projected into product-facing labels, and message bodies use the shared Markdown renderer.

## Deferred

This slice does not implement:

- starting a new Modmail conversation;
- archive/unarchive;
- mute/unmute;
- highlight/unhighlight;
- Android notifications or Inbox badge lifecycle;
- media attachments;
- automatic mark-read mutation.
