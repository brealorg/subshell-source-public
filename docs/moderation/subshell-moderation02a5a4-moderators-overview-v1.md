# MODERATION02A5A4 — Moderator overview and visible permissions

## Product boundary

A read-only `Moderators` destination in the existing Mod Tools workspace.
It is separate from `User management` and does not squeeze a fourth item into
Banned / Muted / Approved. The R4 host keeps one sheet alive. Visual Back,
system Back and the host's hidden-transition veto return to Mod Tools.

Rows keep the existing parser's Reddit order, show the username, shared public
avatar enrichment and a `You` label for the active username. A local search
filters loaded rows; it does not issue People-search requests. Reported
permission labels wrap rather than becoming editing controls.

## Reused source and known limits

The source snapshot is `137ed06e8f73a2280590d9ee04e8d672a63827d0`.
Reuse `RedditCommunityInfoUiState`, `CommunityInfoSnapshot.moderators`,
`CommunityModerator.permissions` and `onLoadRedditCommunityInfo`.
The existing authenticated loader calls the sidebar, rules and moderators
reads, including `GET /r/{subreddit}/about/moderators`. This first presentation
slice deliberately retains that shared load path, including its current
client-configuration and session restrictions. It does not add a parallel
loader, token owner, scope, cache or mutation coordinator.

The inherited parser returns at most 500 unique moderator rows. The count is
labelled **loaded**, not a proven total, and this slice adds no paging API.
It does not currently distinguish an omitted `mod_permissions` field from an
empty list. Both therefore display **Permission details not provided**, never
"No permissions" and never "All permissions". Only the explicit `all` code
produces that label. Unknown permission codes remain visible verbatim.
No seniority, join date, inactivity status, invitation status or ability to
remove another moderator is inferred from list position or permissions.

## State safety

Only matching account and community snapshots are rendered. A failed
MODERATORS section is unavailable, not an empty successful response. A sidebar
or rules failure does not hide successfully returned moderator rows. A failed
refresh can retain matching previous data with an explicit notice.

Avatar lookups use the existing user identity provider for visible lazy rows,
with three concurrent lookups at most. Failures leave a non-network fallback;
cancellation is propagated. No new moderator data is persisted or logged.

## Verification

The A5A4 contract is primary-only; predecessor execution belongs to the global
gate. Behavioural tests cover account/community isolation, unavailable versus
empty data, partial failures, stale refresh, unknown permissions and filtering.
The preview invokes the global contract once, one full unit/build pass and the
canonical DEV2 install. It does not force a second compile or duplicate a
focused test pass. Runtime remains required before checkpoint and land.

## Deferred

Inviting or removing moderators, editing permissions, accepting invitations,
leaving the team, reordering moderators, moderator-profile navigation, and
using this display as an authorization decision are outside A5A4.

## References

- Uploaded `next-moderators-discovery.txt`, SHA-256
  `dcab9807090e92942edbbc817848ad09bb38e7e071bc9074641cbbfb888fe9b6`.
- Reddit API documentation: https://www.reddit.com/dev/api/#GET_about_where
  (read endpoint reference checked 2026-09-10; no live Reddit request was made).
