# COMMUNITYINFO01A1 — typed community context transport foundation

## Purpose

COMMUNITYINFO01A1 establishes the read-only transport/domain foundation for richer
community context without changing the accepted `CommunityScreen` product surface yet.

The existing community identity path already loads `/r/{subreddit}/about` and owns
the compact `public_description`, subscriber count, icon, banner and accent used across
Subshell.

A1 reuses the same read-only `/about` resource for its richer raw `description`
projection without expanding the shared `Community` identity model. It also adds typed,
bounded projections for:

- `GET /r/{subreddit}/about` -> raw sidebar/about Markdown from `data.description`
- `GET /r/{subreddit}/about/rules`
- `GET /r/{subreddit}/about/moderators`

These are kept outside presentation source and are intended for a later authenticated
loader/UI-state successor.

## Domain boundary

`CommunityInfoSnapshot` is a sidecar domain rather than an expansion of the shared
`Community` identity model. This avoids bloating discovery/subscription identity rows
and avoids a Room schema migration.

The sidecar contains:

- full sidebar Markdown
- ordered subreddit rules
- moderator usernames and their reported permissions

All remote collections and strings are bounded before entering product state.

## OAuth boundary

A1 adds no OAuth scope and does not modify authorization/session policy.

About-description, rules and moderator-list reads are within the existing Reddit read boundary.
Wiki is deliberately not included in A1: Reddit documents wiki reads such as
`/r/{subreddit}/wiki/pages` under `wikiread`, which is not part of Subshell's current
daily-driver scope set.

User-flair context is also deferred. Existing daily-driver accounts have `flair`, but
COMMUNITYINFO01 should not make the base community-info surface depend on an optional
write-oriented capability.

## Persistence boundary

A1 adds no Room entity, migration, preference storage or portable backup data.
Persistent/offline policy belongs to the runtime successor once actual payload behavior
has been verified.

## Deferred successors

- A2: authenticated loader, latest-request/account ownership, fail-soft UI state.
- A3: CommunityScreen `About` presentation for sidebar, rules and moderators.
- B1: decide whether wiki warrants adding/authorizing `wikiread`, then add wiki index/page navigation.
- B2: user-flair presentation/editing using the existing flair capability where available.

## Regression boundary

A1 does not change feeds, membership, favorites, Search, Inbox, profile history,
composer flair, Room schema, OAuth scopes, navigation or DEV1.
