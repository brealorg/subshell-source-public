# SUBS04C community navigation and discovery consolidation contract v1

SUBS04C consolidates the existing Subscriptions, discovery, drawer-community, Favorites, Recent, Hidden, custom-feed, and SUBS03 membership product into one coherent information architecture. It does not introduce a new Reddit transport, cache, membership engine, or account-history stack.

## Product ownership

SUBS04C owns only community navigation and subscription/discovery presentation:

- the existing Favorites / Subs / Recent / Feeds primary tabs;
- the integrated Discover communities entry inside Subs;
- the canonical full Discover route and its search entry;
- joined-community presentation, Favorites, Recent, and Hidden presentation;
- drawer community navigation modes;
- settings that control drawer community presentation.

SUBS04C does not own Reddit account history, Saved comments, profile comments, or focused-comment navigation.

## Search-first discovery

The authenticated Subs tab keeps Discover communities as its first content block. A visible Search communities affordance sits directly inside that block before the popular discovery preview.

Tapping Search communities opens the existing `SecondaryDestination.COMMUNITY_EXPLORE` route through `openCommunityExplore(focusSearch = true)`. The full Discover screen remains the sole owner of the editable search field, typeahead, Best match, Most active, Join/Leave, and search-result state.

`See all` continues to open the same canonical Discover route without forcing search focus. The top app bar Search action continues to open the same route with focus.

No parallel search state, query identity, network source, or cache is added to the Subs list.

## Drawer community navigation

The three user-facing modes are:

- `Simple`: one Communities entry that opens Subs;
- `Dropdown`: Communities header, automatic View all communities entry, and an expandable joined-community list;
- `Full list`: Communities header, automatic View all communities entry, and the full joined-community list.

The persisted enum names remain `CURRENT`, `COLLAPSIBLE`, and `FULL` for storage compatibility. `CURRENT` is relabeled to Simple only at the product layer.

The old independent `View all communities shortcut` setting is retired. Whether View all communities appears is derived solely from the selected drawer mode. Recent communities in drawer remains an independent optional preference.

Favorites and Recent remain grouped ahead of Other communities when present. Hidden communities must not appear in the drawer.

## Retired Explore and drawer state

The following are no longer active product state:

- `discoverExpanded`;
- `SubsExploreShortcutMode` / `subsExploreShortcutMode`;
- `subsExploreLauncherVisible`;
- `drawerViewAllCommunities`.

Their old persisted keys remain in the allowlisted settings format so existing backups continue to restore. Current settings canonicalize them to compatibility values only:

- `discover_expanded=false`;
- `subs_explore_shortcut_mode=AUTO`;
- `drawer_view_all_communities` is derived from the selected drawer mode.

Legacy values do not re-enable retired UI or override the current drawer mode.

## Preserved foundations

SUBS04C preserves:

- SUBS03 authoritative membership transport and Join/Leave logic;
- SUBS04B popular discovery loading, NSFW filtering, joined-community exclusion, dedupe, and six-item bound;
- account-scoped Favorites, Recent, Hidden, and custom feeds;
- Hidden device-unlock behavior and local concealment;
- canonical full Discover search/typeahead/sort behavior;
- top-bar community Search;
- existing route-stack/back semantics;
- existing Room/cache/runtime owners.

## Runtime acceptance

1. Subs still exposes Favorites / Subs / Recent / Feeds.
2. Subs > Subs begins with Discover communities and a visible Search communities affordance.
3. Tapping Search communities opens full Discover with the search field focused and keyboard requested.
4. See all opens full Discover without requiring search focus.
5. Popular discovery preview remains bounded, filters joined communities, respects NSFW policy, and uses the existing SUBS03 Join/Leave pipeline.
6. Drawer settings show Simple / Dropdown / Full list and no separate View all communities shortcut toggle.
7. Simple shows only the Communities entry.
8. Dropdown and Full list always expose View all communities; Dropdown reveals joined communities only when expanded.
9. Favorites / Recent / Other communities grouping remains intact and Hidden communities never appear in the drawer.
10. Old Explore/View-all persisted values restore without failure but cannot reactivate retired product state.
11. No Reddit history, Saved-comment, profile-comment, or focused-comment implementation is introduced by SUBS04C.
