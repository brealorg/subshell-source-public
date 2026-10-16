package app.subshell.prototype.ui

import app.subshell.prototype.data.SampleData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic callback/effect ordering, with no sleeps and no device/player allocation. */
class Issue5GoogleFeedStartRaceTest {
    private val viewport = Any()
    private val surfaceA = Any()
    private val surfaceB = Any()
    private val ownerA = Any()
    private val ownerB = Any()

    private fun state(): SubshellState = SubshellState(
        initialCommentActionPlacement = CommentActionPlacement.RIGHT,
    ).also {
        it.updateVideoFeedContext(listOf("a", "b"), terminalVideoId = "b")
        it.registerGoogleFeedVideoViewport(viewport)
    }

    private class RecordingPlayer {
        var owner: Any? = null
        var mediaId: String? = null
        var playWhenReady = false
        var ready = false
        var muted = true
        var transitionId = 0L
        var positionMs = 0L
        var attachCount = 0
        var pauseCount = 0
        val isPlaying: Boolean get() = playWhenReady && ready

        fun owns(candidateOwner: Any, candidateMedia: String): Boolean =
            owner === candidateOwner && mediaId == candidateMedia

        fun attach(candidateOwner: Any, candidateMedia: String, play: Boolean, mute: Boolean, transition: Long) {
            if (mediaId != candidateMedia) positionMs = 0L
            owner = candidateOwner
            mediaId = candidateMedia
            playWhenReady = play
            muted = mute
            transitionId = transition
            attachCount += 1
        }

        fun pauseOwned(candidateOwner: Any, candidateMedia: String) {
            if (!owns(candidateOwner, candidateMedia)) return
            playWhenReady = false
            pauseCount += 1
        }
    }

    private fun queue(
        state: SubshellState,
        player: RecordingPlayer,
        mediaId: String = "a",
        owner: Any = ownerA,
    ): () -> Unit {
        // Capture the scope now, but permission is read by production code on execution.
        val route = state.currentNavigationRoute
        val epoch = state.googleFeedVisibilityEpoch
        return {
            state.applyCurrentGoogleFeedVideoRequest(
                mediaId = mediaId,
                requestRoute = route,
                requestVisibilityEpoch = epoch,
                onApply = { playNow, transition ->
                    if (!playNow && player.owns(owner, mediaId)) {
                        player.pauseOwned(owner, mediaId)
                    } else {
                        player.attach(owner, mediaId, playNow, state.feedVideoMuted, transition)
                    }
                },
                onRevoked = { player.pauseOwned(owner, mediaId) },
            )
        }
    }

    @Test
    fun qualificationRevocationThenQueuedStartCannotAttachOrPlay() {
        val state = state()
        val player = RecordingPlayer()
        player.attach(ownerB, "b", false, false, 1L)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val pendingStart = queue(state, player)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        pendingStart()
        assertNull(state.activeAnimatedMediaId)
        assertEquals(1, player.attachCount)
        assertEquals(0, player.pauseCount)
        assertEquals("b", player.mediaId)
        assertSame(ownerB, player.owner)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun revokedRetainedOwnerIsPausedWithoutReattachOrPositionReset() {
        val state = state()
        val player = RecordingPlayer().apply { ready = true }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        queue(state, player)()
        player.positionMs = 12_345L
        val pendingStart = queue(state, player)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        pendingStart()
        assertFalse(player.isPlaying)
        assertEquals(1, player.pauseCount)
        assertEquals(1, player.attachCount)
        assertSame(ownerA, player.owner)
        assertEquals(12_345L, player.positionMs)
    }

    @Test
    fun cancellationDuringBufferingCannotStartWhenThePlayerBecomesReady() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        queue(state, player)()
        assertTrue(player.playWhenReady)
        assertFalse(player.isPlaying)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        queue(state, player)()
        player.ready = true
        assertFalse(player.playWhenReady)
        assertFalse(player.isPlaying)
        assertEquals(1, player.attachCount)
    }

