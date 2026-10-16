# SUBS04C2 — Communities terminology and drawer IA cleanup

## Product terminology

`Communities` is the canonical user-facing name for the main community product surface. The bottom navigation therefore displays `Communities`. Inside that surface the four tabs are `Favorites | Joined | Recent | Feeds`; `Joined` describes the authoritative membership list without renaming internal subscription transports, models, endpoints, or milestone identifiers.

User-facing membership copy uses `joined communities` / `hidden communities` rather than `Subs` or `subscriptions` where it refers to product navigation.

## Drawer information architecture

The scrolling drawer order is:

1. `Feeds`: Home, Popular, All;
2. `Custom feeds · N`, when any exist, as a collapsed dynamic group under Feeds;
3. `Communities`;
4. `You`: Inbox, Profile, Library.

Saved is not a drawer feed destination. Library is the canonical drawer entry for saved/personal library content.

Custom feeds stay semantically under Feeds. They remain collapsed by default and continue using the existing account-scoped custom-feed transport.

The Communities row is itself the navigation destination. In Dropdown mode its trailing chevron exclusively expands or collapses the inline joined-community groups; there is no `View all communities` row. Full list mode keeps the inline joined list expanded by explicit user preference.

## Compact drawer presentation

Primary drawer rows use a compact 48 dp minimum touch target with 14 dp selected-container corners, 22 dp icons, tighter section dividers, and reduced wordmark/section spacing. This reduces visual mass without shrinking below the Android minimum comfortable interactive target. Community children retain their avatars and use a tighter selected shape and indentation.

Only the actual active destination is selected. A custom-feed group header is selected only while its children are collapsed; an expanded active child owns the selected state.

## You and utilities

The personal section label is `You` and contains `Inbox`, `Profile`, and `Library`. `Profile` routes to the existing `MainDestination.YOU`; the internal route identifier is unchanged.

The fixed utility area below the scrolling drawer contains `Show NSFW posts`, `Settings`, and theme selection. Library no longer appears with settings utilities.

## Compatibility and boundaries

`DRAWER_VIEW_ALL_COMMUNITIES` remains an allowlisted legacy storage/backup key but canonical serialization always writes `false`; restored historical `true` values cannot reactivate product UI. Simple / Dropdown / Full list and Recent communities preferences remain intact.

SUBS04C2 does not change Reddit subscriptions transport, community discovery transport, membership Join/Leave, hidden-community unlock, account scoping, custom-feed transport, Saved/history engines, or PROFILE comment navigation.

## Manual acceptance

1. Bottom navigation says Communities.
2. Communities tabs read Favorites / Joined / Recent / Feeds.
3. Drawer Feeds contains Home / Popular / All and then Custom feeds when present; no Saved row is present.
4. Communities follows Feeds/custom feeds and navigates directly to the Communities surface.
5. Dropdown chevron expands/collapses inline communities without requiring a View all row.
6. You contains Inbox / Profile / Library.
7. Utilities contain Show NSFW posts / Settings / theme.
8. Drawer is visibly denser while preserving 48 dp minimum primary row touch targets.
9. SUBS03 membership, SUBS04 discovery, Hidden, Favorites, Recent, and custom feeds continue to work.


## Checkpoint identity

- `versionCode = 211`
- `versionName = 0.76.54-subs04c2-communities-navigation-dev`
- `SUBSHELL_SUBS04C2_CHECKPOINT_V1`
