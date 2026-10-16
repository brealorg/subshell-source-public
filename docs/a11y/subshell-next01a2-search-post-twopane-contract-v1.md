# NEXT01A2 / NEXT01A2R1 — Search → Post adaptive list/detail

## Accepted predecessors

A11Y01A4 established the expanded list/detail workspace.

NEXT01A1 extended that workspace to Community → Post while retaining compact
and Tablet mode Single behavior.

NEXT01A2 extends the same model to Search.

NEXT01A2R1 corrects the initial Search presentation so the list/detail workspace
exists before a Post has been selected.

## Canonical Search workspace

When:

- the existing expanded-window breakpoint is active;
- Tablet mode is `SPLIT`;

Search itself renders as a list/detail workspace.

The left pane contains the normal editable Search workspace:

- Search field;
- area/scope controls;
- Search results;
- paging and refresh behavior.

Before a Post has been selected, the right detail pane displays a neutral
placeholder:

- `Search for a post` when no submitted query exists;
- `Select a post` when Search has a submitted query.

The pane geometry therefore remains stable before and after selection.

## Search → Post

When a Post result is selected:

- the owning Search result list remains in the fixed A4 list pane;
- the selected Post renders in the detail pane;
- selecting another Post replaces only the current Post detail;
- route depth does not grow for subsequent Post selections.

The implementation reuses `ExpandedListDetailSurface`,
`replaceCurrentPostSelection()` and Search's existing scroll ownership.

## Stable Search owner context

Search query, scope and area are editable only while Search itself is the
current route.

While a Search-owned Post is current in Split mode, the embedded left pane
therefore presents the existing result list but suppresses the editable Search
workspace controls.

The list/detail header identifies the frozen owning Search query.

This prevents mutations of Search state while the underlying Search route is
not current and keeps the route snapshot authoritative.

## Back behavior

Back from a Search-owned Post returns to the Search route.

Because the Search route itself now uses list/detail in expanded Split mode,
Back returns to:

- full editable Search on the left;
- the neutral placeholder on the right.

The workspace therefore does not expand back to full width between selections.

## Compact and Single

Compact behavior is unchanged.

Tablet mode `SINGLE` retains the previous centered/full contextual Search and
Post behavior.

## Other Search results

Community and User Search results retain their existing navigation behavior.

Direct Reddit targets retain their existing behavior.

NEXT01A2R1 does not turn non-Post Search results into detail-pane selections.

## Non-goals

NEXT01A2R1 does not change:

- `SubshellState`;
- `FeedPostOrigin.Search`;
- `FeedPostRoute`;
- `SubshellRouteStack`;
- Search persistence;
- Search transport;
- Reddit reads or writes;
- OAuth;
- Room;
- Community → Post;
- Library → Post;
- User → Post;
- Subscriptions → Community.

## Runtime acceptance

Use DEV1 on Pixel 6 landscape with Tablet mode = Split.

1. Open Search.
2. Search immediately renders with a 360 dp left pane and an empty detail pane.
3. Before submitting a query, the detail pane says `Search for a post`.
4. Submit `android` or another query returning multiple Posts.
5. Detail placeholder changes to `Select a post`.
6. Search field, filters and results remain editable in the left pane.
7. Scroll the result list.
8. Open a Post.
9. Search results remain on the left and Post/comments render on the right.
10. Editable Search controls are suppressed while the Post route is current.
11. Select another Post result.
12. Only the right-hand detail changes and Search scroll does not jump.
13. Press Back.
14. The same split workspace remains: editable Search left, placeholder right.
15. Query/context remains intact.
16. Verify Tablet mode Single retains the previous one-surface behavior.

NEXT01A2R1 is accepted when Search behaves as a stable canonical list/detail
workspace throughout the entire expanded Split interaction.
