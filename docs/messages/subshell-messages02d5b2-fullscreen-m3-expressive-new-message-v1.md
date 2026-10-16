# MESSAGES02D5B2 — full-screen Material 3 Expressive New message

D5B proved that the existing reply composer could be reused, but runtime review showed that a
large modal bottom sheet still made starting a chat feel like a form rather than a first-class
conversation workflow. The raw Markdown source representation was also the wrong default for a
normal messaging surface.

D5B2 adopts the selected mockup 1 direction.

## Navigation and presentation

- `New message` is a full-screen context destination reached from the authenticated Inbox Message
  FAB.
- The destination is represented by `SecondaryDestination.NEW_MESSAGE`, so it uses the existing
  route stack, system back behavior, process snapshot encoding and account-scoped navigation model.
- It is rendered before the main application `Scaffold`, matching the full-screen treatment already
  used by the post composer. Inbox top/bottom chrome and the old modal sheet are therefore absent.
- The old `state.composerVisible` bottom-sheet presentation is no longer the New message entry path.

## Material 3 Expressive direction

- The first phase uses a large, rounded recipient search surface and Material surface-container
  hierarchy.
- Search results are touch-sized identity surfaces with Reddit avatar, username and profile
  description.
- After selection the recipient becomes a compact high-emphasis identity surface with an explicit
  change-recipient affordance.
- The remaining conversation space is intentionally quiet and the composer is anchored at the
  bottom of the destination.
- Failures use a compact `errorContainer` notice immediately above the composer instead of a large
  free-floating text block.

## Visual-first editor

The first-message editor keeps raw Markdown as the transport representation, but **Preview is the
default editing presentation**.

For simple inline formatting the visual transformation hides Markdown delimiters while applying
the corresponding visual style:

- `**bold**`
- `_italic_`
- inline code
- link labels

The toolbar still edits the canonical Markdown text, so Reddit receives the same text semantics.
`Markdown` is an explicit secondary mode available from the Preview chip / overflow for complex or
manual source editing. Link insertion deliberately enters Markdown mode because its URL requires
source-level editing.

This is intentionally a visual-first editor rather than a destructive conversion to rich-text
storage.

## Preserved boundaries

D5B2 changes presentation and route wiring only. It does **not** change:

- Matrix user-directory search;
- Direct-room lookup or its parser;
- `createRoom` policy;
- first-message `m.text` transport;
- shared Reddit web token/session cache;
- non-retrying write policy;
- outcome-unknown behavior;
- read receipts / Seen;
- media capability.

The known D5A `room_lookup ... kind=protocol` runtime failure remains isolated and is deferred until
the D5B2 visual direction is approved.

<!-- SUBSHELL_MESSAGES02D5B2_RUNTIME_UI_APPROVAL_20260829 -->
## Runtime UI approval

The full-screen Material 3 Expressive direction was runtime-reviewed and accepted for this feature
slice: recipient search/results, selected-recipient identity surface, bottom composer and visual
Preview editing are the approved direction.

Known UX debt is intentionally deferred to the later whole-app Material 3 Expressive / composer
audit: inserting a link currently switches to Markdown source so the URL can be edited. The later
composer unification should provide a proper visual insert/edit-link surface instead.

Image/GIF actions are also intentionally absent from the first-message composer in this slice.
Media support is the next functional slice and should reuse the already proven Direct Chat media
pipeline after a chat room is established rather than invent a new transport.
