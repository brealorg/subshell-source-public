# subshell NAV-HAND-01 interaction-side contract v1

NAV-HAND-01 introduces one default interaction side and explicit overrides for
controls whose physical edge matters.

## Settings model

- `Interaction side` is Left or Right.
- `Side menu position` can Follow, Left or Right.
- `Comment actions` can Follow, Left, Balanced or Right.

Follow resolves dynamically from the interaction side. The settings UI names
that dependency explicitly and renders the current value as `Follow · Left` or
`Follow · Right`. Explicit overrides stay fixed when the interaction side changes.

## Drawer and header

The Material drawer receives a local layout direction only for physical edge
placement and edge gestures. Drawer contents and the application scaffold are
restored to the application's original layout direction, so text and semantic
navigation are not mirrored.

The subshell drawer control stays on the same physical edge as the drawer. The
Snoo account button and Search move as one group to the opposite edge.

## Migration

The legacy comment-action setting seeds the new model:

- Left becomes interaction Left with comment actions set to Follow.
- Right becomes interaction Right with comment actions set to Follow.
- Balanced becomes interaction Right with comment actions fixed to Balanced.

Current snapshots write only the three new keys. The legacy key remains readable
and is mirrored in local preferences for downgrade compatibility.
