# MODERATION02A3 — Lock/Unlock and Distinguish/Undistinguish

## Scope

MODERATION02A3 extends the existing moderator review stack with four reversible controls:

- Lock
- Unlock
- Distinguish as moderator
- Undistinguish

The tranche reuses the A1/A2 account binding, exact thing fullname, shared non-queuing write gate, explicit confirmation, fresh preflight, and post-write reconciliation. It does not add mod log, bans, moderator management, sticky behavior, bulk actions, or subreddit configuration.

## Exact Reddit requests

The action network mapping is deliberately narrow:

- Lock: `POST /api/lock` with the exact `id` fullname.
- Unlock: `POST /api/unlock` with the exact `id` fullname.
- Distinguish: `POST /api/distinguish` with `api_type=json`, `how=yes`, and the exact `id` fullname.
- Undistinguish: `POST /api/distinguish` with `api_type=json`, `how=no`, and the exact `id` fullname.

No separate lock or distinguish host is introduced. Existing Approve, Remove, Spam, Ignore reports, and Unignore reports request forms remain unchanged.

## State authority and exact target refresh

A control is offered only after the existing authenticated moderation target loader resolves the exact post or comment with `/api/info?id=<fullname>`.

The moderation DTO preserves three independent pieces of server state:

- `locked`
- `distinguished`
- `can_mod_post`

Unknown or malformed values stay unknown. In particular, an omitted `distinguished` field is not treated as `false`; explicit JSON `null` is the known not-distinguished state. This prevents an incomplete payload from enabling a write control.

The queue projection can display known state, but it is not sufficient authority for a write. Every confirmed action performs another exact-target fetch immediately before dispatch and another exact-target fetch after dispatch.

## Confirmation and concurrency

Every action is pinned to:

- active account stable ID,
- active moderator username,
- community,
- exact `t3_` or `t1_` fullname,
- action,
- current content-control revision.

The confirmation is rejected when account, target, permission, state, actor, or revision changes.

Content controls use the same central moderation write mutex as A1 and A2. A second action is dropped instead of waiting to become a later write. Lock/Unlock and Distinguish/Undistinguish never use the queue-removal optimistic projection or rollback path.

An uncertain dispatch result is reconciled with a fresh target fetch and is never retried automatically. Authorization completion never resumes a pending content-control write: the user must refresh and explicitly confirm again.

## Permission and author boundary

Lock/Unlock require Reddit to report moderator permission and a known current `locked` state.

Distinguish/Undistinguish additionally require the target author to equal the active moderator username. Subshell does not offer Distinguish for another user's contribution, even when the active account moderates the community.

Only the canonical `moderator` distinguished value is interpreted as distinguished by a moderator. Other string values remain unknown and disable the control.

## Presentation

The two existing moderator queue surfaces retain A2's review cards and View reports entry. A compact **Controls** action is added to the same wrapping action group as Approve / Remove / Spam.

The same control dialog can be reopened for:

- a post from the post overflow menu;
- a comment from the comment overflow menu.

The dialog shows the target summary, community, exact fullname, current lock state, current moderator-identity state, explicit confirmation copy, and reversal copy. It uses the existing Material theme and does not introduce a parallel palette.

## Runtime acceptance

Before checkpoint/land, DEV2 runtime acceptance must cover:

1. Open Controls on a test post and test comment from the moderator queue and cancel each confirmation first.
2. Lock one suitable test target, refresh, verify Locked, then Unlock, refresh, and verify Unlocked.
3. On content authored by the active moderator, Distinguish, refresh, verify distinguished state, then Undistinguish and refresh again.
4. Confirm that another author's content does not enable Distinguish.
5. Reopen at least one modified target outside the queue through the post/comment overflow menu and confirm the server state is still represented correctly.
6. Approve / Remove / Spam and Reports remain available as before.

Do not alter legitimate content solely to satisfy the runtime test. If no suitable self-authored moderator target exists, the Distinguish write smoke remains not executed rather than weakening the author boundary.

## Out of scope

MODERATION02A3 does not implement:

- mod log;
- ban/unban or user management;
- sticky/unsticky;
- moderator roster changes;
- subreddit settings;
- bulk moderation;
- automatic retries of moderator writes.
