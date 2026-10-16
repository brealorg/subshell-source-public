# MOD01A1 — Moderator directory and mod queue foundation

## Product boundary

MOD01A1 establishes the first authenticated moderator surface in subshell without adding moderator mutations.

For the active Reddit account, subshell loads the communities that account moderates. A community that appears in this account-scoped directory receives a **Mod tools** action in its existing top app bar. The action opens a read-only **Mod queue** sheet for that community.

## Canonical Reddit reads

MOD01A1 uses the classic OAuth API endpoints already supported by subshell's authenticated HTTP boundary:

- `GET /subreddits/mine/moderator` for the active account's moderated-community directory. The listing is paged with Reddit's `after` cursor and is bounded to ten 100-item pages per refresh.
- `GET /r/{subreddit}/about/modqueue` for posts and comments requiring moderator review. Pages contain up to 50 things and preserve Reddit's `after` cursor.

The transport projects only canonical `t3_*` posts and `t1_*` comments belonging to the requested community. Queue rows carry a bounded summary, author, report count and optional permalink.

## Authorization

No OAuth scope is added by MOD01A1. The daily-driver account already carries the two read scopes used here:

- `mysubreddits` for the moderated-community relationship listing.
- `read` for the moderator listing surface.

The loader is account-owner-bound through `AuthAccountSessionCoordinator`; a token from a different active account cannot be used to hydrate moderator state.

Reddit additionally applies subreddit moderator permissions to the mod queue. If Reddit rejects a queue with HTTP 403, subshell explains that the moderator role may not include the **Posts** permission rather than pretending the queue is empty.

## UI and state

- The moderator directory is account-scoped and refreshed when the authenticated active account changes.
- **Mod tools** is not shown merely because a community exists locally; it is gated by a successfully loaded moderator directory for the active account.
- Opening **Mod tools** loads the selected community's mod queue.
- Refresh replaces the current queue page; **Load more** appends a Reddit `after` page and deduplicates by fullname.
- Loading, empty, retryable failure and 403 permission-denied states are explicit.
- MOD01A1 does not add local persistence or a Room migration for moderation state.

## Deferred successors

MOD01A1 is deliberately read-only. It does **not** call `/api/approve`, `/api/remove`, `/api/distinguish`, `/api/ignore_reports`, ban/mute APIs or any other moderator write endpoint.

Approve/remove/spam and related content actions belong to MOD01 successors and will use the existing `modposts` grant and non-retrying write policy. Modmail remains a separate `MODMAIL01` product line.
