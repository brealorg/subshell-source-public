# MODERATION02A5A3 — Approved user management

## Product scope

A5A3 extends the existing single-sheet **Mod Tools → User management** workspace with
an **Approved** segment alongside **Banned** and **Muted**.

The feature lists approved users for the selected community, searches Reddit users with
the existing people-search provider, adds a user as an approved contributor, and removes
approved-user status with an explicit confirmation.

## Reddit relationship boundary

- Read: `GET /r/{subreddit}/about/contributors`
- Add: `POST /r/{subreddit}/api/friend` with `type=contributor`
- Remove: `POST /r/{subreddit}/api/unfriend` with `type=contributor`
- OAuth authorization owner remains `MODERATION_USERS`.
- Scope remains `modcontributors`; A5A3 adds no OAuth scope.

Reddit owns the exact participation effect of approved-user status according to the
community's settings. Subshell does not infer broader access guarantees.

## Safety and reconciliation

Approve/unapprove writes use the existing non-retrying write transport. Every mutation has
a fresh relationship preflight and authoritative relationship readback. A transport-unknown
write is never blindly repeated.

After authoritative confirmation, the approved list is projected immediately and then
hydrated once from Reddit so a successful action never requires manual Refresh to appear
or disappear.

Authorization completion reloads relationship state only and never replays a pending
approve/unapprove operation.

## Preserved boundaries

- A5A1 banned-user behavior and R4 single-sheet Back semantics remain intact.
- A5A2 muted-user behavior, verified list projection, and mute/unmute semantics remain intact.
- No Room schema migration.
- No moderator roster invite/removal or moderator permission editing.
- No remote operations or release changes are part of this slice.
