# MUTE01A2 — mute visibility and management

MUTE01A2 makes the account-scoped timed community mute introduced by MUTE01A1
visible and manageable without changing its filtering boundary.

## Product behavior

A muted community remains directly accessible. Its community page and posts may
still be opened intentionally, including through direct Reddit links. Mute only
suppresses that community from mixed Home, Popular and All projections.

When the current community is muted, the community header shows a persistent
compact status surface with the exact expiry or `Muted indefinitely`. Pressing
that surface opens the existing duration editor, where the user can change the
duration or unmute explicitly.

The navigation drawer retains every joined community in its normal position.
Muted rows receive a trailing mute indicator; pressing the indicator opens mute
settings while pressing the row still opens the community normally.

When one or more active mutes exist, the drawer shows `Muted communities` with
a count badge. Its management sheet lists active timed mutes nearest-expiry
first and indefinite mutes last. Every row supports `Change` and `Unmute`.

## Expiry

The next mute expiry is observed at application scope rather than only while
Home is visible. The existing foreground clock and lazy pruning remain the
source of truth. No WorkManager, alarm, service or background scheduler is
introduced.

## Runtime observability

Debug builds emit `SubshellMute` projection records for active mute sets. Each
record includes feed source, input count, suppressed count and per-community
matches. This allows Home/Popular/All suppression to be verified without
manually searching large feeds. The log contains community names and counts but
no post title/body.

## Boundaries

- Account-scoped local persistence from MUTE01A1 is unchanged.
- No Reddit endpoint, OAuth scope or remote write is introduced.
- No Room schema or migration is introduced.
- Saved posts, direct community access and direct post access remain available.
- Existing mixed-feed filtering remains pre-presentation.
