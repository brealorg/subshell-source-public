# NEXT01A6 — Public user canonical content adaptive workspace

## Product target

NEXT01A6 extends the existing adaptive list/detail model to public Reddit
profiles.

In an expanded window with Tablet layout mode set to Split:

- a public user's Posts and Comments remain visible in the left pane;
- selecting a post or comment opens the canonical Post detail in the right pane;
- selecting another item replaces only the active detail route;
- switching between Posts and Comments closes an active detail and returns the
  right pane to its contextual placeholder;
- Android/system Back from a selected Post returns to the same public-profile
  workspace and preserved list state.

## Canonical surfaces

NEXT01A6 reuses:

- `UserProfileScreen` for the public profile;
- `PostScreen` for post/comment detail;
- `FeedPostOrigin.User`;
- `SubshellState.openFromUser(...)` for first selection;
- `SubshellState.replaceCurrentUserContentSelection(...)` for subsequent
  selections.

No alternate profile or post implementation is introduced.

## Presentation ownership

`ExpandedListDetailSurface` gains an opt-in `showListHeader` flag whose default
is `true`.

NEXT01A6 sets it to `false` only for the public-user workspace so that the
canonical `UserProfileScreen` context header remains the single visible left
pane header.

The public profile context header retains:

- `u/<username>`;
- Follow state where applicable;
- Block / Unblock state where applicable.

Only its redundant visual Back icon is suppressed in Split. The underlying
Back callback remains present. Compact/full-screen presentation retains the
existing visual Back icon.

## Scope boundary

Public profile content is limited to Posts and Comments. Own-profile content
remains owned by NEXT01A4A and its successors.

Compact windows and Tablet layout mode Single remain unchanged.

NEXT01A6 does not change Reddit transport, authentication, Room, persistence,
write semantics, post-detail behavior, or the route-state implementation.


## Relationship to existing You/Blocked workspace

NEXT01A6 does not claim a public user when that user is the selected detail of
the existing NEXT01A4B You → Blocked workspace. In that context the Blocked
list remains the authoritative left pane.

The same boundary applies when a Post is opened from that Blocked-user detail:
NEXT01A6 does not replace the existing predecessor presentation with a public
profile workspace.

This keeps NEXT01A4B behavior unchanged while allowing ordinary public-profile
routes from other contexts to use the NEXT01A6 adaptive Posts/Comments
workspace.


## Profile destination scroll continuity

Profile destinations retain independent scroll histories.

When the user switches to a destination that has never previously established
a scroll anchor, the destination is seeded once from the current visible
profile viewport. Shared structural item keys such as `context-header`,
`profile-header` and `tabs` allow the new destination to preserve the visible
profile chrome level rather than jumping to the banner at the top.

If the destination already has a stored anchor, NEXT01A6 does not overwrite
it. Returning to Posts, Comments, Saved, Upvotes, Downvotes, Hidden, Blocked
or Drafts therefore restores that destination's own previous scroll position.

No shared `LazyListState` is introduced, and the existing scroll-anchor store,
process snapshot format and `SubshellState` implementation remain unchanged.
