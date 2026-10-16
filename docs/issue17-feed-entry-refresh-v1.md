# Issue #17 — authenticated feed entry revalidation

Issue #17 reports that opening Subshell or revisiting a frequently viewed
community presents old cached posts until the user manually pulls to refresh.

## Root cause

Authenticated Home/community feeds hydrate their visible paging snapshot from
Room first. This provides fast cache-first presentation.

The entry effect previously issued the authoritative Reddit refresh only when
the hydrated paging snapshot contained zero posts. A successfully cached feed
therefore suppressed network revalidation entirely.

For authenticated query keys, the persistent repository initial read is
cache-backed, so no lower data-layer operation compensates for that UI gate.

## Second report (2026-09-20): refresh on every return

The first fix claimed the entry request with a composition-scoped
`remember(pagingKey)`. Route switching in `SubshellApp` is a `when`, so
opening a post (or any other route/destination) removes Home/Community from
composition; returning created a fresh gate and a new REFRESH. A REFRESH
deletes the query's Room entries and writes the first page, so the paged
list collapsed from N items to 25, the item-key scroll anchor disappeared,
and the feed jumped upwards on every return from a post.

This is the failure PROFILE01A7A4 already fixed for profile history with a
state-owned `ProfileHistoryEntryRefreshGate`. Home/Community now use the
same shape, mirroring Paging 3's `RemoteMediator.initialize()`, which decides
once, when the Pager is created. (Until issue #40 the gate also re-ran after a
15-minute window; that is retired, see below.)

## Correct semantics

Entry is stale-while-revalidate, owned by state:

```text
surface enters with authenticated Reddit feed
→ hydrate/show cached posts immediately
→ preserve existing landing/query eligibility checks
→ state.claimFeedEntryRefresh(pagingKey)  (FeedEntryRefreshGate)
   execute_first_entry: first entry for this query key in this process
   defer_deep:          first entry, but the restored position is past page 1
   skip_reentry:        otherwise (return from a post, tab switch, …)
→ on execute: request one authoritative REFRESH
→ keep cached content visible while request is in flight
→ synchronize refreshed Room snapshot when it lands
```

Cached item count is not a refresh eligibility condition. Composition
lifetime is not an entry condition either: returning to a surface keeps the
paged list and the scroll position.

The gate is keyed by query key (which includes the account), records the
claim before the request is sent (a failed request is therefore not retried
automatically; pull-to-refresh is), and is reset by `invalidatePostPaging()`
when the account directory changes.

Home retains its authenticated landing/subscription suppression conditions.
Community retains its query-key request gate. Ordinary recomposition does not
create a refresh loop because the state-owned claim answers `skip_reentry`.

## Manual refresh

Pull-to-refresh remains unchanged and invokes the same authoritative
`onRefreshRedditFeed` path.

## Runtime evidence

Automatic entry requests emit:

```text
tag=subshell-issue17
entry-refresh surface=home ... decision=execute_first_entry cached=25
entry-refresh surface=community ... decision=skip_reentry cached=60
```

The log records the decision and the cached item count visible when the
claim is made. `cached=N` with N greater than zero on an `execute_*` line
proves that cached content does not suppress refresh; `skip_reentry` after
returning from a post proves that the return did not collapse the list.

Device check for the second report: scroll Home well past the first page,
open a post, return — the position must be kept and the log must show
`skip_reentry`, however long the post was open.

## Refresh on first entry only (issue #40)

Until 2026-09-25 the gate also refreshed a re-entry when the previous automatic
refresh was older than 15 minutes (`execute_stale`). An automatic refresh
rewrites the query to its first page (REFRESH deletes the query's stored
pages), and the old list stays on screen while the request runs. A reader past
page 1 who returned from a post after 15 minutes of reading kept scrolling
and was then thrown ~80 posts back up (reproduced on a device: 131 → 25,
position 105 → 22).

Owner decision: follow Paging 3. `RemoteMediator.initialize()` runs only when
a Pager is created, and a refresh within a session is the user's
(pull-to-refresh). `ISSUE40_REFRESH_ON_PAGER_CREATION_ONLY_V1`: the gate
refreshes a query key once per process (and again after `invalidatePostPaging()`).
`ISSUE40_NO_AUTOMATIC_REFRESH_DEEP_IN_LIST_V1`: a first entry whose restored
position is at or past index 25 (`FEED_AUTOMATIC_REFRESH_DEEP_INDEX`, one
Reddit page), e.g. after process restore, returns `defer_deep` and is not
recorded. The next entry near the top refreshes. Consequence: an app kept in
memory for hours shows new Home posts only after pull-to-refresh.

## Failure classification

- Primary: cached Home/community entry suppresses authoritative refresh, the
  state-owned entry gate is lost, or a return from a post re-fires the
  refresh inside the window.
- Predecessor: an older feed/network/cache contract is red while issue #17
  assertions pass.
- Release-blocking: only when a release being prepared cannot safely ship
  because of the failure.
