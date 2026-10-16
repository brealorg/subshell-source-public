# SEARCH01C2 — live Reddit People provider

## Product boundary

SEARCH01C2 promotes **People** from the local-fixture compatibility path to a live Reddit provider for authenticated global Search.

The canonical submitted Search workspace is:

1. Communities
2. People
3. Posts

`See all` narrows the existing submitted query to the matching provider scope without creating a second search session.

## Reddit transport

Authenticated People search uses Reddit's read-scoped `GET /api/subreddit_autocomplete_v2` for submitted queries up to 25 characters with:

- `query=<plain submitted query>`
- `include_profiles=true`
- `include_over_18=true`
- `typeahead_active=true`
- `limit=10`
- `raw_json=1`

The profile-aware response is mixed. Community suggestions are `t5` Subreddit things, while profile suggestions are `t2` Account things. A profile Account carries the canonical username on `data.name` and may carry public profile presentation metadata in nested `data.subreddit`. SEARCH01C2 therefore projects `t2` Account rows into People and rejects ordinary community `t5` rows. The older user-profile-subreddit `t5` shape remains supported as a compatibility fallback.

The query is not rewritten into `u_<username>` before transport. Direct `u/foo` handling remains owned by the canonical `RedditTarget` resolver and is separate from People discovery.

Queries longer than the autocomplete endpoint's 25-character limit retain the read-scoped `GET /users/search` fallback for profile title/description search. Both paths consume one bounded page and result paging is not introduced in this phase.

## Session and provider ownership

The existing RedReader-compatible CORE_READ authorization owner remains authoritative. The live People request:

- requires the active authenticated account;
- obtains the access token through `AuthAccountSessionCoordinator`;
- uses a session-bound bearer-token provider;
- performs no new OAuth grant or account selection;
- performs no Room schema migration.

People requests have an independent job/mutex from community discovery so Community and People typeahead can run concurrently without cancelling each other.

SEARCH01C2A1 also gives the two submitted providers independent dispatch ownership. `SearchScreen` launches Community search only when the submitted scope includes Communities and launches People search separately when the submitted scope includes People. The existing community-discovery callback remains the application wiring bridge, but the internal `query + null sort + null autocomplete` form is reserved as a People-only dispatch and is handled before Community-target parsing. Community completion therefore cannot be the trigger that decides whether People runs in `All`.

## Search session semantics

SEARCH01C2A2 unifies authenticated global draft suggestions. While the user types, the draft workspace may show bounded Communities and People typeahead together after the existing 250 ms debounce. Posts remain submitted-only and never run as draft autocomplete.

- Draft typing never uses local fixture People for an authenticated account.
- Live draft People suggestions run only for global `All` or `People` scope, never for Posts, Communities-only, current-community Search, or canonical direct targets.
- Draft People typeahead is bounded to 2–25 characters so it remains on Reddit's profile-aware autocomplete path rather than the longer profile-text search fallback.
- Submitted People results remain retained by account + submitted query + submission revision while the draft changes, but stale submitted Communities/People/Posts are hidden from the draft workspace until the draft is submitted or restored.
- Draft suggestions own the surface only before the first submit or while the draft differs from the submitted query. Once the draft equals the submitted query after submit, Communities/People draft suggestions are suppressed so submitted provider sections are rendered exactly once.
- `u/foo`, `/u/foo`, Reddit profile URLs, and other direct targets continue through `RedditTarget`; they do not enter the People provider transport.
- Search scoped to the current community remains Posts-only.
- Authenticated global Search exposes `All | Posts | Communities | People`.

## Presentation

Live People rows use the username, public profile title/description, and safe HTTPS profile avatar returned by Reddit. Opening a result calls the existing `SubshellState.openUser(username)` route; profile hydration remains owned by the existing User/Profile pipeline.

The Search result list does not issue `/user/{name}/about` once per row.

Authenticated draft `All` orders suggestions as Communities → People beneath the explicit `Search Reddit for …` action. After submit, `All` orders provider sections as Communities → People → Posts. People has `See all` and narrows to `SearchScope.USERS`.

Unauthenticated/local compatibility search may continue to use repository-backed local users.

## Failure and privacy boundary

People search state carries typed configuration/session/HTTP/transport/protocol/storage failures. The query remains part of the existing private Search session/history policy; SEARCH01C2 adds no new persistence and does not log raw People-search query text. Runtime diagnostics use bounded metadata such as query length, result count, typed failure kind, HTTP status and retryability.

NSFW user-profile results follow the existing Search NSFW visibility preference.

## C2B federated result hierarchy

SEARCH01C2B keeps submit semantics aligned with the federated workspace instead of treating submit as an implicit Posts navigation. Explicit submit from `All` preserves `SearchScope.ALL` and requests the submitted result list to start at its top, so Communities remains the first visible provider after Enter or the submit action. Back/restore does not own this explicit-submit scroll request and therefore retains the existing exact-return scroll contract.

The submitted `All` surface is a bounded provider preview rather than three full result lists stacked vertically:

- Communities shows at most 3 rows before `See all`;
- People shows at most 2 rows before `See all`;
- Posts follows immediately after those bounded directory previews;
- dedicated Communities and People scopes retain their full provider result lists.

The draft `Search Reddit for …` action remains equivalent to the keyboard Search action, but its presentation is a compact neutral surface row rather than a dominant primary-container block. No Reddit transport, OAuth, Room, ranking, paging, direct-target, or provider dispatch semantics change in C2B.

## C2B.1 top-field submit action

SEARCH01C2B.1 makes the persistent search field the single explicit submit surface. The trailing action is a Search icon and delegates to the same `onSearch` callback as the IME Search action; direct Reddit targets therefore keep the same canonical dispatch and ordinary queries keep the same submitted-search path. Back remains the leading field action.

The previous trailing Clear X and the standalone `Search Reddit for …` submit row are not rendered. This removes duplicate submit affordances and returns vertical space to the federated provider hierarchy. Clearing remains available through ordinary text editing, while the existing Search back ladder continues to restore a submitted query, clear to the hub, and then leave Search according to its existing state contract.

C2B.1 does not change provider transport, OAuth, Room, ranking, paging, result scope, direct-target parsing, bounded `All` previews, `See all`, or the C2B explicit-submit scroll-to-top behavior.

## Deferred

- People listing pagination beyond the bounded first page
- AppSearch/offline full-text indexing
- Google/web search
- cross-provider server-side ranking

These remain outside SEARCH01C2.
