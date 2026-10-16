# SCROLL01 — Home scroll-to-top edge trigger

SCROLL01 fixes a Home feed navigation regression where an already-consumed Home reselect request could fire again whenever the Home screen re-entered composition after opening a post.

## Reproduction

1. Reselect Home while already on Home. This legitimately requests one animated scroll to the first item.
2. Scroll down and open a post.
3. Return to Home.
4. Before SCROLL01, `LaunchedEffect(homeScrollToTopRequest)` saw the still-positive historical request and animated to item 0 again. The saved feed anchor was restored first, so the user visibly returned to the correct position and was then pulled to the top.

## Contract

- `homeScrollToTopRequest` remains a monotonic edge/request counter owned by `SubshellState`.
- `handledHomeScrollToTopRequest` records the most recent request that actually triggered the Home animation.
- `consumeHomeScrollToTopRequest()` returns `true` exactly once for each new request value and `false` for historical values.
- Home re-entry, post push/pop, or recomposition must not replay an already-consumed request.
- A later explicit Home reselect still creates a new request and triggers scroll-to-top once.
- Existing FEEDPOST-V2C stable-key/index/pixel scroll anchors remain authoritative for post-back restoration.
- VOTE01 remains unchanged except for making its version gate successor-aware.

## Runtime acceptance

Manual reproduction should show:

- scrolling down, opening a post, and returning restores the prior Home position without any subsequent upward animation;
- tapping Home while already on Home still scrolls to the top once;
- after that scroll-to-top completes, opening and closing another post does not replay it.
