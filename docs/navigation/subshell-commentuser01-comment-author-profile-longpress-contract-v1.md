# COMMENTUSER01 — comment author profile long-press

## Product contract

In a Reddit comment thread, long-pressing a visible comment author opens the existing
`SubshellRoute.User` profile flow through `SubshellState.openUser`.

The normal tap behavior is unchanged: tapping the author label still collapses or expands
the same comment, matching the surrounding comment header.

Blank and Reddit `[deleted]` authors do not produce a user-profile route.

## Architecture boundary

COMMENTUSER01 does not add a comment-specific user route, network loader, cache, or profile
screen. It reuses the existing user-profile navigation and identity pipeline.

## Validation

The feature owns a source contract and unit policy coverage for author-target normalization.
Full source contracts, unit tests, lint, and the DEV2 build must pass before checkpoint/landing.
