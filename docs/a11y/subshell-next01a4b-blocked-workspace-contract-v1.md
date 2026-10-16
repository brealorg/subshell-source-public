# NEXT01A4B — You → Blocked adaptive workspace

## Scope

NEXT01A4B extends the canonical You surface for the authenticated
account's `Blocked` destination (tab 6).

On an expanded window with **Tablet mode → Split**:

- the existing authoritative Blocked list remains the master pane;
- the detail pane starts with `Select an account`;
- selecting a blocked account opens the existing public user-profile
  surface in the detail pane;
- selecting another blocked account replaces the current User detail
  rather than growing the navigation stack;
- Back from the selected User detail returns to the Blocked placeholder;
- switching the master away from Blocked closes the selected User detail.

## Existing behavior preserved

The Blocked list remains owned by the existing USERACTION01A2
authoritative reader, refresh, paging and optimistic projection.

`Unblock` on the Blocked row continues to call the existing
`setUserBlocked(..., blocked = false)` path.

The existing profile-side Block/Unblock controls continue to use the same
USERACTION01A1 write path.

No new Reddit endpoint, OAuth scope, persistence model or route type is
introduced.

## Non-Split behavior

Compact windows and **Tablet mode → Single** keep the existing full-screen
behavior. The optional Blocked-account selection callback is null in
those presentations, so the pre-A4B row remains non-navigating there.

Drafts (tab 7) remains outside NEXT01A4B.

## Relationship to NEXT01A4A

NEXT01A4A continues to own Post detail workspaces for tabs 0–5.

Blocked is not converted into a Post detail workspace. NEXT01A4B adds a
separate User-profile detail workspace for tab 6.

Final tablet visual QA remains deferred.
