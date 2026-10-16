# MOD01A2 — Approve/remove from mod queue

## Product boundary

MOD01A2 turns the read-only MOD01A1 queue into a bounded operational moderation surface.

The moderator can:

- approve a queued post or comment;
- remove a queued post or comment after an explicit confirmation;
- authorize the existing Reddit `modposts` capability if the active session is missing it;
- see a clear failure state when Reddit rejects the action;
- refresh the authoritative queue after success or an ambiguous transport outcome.

## Reddit boundary

The slice uses the classic moderator write endpoints:

- `POST /api/approve` with `id=<t1|t3 fullname>`;
- `POST /api/remove` with `id=<t1|t3 fullname>` and `spam=false`.

Both post and comment fullnames are accepted. Removal reasons, spam removal, approve/remove from ordinary feed cards, report-ignore actions, locks, distinguish, bans, moderator notes and modmail are outside this slice.

## Authorization

No OAuth scope expansion is introduced. The existing `POST_MODERATION` capability already includes `modposts`.

MOD01A2 gives queue actions a dedicated persisted authorization owner, `moderation_action`, so a callback cannot be consumed by unrelated post-management work.

## State and reconciliation

- A queue item is removed optimistically while its mutation is sent.
- A definitive failure restores the exact item at its previous list position.
- A successful action keeps the optimistic projection and immediately refreshes the current mod queue from Reddit.
- Timeout/I/O and selected 5xx/ambiguous responses are treated as outcome-unknown: the item is not blindly retried and the queue is refreshed to reconcile authoritative state.
- Only one moderation mutation is allowed at a time.

## Presentation

Each queue item retains its POST/COMMENT identity, author, report count and summary. The action row adds `Approve` and `Remove`. Remove requires confirmation. Authorization and failure states remain inside Mod tools rather than becoming global app errors.

## Successor boundary

Future MOD01 work may add removal reasons, spam classification, approve/remove entry points outside the queue, ignore reports, locks, distinguish, moderation log, ban/user management and other moderator permissions. MODMAIL remains a separate MODMAIL01 product line.


## Successor: MOD01A2.1

MOD01A2.1 adds optional subreddit removal reasons and a bounded moderator note after a known-successful remove. The classic remove action remains a separate operation; failure to attach the reason or note must not roll back an item Reddit has already removed. MOD01A2.2 will federate actionable moderation into Inbox → Activity and is intentionally outside this contract.
