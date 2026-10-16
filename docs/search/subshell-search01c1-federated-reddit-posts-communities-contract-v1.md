# SEARCH01C1 — federated Reddit posts + communities

SEARCH01C1 turns the SEARCH01B workspace into the first submitted federated result surface.

## Product contract

- Global authenticated Search opens in `All` instead of a Posts-only scope.
- `All` submits one canonical query and concurrently owns:
  - the existing Reddit post search / paging pipeline;
  - Reddit full community search through `/subreddits/search` with relevance order.
- Community autocomplete remains a draft-only provider. It must not overwrite the submitted
  community provider after the query is submitted.
- Submitted community results are retained by submitted query/revision so editing a new draft
  cannot replace the visible submitted result identity.
- `All` renders a bounded Communities section before Posts. `See all` narrows the existing
  submitted query to Communities without introducing a second search screen.
- Posts retain FEEDSTABILITY01 cursor-aware paging and canonical search query identity.
- Current-community Search remains Posts-only because Reddit community search is global rather
  than scoped to a single subreddit.
- Authenticated People search remains deferred to SEARCH01C2. SEARCH01C1 does not pretend local
  fixture users are live Reddit user search results.
- AppSearch/offline full-text remains deferred to SEARCH01D.
- Google/web remains a separate future provider boundary and is not mixed into Reddit ranking.

## API boundary

Reddit `/subreddits/search` is treated as a first-class provider distinct from Reddit link search.
The existing authenticated community-discovery loader, cache and latest-request-wins coordinator
are reused rather than introducing a parallel transport.

## Navigation

SEARCH01B's draft → submitted results → hub → origin Back ladder remains authoritative.
Provider filters are shown only after a stable submitted result set exists.
