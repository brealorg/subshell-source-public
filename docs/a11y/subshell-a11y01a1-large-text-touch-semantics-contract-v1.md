# A11Y01A1 — large text, touch targets and core action semantics

## Probe evidence

The A11Y01 source probe starts from `main` / `dev2/a11y01` at
`b5f97330cab23f745d34040ba846b9b1fede428d`.

The Pixel 6 runtime baseline reports 1080×2400, 420 dpi and `font_scale=1.15`.

The root application already has useful accessibility foundations: edge-to-edge
window handling, explicit system-bar icon contrast, an app-level Reduce motion
setting, a polite live region for persistent viewport errors, labelled main
navigation icons, and interaction-side aware drawer placement.

The probe also exposes several high-confidence gaps that can be corrected without
redesigning accepted information architecture:

- the main header is hard-limited to 60 dp and its feed-sort target to 48 dp even
  though it can contain two independently scaled text lines;
- the bottom navigation is hard-limited to a 64 dp content height;
- post vote controls use 40 dp / 36 dp button layouts;
- the comments action is a custom clickable row without an explicit button role
  and with a numeric label but no Comments semantic label;
- Inbox rows do not expose per-item Read / Unread state and use a 42 dp leading
  action;
- live Composer v4 gallery remove/reorder controls use 40 dp icon buttons.

## A1 contract

A11Y01A1 is deliberately a phone-safe accessibility successor, not the expanded
window redesign.

1. Main header and feed-sort target use minimum heights instead of fixed heights,
   allowing text to grow vertically at large font scales.
2. Bottom navigation keeps the accepted 64 dp compact height below 1.3 font scale
   and expands to 88 dp at or above 1.3.
3. Post upvote/downvote targets are 48 dp and expose the current action:
   `Upvote` / `Remove upvote`, `Downvote` / `Remove downvote`.
4. The comments action has a 48 dp minimum height, explicit Button role and
   `Comments` semantic label while retaining the visible count.
5. Inbox exposes Read / Unread state, labels the leading open action, and gives
   that action a 48 dp target.
6. Composer v4 gallery remove/reorder icon buttons become 48 dp targets.
7. No feed, Reddit transport, auth, persistence, navigation destination, Room
   schema or remote behavior changes.
8. Expanded-width navigation / content-width adaptation remains A11Y01A2+ work.

## Manual runtime acceptance

At the normal device font scale, accepted Subshell visual hierarchy must remain
recognizable and navigation must not regress.

At a large system font setting, verify:

- Home title / sort affordance remains legible and tappable without clipping;
- main bottom navigation remains usable without label overlap;
- post vote/comments/save/share row remains usable on the Pixel 6 width;
- Inbox cards do not overlap and Read / Unread visual behavior is unchanged;
- a gallery composer keeps remove/reorder controls usable.

TalkBack acceptance should additionally verify the post vote action names,
Comments, Inbox read state, and the labelled gallery actions.
