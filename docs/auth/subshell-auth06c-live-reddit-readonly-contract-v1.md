# subshell AUTH06C live Reddit read-only contract V1

AUTH06C promotes the isolated RedReader compatibility boundary from localhost proof to a deliberately user-initiated live Reddit proof. It does not make the compatibility identity an official subshell API grant.

## Version and scope

- `versionCode = 60`
- `versionName = 0.58.0-auth06c-live-reddit-readonly-dev`
- OAuth mode: `REDREADER_COMPATIBILITY`
- Requested scopes: exactly `identity mysubreddits read`
- Authorization duration: `permanent`
- Write scopes and write endpoints: none

## User-supplied client identity

The OAuth client identifier is entered directly in subshell's settings UI and stored in the existing Android Keystore-protected no-backup vault. It is never accepted through chat, shell arguments, environment variables, ADB extras, source, BuildConfig, manifest, logs, or backups. subshell does not extract a client identifier from RedReader or another installed application.

## Atomic first connection

A new authenticated account becomes active only after the following sequence succeeds:

1. Exchange the single-use authorization code.
2. Resolve the account through `GET /api/v1/me`.
3. Fetch one bounded Best listing through `GET /best?raw_json=1&limit=25`.
4. Write that listing to Room under the authenticated account's scoped
   `account|authenticated|…|main|home|best` query key.
5. Persist the provider-qualified account session and activate the account.

A failure during the read-only feed bootstrap deletes the unbound OAuth session and exposes only a sanitized retryable/non-retryable UI state. No response body, token, authorization code, client identifier, or Reddit password enters UI state or host logs.

## Read-only and traffic boundary

- No posting, commenting, voting, saving, subscribing, moderation, or profile-write scope is requested.
- `mysubreddits` is read-only and is used only to load the active account's
  subscriptions through `GET /subreddits/mine/subscriber`.
- No background polling is introduced by AUTH06C. Live listings fetch another
  bounded page only after explicit refresh, a near-end viewport event, or when a
  locally filtered page contains no visible posts and a remote cursor remains.
- The proof fetches one page with a maximum of 25 posts.
- Direct image and preview URLs already present in that listing are mapped into
  the cached post payload. The payload decoder remains compatible with the
  previous 23-field Room representation.
- Image bytes are fetched only for visible cards over credential-free HTTPS,
  with redirects disabled, a 12 MiB compressed-body ceiling, bounded bitmap
  decoding, cancellation, and a process-memory cache. Image requests never
  receive the Reddit OAuth bearer and do not add paging or polling.
- LIVE01 bootstraps the active authenticated account's cached `Home · Best`
  page. FEED02 extends user-selected runtime listings without changing that
  conservative connection bootstrap. Switching accounts invalidates the
  in-memory pager; local fixtures retain their independent account-scoped queries.
- The former global `main|home|best` page is copied once to the active
  authenticated account when upgrading an already-proven installation. The
  legacy page is retained for rollback and proof continuity.
- Authenticated accounts are explicitly read-only in UI: post/comment composers,
  votes, saves, joins, follows, and poll votes remain disabled. Pull refresh and
  bounded read-only viewport paging are available; fixture accounts retain their
  prototype mutations.
- The existing shared HTTP boundary retains bounded calls, sanitized failures, rate-limit metadata, and Room as UI source of truth.
- The exact RedReader compatibility User-Agent is applied to token, identity, and listing calls.

## FEED02 live listing matrix

- Authenticated Home exposes Reddit `best`, `hot`, `new`, `rising`, `top`, and
  `controversial`. Community feeds expose `hot`, `new`, `rising`, `top`, and
  `controversial`; the former Community `Best` alias migrates to `Hot`.
- Every real listing has its own account-scoped query key and Room page. Changing
  sort loads that listing and preserves the order returned by Reddit.
- `top` and `controversial` send one explicit global time period: `hour`, `day`,
  `week`, `month`, `year`, or `all`. The persisted selection is shared by Home
  and every community, defaults to `all`, and is part of the account-scoped
  query key so periods cannot reuse one another's cached pages.

## LIVE05 account-scoped post search

- Authenticated search uses Reddit's read-only `[/r/subreddit]/search` listing
  with `type=link`, relevance order, and an optional `restrict_sr=true` community
  boundary.
- A search is sent only when the user submits or explicitly refreshes it. Typing
  does not issue requests. After submission, another bounded page loads when the
  viewport approaches the end; no permanent manual paging control is required.
- Every request is limited to 25 posts and uses only the stored listing cursor.
  A page hidden entirely by local state, or too short to fill the unscrolled
  viewport, may advance to the next stored cursor so local filtering cannot
  strand the feed.
- Automatic viewport-fill/filter recovery sends two pages back to back per
  query and Room generation. If the feed is still empty or too short, loading
  continues by itself with a 1/2/4/8 s backoff while no page adds a visible
  post; a page that does resets the backoff, and the listing's end stops it.
  There is no manual `Load 25 more` control (FEEDS01, 2026-09-27, replaced the
  former pause at two pages). Changing feed or explicitly refreshing it starts
  a new budget.
- Near-end paging prefetches with six items remaining, issues at most one request
  per active scroll, and applies a 1.5 second minimum request interval. There is
  no background polling.
