# SEARCH02A1 — account-scoped search interaction index

## Product intent

SEARCH02A1 adds a small, device-local interaction index for Search. It does not cache Reddit result pages. It remembers which community and user results were selected so Search can rank otherwise-equivalent matches more usefully and restore recently opened people in the zero-query workspace.

## Persistence boundary

The index is persisted per authenticated account stable ID in `subshell_device_preferences` under `search_index.v1.<account>`. The payload is bounded to 48 normalized entries. Each entry contains only target identity metadata, a selection count, and the last-selection timestamp:

- target kind (`COMMUNITY` or `USER`)
- normalized target key
- display text and compact secondary text
- optional identity image URI
- bounded selection count
- last selected timestamp

No bearer token, Reddit response body, post listing, raw submitted query, comment content, or private account history is written into the index. Existing account-scoped query history remains a separate store.

A preference-backed bounded index is intentional for A1: it is a small interaction signal set, not a full-text document corpus. AppSearch, SQLite FTS, and a Room-backed search document index remain deferred until a later SEARCH02 phase establishes a real document-indexing requirement.

## Ranking policy

Existing semantic match classes remain authoritative:

1. exact primary match
2. primary prefix
3. primary token prefix
4. primary contains
5. secondary prefix/token/contains

Interaction signals add at most 80 points. That cap is lower than the 100-point exact-versus-prefix gap, so personalization can reorder equivalent match classes but cannot demote an exact match below a prefix match.

The interaction boost combines:

- a bounded frequency signal
- a bounded recency signal

The same policy is applied to draft and submitted Community and People results. Provider transport and Reddit relevance remain unchanged.

## Zero-query workspace

When indexed people exist, Search shows a bounded `Recent people` section. The section is account-scoped and can be cleared independently. Community activity contributes to ranking but does not duplicate the existing `Recent communities` surface.

## Navigation and lifecycle

Selecting a Community or People result records the interaction immediately before the existing canonical navigation route is opened. Account switches replace the in-memory index with the selected account's persisted index. Search draft/submitted ownership, Back behavior, route restore, and process snapshots are unchanged.

## Explicit non-goals

SEARCH02A1 does not add:

- cached Reddit search result pages
- post-result interaction indexing
- Room schema migration
- AppSearch or SQLite FTS
- semantic or AI search
- cross-account ranking
- remote telemetry

### Kotlin visibility boundary

`SearchIndexKind` and `SearchIndexEntry` are public value types. `SubshellState` is a public state holder whose constructor accepts injected Search-index load/persist hooks and whose observable Search index is part of the UI state surface. Keeping the value types `internal` would expose an internal type through a public API and fail Kotlin compilation. The types contain only bounded device-local interaction metadata; storage, normalization, ranking and preference-key helpers remain internal.
