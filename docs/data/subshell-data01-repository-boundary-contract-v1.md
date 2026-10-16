# subshell DATA-01 repository boundary contract V1

DATA-01 removes fixture ownership from Compose screens and from navigation/state logic.

## Boundary

`SubshellState` depends on the transport-neutral `SubshellRepository` interface. The interface is
composed from feed, post, community, user, inbox and local-mutation repositories. Compose screens
consume repository-backed projections exposed by `SubshellState`; they do not import `SampleData`.

`FakeSubshellRepository` is the only production class allowed to read `SampleData`. It preserves
the current deterministic local fixtures and owns local create-post, add-comment, save, vote and
poll-vote mutation records.

## Result model

`RepositoryResult` represents:

- loading;
- content;
- refreshing content;
- empty;
- offline with optional cached data;
- error with optional cached data.

Content results expose an optional cursor. `RepositoryPageRequest` provides a bounded cursor/limit
contract for DATA-02 without adding networking or asynchronous loading in DATA-01.

## Identity

Post, comment, community and inbox identities use their existing stable `id` fields. User identity
uses normalized `username`. Repository contract tests reject duplicate identities.

## Compatibility

- FEEDPOST V2A typed origin remains unchanged.
- FEEDPOST V2B canonical route stack remains unchanged.
- FEEDPOST V2C keyed scroll anchors continue to use stable item IDs.
- FEEDPOST V2E snapshots continue to validate against a repository-derived restore catalog.
- Existing account preference persistence remains owned by `SubshellState`.
- Reddit transport, OAuth and a persistent database remain out of scope.
