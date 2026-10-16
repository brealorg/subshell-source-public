# SEARCH02A1.2 — discovery search hub contract

## Status

SEARCH02A1.2 v9 finalizes the search-intent-only zero-query surface and retires the
now-dead Search-owned Reddit Popular data path.

The Search workspace keeps the SEARCH02A1 account-scoped interaction index and
SEARCH02A1.1 personalized zero-query behavior, then adds Google Trends as a
bounded, fail-soft discovery source. The earlier SEARCH02A1.2 `Popular on Reddit`
post section remains retired from Search.

## Product contract

When the submitted query is empty, Search may show:

1. Recent searches and existing personalized zero-query suggestions when present.
2. `Trending now` backed by Google Trends RSS (`geo=US`), bounded to at most eight
   items.

`Popular on Reddit` is not rendered in Search. Search also does not silently load
or page the Reddit Popular feed for that retired section. Reddit Popular remains a
normal Reddit feed elsewhere in the product and is not deleted or redefined by
this change.

A tap on a trend is a canonical Search submission. It does not open Google and it
does not introduce a parallel result surface.

## Data and privacy boundary

- Google Trends uses the public RSS source and requires no Google authentication.
- Trend loading is fail-soft.
- Trend data uses the existing bounded 30-minute device cache.
- Search owns no dedicated Reddit Popular query key, paging snapshot, load request,
  post projection, or Popular-result composable after v9.
- No Room schema migration is introduced.
- No raw submitted-query/result-page cache is introduced.
- No new Reddit endpoint or OAuth grant is introduced.
- Presentation source does not own raw Google network URLs.

## SEARCH02A1.2 v9 successor rule

The v9 source marker is:

`SEARCH02A12_POPULAR_DATA_PATH_RETIRED_V9`

When this marker is present, the authoritative Search presentation must:

- contain `Trending now` and `Google Trends`;
- not contain `Popular on Reddit`;
- not contain the retired Search-specific `popularFeedQueryKey`,
  `popularPagingSnapshot`, `popularFeedMatches`, `popularLoadRequested`,
  `popularPosts`, `CompactPopularPostResult`, or `RedditMainFeed.POPULAR` path;
- preserve canonical trend submission through the existing Search session; and
- preserve Reddit Popular as a product capability outside Search.

v9 is intentionally a cleanup successor to the accepted v7 runtime UI. It does
not change the accepted zero-query hierarchy; it only removes the unused network,
paging, projection, empty-state dependency, and composable left behind when the
Popular section was retired.
