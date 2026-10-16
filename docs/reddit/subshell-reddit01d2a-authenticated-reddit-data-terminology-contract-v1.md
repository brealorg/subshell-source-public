# REDDIT01D2A authenticated Reddit data terminology contract v1

REDDIT01D2A retires the active `AuthReadOnly*` and concrete `RedditReadOnly*` data-layer terminology.

The auth/data contracts use the `AuthenticatedReddit*` prefix.

Concrete implementations in the Reddit package use provider-specific `Reddit*` names.

The two naming layers remain distinct so callers such as `AuthApplicationRuntime` can import both an authenticated data contract and its Reddit implementation without simple-name collisions.

The renamed surface includes bootstrap, feed update, comments, subscriptions, community discovery, Inbox snapshots/results/loaders, and the concrete Reddit implementations of those contracts.

REDDIT01D2A is nomenclature-only. OAuth scopes, client modes, authorization ownership, endpoints, cache keys, Room schema, network request parameters, paging behavior, Inbox mutation behavior, and comment mutation behavior do not change.

Historical AUTH06C milestone names, proof scripts, result labels, and documentation remain historical records.

`LiveReadOnly*`, active `readOnly*` runtime/UI wiring, connection phases such as `SYNCING_READ_ONLY_FEED`, and predecessor helper names such as `auth06cReadOnlyFeedRequest` are deferred to later REDDIT01D phases.
