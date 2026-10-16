# MODERATION02A1 — Explicit Spam action in existing moderation queues

## Scope

Add an explicit Spam action for both post and comment targets to the existing
Inbox aggregate moderation queue and the per-community Mod tools queue.
The canonical action is `RedditModeratorAction.SPAM`. It submits exactly one
classic `POST /api/remove` form with `id=<t1|t3 fullname>` and `spam=true`.
The target is always the item's community, never the aggregate `mod` label.

Approve remains `POST /api/approve` with `id`. Ordinary Remove remains
`POST /api/remove` with `id` and `spam=false`. Existing Remove confirmation,
optional removal reasons and moderator note handling are unchanged.
Spam requests cannot carry a removal reason or moderator note. The existing
writer attaches that metadata only to a successful ordinary Remove.

Reference: Reddit's API documentation, `POST /api/remove`, `modposts` scope,
https://www.reddit.com/dev/api/#POST_api_remove (checked 2026-09-07).

## Confirmation and concurrency

Both queue surfaces use one shared `ModeratorSpamAction` component.
The first tap only creates an immutable pending request and opens a dialog.
The dialog names the community and exact target fullname, shows its summary,
and explains the difference from ordinary Remove. Cancel and Back send nothing.
Only the explicit `Mark as spam` button submits the pinned request.
The pending request is consumed before dispatch to reject a stale second click.

Pending confirmation state is keyed to account, community, fullname and kind.
A new account or target discards it; it is intentionally not persisted across
UI recreation. Confirmation revalidates those identities and the busy state.
The existing coordinator checks the actual active account before invoking the
writer, independently of UI checks.

The existing coordinator's suspending mutex previously serialized callers by
waiting. Its mutation entry now uses `tryLock`: extra calls during an in-flight
write are dropped, not replayed later. This applies to all three moderation
actions and is covered by a concurrent two-request regression test. The lock
is released in `finally`, including coroutine cancellation.

## Authorization, results and reconciliation

Reuse the existing `moderation_action` authorization owner, `modposts` scope,
configured non-retrying write call factory and account-bound writer.
No new OAuth scope, login flow or automatic network retry is introduced.
Existing optimistic queue removal, definitive-failure rollback and
success/outcome-unknown queue reconciliation are preserved.
An outcome-unknown result is never kept retryable, even if a writer supplies
an inconsistent retry flag. The UI continues to surface authorization,
permission-denied and uncertain-outcome states through the existing notices.

No change to HTTP response parsing, global JSON settings, queue transport,
Room, persistence, route state, Modmail or ordinary feed-card actions.
Ignore reports, Lock/Unlock, Distinguish, bans and modlog remain out of scope.
Spam classification is a real remote moderation operation, not a local filter.

## Tests and contract ownership

New behavioral tests cover exact forms for both target kinds; unchanged
Approve/Remove forms; rejected metadata on Spam; invalid or mismatched target
identity; confirmation account/target/phase checks; one-shot success;
permission-denied rollback; authorization-required continuity;
outcome-unknown no-retry; busy-call rejection; and lock release on cancellation.
Presentation tests cover both shared callers and the explicit-confirm-only path.

The new primary contract is registered in the existing source gate and does
not invoke predecessors internally. The preview driver runs MOD01A22 once,
which already runs MOD01A21, MOD01A2 and MOD01A1. It reports primary and
predecessor failures separately and never bypasses either for installation.
Full release qualification is not performed by the preview driver.

## Runtime acceptance

1. In DEV2, open Inbox > Mod > Queue and community Mod tools. Existing Approve
   and Remove remain visible; Spam opens the shared confirmation for the
   correct post/comment. Cancel sends no action and leaves the item untouched.
2. Confirm Spam only on content that should actually be classified as spam
   in a community the tester moderates. Verify the exact item through Reddit's
   own moderator view after the authoritative refresh. Check both post and
   comment paths when appropriate test content is available.
3. Retest ordinary Remove and its optional reason separately from Spam.
   Do not classify legitimate content as spam solely to exercise the button.
4. Permission denial must remain visible; uncertainty must reconcile rather
   than silently resend. Local unit tests cover these failure paths without
   generating remote actions.

Build/install success alone is not runtime acceptance. No checkpoint, land,
push, release creation or automated moderation write is part of the preview.
