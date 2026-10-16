# NEXT01A1 — Community → Post adaptive list/detail

## Accepted predecessor

A11Y01A4 established the expanded list/detail workspace for Home → Post and
Inbox → Detail. A11Y01A41/A411 subsequently made Tablet mode configurable and
kept its Settings preview geometry stable.

NEXT01A1 is a narrow adaptive-navigation successor. It does not replace or
reinterpret A11Y01A4.

## Community → Post

When all of these conditions hold:

- the current route is a Post opened from `FeedPostOrigin.Community`;
- the window is at the existing expanded breakpoint;
- Tablet mode is `SPLIT`;

the owning Community remains visible in the fixed A4 list pane and the Post
renders in the existing detail pane.

The implementation reuses:

- `ExpandedListDetailSurface`;
- the existing 360 dp list pane;
- the existing 1200 dp combined maximum width;
- `replaceCurrentPostSelection()` for subsequent post selections;
- Community's existing `FeedScrollKey.Community` scroll ownership.

Selecting another Community post replaces only the current Post route. It must
not grow route depth.

## Community list presentation

The left pane uses the production `CommunityScreen`.

Its phone-style contextual Back header is suppressed only for this embedded
list-pane presentation because the A4 list/detail shell already owns the
workspace header. Normal Community presentation is unchanged.

The community header, sort control, post feed, paging and moderation/community
surfaces remain production code.

## Compact and single-pane behavior

When the window is not expanded or Tablet mode is `SINGLE`, Community → Post
continues through the existing `state.openFromCommunity(...)` route.

NEXT01A1 intentionally does not alter Community's existing PostCard comments
behavior.

## Non-goals

NEXT01A1 does not change:

- `FeedPostOrigin` or `FeedPostRoute`;
- `SubshellRouteStack`;
- process-state persistence;
- feed scroll persistence;
- Reddit reads or writes;
- OAuth;
- Room;
- account switching;
- custom feeds;
- Search → Post;
- Library → Post;
- User → Post;
- Subscriptions → Community.

## Runtime acceptance

Use DEV1 on Pixel 6 landscape with Tablet mode = Split.

1. Open a Community.
2. Scroll the Community feed away from its initial position.
3. Open a post.
4. Community remains visible in the 360 dp left pane.
5. Post/comments render in the right pane.
6. Select another post in the left pane.
7. Only the right-hand detail changes; the Community list does not jump.
8. Press Back.
9. The Post detail closes and normal expanded Community remains coherent.
10. Rotate to portrait while a Community Post is open.
11. Compact single-detail navigation remains usable.
12. Rotate back and confirm route/Community context remains coherent.

Also verify Tablet mode = Single retains the existing centered Community/Post
behavior.

NEXT01A1 is accepted when Community → Post feels like the same continuous
wide-screen workspace already established by A11Y01A4.
