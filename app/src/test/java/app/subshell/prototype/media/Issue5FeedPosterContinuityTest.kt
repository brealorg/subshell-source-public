package app.subshell.prototype.media

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue5FeedPosterContinuityTest {
    @Test
    fun aNewCardStartsLoadingWithoutAnotherCardsImage() {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        assertNull(image.result)
        assertEquals("loading", image.phase)
        assertEquals(0, image.retryNonce)
    }

    @Test
    fun staticCardAndShutterReadTheExactSameDecodedResult() = runBlocking {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        val decoded = RemoteMediaLoadResult.Ready("decoded-poster-a")
        var requests = 0
        image.load { uri ->
            assertEquals("poster-a", uri)
            requests += 1
            decoded
        }
        val staticCardResult = image.result
        // Readers do not own loading; a newly composed shutter reads the ready result immediately.
        repeat(20) { assertSame(staticCardResult, image.result) }
        assertSame(decoded, image.result)
        assertEquals("ready", image.phase)
        assertEquals(1, requests)
        assertEquals(0, image.retryNonce)
    }

    @Test
    fun bothPostAndImageUriMustMatchBeforeDrawing() {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        assertTrue(image.matches("a", "poster-a"))
        assertFalse(image.matches("b", "poster-a"))
        assertFalse(image.matches("a", "poster-b"))
    }

    @Test
    fun aLateOldCardLoadCannotPublishIntoANewCardOrUri() = runBlocking {
        val oldImage = FeedVideoPosterImageState<String>("a", "old-uri")
        val newImage = FeedVideoPosterImageState<String>("a", "new-uri")
        val oldResult = CompletableDeferred<RemoteMediaLoadResult<String>>()
        val job = launch { oldImage.load { oldResult.await() } }
        yield()
        val newResult = RemoteMediaLoadResult.Ready("new-image")
        newImage.load { newResult }
        oldResult.complete(RemoteMediaLoadResult.Ready("old-image"))
        job.join()
        assertSame(newResult, newImage.result)
        assertFalse(oldImage.matches(newImage.mediaId, newImage.uri))
    }

    @Test
    fun aFailedPosterRetainsItsFailureAndOnlyExplicitRetryClearsIt() = runBlocking {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        val failure = RemoteMediaLoadResult.Failed(
            FailureDetails(retryable = false, localKind = LocalFailureKind.DECODING),
        )
        image.load { failure }
        assertSame(failure, image.result)
        assertEquals("failed", image.phase)
        image.retry()
        assertNull(image.result)
        assertEquals("loading", image.phase)
        assertEquals(1, image.retryNonce)
        val ready = RemoteMediaLoadResult.Ready("retried-image")
        image.load { ready }
        assertSame(ready, image.result)
    }

    @Test
    fun aSupersededRetryResultCannotOverwriteTheNewRequest() = runBlocking {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        val staleResult = CompletableDeferred<RemoteMediaLoadResult<String>>()
        val oldJob = launch { image.load { staleResult.await() } }
        yield()
        image.retry()
        val current = RemoteMediaLoadResult.Ready("new-image")
        image.load { current }
        staleResult.complete(RemoteMediaLoadResult.Ready("stale-image"))
        oldJob.join()
        assertSame(current, image.result)
    }

    @Test
    fun cancellationDoesNotReplaceLoadingWithASpuriousFailure() = runBlocking {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        try {
            image.load { throw CancellationException("card disposed") }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertNull(image.result)
            assertEquals("loading", image.phase)
        }
    }

    @Test
    fun evenALoaderReturningAfterCancellationCannotPublish() = runBlocking {
        val image = FeedVideoPosterImageState<String>("a", "poster-a")
        val job = launch {
            image.load {
                cancel("card disposed")
                RemoteMediaLoadResult.Ready("must-not-publish")
            }
        }
        job.join()
        assertNull(image.result)
    }

    @Test
    fun productionImageLifetimeIsAboveBothConditionalReturns() {
        val media = source("ui/components/VideoPlayer.kt")
        val player = media.substringAfter("fun LocalVideoPlayer(")
        val remembered = player.indexOf("val feedPosterImage = rememberFeedVideoPosterImage(")
        assertTrue(remembered >= 0)
        assertTrue(remembered < player.indexOf("if (posterOnly)"))
        assertTrue(remembered < player.indexOf("if (!surfaceSelected)"))
        val rememberImage = media.substringAfter("private fun rememberFeedVideoPosterImage(")
            .substringBefore("fun LocalVideoPlayer(")
        assertTrue(rememberImage.contains("remember(post.id, uri)"))
        assertTrue(rememberImage.contains("LaunchedEffect(image, image.retryNonce)"))
        assertTrue(rememberImage.contains("image.load(RemoteImageLoader::load)"))
        assertFalse(rememberImage.contains("LaunchedEffect(playing"))
        assertFalse(rememberImage.contains("LaunchedEffect(transitionId"))
        assertTrue(rememberImage.contains("!post.localMediaUri.isNullOrBlank()"))
    }

    @Test
    fun productionStaticPosterAndNativeShutterUseTheSameCardModel() {
        val media = source("ui/components/VideoPlayer.kt")
        val player = media.substringAfter("fun LocalVideoPlayer(")
        val static = player.substringAfter("Box(staticFrameModifier.background(Color.Black)) {")
            .substringBefore("Surface(")
        val shutter = player.substringAfter("} else if (feedPausePresentation) {")
            .substringBefore(
                "} else {\n            // Standalone/post surfaces need the same per-surface readiness rule as fullscreen.",
            )
        val fullscreenFreshSurface = player
            .substringAfter("// SUBSHELL_ISSUE20_ENDED_SURFACE_POSTER_FALLBACK_V1")
            .substringBefore("} else if (feedPausePresentation) {")
        val standaloneFreshSurface = player
            .substringAfter("// Standalone/post surfaces need the same per-surface readiness rule as fullscreen.")
            .substringBefore("// Feed readiness belongs to ContentFrame, not the coordinator's media-ID-only marker.")
        assertTrue(static.contains("feedImageState = feedPosterImage"))
        assertTrue(shutter.contains("feedImageState = feedPosterImage"))
        assertTrue(shutter.contains("ContentFrame("))
        assertTrue(shutter.contains("keepContentOnReset = false"))
        assertFalse(shutter.contains("surfaceType ="))
        assertFalse(shutter.contains("RemoteImage("))
        assertFalse(shutter.contains("delay("))
        assertTrue(player.contains("if (!feedPausePresentation && posterVisible)"))

        // ISSUE20 successor: readiness belongs to each newly attached surface, not to the
        // media-ID-wide rendered history. Fresh fullscreen and standalone surfaces therefore
        // keep a poster shutter until that surface receives its own first frame.
        assertTrue(fullscreenFreshSurface.contains("ContentFrame("))
        assertTrue(fullscreenFreshSurface.contains("surfaceType = SURFACE_TYPE_TEXTURE_VIEW"))
        assertTrue(fullscreenFreshSurface.contains("keepContentOnReset = false"))
        assertTrue(fullscreenFreshSurface.contains("shutter = {"))
        assertTrue(fullscreenFreshSurface.contains("VideoPosterFrame(post = post, modifier = Modifier.fillMaxSize())"))
        assertTrue(standaloneFreshSurface.contains("ContentFrame("))
        assertTrue(standaloneFreshSurface.contains("keepContentOnReset = false"))
        assertTrue(standaloneFreshSurface.contains("shutter = {"))
        assertTrue(standaloneFreshSurface.contains("VideoPosterFrame(post = post, modifier = Modifier.fillMaxSize())"))
        assertFalse(player.contains("PlayerSurface("))
    }

    @Test
    fun productionReadyPosterDoesNotLaunchAnotherLoaderAndPreservesRetry() {
        val media = source("ui/components/VideoPlayer.kt")
        val poster = media.substringAfter("private fun VideoPosterFrame(")
            .substringBefore("private fun rememberFeedVideoPosterImage(")
        val shared = poster.substringAfter("feedImageState?.matches(post.id, remoteUri) == true -> {")
            .substringBefore("remoteUri != null -> RemoteImage(")
        assertTrue(shared.contains("when (val result = feedImageState.result)"))
        assertTrue(shared.contains("bitmap = result.value.asImageBitmap()"))
        assertTrue(shared.contains("onRetry = feedImageState::retry"))
        assertFalse(shared.contains("produceState"))
        assertFalse(shared.contains("LaunchedEffect"))
        assertFalse(shared.contains("RemoteImage("))
        // The ordinary/fullscreen fallback still uses the unchanged generic RemoteImage path.
        assertTrue(poster.contains("remoteUri != null -> RemoteImage("))
    }

    private fun source(path: String): String =
        File("src/main/java/app/subshell/prototype/$path").readText()
}
