# MOD01A2.1 — Removal reasons and moderator note

## Product boundary

MOD01A2.1 completes the first-stage Remove workflow in Mod tools without expanding it into removal messaging, spam classification or Inbox federation.

When a moderator chooses `Remove`, Subshell may show the active community's configured Reddit removal reasons and an optional moderator note. Plain removal remains available when no reason is configured, when the reason list is unavailable, or when the moderator does not authorize the additional reason-list capability.

## Reddit boundary

The slice uses the classic Reddit moderation endpoints already used by mature clients:

- `GET /api/v1/{subreddit}/removal_reasons` to load configured reasons;
- `POST /api/remove` to remove the `t1` or `t3` item;
- `POST /api/v1/modactions/removal_reasons` to attach `reason_id` and a bounded `mod_note` after a successful remove.

The reason list preserves Reddit's `order` array and projects only bounded `id`, `title`, and `message` data. The moderator note is capped at 100 characters.

A removal reason/mod note is moderation metadata. This slice does **not** send a removal message or comment to the author. Removal-message delivery is a distinct Reddit operation and remains deferred.

## Authorization

Existing Approve/Remove continues to use the dedicated `moderation_action` owner and the existing `modposts` capability.

Loading the classic removal-reason list is isolated behind a second persisted owner, `moderation_reason`. Its OAuth selection preserves the current daily-driver scopes and additionally requests `modcontributors`; this extra scope is not added to unrelated moderator actions.

If that capability is unavailable, the moderator can still remove without a reason. After the required OAuth scope is already present, a Reddit HTTP 403 is treated as a moderator-permission/provider rejection rather than triggering another authorization loop.

Missing the incremental `modcontributors` grant is **not** treated as an invalid Reddit account session. The existing base session remains connected; the removal-reason feature reports authorization-required and may launch its owner-bound incremental OAuth flow. Global account state is reserved for genuine session invalidation.

## State and transaction semantics

Remove and reason attachment are deliberately treated as two operations:

1. Subshell sends the non-retrying `/api/remove` mutation using MOD01A2 semantics.
2. Only after Reddit confirms removal does Subshell attempt to attach the selected reason/mod note.
3. If reason attachment succeeds, normal authoritative modqueue reconciliation follows.
4. If reason attachment fails, the queue item remains removed because the primary mutation is already known to have succeeded. Subshell surfaces a bounded `Item removed` warning instead of rolling back or blindly repeating `/api/remove`.

This preserves the MOD01A2 ambiguous-outcome rule: Subshell never repeats a moderator removal simply because a secondary metadata write failed.

## Presentation

The existing Remove confirmation dialog becomes a bounded workflow:

- loading state for reasons;
- explicit authorization action when the reason-list capability needs `modcontributors`;
- `No removal reason` plus configured reason choices when available;
- optional `Mod note` with a 100-character counter;
- clear copy that plain removal remains possible;
- clear partial-success notice if Reddit removed the item but reason/note attachment could not be confirmed.

No artificial reason is fabricated when Reddit returns an empty reason list.

## Deferred successors

- MOD01A2.2: federate actionable moderator queue/report state into `Inbox → Activity` with exact post/comment targets and account-scoped attention state.
- Later moderation slices: spam classification, removal-message/comment delivery, removal reason management, ignore reports, locks, distinguish, mod log, ban/user management.
- MODMAIL remains the separate MODMAIL01 product line.
