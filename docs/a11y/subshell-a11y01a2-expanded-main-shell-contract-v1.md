# A11Y01A2 — expanded/adaptive main shell

## Accepted predecessor

A11Y01A1 + A11Y01A1.1 are runtime accepted on the Pixel 6 at large font scale.

The compact phone shell now reflows its header, expands bottom navigation for
large text, uses `Subs` as the large-text visual label while retaining the full
`Communities` accessibility name, and fixes the high-confidence sub-48 dp action
targets covered by A1/A1.1.

## Probe gap

The root application still uses one phone-first main-shell architecture at every
window width: modal drawer + bottom navigation + effectively unbounded main
content. Search and Composer already contain isolated wider-layout logic, but the
Home / Communities / Inbox / You shell does not adapt as the window expands.

## A2 contract

A2 introduces the first root-level adaptive layout without changing accepted
product destinations or Reddit behavior.

1. `840 dp` is the expanded-main breakpoint, matching the Material expanded
   width-class boundary.
2. Below 840 dp, the accepted compact shell is preserved: modal drawer, bottom
   navigation, existing large-text behavior and existing content widths.
3. At or above 840 dp, main destinations replace bottom navigation with a
   Material 3 navigation rail.
4. The rail exposes the same Home / Subs / Inbox / You destinations, unread
   Inbox badge, selected state, navigation-label preference, and You long-press
   account-switch interaction.
5. The rail uses compact visual `Subs` while preserving the full
   `Communities` accessibility identity.
6. Expanded main content is centered and bounded to 840 dp after the 88 dp rail,
   preventing feed, Inbox and profile surfaces from stretching edge-to-edge on
   tablets, desktop windows and wide landscape configurations.
7. The main content bottom reserve no longer includes compact bottom-navigation
   height while the rail layout is active.
8. Context routes (post detail, search, community detail, settings, library,
   media, conversation) retain their current owners in A2; they are not forced
   into a phone bottom-navigation shell because that chrome was already hidden.
   The later New Modmail thread successor follows the same rule explicitly:
   `modmailThreadOpen` suppresses compact bottom navigation while thread detail
   owns its own chrome.
9. Existing modal drawer remains available from the main header. Permanent
   drawer / two-pane detail architectures are later adaptive successors.
10. No Reddit transport, auth, persistence, Room schema, feed query, navigation
    destination, Composer domain or remote behavior changes.

## Runtime acceptance

The Pixel 6 can exercise both sides of the breakpoint without another device:

- portrait: compact bottom navigation remains exactly the accepted A1.1 shell;
- landscape: if the current window width is >= 840 dp, bottom navigation
  disappears and the navigation rail appears.

Verify in landscape:

- rail Home / Subs / Inbox / You navigation;
- Inbox unread badge;
- Home feed centered rather than stretched across the whole wide window;
- Communities, Inbox and You remain usable and centered;
- drawer remains reachable from the header;
- You long-press still opens account switching;
- rotate portrait ↔ landscape without losing the current main destination.

A tablet / resizable-window smoke test remains desirable before final A11Y01
checkpoint, but is not required to validate the breakpoint mechanics on Pixel 6.
