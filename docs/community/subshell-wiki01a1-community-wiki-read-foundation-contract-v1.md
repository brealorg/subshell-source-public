# WIKI01A1 — community wiki read foundation

## Purpose

WIKI01A1 implements the deferred COMMUNITYINFO01 wiki-read foundation without
changing Community UI yet.

The existing Community About sidecar remains responsible for sidebar Markdown,
rules and moderators. Wiki is a separate navigable sidecar because one community
may expose many wiki pages and page loads occur independently.

## OAuth and authorization

Wiki reads use the dedicated Reddit OAuth scope `wikiread`.

The capability scope is exactly:

`CORE_READ + wikiread`

`wikiread` is intentionally **not** added to `dailyDriverScopes`. Existing users
are not silently widened. A missing grant is reported as
`AuthorizationRequired`, and a later presentation slice may launch the existing
incremental capability authorization flow.

The persisted logical authorization owner is:

`COMMUNITY_WIKI_READ` / `community_wiki_read`

No wiki write scope or endpoint is introduced.

## Transport

WIKI01A1 adds typed GET transport for:

- `/r/{community}/wiki/pages`
- `/r/{community}/wiki/{page}`

The index is bounded and case-insensitively deduplicated while preserving API
order. Wiki page paths are normalized as relative paths and reject traversal,
query, fragment, backslash and control-character shapes before URL construction.

Page Markdown and revision metadata are bounded before entering domain state.

## Failure semantics

A missing incremental OAuth scope is a typed authorization requirement.

HTTP 403 and 404 from an already authorized wiki request are classified as
`UNAVAILABLE`. This covers private/disabled/missing legacy wiki resources without
misrepresenting them as a generic transport failure.

Other HTTP, transport and protocol failures remain typed and retain bounded
failure metadata.

## Runtime

The app runtime constructs a dedicated `COMMUNITY_WIKI_READ` OAuth selection,
generic owner-capable authorizer and authenticated wiki loader.

The loader is pinned to the active authenticated account, requires the exact
wiki-read capability configuration and uses the existing session-bound bearer
token provider.

## Persistence and product boundary

WIKI01A1 adds no:

- Room entity or migration;
- preference or backup format;
- Community UI;
- wiki deeplink/navigation;
- wiki edit, settings, moderator-wiki or contributor writes;
- Inbox/DEV1 behavior.

WIKI01A2 owns user-facing wiki navigation, incremental authorization presentation
and page rendering.
