# MODERATION02A5 — Final UX and Material 3 hardening

## Scope

This finalization follows the A5A1–A5A7 runtime audit. It changes only
presentation and A5A5 target-selection clarity. It does not add a Reddit endpoint,
OAuth scope, persistence model, optimistic relationship state, or automatic retry.

## Functional correction: moderator invitation exact username fallback

Reddit people autocomplete is helpful identity discovery, but absence from an
autocomplete result is not treated as proof that a normalized Reddit username
does not exist. A5A5 already builds an invite request from a valid normalized,
non-self username that is not in the active moderator list.

The UI now makes that policy explicit. When autocomplete returns no profile match
for an otherwise valid target, the editor shows **No profile match** and explains
that the exact username can still be tried. Reddit remains authoritative when the
invitation is sent, and the existing explicit confirmation remains required.

Existing self-target and active-moderator exclusions are unchanged.

## Critical Material 3 corrections

The compact User management header now follows the same navigation hierarchy as
the Moderators child surface:

- leading Back navigation;
- one-line title and community context with ellipsis protection;
- compact refresh icon action instead of a competing text action;
- no decorative section icon consuming title width.

Ban, Mute, and Approve editors now use a leading Back icon because Back is
navigation, not a trailing low-emphasis command.

## Audit decisions intentionally preserved

The three Banned / Muted / Approved modes remain a
`SingleChoiceSegmentedButtonRow`, which matches their mutually-exclusive view
semantics.

The full-width `FilledTonalButton` for Ban / Mute / Approve is retained. Each is
the significant primary action for the currently selected management mode rather
than a routine navigation destination.

The Mod tools root hierarchy is retained: management destinations are visually
separate from the Find reports action and queue content.

User-management rows already route `avatarImageUri` through
`RemoteIdentityImage`. Letter fallbacks seen at runtime therefore do not by
themselves prove a presentation defect; Reddit relationship payloads may omit an
image. This finalization deliberately does not add N+1 profile reads or a new
identity transport.

Small 18–20 dp icons discovered by the static audit are contained by controls
with appropriate interactive sizing or are non-interactive artwork. They are not
treated as touch targets themselves. The 44 dp identity image is likewise
non-interactive.

The existing single Mod tools bottom-sheet owner, destructive moderator-removal
semantics, authoritative relationship readback, and no-blind-retry policies are
unchanged.
