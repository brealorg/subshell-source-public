# REDDIT01D3B active Reddit runtime and UI terminology contract v1

REDDIT01D3B retires active `readOnly*`, `liveReadOnly*`, callback `*ReadOnly*`, and `READ_ONLY_FEED*` terminology from the current authenticated Reddit runtime and UI wiring.

Runtime data dependencies use `redditFeedUpdater`, `redditInboxLoader`, `redditCommentLoader`, `redditSubscriptionsLoader`, and `redditCommunityDiscoveryLoader`.

ViewModel state, jobs, coordinators, functions, MainActivity bindings, SubshellApp parameters, and screen callbacks use provider-specific `reddit*` and `*Reddit*` terminology.

Authenticated remote feed/search booleans use `authenticatedRedditFeed` and `authenticatedRedditSearch`. The account-scoped feed query helper is `authenticatedRedditFeedQueryKey`.

Connection state uses `SYNCING_REDDIT_FEED`, `REDDIT_FEED`, and `REDDIT_FEED_SYNC`.

`AuthConnectViewModel.loadRedditCommunityDiscovery` and the data-layer `loadRedditCommunityDiscovery` helper intentionally share the same simple name in different scopes.

REDDIT01D3B is nomenclature-only. OAuth scopes, authorization ownership/context routing, request ordering, endpoints, request parameters, cache identities, Room schema, Inbox mutation semantics, comment mutation semantics, and paging behavior do not change.

Historical AUTH06C class names, proof methods, scripts, result labels, and documentation remain historical records.

The active predecessor helper identifiers `auth06cReadOnlyFeedRequest` and `live02ReadOnlyFeedRequest` remain temporarily preserved for final REDDIT01D cleanup because their names encode historical milestone provenance rather than current UI or runtime capability policy.

`ByteBuffer.asReadOnlyBuffer()` in `RemoteImageLoader` is unrelated Java/NIO API usage and is explicitly preserved.
