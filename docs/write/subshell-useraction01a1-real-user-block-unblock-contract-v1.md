# USERACTION01A1 — Real Reddit user block and unblock

## Product boundary

USERACTION01A1 replaces the disabled authenticated-profile Follow placeholder with real Reddit **Block** and **Unblock** actions. Local fixture profiles retain the existing Follow/Following behavior.

The first slice is deliberately profile-scoped. Comment/post overflow entry points, an authoritative blocked-users listing, and feed/comment filtering remain successor work.

## Transport and authorization

- Block uses Reddit `POST /api/block_user` with the canonical username.
- Unblock removes the `enemy` relationship through `POST /api/unfriend`; the current account fullname is derived from the authenticated Reddit identity.
- The existing owner-bound content-safety authorization route carries `account`, `privatemessages`, and existing safety scopes.
- HTTP 401 re-enters capability authorization. HTTP 403 is presented as a provider restriction rather than causing an authorization loop because Reddit documents user blocking as restricted to approved OAuth applications.
- Mutations use the existing non-retrying write call factory and explicit user retry.

## UI and state

- Blocking requires a confirmation dialog.
- A successful mutation immediately projects Block ↔ Unblock on the current profile.
- Failed mutations roll the optimistic profile state back.
- The projection is account-session-local and intentionally not persisted. USERACTION01A3 will replace it with the authoritative blocked-users listing.
- Own profiles and unavailable profiles never expose the control.

## Regression boundary

USERACTION01A1 preserves existing post hide/unhide, report, authorization callback continuity, local Follow fixtures, profile navigation, and viewport error hosting.
