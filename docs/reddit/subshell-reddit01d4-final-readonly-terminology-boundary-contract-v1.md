# REDDIT01D4 final read-only terminology boundary contract v1

REDDIT01D4 completes the active Reddit read-only terminology retirement.

The historical request helpers previously named `auth06cReadOnlyFeedRequest` and `live02ReadOnlyFeedRequest` are active production helpers, so their current names are now `auth06cInitialFeedRequest` and `live02FeedPageRequest`.

The historical AUTH06C and LIVE02 provenance remains visible in those helper names while the obsolete current-runtime `ReadOnly` capability implication is removed.

The active validation text now describes an authenticated Reddit feed target rather than a read-only feed target.

REDDIT01D4 is nomenclature-only. OAuth scopes, authorization ownership and context routing, Reddit endpoints, HTTP methods, request parameters, cache keys, Room schema, paging semantics, mutation behavior, and account identity do not change.

Historical milestone artifacts remain historical. AUTH06C test class names, proof method names, scripts, result tokens, contract labels, historical documentation, and historical test descriptions are not rewritten merely to erase the words read-only.

Three current source occurrences are explicitly outside the Reddit architecture terminology migration:

- `ByteBuffer.asReadOnlyBuffer()` is Java/NIO API behavior required by remote animated-image decoding.
- `Read-only poll` describes an actually non-mutable local poll state.
- `Read-only runtime diagnostics` describes diagnostics that intentionally cannot mutate runtime state.

No other current `ReadOnly`, `readOnly`, `READ_ONLY`, `read-only`, or `readonly` terminology is permitted in `app/src/main/java`.