    @Test
    fun oldRequestCannotStealOrPauseTheNewlySelectedOwner() {
        val state = state()
        val player = RecordingPlayer().apply { ready = true }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val oldRequest = queue(state, player)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceB, "b", qualified = true)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        queue(state, player, "b", ownerB)()
        oldRequest()
        assertEquals("b", player.mediaId)
        assertSame(ownerB, player.owner)
        assertTrue(player.isPlaying)
        assertEquals(1, player.attachCount)
        assertEquals(0, player.pauseCount)
    }

    @Test
    fun anUnselectedRequestCannotPauseAnotherSurfaceOfTheSameMedia() {
        val state = state()
        val player = RecordingPlayer().apply { ready = true }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val oldRequest = queue(state, player)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        player.attach(ownerB, "a", false, true, 9L)
        oldRequest()
        assertSame(ownerB, player.owner)
        assertEquals(0, player.pauseCount)
        assertEquals(1, player.attachCount)
    }

    @Test
    fun requalificationUsesCurrentAuthorityAndTransitionRatherThanCapturedFlags() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val firstTransition = state.activeVideoTransitionId
        val pendingRequest = queue(state, player)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = false)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        pendingRequest()
        assertTrue(player.playWhenReady)
        assertEquals(state.activeVideoTransitionId, player.transitionId)
        assertTrue(firstTransition != player.transitionId)
    }

    @Test
    fun manualPauseAfterQueueingCannotBeOverriddenByTheQueuedPlay() {
        val state = state()
        val player = RecordingPlayer().apply { ready = true }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        queue(state, player)()
        val pendingPlay = queue(state, player)
        state.stopVideo("a")
        pendingPlay()
        assertFalse(player.isPlaying)
        assertTrue(state.isVideoManuallyPaused("a"))
        assertEquals(1, player.attachCount)
    }

    @Test
    fun manualPauseCanStillTransferAnUnownedPresentationWithoutPlaying() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val pendingPlay = queue(state, player)
        state.stopVideo("a")
        pendingPlay()
        assertEquals("a", player.mediaId)
        assertSame(ownerA, player.owner)
        assertFalse(player.playWhenReady)
        assertEquals(1, player.attachCount)
    }

    @Test
    fun changingAutoplayToNeverRevokesTheQueuedAutomaticStart() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val pendingPlay = queue(state, player)
        state.updateVideoAutoplay(VideoAutoplay.NEVER)
        pendingPlay()
        assertEquals(0, player.attachCount)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun explicitPlayStillWorksWithAutoplayNeverAndUsesCurrentMute() {
        val state = state()
        val player = RecordingPlayer()
        state.updateVideoAutoplay(VideoAutoplay.NEVER)
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", present = true)
        state.playVideo("a")
        val pendingPlay = queue(state, player)
        state.toggleVideoMuted()
        pendingPlay()
        assertTrue(player.playWhenReady)
        assertEquals(state.feedVideoMuted, player.muted)
    }

    @Test
    fun aPreviousForegroundEpochCannotActAfterFreshQualification() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val stale = queue(state, player)
        state.suspendVideoPlaybackForBackground()
        stale()
        state.resumeVideoPlaybackAfterForeground()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        stale()
        assertEquals(0, player.attachCount)
        queue(state, player)()
        assertTrue(player.playWhenReady)
        assertEquals(1, player.attachCount)
    }

    @Test
    fun disposedFeedDoesNotExecuteAnOldStart() {
        val state = state()
        val player = RecordingPlayer()
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, "a", qualified = true)
        val pendingPlay = queue(state, player)
        state.removeGoogleFeedVideoViewport(viewport)
        pendingPlay()
        assertEquals(0, player.attachCount)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun oldFeedRequestDoesNotControlFullscreenEvenWithTheSameMediaId() {
        val state = state()
        val post = SampleData.posts.first { it.id == "video-lab-a" }
        val player = RecordingPlayer().apply { ready = true }
        state.updateGoogleFeedVideoVisibility(viewport, surfaceA, post.id, qualified = true)
        val pendingPlay = queue(state, player, post.id, ownerA)
        state.openMedia(post)
        state.completeVideoPresentationHandoff(post.id)
        player.attach(ownerB, post.id, true, false, 9L)
        pendingPlay()
        assertSame(ownerB, player.owner)
        assertTrue(player.isPlaying)
        assertEquals(1, player.attachCount)
        assertEquals(0, player.pauseCount)
    }

    @Test
    fun productionFeedEffectDispatchesLiveAuthorityAndRetainedPauseIsOwnerScoped() {
        val media = File("src/main/java/app/subshell/prototype/ui/components/VideoPlayer.kt").readText()
        val state = File("src/main/java/app/subshell/prototype/ui/SubshellState.kt").readText()
        val effect = media.substringAfter("val requestFeedRoute = state.currentNavigationRoute")
            .substringBefore("// Feed visibility transitions are not surface lifetime transitions.")
        val feed = effect.substringAfter("if (feedPausePresentation) {")
            .substringBefore("// Standalone post/fullscreen behavior is unchanged.")
        assertTrue(feed.contains("state.applyCurrentGoogleFeedVideoRequest("))
        assertTrue(feed.contains("playWhenReady = playNow,"))
        assertTrue(feed.contains("transitionId = currentTransitionId,"))
        assertTrue(feed.contains("muted = state.feedVideoMuted,"))
        assertFalse(feed.contains("playWhenReady = playing,"))
        assertFalse(feed.contains("delay("))
        val revoked = feed.substringAfter("onRevoked = {")
        assertTrue(revoked.contains("coordinator.ownsPresentation(playbackOwner, post.id)"))
        assertTrue(revoked.contains("coordinator.pauseForFeedVisibility(post.id)"))
        assertFalse(revoked.contains("coordinator.attach("))
        assertFalse(revoked.contains("coordinator.detach("))
        val dispatch = state.substringAfter("internal fun applyCurrentGoogleFeedVideoRequest(")
            .substringBefore("internal fun registerGoogleFeedVideoViewport(")
        assertTrue(dispatch.contains("googleFeedVisibilityEpoch != requestVisibilityEpoch"))
        assertTrue(dispatch.contains("routeStack.current != requestRoute"))
        assertTrue(dispatch.contains("activeVideoMediaId == mediaId"))
        assertTrue(dispatch.contains("activeAnimatedMediaId == mediaId"))
        assertFalse(dispatch.contains("delay("))
        assertFalse(dispatch.contains("launch("))
    }
}
