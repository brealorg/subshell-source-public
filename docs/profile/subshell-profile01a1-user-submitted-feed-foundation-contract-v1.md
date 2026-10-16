# PROFILE01A1 user submitted feed foundation contract v1

PROFILE01A1 extends the existing authenticated Reddit post-feed pipeline with public user submitted-post listings.

`AuthenticatedRedditFeedTarget.user(username)` is a fourth authenticated feed target alongside main feeds, communities, and search.

User submitted listings use Reddit `/user/{username}/submitted` with `sort=new`, `raw_json=1`, the existing 25-item authenticated page size, and Reddit `after` cursor paging.

The listing uses the existing `RedditFeedUpdater`, `OfflineFirstPostFeedSynchronizer`, `RepositoryQueryKey`, account scoping, Room post/query cache, refresh behavior, append behavior, and failure mapping.

The cache identity is account-scoped and username-scoped through `redditUserSubmittedPostFeedQueryKey`.

PROFILE01A1 does not add a parallel user-listing loader.

PROFILE01A1 does not change OAuth authorization. The existing Reddit `CORE_READ` scope set remains `identity`, `mysubreddits`, and `read`.

PROFILE01A1 does not change Room schema version or introduce user-specific post tables.

The existing COMPOSER09 submit-reconciliation request remains independent. Its `/user/{username}/submitted?sort=new` lookup is not reused as profile paging transport and its reconciliation semantics are unchanged.

PROFILE01A1 is foundation-only. `UserProfileScreen`, `AuthConnectViewModel`, `MainActivity`, and `SubshellApp` are not wired to request the new target in this tranche.

Authenticated profile UI wiring is deferred to PROFILE01A2.

Authenticated profile comments or overview are deferred to a later PROFILE01 tranche because the current `ProfileCommentFixture` path remains a legacy fixture/cache model.
