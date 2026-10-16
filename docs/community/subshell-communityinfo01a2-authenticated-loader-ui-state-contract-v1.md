# COMMUNITYINFO01A2 — authenticated loader and latest-request UI state

A2 connects the A1 sidebar/rules/moderator transport to the existing authenticated
Reddit session runtime. It does not render new CommunityScreen UI yet.

The loader requires the current RedReader-compatible core-read configuration, binds
requests to the authoritative active authenticated account, refreshes the access token
through `AuthAccountSessionCoordinator`, and uses a session-bound HTTP client.

Sidebar, rules and moderators are independent resources. Successful sections survive
an individual section failure. Only when all three fail does A2 emit a top-level
failure.

`RedditCommunityInfoUiState` is keyed by account stable ID plus normalized community
name. `AuthConnectViewModel` cancels a previous request and verifies the active target
before applying a result, so a late response cannot replace a newer community target.

No new OAuth grant is requested. No Room schema or backup format changes. Wiki remains
deferred because `wikiread` is not in the current daily-driver scope set. A3 owns the
actual Community About presentation.


## SEARCH01C2 successor compatibility

The historical SEARCH01C2 checker previously located Search's target by taking
the first `val target = try {` in the entire `AuthConnectViewModel`. A2 adds an
unrelated Community Info target before Search, so that file-order assumption is
no longer valid.

The checker now constrains the assertion to the
`loadRedditCommunityDiscovery()` method boundary and still verifies that
`loadRedditPeopleSearch()` dispatch occurs before Search constructs its own
community-discovery target. No Search runtime/product source changes.
