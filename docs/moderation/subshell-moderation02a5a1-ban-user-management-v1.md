# MODERATION02A5A1 — Banned-user management

A5 is the larger user-management tranche. A5A1 establishes its first bounded
operational slice: viewing a community's banned-user relationship list and
explicitly banning or unbanning one Reddit account.

## Reddit boundary

A5A1 uses the classic subreddit relationship endpoints:

- `GET /r/{subreddit}/about/banned` with bounded 100-item pages;
- `POST /r/{subreddit}/api/friend` with `type=banned` to ban;
- `POST /r/{subreddit}/api/unfriend` with `type=banned` to unban.

A ban may carry `duration`, `ban_reason`, `note`, and `ban_message`. Duration is
blank for permanent or 1–999 days. Reason is capped at 100 characters, moderator
note at 300, and the user-visible ban message at 1000.

The list and write endpoints use `modcontributors`. Subshell adds a dedicated
incremental `MODERATION_USERS` capability and persisted `moderation_users` owner.
The scope is not added to the daily-driver account grant.

A Reddit HTTP 403 is presented as missing Manage Users / Access permission for
that community, not as an empty list and not as a global account reconnect.

## Mutation semantics

Ban and unban are destructive moderator operations.

- Every request is pinned to active account, community and canonical username.
- The active moderator cannot target their own username.
- A fresh banned-user read is required before dispatch.
- A second concurrent user-management write is rejected instead of queued.
- The write path uses the existing non-retrying HTTP transport.
- Authorization completion reloads only; it never sends a pending ban or unban.
- After an acknowledged or outcome-unknown dispatch, Subshell reads the banned
  relationship again. Only the requested observed state is shown as verified.
- If bounded readback cannot confirm the state, the outcome remains unknown and
  no automatic retry is offered.

The targeted status scan first asks Reddit for the one user (`user=<name>` on
the list endpoint, MODUSERS01, #125), so a list of any size is settled by one
request; ban, mute and approved-user checks all do this. If the filter were
ignored, the scan continues page by page, bounded to ten 100-item pages. If
absence cannot be proved within that bound, the action fails closed rather than
guessing.

## Presentation

Community `Mod tools` gains `User management`. The Material sheet contains
Refresh, Ban user, the current banned-user list with paging, and explicit Unban
per row. Ban confirmation exposes duration, reason, moderator note and the
user-visible message as bounded fields.

## A5A1R1 runtime presentation polish

Runtime review exposed two presentation defects before any ban/unban write smoke:

- the primary Ban user action could remain visually disabled after the banned-user
  list had successfully loaded because a settled read did not explicitly clear the
  transient authorization flag;
- the multi-field ban form inside an AlertDialog resized with the Android IME and
  could move the confirmation actions out of view.

A5A1R1 settles the transient authorization flag on loaded and failed reads, uses
a prominent Material tonal Ban user action, presents banned accounts as Material
surface/list rows with labelled moderator-note and ban-age metadata, and moves the
form-heavy ban editor into the existing modal sheet with IME/navigation-bar
padding. The simple Unban confirmation remains an AlertDialog.

No Reddit endpoint, OAuth scope, mutation, retry, preflight or readback semantics
change in A5A1R1.

## A5A1R2 searchable targets and identity avatars

Runtime review of A5A1R1 showed that a blind username field is unnecessarily
error-prone for a destructive moderator action. A5A1R2 reuses Subshell's existing
authenticated Reddit People search state and transport rather than adding a
parallel user lookup stack.

- Ban target search starts after a 300 ms debounce for 2–24 character queries.
- At most six matching Reddit users are presented with avatar, username and
  available profile description.
- Selecting a result replaces the search list with a selected-target identity
  surface and an explicit change action.
- Direct exact-username entry remains available as a fallback; search selection is
  not required to submit a valid ban.
- Banned-user rows render a circular identity avatar. The banned relationship DTO
  accepts optional secure Reddit `snoovatar_img` / `icon_img` values and otherwise
  falls back to a deterministic username initial.

The existing SEARCH01C2 People provider remains authoritative for searchable ban
targets. A5A1R2 adds no new search endpoint, OAuth scope, write operation, Room
state, retry rule or ban/unban readback state-machine change.

## Deferred A5 successors

A5A1 does not add:

- moderator roster invites/removal or permission editing (`modothers`);
- approved-user/contributor management;
- mute/unmute management;
- native mod notes;
- bulk ban/unban;
- background polling, notifications or automatic moderation.

These remain separate successors so ban semantics can be runtime verified before
the broader user-management surface grows.
