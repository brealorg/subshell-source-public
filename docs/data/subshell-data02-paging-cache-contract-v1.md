# subshell DATA-02 paging and cache contract

DATA-02 turns the DATA-01 repository boundary into a deterministic page owner. It remains local and
in-memory: Reddit transport belongs to NET-01 and persistent database storage belongs to DATA-03.

## Query identity

Every post-bearing surface owns a stable `RepositoryQueryKey`:

- main feed: source and sort,
- community: normalized community name and sort,
- search: origin destination, origin community, query, scope and area,
- user: normalized username, profile tab and activity tab,
- library: selected tab,
- you: selected tab.

Changing any identity component selects another cache entry instead of mutating the previous query.

## Page state

`RepositoryPagingSnapshot` keeps the loaded items, next cursor, cache generation and three independent
load states:

- initial,
- refresh,
- append.

Only append consumes the current cursor. Initial and refresh always request the first page.

## Merge and refresh policy

Append merges by stable item ID through insertion-ordered maps. Existing order is retained and a
backend page that repeats its previous boundary item cannot duplicate the UI row.

A successful refresh replaces the loaded generation, resets the cursor to the refreshed first page
and clears stale/error state. Refresh failure keeps the existing items and marks them stale.

## Offline and error policy

Initial offline/error results may expose a cached first page. Refresh and append failures never erase
already rendered content. Failed append keeps its original cursor so retry requests the same page and
cannot skip data.

`lastFailedOperation` makes retry deterministic for initial, refresh and append independently.

## End-of-feed policy

A null `nextCursor` is terminal. Further append requests are ignored and do not reach the repository.

## UI ownership

`SubshellState` owns keyed `RepositoryPager<Post>` instances. Screens receive immutable snapshots and
can only request refresh, append or retry through state methods. Compose screens do not access the
fake backend directly.

`RefreshableLazyColumn` delegates pull-to-refresh to the current query and triggers append near the
viewport end. Paging status and retry are rendered by a shared footer.

## Navigation and scroll invariants

The existing V2C `FeedScrollKey` remains the scroll owner. Paging keys mirror the same identity
components but do not replace the anchor store. Stable IDs therefore preserve the visible anchor
through append, deduplication and refreshed ordering.

## Deliberate exclusions

- no Room or other persistent database,
- no Reddit networking,
- no OAuth,
- no background synchronization,
- no changes to media runtime behaviour.
