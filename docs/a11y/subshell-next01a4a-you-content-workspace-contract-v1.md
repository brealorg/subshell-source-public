# NEXT01A4A — You canonical content adaptive workspace

## Canonical information architecture

The current own-profile `You` surface owns eight content destinations:

1. Posts
2. Comments
3. Saved
4. Upvotes
5. Downvotes
6. Hidden
7. Blocked
8. Drafts

The visible compact hierarchy may group Saved through Drafts under the
`Library` folder affordance, but these are inline `UserProfileScreen`
destinations.

The legacy `SecondaryDestination.LIBRARY` route remains compatibility-only and
is not the NEXT01A4A product target.

## Adaptive content boundary

On an expanded window with Tablet mode `Split`, these destinations use the
canonical list/detail workspace:

- Posts
- Comments
- Saved
- Upvotes
- Downvotes
- Hidden

Blocked and Drafts do not.

Blocked is account-management content rather than Reddit Post detail.

Drafts open the existing post composer and therefore retain their existing
full-surface behavior.

## Split from entry

For a supported You content destination, the workspace is split before a
selection is made.

The left pane contains the existing `UserProfileScreen`, including the existing
profile header, destination hierarchy, list state, refresh and paging behavior.

The right pane initially shows content-specific guidance:

- Posts / Upvotes / Downvotes / Hidden: `Select a post`
- Comments: `Select a comment`
- Saved: `Select a saved item`

## Detail selection

Existing UserProfileScreen data and target resolution remain canonical.

Posts and post-history entries resolve directly to their existing Post model.

Comments retain the existing exact comment workflow:

- preserve the comment return anchor;
- resolve the target Post;
- retain the exact focused comment ID.

Saved remains a mixed history:

- saved posts open Post detail;
- saved comments resolve their target Post and retain the saved comment ID.

When a detail is already open, a new You content selection replaces the current
Post route through the existing `replaceCurrentPostRoute` engine instead of
growing the navigation stack.

## Tab switching while detail is open

Switching to a different You destination closes the current detail first.

The destination state itself remains owned by the existing
`SubshellState.selectUserProfileTab` path.

The newly selected content destination therefore returns as its normal
split-from-entry workspace, while Blocked and Drafts return as their existing
full-surface destinations.

## Scroll continuity

NEXT01A4A does not introduce a new list state.

Existing `userFeedScrollKey(username, tab, activityTab)` identity, captured
scroll anchors, comment return anchors, paging state and refresh behavior remain
the source of truth.

## Compatibility

Compact and Tablet mode `Single` retain the predecessor navigation behavior.

Other-user profile navigation outside canonical `MainDestination.YOU` is not
claimed by NEXT01A4A.

The legacy Library compatibility route remains unchanged.

## Unchanged systems

NEXT01A4A does not change:

- Reddit network transport;
- OAuth scopes or authorization behavior;
- Room or persistent schemas;
- account-history query identities;
- Saved mixed-history transport;
- comment target resolution;
- Post or comment write behavior;
- route model types;
- Blocked account behavior;
- Draft storage or composer behavior.

## Runtime acceptance

With Tablet mode `Split`:

- You -> Posts starts with Posts left and `Select a post` right.
- Opening a post places Post detail right.
- Selecting another Post replaces only the right detail.
- Back returns Posts plus the placeholder.
- Comments starts with `Select a comment`.
- Opening a Comment lands on its exact focused comment in the right pane.
- Saved starts with `Select a saved item`.
- Both a saved Post and saved Comment can become right-pane detail.
- Upvotes, Downvotes and Hidden use Post detail in the same workspace.
- Switching tabs while detail is open removes the old detail.
- Blocked remains the existing non-detail surface.
- Drafts remains the existing Drafts/composer surface.
- Tablet Single remains the predecessor full-screen navigation behavior.

Final tablet visual QA remains deferred.
