# COMMENTSAVE01A2 — live comment save / unsave UI

## Product intent
Expose Reddit comment save/unsave in the existing comment overflow menu without
creating a second save transport or second authorization flow.

COMMENTSAVE01A1 already made the HOMEACTION01 save pipeline capable of carrying
canonical `t1_` comment fullnames and projected Reddit's `saved` boolean into the
comment model/cache. A2 consumes that foundation in the live comment UI.

## Required behavior
- Authenticated Reddit comments expose `Save comment` / `Unsave comment` in the
  existing comment overflow menu.
- Menu state is derived from server `Comment.saved` plus the existing optimistic
  save override map.
- Tapping the action immediately applies an optimistic saved-state override.
- The existing `PostSaveCoordinator` / `/api/save` / `/api/unsave` path remains
  the sole remote mutation path.
- The existing `PostSaveUiState` completion path confirms or rolls back the
  optimistic comment state.
- While one save mutation is in flight, another comment-save action is disabled,
  matching the existing serialized post-save behavior.
- Save failures use the shared write error surface but identify `comment` rather
  than `post` when the target is a canonical `t1_` fullname.

## Saved history consistency
The authoritative mixed Saved surface already refreshes on entry. A2 does not
create a parallel local saved-comments list and does not mutate the Saved-history
cache directly. After a successful save/unsave, entering Saved refreshes from the
authoritative Reddit mixed `t3 + t1` listing.

## Boundaries
- No OAuth scope change.
- No Room schema migration.
- No new Reddit mutation endpoint.
- No new save coordinator.
- Existing post save behavior remains unchanged.