- An in-progress request deduplicates only the same query. Changing community,
  sort, or search target cancels the obsolete work and starts the latest target.
- Search query keys include the authenticated account plus the normalized query
  and optional community. Switching accounts invalidates the in-memory pager;
  Room remains the account-scoped source of truth for restart and offline reads.
- Authenticated LIVE05 exposes post search only. Local fixture accounts retain
  their existing offline post, community, and user search surfaces.

## SUBS01B read-only community discovery

- The authenticated Subs surface opens on the joined-community list. A compact `Explore communities` launcher remains above that list. The separate `Subs Explore shortcut` setting is `Automatic` by default, showing the top-app-bar Explore action only after the launcher leaves the viewport; `Always visible` and `Hidden` remain explicit alternatives. This shortcut does not override the independent global top-bar scroll behavior. Explore is a canonical secondary route, not a peer primary tab and not an inline expansion.
- The Subs search action is contextual: its accessible label is `Search communities`, it opens the same Explore route, and—when the existing `Open keyboard when entering Search` preference is enabled—focuses the community field. The Explore action opens the same route without forcing search focus.
- The default Explore result is one bounded `GET /subreddits/popular` listing. Reddit activity order is preserved. Visible topic chips use `GET /subreddits/search` with `sort=activity`; manually submitted names use the same endpoint with `sort=relevance`, `show_users=false`, and at most 50 communities.
- Queries from 2 through 25 characters start a 275 ms debounced, latest-request-wins `GET /api/subreddit_autocomplete_v2` request. It asks for at most eight subreddit suggestions, excludes profiles, and forwards the existing NSFW preference through `include_over_18`. Matching joined communities are surfaced immediately from the active subscription snapshot and merged case-insensitively with cached or remote suggestions.
- Autocomplete, Popular, topic, and submitted search targets have distinct account-scoped Room cache identities. The ordered community payload, icon URI, and `over18` flag survive restart and offline use without a schema change beyond the existing Room schema 4 discovery tables.
- Popular and topic discovery omit communities already present in the active subscription snapshot. Autocomplete and submitted search keep those communities visible with a `Joined` badge so an explicit lookup never appears broken. NSFW communities remain hidden unless `Show NSFW posts` is enabled.
- Community avatars prefer Reddit's secure `community_icon`, then `icon_img`, use the shared remote image cache, retain a fixed circular frame while loading, and fall back to the existing deterministic letter avatar. The shared avatar is used by joined, Explore, autocomplete, and community-search rows.
- Malformed or partially missing subreddit children are skipped instead of invalidating the complete response. Autocomplete failure retains local or cached suggestions with a compact retry state; a failed broader search retains its quick suggestions. New queries cancel obsolete work so an older response cannot replace a newer draft.
- Returning from Explore restores the exact joined-list position; returning from a community opened inside Explore restores the Explore query and list position. The inert legacy `discover_expanded` preference still round-trips through settings backup for compatibility, but it never owns rendering or navigation.
- Explore remains read-only. Selecting a result opens the community; no subscribe scope, `/api/subscribe`, vote, save, or other write endpoint is added. All requests reuse the existing exact `identity mysubreddits read` grant, bounded HTTP client, sanitized diagnostics, explicit retry actions, and no background polling or location-based recommendation.

## COMMENTS02 bounded comment expansion

- Authenticated comment order is selected by Reddit, using the exact server
  sorts `confidence`, `top`, or `new`. Each sort has an independent
  account-and-post-scoped Room cache; fixture comments retain their local sort.
- Reddit `kind=more` entries are preserved as explicit controls rather than
  discarded. A tap performs one read-only `GET /api/morechildren` request for
  at most 25 advertised child IDs with `limit_children=true`.
- No more-comment request is automatic. Only one expansion can run at a time,
  failures remain attached to the tapped row, and a retry requires another tap.
- Large or deeply nested branches use a focused `Continue thread` view so the
  full post does not grow without bound. Android back returns to the complete
  thread, and the existing single scroll container remains available to system
  scroll capture.
- Expanded trees replace only their originating `kind=more` node and are written
  back to the matching account, post, and sort cache. Remaining child IDs stay
  represented by a smaller manual expansion node across restart and offline use.
- The endpoint uses only the existing `read` grant. COMMENTS02 adds no comment,
  vote, save, subscribe, moderation, or other write operation.

## Runtime proof lanes

The deterministic localhost lane performs token exchange, identity resolution, one Best listing fetch, Room write, account registration, and process-reopen verification without live Reddit traffic or a live client identifier.

The manual live lane is locked to DEV 1
(`io.github.brealorg.subshell.debug.dev1`) and resolves an installed
instrumentation runner targeting that exact package before touching runtime
state. It never receives the client identifier. The user enters it in the app,
authorizes inside subshell's isolated Reddit sign-in view, and the view intercepts
`redreader://rr_oauth_redir` before it can be treated as a webpage. The lane then
runs a no-network inspector after process restart. That inspector verifies only
sanitized facts: RedReader mode, account/session presence, exact read-only scopes,
and a non-empty account-scoped Room page of at most 25 posts.

## Preserved boundaries

AUTH05E2's production HTTPS App Link remains unchanged. AUTH06B's explicit compatibility opt-in, exact custom-scheme callback, mode fingerprint, encrypted no-backup configuration, and lack of bundled client identity remain mandatory.
