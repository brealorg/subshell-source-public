# REDDIT01D3A Reddit UI-state terminology contract v1

REDDIT01D3A retires active `LiveReadOnly*` terminology from the authenticated Reddit UI-state and feed request-coordination layer.

Canonical UI-state names are provider-specific `Reddit*UiState` names for feed, Inbox, subscriptions, community discovery, and comments. UI failure enums use explicit `Reddit*UiFailureKind` names so they remain distinct from data and transport failure models.

The feed request coordinator is `RedditFeedRequestCoordinator`. The shared authenticated Reddit paging component is `RedditPagingControl`, and its internal Compose item key is `reddit-paging`.

REDDIT01D3A is nomenclature-only. Feed request ordering, stale-result rejection, paging behavior, cache behavior, Inbox behavior, comments, OAuth scopes, authorization ownership, network endpoints, and Room persistence do not change.

Historical AUTH06C class names, test filenames, proof methods, runtime scripts, and documentation retain `LiveReadOnly` because they describe the historical AUTH06C read-only milestone.

Lowercase active runtime and UI wiring such as `liveReadOnlyFeed`, `liveReadOnlySearch`, `liveReadOnlyFeedQueryKey`, `readOnlyFeedState`, `readOnlyInboxState`, and `onLoadReadOnlyInbox` is deferred to REDDIT01D3B.

Connection state terminology including `SYNCING_READ_ONLY_FEED`, `READ_ONLY_FEED`, and `READ_ONLY_FEED_SYNC` is also deferred to REDDIT01D3B.
