# subshell FEEDPOST-V2C scroll anchor contract v1

FEEDPOST-V2C gives each feed-like surface its own deterministic scroll anchor. It builds on the typed post origin from V2A and the canonical route stack from V2B.

## Anchor identity

Each anchor contains:

- the stable item key at the top of the viewport,
- a fallback item index,
- the exact first-visible-item pixel offset.

Stable identity wins when a list is reordered. The fallback index is used when the stable item no longer exists, and is clamped to the current list.

## Independent keys

Anchors are isolated by the complete surface identity:

- Home, Popular, All and Saved plus feed sort,
- normalized community name plus community sort,
- normalized search query plus destination, origin community, scope and area,
- normalized username plus profile tab and activity tab,
- Library tab,
- the retained legacy You surface tab.

Changing a sort, query, scope or tab switches to that surface's own anchor instead of reusing the previous list state.

## Post return

Screens continuously record their first visible stable key, fallback index and pixel offset. Disposal records the final position synchronously when a Post route is pushed. When Back pops the Post route, the underlying surface recreates its LazyListState from the same keyed anchor.

## Boundaries

V2C keeps the V2A origin model and V2B route stack unchanged. It does not add Reddit networking and does not modify media ownership. The in-memory anchor registry intentionally does not survive process death; full route and anchor serialization belongs to FEEDPOST-V2E.
