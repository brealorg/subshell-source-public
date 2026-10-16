# NEXT01A4C — You → Drafts adaptive workspace

## Scope

NEXT01A4C completes the canonical You adaptive workspace for Drafts
(tab 7).

On an expanded window with **Tablet mode → Split**:

- the existing Drafts list remains the master pane;
- the detail pane starts with `Select a draft`;
- selecting a draft opens the existing Create Post composer in the
  detail pane;
- selecting another draft archives the current draft and rebinds the
  same `PostComposer` route without growing the navigation stack;
- Back archives the current draft, pops the existing composer overlay,
  and returns to the Drafts placeholder;
- selecting another You tab while editing archives and closes the draft
  before showing the selected destination.

## Draft safety

NEXT01A4C does not introduce a second dirty-state model.

The existing composer remains authoritative:

- edits update `postComposerDraft`;
- the existing autosave continues to persist drafts;
- `closePostComposer()` archives before popping the route;
- explicit Discard keeps its existing confirmation and permanent-delete
  semantics.

If the currently selected draft is deleted from the master pane while its
editor is open, the existing discard path is used so the active editor
cannot autosave the deleted draft back into the list.

Deleting a different draft continues to use the existing
`deletePostComposerDraft(draft.id)` path.

## Preserved behavior

NEXT01A4A content workspaces and NEXT01A4B Blocked remain unchanged.

Compact windows and **Tablet mode → Single** retain the existing
full-screen Drafts and Create Post behavior.

Create Post continues to own its existing IME handling, validation,
community/flair policy, media handling, submission, autosave and discard
UI.

No new route type, Reddit endpoint, OAuth scope or persistence model is
introduced.

Final tablet visual QA remains deferred.
