# subshell DATA-03 — persistent offline cache contract

DATA-03 replaces DATA-02's process-only page cache with a Room-backed persistence layer while
preserving the synchronous repository contract used by the local prototype.

## Ownership

`PersistentSubshellRepository` decorates a transport-neutral `SubshellRepository`.

```text
SubshellState / RepositoryPager
            |
PersistentSubshellRepository
       |                 |
remote/fake delegate   RoomSubshellCacheStore
```

The UI and pager do not access Room, DAOs, entities, database paths, or Android `Context`.

## Database

`SubshellCacheDatabase` is a singleton production Room database:

- Room `2.8.4`;
- KSP2 `2.3.6` code generation;
- schema version `4`;
- exported schema under the module build directory;
- explicit `Migration1To2`, `Migration2To3`, and `Migration3To4`;
- database name `subshell-offline-cache.db`.

The database contains:

- normalized post payload rows;
- query metadata;
- ordered query/post entries;
- communities;
- account-scoped ordered subscription snapshots;
- account-and-query-scoped ordered community discovery snapshots with NSFW metadata;
- users;
- inbox items;
- root comment trees keyed by cache scope plus comment ID;
- profile comments keyed by username scope plus comment ID;
- local mutation records.

Nested post and comment structures use a length-prefixed, version-local codec. IDs, enum values,
newlines, delimiters, poll state, galleries, crossposts, inline media, code samples and message
conversation data round-trip without depending on JSON or network DTOs.

## Query persistence

A query row owns:

- canonical DATA-02 query key;
- generation;
- remote `nextCursor`;
- last successful refresh time;
- last access time;
- terminal state.

A query entry owns:

- query key;
- post ID;
- stable position;
- generation.

Refresh and initial page replacement are single Room transactions:

1. upsert post objects;
2. delete the previous ordered entries;
3. insert the new ordered generation;
4. update query metadata;
5. run LRU query eviction and orphan cleanup.

Append is also transactional. Existing post IDs keep their first position; only unseen stable IDs
receive new positions.

## Cold start and offline policy

For initial loads, a fresh persisted page is returned before invoking the delegate. Cached append
ranges are also returned before invoking the delegate.

When persisted content is stale, the delegate is attempted. Offline or error results retain the
persisted page and cursor as stale content. DATA-02 remains the owner of visible initial, refresh,
append, retry and stale load states.

The default freshness interval is fifteen minutes. Tests use an injected deterministic clock.

## Eviction and clearing

At most 24 post-query identities are retained by default. Least-recently-accessed queries are removed,
then remote post rows with no query entry are deleted. Community discovery retains at most 12
account-and-query snapshots and evicts the least recently accessed snapshot transactionally.

`clearRemoteCache()` removes:

- query entries;
- query metadata;
- cached posts;
- cached communities;
- cached subscription lists and entries;
- cached community discovery lists and entries;
- cached users;
- cached inbox;
- cached comments;
- cached profile comments.

It does not remove local mutations, media downloads, media cache files, settings preferences or
settings backup snapshots.

## Local mutation boundary

Created posts, local comments, saves, votes and poll votes are stored independently of the remote
query cache. The Room records are the persistent local source of truth for this work.
Remote-cache eviction therefore cannot remove local actions.

A new repository instance runs a dependency-aware replay plan before serving reads:

1. created posts;
2. comments, after all created-post parents are available;
3. saves, votes and poll votes.

Timestamp and mutation key are deterministic tie-breakers only inside a dependency tier. They never
place a child mutation ahead of its parent.

Each replayed record produces an explicit result:

- `APPLIED`;
- `ALREADY_APPLIED`;
- `DEFERRED`;
- `FAILED`.

The repository retains the latest replay summary. Malformed payloads, unknown mutation kinds and
missing parents cannot disappear silently. Replaying the same records is idempotent: created posts
and comments are detected before insertion, while saved, vote and poll state use setter semantics.

The current delegate replay is a prototype adapter for the bundled local backend. DATA-03 does not
schedule network synchronization. When NET01 adds a remote Reddit data source, the same persistent
queue can be drained as unique, constrained `WorkManager` work with retry/backoff; that network
worker is intentionally outside the DATA-03 scope.

Existing account-action SharedPreferences remain authoritative for UI account state. DATA-03 does
not migrate or delete those preferences.

## Backup policy

The Room database is explicitly excluded from cloud backup and device transfer because remote
cache data is reconstructable. Existing settings and account preferences retain their previous
backup behavior.

## Runtime proof

The device runtime uses two separate instrumentation invocations:

1. write two ordered pages plus five local mutations;
2. force-stop the app and test packages;
3. open a new repository with an offline delegate;
4. dependency-sort five equal-timestamp mutations and replay the created post before its comment;
5. record explicit replay outcomes and prove a second replay is idempotent;
6. reset the fake collection-request history after fresh constructor replay, then hydrate initial and append post pages, including persisted generation and cursor, without delegate post-page requests while fresh;
7. open the stale repository, reset constructor-replay request history again, then prove stale offline fallback performs exactly one delegate post-page request;
8. clear remote cache and prove all five local mutations remain;
9. validate the version-1-to-version-4 migration and current Room schema.

DATA-03 does not add Reddit networking, OAuth, remote DTOs or media-download database ownership.
