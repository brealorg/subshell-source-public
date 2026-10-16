# SUBS05A1 community discovery layout preference contract v2

SUBS05A1 keeps the authenticated Communities > Joined discovery entry and makes its presentation user-selectable. The existing Reddit discovery provider, canonical full Discover route, Search ownership, and SUBS03 membership engine remain authoritative.

## Product decision

Runtime review showed that the original horizontal carousel is the preferred default, while the contextual full-width list is still useful as an alternative.

The product therefore exposes two layouts:

- **Carousel** — default. Restores the original compact horizontal discovery cards and up to six eligible popular communities.
- **List** — optional. Shows up to three compact full-width popular-community rows with member/description context.

The setting is named `Community discovery` and is persisted as `community_discovery_layout`.

## Settings preview

`Settings > Navigation & gestures` exposes `Carousel` and `List` as direct choices.

Selecting either option switches the existing fixed-size live preview into a dedicated community-discovery context. Both layouts render inside the same preview viewport height so switching layouts does not resize or jump the Settings surface.

The preview is illustrative only. It owns no discovery data, membership state, or navigation state.

## Communities surface

Carousel mode preserves the earlier presentation:

1. `Discover communities` header with `See all`.
2. `Search communities`.
3. Horizontal cards with subreddit identity, member count, and Join / Joined.

List mode presents:

1. `Discover communities`.
2. `Search communities by name or topic`.
3. `Popular communities` with `See all`.
4. Up to three full-width rows with subreddit identity, member/description context, and Join / Joined.

Both modes use the same provider-ordered discovery items after existing case-insensitive dedupe, joined-community exclusion, and NSFW policy.

## Preserved ownership

SUBS05A1 preserves:

- `openCommunityExplore(focusSearch = true)` for the inline search action;
- `openCommunityExplore()` for `See all`;
- the full Discover screen as sole owner of editable community search and result state;
- Reddit popular discovery as the inline provider;
- SUBS03 projected membership, authorization, in-progress, failure, Join, and Leave behavior;
- Favorites / Joined / Recent / Feeds tabs;
- Hidden subscriptions and device-unlock behavior;
- drawer community IA and account-scoped persistence;
- Search02 and route-stack behavior.

No Reddit endpoint, OAuth scope, Room schema, cache namespace, or membership transport is added.

## Runtime acceptance

1. A fresh/default installation shows the original Carousel layout.
2. Settings exposes `Community discovery` with `Carousel` and `List`.
3. Switching the setting updates a stable same-size live preview.
4. Returning to Communities > Joined reflects the selected layout immediately.
5. The selected layout survives app restart and saved-settings capture/restore.
6. Carousel shows up to six eligible candidates; List shows up to three.
7. Join / Joined behavior remains authoritative and shared in both layouts.
8. Search and `See all` continue to open canonical Discover.
