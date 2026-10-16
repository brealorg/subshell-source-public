# subshell NAV-HAND-02 floating-action side contract v1

NAV-HAND-02 applies the interaction side directly to the global extended
floating action button used for Create and Message actions.

## Policy

- `InteractionSide.LEFT` places the floating action at `FabPosition.Start`.
- `InteractionSide.RIGHT` places it at `FabPosition.End`.
- The floating action has no separate override and always follows Interaction side.
- Existing drawer and comment overrides remain independent.
- Visibility, click behavior, hide-on-scroll translation and bottom-bar clearance
  are unchanged.

## Explicit exclusions

Home scroll-to-top remains a navigation-reselection action and is not converted
into a floating button. Media controls, composer counters and remove buttons,
profile geometry, content badges and semantic text alignment are not mirrored
by NAV-HAND-02.
