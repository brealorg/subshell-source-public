# COMMENTSAVE01A3.1 — comment overflow icon system

## Product intent

COMMENTSAVE01A3 established the bookmark identity for comment Save/Unsave. In isolation that made the same overflow menu visually inconsistent because the remaining actions were text-only. A3.1 completes the local action-menu grammar instead of expanding COMMENTSAVE01 into an app-wide menu redesign.

## Contract

- Every action in the comment overflow uses a leading Material icon and explicit text label.
- `Edit comment` uses the existing rounded Edit icon.
- `Delete comment` uses the existing rounded Delete icon and preserves error-colored text and icon tint.
- `Save comment` / `Unsave comment` retain the A3 outline/filled bookmark state semantics.
- `Share comment` uses the rounded Share icon.
- `Copy text` uses the rounded ContentCopy icon.
- `Collapse thread` uses the existing rounded ExpandLess disclosure icon.
- No extra inline action-row button is introduced.
- COMMENTSAVE01A1/A2 mutation, optimistic rollback, server-state projection and authoritative Saved history remain unchanged.
- Post Save/Unsave remains unchanged.
- This phase does not standardize every overflow menu in Subshell; an app-wide action-menu consistency audit is explicitly out of scope.
- No OAuth scope, Room schema, remote, main, or DEV1 mutation is introduced.

## Runtime acceptance

1. A normal comment overflow shows icons for Save/Unsave, Share, Copy and Collapse.
2. An own-comment overflow additionally shows Edit and destructive Delete icons with Delete retaining error emphasis.
3. Save/Unsave still switches bookmark state and behaves exactly as accepted in A2/A3.
