# A11Y01A4.1 — Tablet mode setting + sticky preview integration

## Runtime predecessor

A11Y01A4 compiled, installed and was manually accepted for Home → Post and
Inbox → Post list/detail. The previous runner then restored DEV2 source only
because an unrelated parallel DEV1 change tripped an overly strict fingerprint
fence.

A4.1 therefore reapplies the accepted A4 source and adds a user preference
instead of first performing a separate source-only restore.

## Product preference

`Settings → Navigation & gestures` exposes **Tablet mode** with two choices:

- **Split** — default; the accepted A4 list/detail behavior.
- **Single** — the accepted A3 behavior: selected contextual content replaces the
  list and is shown as one centered, bounded surface.

The preference applies only when the existing expanded-window breakpoint is
active. Compact phone behavior is unchanged.

The internal persistence model is `TabletLayoutMode.SPLIT` / `SINGLE`.
Older settings and backups without the new key default to `SPLIT`.

## Existing sticky preview host

A4.1 does not add a second settings preview.

The existing sticky Navigation preview becomes a contextual host:

- normal navigation controls show the existing phone/chrome preview;
- tapping either Tablet mode chip switches the same sticky host to a compact
  landscape tablet preview;
- changing between Split and Single recomposes that preview immediately;
- interacting with another navigation control switches the host back to the
  existing navigation preview.

The tablet preview is presentation-only and uses Material theme colors. It does
not use screenshots or mutate navigation state.

## Persistence

The mode is part of `SubshellSettings`, individual preference persistence,
capture/restore and settings backup round-trip. No Room schema is involved.

## Deliberate deferral

The global Settings search index is not expanded in A4.1. The setting is
available directly in Navigation & gestures. Search-index successor work can be
done with the broader settings-index contract rather than weakening its
currently exact row-count predecessor gate inside this adaptive slice.

## Runtime acceptance

1. Open Settings → Navigation & gestures.
2. Existing sticky preview initially behaves as before.
3. Tap Split: the same preview container shows the two-pane tablet snapshot.
4. Tap Single: the same preview container changes to the centered single surface.
5. Touch another navigation setting: the preview returns to the normal
   navigation/chrome example.
6. Leave settings, rotate landscape and open Home → Post:
   - Split keeps Home on the left;
   - Single opens the centered A3 Post surface with no left list.
7. Repeat quickly for Inbox if convenient.
8. Restart the app and confirm the chosen mode persists.
