# ISSUE19 — Inline comment images open fullscreen

## Problem

Reddit comment `media_metadata` is already projected into `RemoteCommentMedia`, and the
comment renderer already displays those images. The missing behavior was interaction:
the rendered static image had no path into Subshell's fullscreen image experience.

## Contract

- A `RemoteCommentMediaKind.IMAGE` rendered by `CommentContent` is tappable.
- The tap opens `SubshellRoute.CommentMedia`, an overlay route carrying the originating
  comment id and the exact projected media URI.
- The URI is not replaced with a thumbnail or resized URL. The authoritative URI produced
  by the Reddit metadata projection is handed directly to fullscreen rendering.
- `CommentMediaViewerScreen` reuses the existing fullscreen system-bar behavior,
  `ZoomableContent`, `RemoteImage`, source-aspect-ratio reporting and control auto-hide.
- Post media and comment-image fullscreen share one Material 3 top chrome: the same
  scrim, typography, 48 dp Back target, container treatment and system-bar spacing.
- `SubshellRouteStack.surface` ignores `CommentMedia`, so the underlying Post route remains
  the canonical surface while fullscreen is open.
- Back is one normal overlay pop. The originating Post route, visit id and Compose
  saveable/lazy-list state are therefore retained rather than reconstructed as a new post visit.
- GIF comment media keeps its predecessor inline playback behavior; this slice adds the
  fullscreen affordance only to static comment images.
- Ordinary Markdown/text links remain owned by `MarkdownText`; the image click modifier
  exists only on the projected image preview.
- Missing or invalid media metadata produces no `RemoteCommentMedia` target and therefore
  no fullscreen affordance.
- The ephemeral comment-media overlay is intentionally not persisted as a post-media route.
  If the process dies while it is open, existing process restoration returns to the canonical
  underlying content surface instead of fabricating a post identity for comment media.

## Failure classification

- Primary: fixed when focused tests, contract and runtime preview pass.
- Predecessor: not established by this slice.
- Release-blocking: no, unless the candidate introduces a broader media/navigation regression.
