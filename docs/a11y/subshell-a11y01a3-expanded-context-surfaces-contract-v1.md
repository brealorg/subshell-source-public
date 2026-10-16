# A11Y01A3 — expanded contextual surfaces

## Accepted predecessor

A11Y01A1, A11Y01A1.1 and A11Y01A2 are runtime accepted.

A2 establishes the root adaptive shell at the 840 dp expanded breakpoint:
compact windows retain bottom navigation, while expanded main destinations use a
navigation rail and bounded main content.

The remaining gap is contextual navigation. Opening a post, user, community,
search, Library, Settings, Explore or an Inbox conversation still gives those
screens the full available landscape/tablet width.

## A3 contract

A3 adds a shared expanded contextual surface. It deliberately does **not**
introduce list/detail two-pane state yet.

1. The existing 840 dp expanded-window breakpoint is reused.
2. Compact contextual routes retain their exact existing scaffold-padding
   contract; A3 is a no-op below the breakpoint.
3. Expanded contextual content is horizontally centered.
4. Reading/detail surfaces are bounded to 840 dp:
   Post, contextual User and Library.
5. Utility/conversation surfaces are bounded to 720 dp:
   Settings, Help and Inbox Conversation.
6. Discovery/community surfaces are bounded to 960 dp:
   Search, Community and Community Explore.
7. Media remains full-screen.
8. The root A2 navigation rail behavior is unchanged.
9. Context routes remain context routes: A3 does not force the navigation rail
   to remain visible after entering a post/community/search/detail destination.
10. No Reddit transport, auth, persistence, Room schema, feed query, navigation
    destination, account-switch, Composer domain or remote behavior changes.
11. Two-pane feed→post, Inbox→conversation, and community list/detail behavior
    remains a later A11Y01 adaptive successor.

## Runtime acceptance

Use the Pixel 6 in landscape at the same expanded width used for A2.

Verify:

- Home remains the accepted A2 rail + bounded main shell.
- Open a post: the detail/comments surface is centered instead of stretching
  across the full landscape width.
- Open a community: header/feed is centered with a wider 960 dp discovery bound.
- Search remains centered and does not become wider than its existing design.
- Open You → Library: history content is centered at the reading bound.
- Open Settings: preference content is visibly narrower than feed/detail
  content.
- Inbox → conversation uses the narrower utility bound.
- Rotate back to portrait from each sampled context: compact behavior remains
  unchanged and back navigation still returns to the correct owner.

A3 is complete when the contextual layouts look intentional rather than merely
being phone screens stretched to the available width.
