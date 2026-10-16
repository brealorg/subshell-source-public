# COMMENTUSER01 — comment author profile tap v2

## Product contract

Comment authors use the same primary navigation affordance as post authors: tapping the
visible comment avatar or username opens the existing `SubshellRoute.User` flow through
`SubshellState.openUser`, carrying `ProfileActivityEvidence.COMMENT`.

Collapsing and expanding remain comment-thread actions rather than author actions. Tapping
the remainder of the comment header collapses or expands that comment, and the disclosure
icon at the trailing edge is an explicit collapse/expand target. A collapsed comment also
keeps its existing collapsed-summary tap target so the thread can always be reopened.

Blank and Reddit `[deleted]` authors do not produce a user-profile route. The author
hitbox is still owned by author interaction: tapping `[deleted]` shows the shared ERROR01
inline `ErrorNotice` with the existing red error treatment and must not fall through to the
parent comment collapse/expand target. This feedback is contextual and persistent in-place;
it does not use Toast or Snackbar presentation.

## Compatibility

This supersedes the long-press-only interaction from COMMENTUSER01 v1 without introducing a
new route, profile loader, cache, or account scope. Long-press is no longer required to
discover user-profile navigation.

## Validation

The COMMENTUSER01 source contract verifies tap ownership, the deleted-author guard, the
existing profile route, and explicit collapse/expand affordances. PROFILEUSER01 continues to
own privacy-aware public-profile presentation.
