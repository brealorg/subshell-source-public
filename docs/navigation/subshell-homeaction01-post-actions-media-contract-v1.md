# HOMEACTION01 — post actions and media interaction contract v1

HOMEACTION01 completes the user-facing Home/Post action surface on top of VOTE01 and SCROLL01. The phase does not replace FEEDPOST-V2 navigation ownership, the existing media playback/download stack, or the shared error presentation family.

## Stable post action row

Every shared `PostCard` uses the same primary action row regardless of content kind:

`Vote · Comments · Save · Share`

Media-specific `Download` is not a primary post action. Duplicate `Share` is not kept in the overflow menu. `Copy link` remains an overflow action because copying a canonical Reddit permalink to the clipboard is distinct from opening Android's system sharesheet.

## Comments entry target

The comment count action opens the same typed post route as a normal Home post tap, but with `FeedPostEntryTarget.COMMENTS`. The destination waits until the comment-controls item is present in the measured lazy-list layout, then consumes that target exactly once for the route visit and moves to the comment controls. The target is never consumed before the destination is scrollable. A normal post-card tap keeps `FeedPostEntryTarget.TOP` semantics. Returning from the post must not replay the comment-entry request or disturb SCROLL01's restored Home anchor.

## Live Reddit Save / Unsave

Authenticated accounts use an explicit `POST_SAVE` OAuth capability with the narrow `save` scope. HOMEACTION01 extends the daily-driver grant with `save` while preserving all previous scopes. The write is owner-bound, pinned to the active account, serialized by the existing authorization architecture, and uses Reddit's absolute `/api/save` or `/api/unsave` mutation with a canonical `t3_` fullname.

The UI applies optimistic saved membership and rolls it back on a typed failure. The Library Saved tab projects saved posts from all currently known post surfaces rather than requiring the Library pager itself to already contain the post. Failed writes use the shared Reddit write error presentation and do not introduce blind transport retries.

## Post share and copy link

The primary `Share` action opens Android's `ACTION_SEND` sharesheet for the canonical Reddit post permalink. `Copy link` writes that same canonical permalink to the clipboard. These post-level actions are independent from direct media sharing.

Reddit listing mapping preserves both the provider permalink and `url_overridden_by_dest`. Cache payloads remain backward-compatible while persisting both targets.

## Link-post click ownership

Link posts retain separate click owners:

- tapping the post card opens the Subshell post route;
- tapping the link preview / link affordance opens the external HTTP(S) destination;
- tapping Comments opens the post at comments.

The link child owns its click instead of the surrounding post-card click swallowing it. The same external link affordance works from Home and from the shared PostScreen card.

## Media long-press actions

The shared `PostMedia` surface owns a Material media-action sheet, so Home and PostScreen behave identically. Long-pressing an image, GIF, video, or gallery item offers:

- **Share media** — share the exact media item through Android's sharesheet;
- **Share post link** — share the canonical Reddit post permalink;
- **Download** — save the exact media item using the existing media/download primitives.

For galleries, the long-pressed `GalleryItem` is the target. Remote image bytes are staged through the existing `RemoteImageLoader`. Remote adaptive video (DASH/HLS) is exported through Media3 Transformer to a real progressive MP4 before share or system-download, so an adaptive manifest must never be copied and mislabeled as an MP4. The persistent offline-download path rejects adaptive manifest URLs unless it has a real single-file progressive source. Shareable temporary files use a non-exported AndroidX `FileProvider` with URI grants. The post action row therefore does not vary just because a post contains downloadable media.

## Full-screen still-image gestures

Full-screen still images support pinch-to-zoom, bounded pan while zoomed, and double-tap zoom/reset. Pan translation is clamped to the scaled viewport so the image cannot be dragged arbitrarily off-screen into an all-black canvas. Remote still images use the same zoom container as packaged/local still images.

For a full-screen gallery, the current still image owns pan gestures while zoomed; horizontal pager swiping is enabled again when scale returns to fit. GIF/video playback remains non-zoomable.

`SUBSHELL_HOMEACTION01_FULLSCREEN_GALLERY_GESTURE_HANDOFF_V4` makes that ownership explicit at the Compose gesture boundary: `Modifier.transformable` may start a pan only after the still image is actually zoomed beyond fit scale. At fit scale, a one-finger horizontal drag remains available to the parent `HorizontalPager`; pinch zoom and double-tap zoom remain available, and once zoomed the image keeps bounded pan ownership until scale returns to fit.

## Comment share action

The per-comment overflow menu exposes **Share comment** in addition to Copy text and collapse/edit/delete actions. It shares a canonical Reddit deep link to that exact `t1_` comment, derived from the post permalink when available.

## Runtime polish v2

`SUBSHELL_HOMEACTION01_RUNTIME_POLISH_V2` records the first device-test corrections: measured-layout comments targeting, Saved Library projection, adaptive-video MP4 export, bounded zoom translation, and comment deep-link sharing.

## Predecessor preservation

- VOTE01 retains live post/comment voting and its Comfortable/Compact controls.
- SCROLL01 retains consume-once Home scroll-to-top behavior.
- HOMEACTION01 revalidates the SCROLL01 predecessor once. SCROLL01 already revalidates VOTE01, and VOTE01 already revalidates AUTH07B1, so HOMEACTION01 does not run AUTH07B1 a second redundant time.
- FEEDPOST-V2 retains typed post-route and Home scroll-anchor ownership.
- Existing media playback, persistent video download, and shared error handling remain the implementation foundation rather than parallel replacements.
## Runtime polish v3

- Comments entry waits until authenticated comment content is materialized (or the load is terminal) before consuming the one-shot `COMMENTS` target and scrolling the comment-controls anchor.
- Authenticated Saved projection excludes legacy local fixture posts; Reddit `t3_` posts remain eligible.
- Fullscreen still-image pan bounds are derived from the fitted source aspect ratio, so portrait/landscape letterboxing cannot be panned into an all-black viewport.
