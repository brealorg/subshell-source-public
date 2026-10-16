# SUBS04C1 drawer dynamic groups contract v1

SUBS04C1 is a narrow density repair on top of SUBS04C. It changes only the navigation drawer composition. Subscription transport, discovery, membership, account history, Saved, and the main Subs screen remain outside this successor.

## Drawer order

The scrolling drawer area is ordered as:

1. core Feeds: Home, Popular, All, Saved;
2. Communities;
3. Custom feeds, when the account has any;
4. Account: Inbox and You.

The fixed utilities below the scrolling area remain Show NSFW posts, Library, Settings, and theme selection.

Custom feeds can therefore never push the Communities entry below themselves.

## Dynamic group ownership

Dropdown Communities and Custom feeds are independent content groups but share one expansion policy. Both start collapsed for a fresh account-scoped drawer state. Opening one collapsible group closes the other collapsible group. Closing a group never opens another group.

Full list Communities remains intentionally expanded because the user explicitly selected Full list. Custom feeds still start collapsed and expand only after user action.

## Custom feeds

When custom feeds exist, the drawer shows one `Custom feeds · N` header. The individual feed rows are hidden until that header is expanded. The header reflects an active custom-feed route even while its children are collapsed. Existing custom-feed loading, ordering, selection, and navigation remain unchanged.

## Preserved boundaries

SUBS04C1 preserves:

- SUBS04C Search communities and canonical Discover behavior;
- Simple / Dropdown / Full list community modes;
- automatic View all communities semantics;
- Favorites / Recent / Other communities grouping and Hidden exclusion;
- SUBS03 membership and Join/Leave;
- custom-feed transport and account scoping;
- fixed NSFW / Library / Settings / theme utilities;
- account-history and Saved isolation.

## Runtime acceptance

1. Core feed entries stay first.
2. Communities appears before Custom feeds and Account.
3. One or many custom feeds occupy only the `Custom feeds · N` header while collapsed.
4. Custom feeds are collapsed when the drawer is first opened for an account.
5. Dropdown Communities are collapsed when the drawer is first opened for an account.
6. Opening Dropdown Communities closes an open Custom feeds group and vice versa.
7. Full list Communities remain expanded by explicit mode choice; Custom feeds remain collapsed until tapped.
8. Selecting a custom feed still opens the same feed and the header shows the active state when children are collapsed.
9. Inbox and You follow the dynamic community/feed groups.
10. Fixed utilities remain unchanged below the scrolling drawer.
