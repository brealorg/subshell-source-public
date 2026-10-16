# MODERATION02A4 — Moderator activity log

MODERATION02A4 adds a read-only Reddit moderation-history surface to the existing
Community → Mod tools workflow.

## Canonical Reddit transport

The feature reads:

- `GET /r/{subreddit}/about/log`
- `limit=100`
- `raw_json=1`
- `after=<ModAction ID>` for paging

The paging token is preserved exactly. It is not lowercased because a Reddit
`ModAction` ID is an opaque cursor.

The response is projected into a bounded product model containing the action ID,
community, moderator, action type, target context, optional description/details,
and creation time. Unknown response fields are ignored.

## Authorization

`/about/log` uses the OAuth `modlog` scope. Subshell therefore adds a dedicated
incremental capability:

- OAuth capability: `MODERATION_LOG`
- authorization owner: `moderation_log`
- scopes: existing core read scopes + `modlog`

`modlog` is deliberately not added to the normal daily-driver account grant.
Opening Mod log requests the additional capability only when required.

Authorization is pinned to the active account. A completed callback reloads the
same community. Account changes fail closed and stale results are not projected.

## Presentation

Community Mod tools exposes a `Mod log` action. Opening it dismisses the queue
sheet and shows a Material bottom sheet for that community.

Each row shows a product-facing action label, moderator, age, and available target
context. Refresh is explicit. Older pages are requested when the paging footer
becomes visible.

The surface performs no moderation writes and does not alter A1/A2/A3 actions.

## Boundaries

MODERATION02A4 does not add:

- moderator action writes;
- mod-log filters by moderator/action type;
- aggregate `/r/mod/about/log`;
- local persistence or Room schema changes;
- background polling or notifications;
- target navigation from log rows.

Those can be considered separately after the basic read path is runtime verified.

## Compact Mod Tools child-header parity

The Mod log presentation uses the same compact child-header hierarchy as the other Mod Tools workspaces: leading Back navigation when embedded, a one-line title and community subtitle protected by ellipsis, and a trailing refresh icon action. The decorative history icon remains on the Mod log entry button where it identifies the destination, but it is not repeated inside the child header.

This is presentation-only. Mod log transport, OAuth scope, paging, account and community pinning, and read-only behavior are unchanged.
