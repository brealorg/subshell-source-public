# A11Y01A4 — Tablet mode / expanded list-detail

## Accepted predecessor

A11Y01A1, A11Y01A1.1, A11Y01A2 and A11Y01A3 are runtime accepted.

A2 establishes the expanded main shell and A3 gives contextual destinations
purpose-specific centered width bounds. The remaining desktop/tablet gap is
navigation continuity: opening a detail still replaces the entire list context.

## A4 slice

A4 introduces **Tablet mode**: the first real list/detail behavior on expanded windows.

### Home → Post

For a post opened from the normal Home main feed:

- the Home feed remains visible in a fixed 360 dp list pane;
- the selected Post + comments render in the remaining detail pane;
- selecting another Home post replaces the current detail instead of pushing
  another route onto the back stack;
- Comments entry selection uses the same replacement behavior and preserves the
  requested comments entry target;
- custom-feed posts retain the A3 contextual detail behavior in this slice.

### Inbox → Detail

For Inbox detail:

- Inbox remains visible in the 360 dp list pane;
- message/fallback detail renders as Conversation in the right pane;
- activity items that resolve to posts render Post detail in the right pane;
- selecting another Inbox item replaces the current right-hand detail instead
  of growing route depth.

### Layout

- list/detail is active only at the existing expanded-window breakpoint;
- the combined list/detail surface is centered and capped at 1200 dp;
- compact portrait behavior remains unchanged;
- the A3 centered contextual surface remains the fallback for unsupported post
  origins such as Community, Search, User, Library and custom feeds;
- the root navigation rail is not duplicated inside contextual list/detail;
  Back closes the detail and returns to the owning main list.

## Non-goals

A4 does not yet add Community→Post, Search→Post, Library→Post or
Subscriptions→Community two-pane behavior. It also does not change Reddit
transport, auth, persistence, Room schema, feed queries, content filtering,
account switching or remote behavior.

## Runtime acceptance

Use Pixel 6 landscape at the same expanded width used for A2/A3.

1. Home → open a post: Home stays on the left and Post opens on the right.
2. Scroll/select another Home post: only the right detail changes.
3. Press Back: return to normal expanded Home with its rail.
4. Inbox → Messages → open a conversation: Inbox stays on the left.
5. Select another Inbox message: only the right detail changes.
6. If convenient, open an Inbox activity item that resolves to a post and
   confirm the right pane changes from Conversation to Post without losing the
   Inbox list.
7. Rotate an open two-pane detail to portrait: compact single-detail navigation
   remains usable; rotate back and the route remains coherent.

A4 is accepted when list/detail feels like one continuous wide-screen workspace,
not two independent phone screenshots placed side by side.
