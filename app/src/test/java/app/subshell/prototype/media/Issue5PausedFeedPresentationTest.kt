package app.subshell.prototype.media

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue5PausedFeedPresentationTest {
    @Test
    fun pausedOwnerKeepsItsSurfaceWithoutAnAutoplaySelection() {
        assertTrue(retainPausedFeedVideoSurface(true, false, "a", null, true))
    }

    @Test
    fun resumingTheSameOwnerKeepsTheSameSurface() {
        assertTrue(retainPausedFeedVideoSurface(true, false, "a", "a", true))
    }

    @Test
    fun anotherSelectedClipRevokesRetentionBeforeItAttaches() {
        assertFalse(retainPausedFeedVideoSurface(true, false, "a", "b", true))
    }

    @Test
    fun duplicateOrDetachedSurfacesCannotClaimThePlayersFrame() {
        assertFalse(retainPausedFeedVideoSurface(true, false, "a", null, false))
        assertFalse(retainPausedFeedVideoSurface(true, false, "a", "a", false))
    }

    @Test
    fun fullscreenAndOtherRoutesDoNotUseFeedRetention() {
        assertFalse(retainPausedFeedVideoSurface(true, true, "a", null, true))
        assertFalse(retainPausedFeedVideoSurface(false, false, "a", null, true))
    }

    @Test
    fun everyRetentionCombinationRequiresExactPresentationOwnership() {
        for (feed in listOf(false, true)) {
            for (fullscreen in listOf(false, true)) {
                for (owner in listOf(false, true)) {
                    for (selected in listOf<String?>(null, "a", "b")) {
                        val actual = retainPausedFeedVideoSurface(feed, fullscreen, "a", selected, owner)
                        if (actual) {
                            assertTrue(feed && !fullscreen && owner)
                            assertTrue(selected == null || selected == "a")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun retentionCannotAttachAndTransitionTelemetryCannotDisposeTheFeedSurface() {
        val media = source("ui/components/VideoPlayer.kt")
        val attachEffect = media.substringAfter("val requestFeedRoute =")
            .substringBefore("// Feed visibility transitions are not surface lifetime transitions.")
        assertTrue(attachEffect.contains("state.applyCurrentGoogleFeedVideoRequest("))
        val revoked = attachEffect.substringAfter("onRevoked = {")
            .substringBefore("// Standalone post/fullscreen behavior is unchanged.")
        assertTrue(revoked.contains("coordinator.ownsPresentation(playbackOwner, post.id)"))
        assertTrue(revoked.contains("coordinator.pauseForFeedVisibility(post.id)"))
        assertFalse(revoked.contains("coordinator.attach("))
        assertFalse(revoked.contains("coordinator.detach("))
        val standalone = attachEffect.substringAfter("// Standalone post/fullscreen behavior is unchanged.")
        val guard = "if (!playing && !manuallyPaused) return@LaunchedEffect"
        assertTrue(standalone.indexOf(guard) >= 0)
        assertTrue(standalone.indexOf(guard) < standalone.indexOf("coordinator.attach("))
        assertTrue(media.contains("val surfaceSelected = playing || manuallyPaused || retainedFeedSurface"))
        assertTrue(media.contains("val surfaceLifetimeKey = if (feedPausePresentation) 0L else transitionId"))
        assertTrue(media.contains("DisposableEffect(playbackOwner, media.mediaId, surfaceLifetimeKey)"))
        val coordinator = source("media/VideoPlayback.kt")
        assertTrue(coordinator.contains("activeOwner === owner && activeMedia?.mediaId == mediaId"))
        assertTrue(coordinator.contains("private var activeOwner by mutableStateOf<Any?>(null)"))
    }

    @Test
    fun nativeContentFrameOwnsTheFeedShutterWithoutKeepingADifferentClip() {
        val media = source("ui/components/VideoPlayer.kt")
        val feedFrame = media.substringAfter("} else if (feedPausePresentation) {")
            .substringBefore("} else {\n            PlayerSurface(")
        assertTrue(feedFrame.contains("ContentFrame("))
        assertTrue(feedFrame.contains("keepContentOnReset = false"))
        assertTrue(feedFrame.contains("shutter = {"))
        assertTrue(feedFrame.contains("VideoPosterFrame(post = post"))
        assertTrue(media.contains("val posterVisible = coordinator.renderedMediaId != post.id"))
        assertTrue(media.contains("surfaceType = SURFACE_TYPE_TEXTURE_VIEW"))
        assertFalse(feedFrame.contains("player.play("))
        assertFalse(feedFrame.contains("setMediaItem("))
        assertFalse(feedFrame.contains("Bitmap"))
    }

    @Test
    fun nativeEightyPercentDwellAndImmediatePauseRemainInPlace() {
        val visibility = source("ui/GoogleFeedVideoVisibility.kt")
        assertTrue(visibility.contains("minFractionVisible = 0.8f,"))
        assertTrue(visibility.contains("minDurationMs = 500,"))
        assertTrue(visibility.contains("viewportBounds = scope.bounds,"))
        assertTrue(visibility.contains("coordinator.pauseForFeedVisibility(mediaId)"))
        assertFalse(visibility.contains("delay("))
        val pause = source("media/VideoPlayback.kt")
            .substringAfter("fun pauseForFeedVisibility(mediaId: String) {")
            .substringBefore("fun retry(")
        assertTrue(pause.contains("if (activeMedia?.mediaId != mediaId) return"))
        assertTrue(pause.contains("playRequested = false"))
        assertTrue(pause.contains("player.pause()"))
        assertFalse(pause.contains("delay("))
        assertFalse(pause.contains("player.play()"))
    }

    private fun source(path: String): String =
        File("src/main/java/app/subshell/prototype/$path").readText()
}
