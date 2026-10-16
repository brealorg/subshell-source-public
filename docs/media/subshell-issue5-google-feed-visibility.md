# Issue 5: Google-reference feed video visibility

Reference: Android Developers `onVisibilityChanged`, VideoFeed example with a viewport:
https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/onVisibilityChanged.modifier

## Scope and authority

HomeScreen and CommunityScreen supply a LayoutBoundsHolder for their actual list
area. The full-bleed Home host subtracts its visible, translated scaffold chrome;
context/pane hosts are already laid out inside their scaffold/pane boundaries.
Trailing content padding for scrolling/FAB space is not treated as an obstruction.
No list padding, scroll anchor, header, paging or user preference is changed.

Each actual video frame observes the native modifier with minDurationMs = 500 and
minFractionVisible = 0.8f. This is an explicitly chosen Subshell trial value, not a
Google recommendation: the first preview used the sample's 1f and felt too rigid. The observer remains mounted when the player switches
from poster to playback. Compose, not application timers or area calculations,
provides the start/stop eligibility. False does not acquire a local delay.

A separate native zero-duration/zero-fraction presence observation supports
explicit tap-to-play and preserves manual pause until the item leaves. Presence
cannot grant autoplay permission. This is Subshell integration, not part of the
Google example's autoplay threshold.

While this feed owns playback, legacy direction, fling, edge, retained-percentage,
background auto-resume and scrubbing-owner exceptions cannot grant autoplay.
Preload selection and the retained Media3 player remain in place. Mute and stored
autoplay settings remain separate. This change does not redefine the existing
Wi-Fi-only network policy.

The app-specific single-owner rule retains an eligible owner; otherwise it selects
the first eligible video in feed order. An empty eligible set stops playback even
when there is no next video. Visibility exit also synchronously pauses the current
matching Media3 media ID; source and position remain available for reuse.

The standalone post/fullscreen playback path is not replaced by this feed policy.
A stale feed callback cannot control a fullscreen player or clear its manual pause.
Returning to a Google-managed feed terminates the old route handoff and requires
fresh feed qualification; no stale handoff exemption can keep sound playing.

## Qualification

Focused JVM tests cover eligibility authority, independent stop, fling/edge
non-interference, one owner, duplicate/disposed surfaces, stale callbacks,
manual play/pause, foreground requalification and fullscreen separation.
Structural checks require the native modifier and explicit viewport, and reject
application-side dwell timers. These are not on-device timing tests.

Runtime review: scroll a real video-containing subreddit in both directions,
pass clips quickly, hold most of a clip visible, scroll it below the threshold, stop on
an image/text item, and check manual pause and mute. Test fullscreen separately as
a regression check, not as the reproduction of issue 5.

## 80% comparison and temporary debug observations

The only playback parameter changed from preview v1 is minFractionVisible (1f to
0.8f). Dwell, immediate visibility exit, arbitration, viewport, preload, mute,
manual controls and poster replacement behavior are unchanged.

In debug builds only, onLayoutRectChanged observes the same frame and records
RelativeLayoutBounds.fractionVisibleIn against the same explicit viewport.
Geometry samples are logged at most once per 100 ms per moving frame; native
visibility callbacks are logged separately without this sampling limit. There is
no new eligibility calculation and diagnostics cannot start or pause the player.
Bounds age is the time since the last frame layout sample; an unchanged frame can
legitimately have an older sample. Callback/layout ordering is not atomic.

The existing subshell.media.perf stream adds selected media, player readiness,
playing state and mute to correlate geometry, qualification, and actual playback.
Only the trace tag for the chosen DEV1 process is captured. Media IDs and frame /
viewport geometry are included; no URLs or post text are added by these probes.
This is not a measurement of sound reaching the speaker or content occlusion by
arbitrary floating overlays. Recheck final feel without the temporary diagnostics
before release if logging itself appears to affect scroll performance.

## Paused-surface presentation preview

No visibility setting changed: 0.8f, 500 ms, native exit, viewport and single-player
selection remain as in the 80% comparison. The native visibility callback still
calls pauseForFeedVisibility immediately. Mute, preload and player source handling
are unchanged.

LocalVideoPlayer now keeps its already attached Google-feed surface composed while
paused, provided that the coordinator still owns that exact surface/media pair and
no different media ID has been selected. Retention never calls attach or play.
Changing the telemetry transition ID no longer disposes that feed surface. Actual
composition disposal, a different selected video, background/owner loss or leaving
the feed still ends retention. Only the current surface is retained: this is NOT a
bitmap cache of previously seen videos, and an evicted/recycled card can show its
own poster again. No extra player or decoder is allocated.

The inline Google-feed branch uses Media3 ContentFrame with its PresentationState
shutter. The shutter shows that post's poster until a new surface has rendered its
first frame, rather than exposing a black surface based on a stale media-ID-only
flag. keepContentOnReset remains false to avoid keeping content across resets to a
different clip. Existing standalone-post and fullscreen branches remain unchanged.

Reference (Media3 1.10.1, the app's existing dependency):
https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/ContentFrame.composable
https://github.com/androidx/media/blob/1.10.1/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/ContentFrame.kt

The feed_surface_retention trace is observational only. Confirm with a screen
recording that pause/requalification of the SAME still-mounted video no longer
jumps to its poster and remounts a black surface. Also check that selecting another
video never leaves it visible inside the previous card. Unit/structural tests do
not prove SurfaceView rendering or physical audio timing on a device.

## Queued-start cancellation repair

The paused-frame runtime trace contained qualification -> revocation -> a queued
attach(playWhenReady=true). The effect had captured its playing flag before the
visibility callback ran. This is an integration ordering error, not a change to
the native visibility threshold or dwell.

Feed effects now capture route/visibility epoch, then synchronously re-read the
existing current selection and manual-pause/foreground state when executing.
applyCurrentGoogleFeedVideoRequest dispatches only that current authority. A stale
route or epoch is ignored, so an old feed effect cannot operate a newer fullscreen
or foreground session. There is no additional player, timer, eligibility threshold,
queue, or permission store. onApply must remain synchronous with its authority check.

A revoked request never attaches a different source. If the exact surface/media
pair still owns the player, it is paused again via pauseForFeedVisibility, which
also clears pending playWhenReady during buffering without detaching its surface.
The matching owner's manually paused presentation is also paused without a fresh
attach. An explicit manual-pause transfer may still attach with playWhenReady=false.
Other owners and media are not paused by this delayed effect. The feed uses current
mute/transition/rank at dispatch; the standalone post/fullscreen attach branch stays
unchanged.

The native 0.8f/500 ms observer, immediate false callback, viewport, preload,
selection rules, and ContentFrame presentation are unchanged by this repair.
The brief black flash during a different-media/surface switch remains a separate
visual finding, not something this race repair claims to fix.

Focused tests queue requests against real SubshellState, change qualification before
executing the request, and record playback commands with a fake player. They also
cover retained pause, buffering, a replacement owner, manual play/pause, autoplay
NEVER, foreground epoch and route changes. They do not measure physical audio or
SurfaceView pixels; runtime remains required.

Reference for effect lifetime and keys:
https://developer.android.com/develop/ui/compose/side-effects
Reference for the Media3 application-thread boundary:
https://developer.android.com/media/media3/exoplayer/hello-world#threading
