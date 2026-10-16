package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import app.subshell.prototype.media.FeedScrollDirection
import app.subshell.prototype.media.FeedViewportMotion
import app.subshell.prototype.media.FeedViewportState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue5GoogleFeedVisibilityTest {
    private val viewport = Any()
    private val surfaceA = Any()
    private val surfaceB = Any()

    private fun state(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
        it.updateFeedViewportState(FeedViewportState())
    }

    @Test
    fun geometryAndPresenceNeverStartBeforeGoogleQualification() {
        val state = state()
        // Even a formerly perfect geometry candidate cannot bypass the 500 ms callback.
        state.updateVideoVisibility("a", surfaceA, 1f)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = true)
        assertNull(state.activeAnimatedMediaId)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        assertEquals("a", state.activeAnimatedMediaId)
    }

    @Test
    fun falseStopsWithoutAReplacementAndOldGeometryCannotReacquire() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        assertNull(state.activeAnimatedMediaId)
        state.updateVideoVisibility("a", surfaceA, 1f)
        state.updateFeedViewportState(
            FeedViewportState(motion = FeedViewportMotion.DRAGGING, atEnd = true),
        )
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun flingDirectionAndPhysicalEdgesDoNotOverrideGoogle() {
        val state = state()
        state.updateFeedViewportState(
            FeedViewportState(
                motion = FeedViewportMotion.FLINGING,
                direction = FeedScrollDirection.UP,
                atStart = true,
                atEnd = true,
            ),
        )
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "b", qualified = true)
        assertEquals("b", state.activeAnimatedMediaId)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "b", qualified = false)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun onlyOneEligibleOwnerPlaysAndLosingOwnerCanYieldImmediately() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "b", qualified = true)
        assertEquals("a", state.activeAnimatedMediaId)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        assertEquals("b", state.activeAnimatedMediaId)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "b", present = false)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun detachedSurfaceCannotLeaveAnActiveVideo() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.removeGoogleFeedVideoSurface(surfaceA)
        assertNull(state.activeAnimatedMediaId)
        state.updateVideoVisibility("a", surfaceA, 1f)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun duplicateSurfacesOnlyReleaseAfterTheirLastQualifiedSurface() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "a", qualified = true)
        state.removeGoogleFeedVideoSurface(surfaceA)
        assertEquals("a", state.activeAnimatedMediaId)
        state.removeGoogleFeedVideoSurface(surfaceB)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun disposedViewportRejectsLateCallbacksAndDoesNotRestoreLegacySelection() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.removeGoogleFeedVideoViewport(viewport)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.updateVideoVisibility("a", surfaceA, 1f)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun oldViewportDisposalDoesNotClearTheNewViewport() {
        val state = state()
        val replacement = Any()
        state.registerGoogleFeedVideoViewport(replacement)
        state.updateGoogleFeedVideoVisibility(replacement, surfaceB, "b", qualified = true)
        state.removeGoogleFeedVideoViewport(viewport)
        assertEquals("b", state.activeAnimatedMediaId)
    }

    @Test
    fun neverStillAllowsExplicitTapButNotAutomaticQualification() {
        val state = state()
        state.updateVideoAutoplay(VideoAutoplay.NEVER)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        assertNull(state.activeAnimatedMediaId)
        state.playVideo("a")
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        assertEquals("a", state.activeAnimatedMediaId)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = false)
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun manualPauseIsNotUndoneByAnotherVisibilityCallback() {
        val state = state()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        state.stopVideo("a")
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        assertNull(state.activeAnimatedMediaId)
        assertTrue(state.isVideoManuallyPaused("a"))
        state.playVideo("a")
        assertEquals("a", state.activeAnimatedMediaId)
    }

    @Test
    fun foregroundResumeMustReceiveFreshGoogleQualificationAndPreservesMute() {
        val state = state()
        state.toggleVideoMuted()
        val muted = state.feedVideoMuted
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val epoch = state.googleFeedVisibilityEpoch
        state.suspendVideoPlaybackForBackground()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        assertNull(state.activeAnimatedMediaId)
        state.resumeVideoPlaybackAfterForeground()
        state.updateFeedViewportState(FeedViewportState())
        state.updateVideoVisibility("a", surfaceA, 1f)
        assertNull(state.activeAnimatedMediaId)
        assertTrue(state.googleFeedVisibilityEpoch > epoch)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        assertEquals("a", state.activeAnimatedMediaId)
        assertEquals(muted, state.feedVideoMuted)
    }

    @Test
    fun staleFeedCallbacksCannotPauseAFullscreenVideo() {
        val state = state()
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = true)
        state.openMedia(post)
        state.completeVideoPresentationHandoff(post.id)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = false)
        assertEquals(post.id, state.activeAnimatedMediaId)
        assertFalse(state.isGoogleFeedVideoViewportCurrent(viewport))
    }

    @Test
    fun feedSurfaceDisposalPreservesManualPauseInFullscreen() {
        val state = state()
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = true)
        state.stopVideo(post.id)
        state.openMedia(post)
        state.removeGoogleFeedVideoSurface(surfaceA)
        state.removeGoogleFeedVideoViewport(viewport)
        state.completeVideoPresentationHandoff(post.id)
        assertTrue(state.isVideoManuallyPaused(post.id))
        assertNull(state.activeAnimatedMediaId)
    }

    @Test
    fun returningToTheFeedCannotKeepAnUnqualifiedHandoffAlive() {
        val state = state()
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = true)
        state.openMedia(post)
        state.removeGoogleFeedVideoSurface(surfaceA)
        state.removeGoogleFeedVideoViewport(viewport)
        state.completeVideoPresentationHandoff(post.id)
        state.closeMedia()
        state.registerGoogleFeedVideoViewport(Any())
        assertNull(state.activeAnimatedMediaId)
        assertFalse(state.isVideoPresentationHandoff(post.id))
    }

    @Test
    fun productionWiringUsesNativeDwellAndAStableExplicitViewport() {
        val root = File("src/main/java/app/subshell/prototype")
        val google = File(root, "ui/GoogleFeedVideoVisibility.kt").readText()
        val media = File(root, "ui/components/MediaComponents.kt").readText()
        val state = File(root, "ui/SubshellState.kt").readText()
        val player = File(root, "media/VideoPlayback.kt").readText()
        val googleDecision = state.substringAfter("private fun reconcileGoogleFeedVideoPlayback()")
            .substringBefore("fun updateVideoVisibility(")
        assertTrue(google.contains("import androidx.compose.ui.layout.onVisibilityChanged"))
        assertTrue(google.contains("minDurationMs = 500,"))
        assertTrue(google.contains("minFractionVisible = 0.8f,"))
        assertTrue(google.contains("viewportBounds = scope.bounds,"))
        assertTrue(google.contains(".layoutBounds(scope.bounds)"))
        assertFalse(google.contains("delay("))
        assertFalse(google.contains("boundsInWindow("))
        assertTrue(google.contains("if (BuildConfig.DEBUG) GoogleFeedVisibilityTrace() else null"))
        assertTrue(google.contains("bounds.fractionVisibleIn(viewport)"))
        val diagnostics = google.substringAfter("val diagnosticsModifier =")
            .substringBefore("Box(diagnosticsModifier.then(visibilityModifier))")
        assertFalse(diagnostics.contains("state.update"))
        assertFalse(diagnostics.contains("coordinator."))
        assertFalse(diagnostics.contains(".onVisibilityChanged("))
        assertTrue(media.contains("PostKind.VIDEO -> GoogleFeedVideoFrame("))
        assertTrue(player.contains("fun pauseForFeedVisibility(mediaId: String)"))
        assertTrue(player.substringAfter("fun pauseForFeedVisibility(mediaId: String)")
            .substringBefore("fun retry(").contains("player.pause()"))
        assertTrue(google.contains("coordinator.pauseForFeedVisibility(mediaId)"))
        assertFalse(googleDecision.contains("selectViewportOwner("))
        assertFalse(googleDecision.contains("feedViewportState.motion"))
        assertFalse(googleDecision.contains("OWNER_RETAIN"))
        for (screen in listOf("HomeScreen", "CommunityScreen")) {
            assertTrue(File(root, "ui/screens/$screen.kt").readText()
                .contains("GoogleFeedVideoViewport("))
        }
    }
}
