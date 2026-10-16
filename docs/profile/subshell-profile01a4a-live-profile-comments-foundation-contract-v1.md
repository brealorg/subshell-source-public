# PROFILE01A4A live profile comments foundation contract v1

PROFILE01A4A adds the authenticated Reddit data foundation required for the
existing profile Comments tab. It does not enable the tab in UI yet.

Authenticated profile comment history uses Reddit
`/user/{username}/comments` with `sort=new`, `raw_json=1`, a bounded 25-item
page size, and Reddit's opaque `after` cursor.

The response is mapped as a flat `t1` history listing into the existing
`ProfileCommentFixture` projection. `link_id` provides the parent post
identity. Thread replies, comment-tree depth, `morechildren`, and the
post-thread `RedditCommentNetworkDataSource` are not reused for this listing.

PROFILE01A4A reuses the existing `cached_profile_comments` Room storage and
therefore adds no Room table, migration, or database version. Authenticated
cache identity is isolated by an account-and-username composite cache scope
stored in the existing username key. Local fixture profile-comment keys remain
unchanged.

A successful refresh replaces the authenticated cache scope. Append combines
the existing cached ordering with the new Reddit page, deduplicates by comment
identity, persists the combined projection, and exposes the persisted result.

The existing profile-comment Room table has no Reddit cursor metadata.
Therefore a cached snapshot restored after process restart intentionally has
no append cursor. The later UI tranche refreshes the listing to recover the
authoritative Reddit cursor before further paging.

Reddit profile history requires the `history` OAuth scope. PROFILE01A4A adds
the `PROFILE_HISTORY` capability as `CORE_READ + history`; it does not widen
`CORE_READ`. Current daily-driver account grants already contain `history`.

An older authenticated account without `history` returns
`AuthorizationRequired`. PROFILE01A4A does not start a new OAuth authorization
and does not add an authorization owner. PROFILE01A4B owns the explicit
user-facing upgrade action, persisted authorization owner, callback routing,
and completion behavior.

PROFILE01A4A does not change `UserProfileScreen`. The authenticated Comments
tab therefore continues to show its existing unavailable state until
PROFILE01A4B.

The existing local profile Activity semantics remain unchanged. Saved and
Upvoted activity is not replaced by Reddit overview history.

The AUTH07A/B1/B2/C1/C2 predecessor contract version gates explicitly
recognize PROFILE01A4A v198 as a successor while retaining their substantive
policy checks.

PROFILE01A4A adds no write endpoint and changes no post, comment, Inbox,
subscription, custom-feed, composer, moderation, or account-switch behavior.
