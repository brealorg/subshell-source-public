# subshell NET02 — network-to-Room synchronization contract V1

## Purpose

NET02 turns the secure asynchronous HTTP boundary from NET01 into a pull-based offline-first
synchronization path for post listings.

The phase follows Android's current data-layer guidance:

- Room remains the canonical and exclusive source of data read by higher layers.
- The repository/synchronizer owns both the network and local data sources.
- Successful network pages are committed to Room before the synchronization result is reported.
- Network failures never replace or clear an existing local snapshot.
- Database changes that belong to one page are atomic.

NET02 does not connect the UI directly to network data. It does not introduce Reddit OAuth,
background scheduling, remote writes, or live Reddit traffic.

## Boundaries

```text
localhost HTTPS fixture
        ↓
RedditListingNetworkDataSource
        ↓
RedditPostFeedNetworkSource
        ↓
OfflineFirstPostFeedSynchronizer
        ↓
RoomSubshellCacheStore transaction
        ↓
Room readback / canonical feed snapshot
```

The UI and domain presentation layers still have no OkHttp, serialization, Reddit DTO, or sync
imports.

## Pull synchronization

`OfflineFirstPostFeedSynchronizer.synchronize()` is a suspend API and is main-safe:

- work is moved to an injected I/O dispatcher;
- one synchronization operation is serialized at a time;
- the network source returns a typed `NetworkResult`;
- only `NetworkResult.Success` may enter a Room write transaction;
- the synchronizer returns counts, cursor metadata, generation, and rate-limit metadata;
- it never returns the network page as application content.

The feed data itself is read back from `RoomSubshellCacheStore.readPostFeedSnapshot()`.

## Cursor policy

The Room query metadata stores the opaque remote continuation cursor.

- `INITIAL` and `REFRESH` must not carry a cursor.
- `APPEND` must carry the exact remote cursor currently stored for that query.
- a terminal query rejects append before any network request;
- a stale or mismatched cursor rejects append before any network request;
- opaque remote cursors are never interpreted as local numeric cache offsets.

This prevents stale, duplicated, or out-of-order network pages from being committed.

## Atomic write policy

A successful network page is written in one Room transaction:

1. upsert post payloads;
2. replace query entries for initial/refresh, or append stable-ID-deduplicated entries;
3. write query generation, remote continuation cursor, terminal state, and successful receipt time;
4. invoke the transaction boundary observer used by deterministic rollback testing;
5. apply query LRU and orphan cleanup;
6. commit.

If any step throws, Room rolls back the complete page. The synchronizer reports a sanitized
`StorageFailure`, and the prior local snapshot remains readable.

The observer defaults to a no-op and is internal to the data layer. It does not alter production
behavior.

## Refresh and append semantics

- Initial sync creates the first local generation.
- Append keeps the current generation, preserves the original successful-refresh timestamp,
  deduplicates stable post IDs, and may update the payload for an existing post.
- Refresh replaces query membership atomically and increments the generation.
- A terminal network page stores a null remote cursor.
- The network response receipt timestamp is used for initial/refresh freshness metadata.
- The Room schema remains version 2; NET02 requires no migration.

## Failure semantics

Typed HTTP, transport, and protocol failures are returned without response bodies, URLs, tokens,
or exception messages.

- HTTP retryability and `Retry-After` metadata are preserved.
- Transport retryability remains owned by NET01.
- Protocol failures remain explicit.
- Every failure path leaves Room unchanged.
- Cancellation is propagated rather than converted into a failure value.

## Deterministic runtime proof

The localhost-only instrumentation test proves:

- secure HTTPS listing fetches flow through the real NET01 client;
- initial pages commit to Room;
- append uses the exact opaque `after` cursor;
- duplicate IDs do not create duplicate query entries;
- refresh replaces query membership and increments generation;
- higher-layer sync results do not expose network post objects;
- Room readback contains the canonical ordered feed;
- stale cursor rejection performs no network request;
- HTTP 503 preserves the prior Room snapshot and retry metadata;
- an injected transaction failure rolls back all partial changes;
- opaque remote cursors cannot loop back into local cache paging;
- DATA01, DATA02, DATA03, and V2E device regressions remain green.

No request is sent to Reddit. OAuth remains owned by AUTH01. Background synchronization and
WorkManager policy remain deferred until a later phase.
