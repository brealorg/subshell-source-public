# SUBS04B integrated discovery surface contract v1

SUBS04B turns the authenticated Subs > Subs destination into an integrated browse-and-manage surface without replacing the existing SUBS04A1/A2 Discover route.

## Product boundary

- The primary information architecture remains Favorites, Subs, Recent, Feeds.
- Discover communities is the first content section on the authenticated Subs list.
- The Subscriptions section keeps the authoritative visible joined count.
- Hidden subscriptions is a compact management row inside the Subscriptions section, immediately before the joined subscription rows.
- The inline surface is backed by the existing Reddit popular community discovery state; it does not invent personalized recommendations.
- The top-bar search icon remains the direct search entry and opens the canonical Discover route with focus requested.
- See all on the inline surface opens the same canonical Discover route without forcing search focus.

## Inline candidate policy

The inline surface:

- preserves the provider order of popular discovery results;
- excludes communities already joined by the active Reddit account;
- respects the existing NSFW visibility setting;
- deduplicates communities case-insensitively;
- is bounded to six candidates;
- falls back to a browse/search affordance when suggestions are unavailable.

## Membership

Join and leave actions reuse the existing SUBS03 live membership pipeline. Buttons are enabled only when fresh subscription membership truth is available and no conflicting membership operation is active. Optimistic projection, authorization-required state, failure presentation, and exact account/community ownership remain shared with the full Discover surface.

## Existing discovery preservation

SUBS04A1/A2 remain canonical for the full Discover screen:

- blank Discover loads Popular on Reddit;
- typeahead remains debounced;
- full search retains Best match and Most active;
- Join/Leave remains available in full results;
- top-bar search and route-stack/back semantics are unchanged.

## Runtime acceptance

1. Subs > Subs shows Discover communities first, then Subscriptions count, Hidden subscriptions, and the joined list.
2. Inline cards never repeat a currently joined community.
3. See all opens Discover communities with Popular on Reddit.
4. Top-bar search opens the same screen with search focused.
5. Join from an inline card updates membership through the existing live Reddit flow.
6. Returning from Discover preserves the Subs list route and scroll state.

## Density polish v2

The integrated surface is deliberately subordinate to the joined subscriptions list:

- Hidden subscriptions is a single compact row; supporting copy and the redundant chevron are removed.
- Discover communities uses the page background rather than a second full-width container card.
- The inline Popular on Reddit subtitle is removed from the normal populated state.
- Suggestion cards are compact, horizontally scrollable previews with icon, subreddit name, member count, and membership action only.
- Descriptions are omitted from inline suggestions; full community context remains available by opening the community or full Discover surface.
- Join/Joined uses a compact low-emphasis action treatment so the discovery strip does not visually dominate the user's subscriptions.
- Card width is intentionally narrow enough to expose the next candidate and communicate horizontal scrolling.

## Runtime acceptance v2

1. Hidden subscriptions is visibly shorter than the v1 card and remains inside the Subscriptions section.
2. Discover communities has no enclosing full-width card and no separate Popular on Reddit subtitle when candidates are present.
3. At least part of a third discovery candidate is visible on a typical phone viewport.
4. Inline cards show no description text and remain materially shorter than v1.
5. Join/Joined, See all, top-bar search, full Discover, NSFW filtering, and joined-community exclusion remain unchanged functionally.
6. The first joined subscription should remain visible or nearly visible on initial Subs viewport at normal font/display sizing.

## Discovery-first hierarchy v3

The authenticated Subs page uses a discovery-first content hierarchy without changing the underlying discovery or membership behavior:

- Discover communities is the first content block beneath the Favorites / Subs / Recent / Feeds tabs.
- The Subscriptions heading follows the discovery strip and remains the owner of the visible joined count.
- Hidden subscriptions follows the Subscriptions heading as a compact special-management row.
- Normal joined communities follow Hidden subscriptions directly.
- Subscription failure/empty presentation remains within the Subscriptions section rather than interrupting Discover.
- See all, top-bar search, Join/Joined, NSFW filtering, and the canonical Discover route are unchanged.

## Runtime acceptance v3

1. Initial Subs viewport reads in this order: Discover communities, Subscriptions, Hidden subscriptions, joined subreddit rows.
2. Discover remains visually compact and horizontally scrollable.
3. Hidden remains a single-line row and does not precede Discover.
4. The visible joined count remains attached to the Subscriptions heading.
5. Join/Joined and See all behavior remain identical to v2.

## Compact Join action v4

The discovery-first hierarchy is locked. This pass changes only the inline membership action emphasis:

- Join/Joined is a compact outlined action rather than a full-width tonal bar.
- The action remains centered within each discovery card and keeps the same SUBS03 membership state, authorization, progress, and failure semantics.
- Card width, card height, discovery ordering, candidate policy, See all, top-bar search, and the Subscriptions / Hidden hierarchy are unchanged.
- The intent is visual hierarchy only: subreddit identity should dominate the preview card, while membership remains clearly available but secondary.

## Runtime acceptance v4

1. Inline Join/Joined no longer visually spans the card width.
2. The action remains comfortably tappable and centered.
3. Joining and leaving still update through the existing live Reddit membership pipeline.
4. Discovery-first ordering and the compact Hidden subscriptions row remain unchanged.
