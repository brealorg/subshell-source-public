# LIBRARY02A1 — nested compact You / Library destinations

## Product boundary

LIBRARY02A1 replaces the rejected eight-item scrollable You destination strip with a two-level compact presentation inside the existing `UserProfileScreen`.

The primary row is always `Posts`, `Comments`, `Library`. It retains the existing 48dp compact button-row language: icon on the left, label on the right, and an underline for the selected primary section. Library uses a distinct `Folder / FolderOpen` identity instead of the bookmark language reserved for Saved.

When `Library` is selected, a second 48dp compact row appears immediately below it. The secondary row preserves all existing private destinations: `Saved`, `Upvotes`, `Downvotes`, `Hidden`, `Blocked`, and `Drafts`. Only the selected secondary destination expands to icon + label; the remaining destinations remain icon-only, matching the previously accepted compact You strip. The selected slot uses a fixed relative weight (2.0) and every unselected slot uses weight 1.0, so label length no longer changes row geometry when switching between Saved, Upvotes, Downvotes, Hidden, Blocked, and Drafts.

## State and routing

The underlying profile tab IDs remain unchanged. `Posts` remains tab 0, `Comments` tab 1, and Library children continue to use tabs 2–7. This means no repository, paging, saved-history, blocked-account, draft, or route identity is migrated.

The Library primary destination remembers the last selected valid child for the current profile while the screen is saveable. Entering Library without a remembered child falls back to `Saved`.

## USERACTION01 continuity

The authoritative blocked-account surface from USERACTION01A2 remains tab 6 and continues to use `GET /prefs/blocked` plus USERACTION01A1 unblock reconciliation. No OAuth scopes, endpoints, owner boundaries, write semantics, or blocked-account transport are changed by LIBRARY02A1.

## Retired experiments

The uncommitted USERACTION01A2.1 scrollable destination strip and USERACTION01A2.1.1 edge-fade affordance are retired by this successor. Their policy tests, contract documents, global-gate entries, and build markers are removed rather than carried forward as historical requirements.

## Acceptance

- Posts / Comments / Library are fully visible without horizontal scrolling.
- Library is visually distinct from Saved through Folder / FolderOpen iconography.
- Secondary destination geometry is stable across different selected label lengths, including the full `Downvotes` label.
- Library opens a compact second row without introducing a separate screen or route.
- Saved, Upvotes, Downvotes, Hidden, Blocked, and Drafts remain reachable.
- Blocked remains backed by authoritative Reddit state and Unblock behavior from USERACTION01A1/A2.
- Other users still expose only Posts and Comments.
- No backend, OAuth, Room schema, or remote mutation is introduced by this presentation slice.
