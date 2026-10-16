# Issue #5: feed poster continuity preview v1

## Basis and scope

This is an additive local preview on the exact START_GUARD_V1 worktree, not the
alternative LIVE_START_GUARD_V1. The two uploaded source archives plus the Google
feed, 80%, paused-frame and original start-guard patches reproduce all original
start-guard manifest hashes. No main/head synchronization or variant replacement
is required.

The reviewed recording shows a brief black player area with a loading indicator
between an already visible poster and returning image content. That recording
alone does not identify a GPU, SurfaceView or decoder bug. Source inspection does
identify an avoidable image-load discontinuity: the unselected card, ContentFrame
shutter and outer poster instantiate independent RemoteImage compositions; each
starts with Loading before the asynchronous cache/load result is published.

## Change

For remote-image posters on the Google-managed feed, LocalVideoPlayer remembers
one FeedVideoPosterImageState<Bitmap> by post ID and exact image URI, before either
posterOnly or !surfaceSelected can return. The existing RemoteImageLoader still
loads and caches the image. Both the static poster and the native ContentFrame
shutter draw the same decoded result directly. Neither selection nor first-frame
visibility is an image-load key. The model remains for the lifetime of that
composed card, including while the shutter is not drawn. It is not a global cache,
does not allocate another decoder, and does not capture video frames. An evicted
card may load its poster again. Cancellation cannot publish a result after the
loading effect is cancelled; retry-generation checks reject superseded results.

The Google-feed branch now uses only the Media3 ContentFrame shutter. Its
keepContentOnReset=false and default SurfaceView type are unchanged. The separate
coordinator.renderedMediaId overlay is retained outside the feed but no longer
participates in feed shutter readiness. No fixed delays or fades are added.
Fullscreen, local posters and ordinary RemoteImage consumers retain their prior
loading and rendering paths. Failure presentation and explicit retry are retained.
The playback coordinator, start-guard effect body, SubshellState, native visibility
observer, thresholds (0.8f / 500 ms), pause retention, audio and preload are unchanged.

## Telemetry

feed_poster_image_state identifies the card-local model with an opaque identity
and a loading/ready/failed phase. No image URL, title or account data is logged.
feed_poster_shutter marks native shutter composition entry/exit; it is not a
physical-screen presentation measurement. For an already loaded card, expected
entry is visible=true image_state=ready, with no intervening ready -> loading
unless a retry or a different card/image identity is involved.

## Qualification

Primary tests: Issue5FeedPosterContinuityTest (state behavior and production wiring).
Predecessor regressions, run independently: Issue5GoogleFeedStartRaceTest,
Issue5PausedFeedPresentationTest, Issue5GoogleFeedVisibilityTest,
VideoPlaybackAudioPolicyTest and VideoPlaybackOwnershipPolicyTest. No predecessor
assertion is rewritten. Any failed required group prevents the canonical DEV1
build/install, but this is preview qualification, not public-release qualification.
No commit, push, release, issue closure, app-data clear or stable/DEV2 install.

A real Android build and device recording are required. JVM/state and structural
tests cannot prove rendered pixels. In particular, this repair removes a concrete
poster reload gap; it does not establish that all possible black flashes have the
same cause or that new-surface first-frame synchronization is universally correct.

## Runtime check

Use the same feed and several different videos. Let the poster appear before a
new video qualifies. Confirm poster -> video without the temporary black loading
state. Pause and requalify the SAME still-owned clip; the paused video frame must
remain, not revert to its poster. Move to another video and back; previous-card
pixels must not appear on the new card. Check immediate stop when leaving the
threshold, fast scroll, mute and one fullscreen round trip. Capture runtime.log
with a screen recording; acceptance is pending until that check.

## Upstream reference inspected

Media3 1.10.1 (the existing app dependency):
https://github.com/androidx/media/blob/1.10.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/ContentFrame.kt
https://github.com/androidx/media/blob/1.10.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/state/PresentationState.kt
https://github.com/androidx/media/blob/1.10.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/PlayerSurface.kt
