package app.subshell.prototype.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.spatial.RelativeLayoutBounds
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.media.MediaPerformanceTrace
import androidx.compose.ui.unit.dp
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator

// Diagnostic storage only: plain fields, never playback eligibility or Compose state.
private class GoogleFeedVisibilityTrace {
    var bounds: RelativeLayoutBounds? = null
    var measuredAtMs = 0L
    var nextSampleAtMs = 0L
}

private class GoogleFeedVideoViewportScope {
    val bounds = LayoutBoundsHolder()
    val owner = Any()
}

private val LocalGoogleFeedVideoViewport =
    staticCompositionLocalOf<GoogleFeedVideoViewportScope?> { null }

/**
 * VIDEOTABSWITCH01: the main tab a pane of the tab switch belongs to (null
 * outside the main tabs). A pane fading out after a tap on another tab is
 * still composed, but it is no longer the tab shown.
 */
internal val LocalMainPaneDestination = staticCompositionLocalOf<MainDestination?> { null }

internal fun visibleChromePadding(
    totalPadding: androidx.compose.ui.unit.Dp,
    hiddenFraction: Float,
    minimumPadding: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp =
    (totalPadding * (1f - hiddenFraction)).coerceAtLeast(minimumPadding)

/**
 * A retained Activity player may outlive the post presentation that owned it.
 * A returning feed must not let that player advance until the same media has
 * regained feed playback authority through the normal visibility policy.
 */
internal fun returnedFeedPlayerNeedsPause(
    playerMediaId: String?,
    selectedMediaId: String?,
): Boolean = playerMediaId != null && playerMediaId != selectedMediaId

/**
 * The viewport is the list's actual bounds, less any chrome drawn over that list.
 * Content padding (for example the trailing FAB spacer) is not viewport occlusion.
 */
@Composable
internal fun GoogleFeedVideoViewport(
    state: SubshellState,
    sessionKey: Any,
    modifier: Modifier = Modifier,
    viewportPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val scope = remember(state, sessionKey) { GoogleFeedVideoViewportScope() }
    val coordinator = LocalVideoPlaybackCoordinator.current
    // A list behind an expanded post detail must not compete with the detail player.
    // VIDEOTABSWITCH01_LEAVING_TAB_PAUSES_AT_ONCE_V1: a feed in a tab that is
    // fading out does not claim the next tab's route; its video pauses on the
    // tap instead of playing on until the pane is gone (the surface showed
    // later frames over the incoming tab).
    val paneDestination = LocalMainPaneDestination.current
    val paneShown = paneDestination == null || state.destination == paneDestination
    val enabled = paneShown && state.selectedPost == null && state.selectedMediaPost == null
    val route = state.currentNavigationRoute
    DisposableEffect(state, scope, enabled, route) {
        if (enabled) {
            // Presentation handoff may preserve the Activity-retained player even
            // after state has already released the old feed candidate. Never let
            // that orphaned player advance audibly while the returning feed waits
            // for Google's visibility qualification.
            val playerMediaId = coordinator.uiState.mediaId
            state.registerGoogleFeedVideoViewport(scope.owner)
            if (
                returnedFeedPlayerNeedsPause(
                    playerMediaId = playerMediaId,
                    selectedMediaId = state.activeAnimatedMediaId,
                )
            ) {
                coordinator.pauseForFeedVisibility(requireNotNull(playerMediaId))
            }
        }
        onDispose {
            val wasCurrent = state.isGoogleFeedVideoViewportCurrent(scope.owner)
            val heldPlayback = state.googleFeedVideoViewportHeldPlayback(scope.owner)
            val previous = state.activeAnimatedMediaId
            state.removeGoogleFeedVideoViewport(scope.owner)
            if (wasCurrent && previous != null && state.activeAnimatedMediaId != previous) {
                coordinator.pauseForFeedVisibility(previous)
            } else if (heldPlayback && paneDestination != null && state.destination != paneDestination) {
                // The tab was left: whatever this feed had playing stops now.
                (previous ?: coordinator.uiState.mediaId)?.let(coordinator::pauseForFeedVisibility)
            }
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(viewportPadding)
                .layoutBounds(scope.bounds),
        )
        CompositionLocalProvider(
            LocalGoogleFeedVideoViewport provides scope.takeIf { enabled },
            content = content,
        )
    }
}

/**
 * A stable wrapper outside LocalVideoPlayer's poster/playing branches: selecting a
 * player must not detach/recreate the visibility observer or restart its dwell timer.
 *
 * Autoplay uses Google's native modifier with 500 ms and an 80% trial threshold.
 * The sample's 1f threshold was too rigid in the observed mixed-feed scroll test.
 * Debug geometry logging is observational and cannot grant playback permission.
 * The immediate presence signal is only for explicit tap-to-play and manual pause;
 * it never grants autoplay permission. No local timers or fraction calculations.
 */
@Composable
internal fun GoogleFeedVideoFrame(
    mediaId: String,
    state: SubshellState,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val scope = LocalGoogleFeedVideoViewport.current
    if (scope == null) {
        content()
        return
    }
    val coordinator = LocalVideoPlaybackCoordinator.current
    key(scope.owner, mediaId, state.googleFeedVisibilityEpoch, enabled) {
        val surfaceOwner = remember { Any() }
        val observe = enabled && state.googleFeedVideoForeground
        val diagnostics = remember {
            if (BuildConfig.DEBUG) GoogleFeedVisibilityTrace() else null
        }
        fun traceVisibility(event: String, signal: String) {
            val trace = diagnostics ?: return
            val bounds = trace.bounds
            val viewport = scope.bounds.bounds
            val frame = bounds?.boundsInWindow
            val view = viewport?.boundsInWindow
            val fraction = if (bounds != null && viewport != null) {
                bounds.fractionVisibleIn(viewport)
            } else null
            val playback = coordinator.uiState
            MediaPerformanceTrace.mark(
                event = event,
                mediaId = mediaId,
                transitionId = state.activeVideoTransitionId,
                details = "signal=$signal native_fraction=${fraction ?: "unknown"} " +
                    "threshold=0.8 dwell_ms=500 " +
                    "frame_px=${frame?.left},${frame?.top},${frame?.right},${frame?.bottom} " +
                    "viewport_px=${view?.left},${view?.top},${view?.right},${view?.bottom} " +
                    "bounds_sample_age_ms=${if (bounds == null) -1L else MediaPerformanceTrace.nowMs() - trace.measuredAtMs} " +
                    "selected=${state.activeAnimatedMediaId ?: "none"} " +
                    "player_media=${playback.mediaId ?: "none"} phase=${playback.phase} " +
                    "play_when_ready=${playback.playWhenReady} is_playing=${playback.isPlaying} " +
                    "muted=${state.feedVideoMuted} manual_pause=${state.isVideoManuallyPaused(mediaId)}",
            )
        }
        fun pauseIfReleased() {
            if (state.isGoogleFeedVideoViewportCurrent(scope.owner) &&
                state.activeAnimatedMediaId != mediaId
            ) {
                coordinator.pauseForFeedVisibility(mediaId)
            }
        }
        DisposableEffect(state, scope, surfaceOwner) {
            onDispose {
                state.removeGoogleFeedVideoSurface(surfaceOwner)
                pauseIfReleased()
            }
        }
        val visibilityModifier = if (observe) {
            Modifier
                .onVisibilityChanged(
                    minDurationMs = 500,
                    minFractionVisible = 0.8f,
                    viewportBounds = scope.bounds,
                ) { visible ->
                    traceVisibility("google_feed_callback", "qualified_$visible")
                    state.updateGoogleFeedVideoVisibility(
                        scope.owner, surfaceOwner, mediaId, qualified = visible,
                    )
                    if (!visible) pauseIfReleased()
                }
                .onVisibilityChanged(
                    minDurationMs = 0,
                    minFractionVisible = 0f,
                    viewportBounds = scope.bounds,
                ) { present ->
                    traceVisibility("google_feed_callback", "present_$present")
                    state.updateGoogleFeedVideoVisibility(
                        scope.owner, surfaceOwner, mediaId, present = present,
                    )
                    if (!present) pauseIfReleased()
                }
        } else {
            Modifier
        }
        // Observe Google's bounds on the same frame. Only log sampling is rate limited;
        // the native eligibility callbacks above remain independent and undelayed.
        val diagnosticsModifier = if (observe && diagnostics != null) {
            Modifier.onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) { bounds ->
                val now = MediaPerformanceTrace.nowMs()
                diagnostics.bounds = bounds
                diagnostics.measuredAtMs = now
                if (now >= diagnostics.nextSampleAtMs) {
                    diagnostics.nextSampleAtMs = now + 100L
                    traceVisibility("google_feed_geometry", "sample")
                }
            }
        } else Modifier
        Box(diagnosticsModifier.then(visibilityModifier)) { content() }
    }
}
