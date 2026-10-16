# SUBSHELL MUTE01A1 — timed community mutes

## Product boundary

MUTE01A1 adds a Subshell-native, account-scoped community mute for spoiler and
noise control. It does not call or emulate an undocumented Reddit community
mute endpoint.

A mute can expire after 1 hour, 8 hours, 24 hours, 3 days, or 1 week; the user
can also choose an exact local date/time or mute indefinitely.

## Feed semantics

Active mutes suppress matching communities from mixed Home destination feeds:
Home, Popular, and All. Filtering occurs before `PostCard` items are emitted,
so a muted post is not briefly rendered and then removed. Surviving post order
and stable post IDs are preserved.

Saved posts are not suppressed. Direct community navigation, direct post
navigation, comments, search/navigation entry points, and cached post bytes are
not deleted or blocked.

When all loaded posts are suppressed by community mutes, the existing live-feed
auto-append policy may request additional pages within its bounded budget.

## Time and expiry

Expiry is an absolute epoch-millisecond timestamp. Indefinite entries have no
expiry. A timed mute is active only while `expiresAt > now`; it is inactive at
the exact expiry boundary.

The foreground Home projection arms a coroutine delay for the nearest active
expiry and refreshes the injected state clock when that deadline passes. Any
state read or account reload also ignores expired entries. Expired entries may
be pruned lazily and persisted without requiring WorkManager, alarms, or a
background service.

## Account scoping and persistence

Entries are stored in application-private SharedPreferences under a key derived
from the stable account ID. Switching accounts replaces the in-memory mute set
with that account's independently persisted entries.

The storage representation contains only canonical community name and optional
expiry. Duplicate names are normalized case-insensitively; an indefinite entry
wins over timed duplicates, otherwise the latest expiry wins. Invalid entries
are discarded.

MUTE01A1 does not add a Room schema or migration and does not alter Reddit
account authorization.

## Presentation

Post overflow exposes `Mute r/<community>…` and, while active,
`Unmute r/<community>`. Community top-bar overflow exposes the same state.

The Material bottom sheet explains the mixed-feed boundary and offers preset,
custom, and indefinite durations. Custom expiry uses the platform date and
24-hour-aware time pickers and rejects a past time.

## Explicitly absent

- no Reddit write transport or OAuth scope;
- no post/cache deletion;
- no filtering inside direct community feeds;
- no filtering of Saved;
- no WorkManager/alarm/background scheduler;
- no Room schema or migration;
- no cross-account mute leakage;
- no commit, main mutation, DEV1 source mutation, or remote operation in the
  preview slice.
