package app.subshell.prototype.media

import android.app.Application
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (issue11-video-error): a video whose sources fail is told apart by the
 * real coordinator, with a real player against a local HTTP server:
 * the fallback source is tried only after the first one failed, the terminal
 * failure keeps every source's HTTP status in order, only the surface that
 * owns the video may retry it (a retry is a new attempt with no old failure),
 * and attaching the same failed video again does not replay its sources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3VideoFailureCoordinatorTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var coordinator: VideoPlaybackCoordinator
    private lateinit var server: HttpServer
    private val hits = CopyOnWriteArrayList<String>()
    private val owner = Any()

    @Before
    fun startServer() {
        ensureWorkManagerInitialized(application)
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            hits += path
            val status = when (path) {
                "/a.mp4" -> 403
                "/b.mp4" -> 404
                else -> 500
            }
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        coordinator = VideoPlaybackCoordinator(application)
    }

    @After
    fun stopServer() {
        coordinator.release()
        shadowOf(Looper.getMainLooper()).idle()
        server.stop(0)
    }

    private fun source(path: String) = VideoMediaSource(
        uri = "http://127.0.0.1:${server.address.port}$path",
        kind = VideoMediaSourceKind.PROGRESSIVE,
        mimeType = MimeTypes.VIDEO_MP4,
    )

    private val media by lazy {
        VideoMedia(mediaId = "t3_broken", sources = listOf(source("/a.mp4"), source("/b.mp4")))
    }

    private fun attach(by: Any = owner) {
        coordinator.attach(
            owner = by,
            media = media,
            muted = true,
            transitionId = 1L,
            rank = VIDEO_UNRANKED,
            playWhenReady = true,
        )
    }

    /** The player thread is real; the main looper and the fake clock are moved by hand. */
    private fun pumpUntil(timeoutMs: Long = 30_000L, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!done()) {
            assertTrue("timed out; hits=$hits state=${coordinator.uiState}", System.currentTimeMillis() < deadline)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            Thread.sleep(10)
        }
    }

    /** Lets time pass without expecting anything to happen. */
    private fun pumpFor(fakeMs: Long) {
        repeat((fakeMs / 250).toInt()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            Thread.sleep(10)
        }
    }

    private fun failed() = coordinator.uiState.phase == VideoPlaybackPhase.ERROR

    @Test
    fun theFallbackSourceIsTriedAfterTheFirstAndTheFailureKeepsBothStatuses() {
        attach()
        pumpUntil(done = ::failed)

        val failure = requireNotNull(coordinator.uiState.failure) { "no failure: ${coordinator.uiState}" }
        assertEquals("t3_broken", failure.mediaId)
        assertEquals(
            listOf(0 to 403, 1 to 404),
            failure.sources.map { it.sourceIndex to it.httpStatusCode },
        )
        assertNotNull(coordinator.uiState.errorCode)
        assertTrue("a.mp4 asked: $hits", "/a.mp4" in hits)
        assertTrue("b.mp4 asked: $hits", "/b.mp4" in hits)
        assertTrue(
            "the fallback is asked only after the first source failed: $hits",
            hits.lastIndexOf("/a.mp4") < hits.indexOf("/b.mp4"),
        )
    }

    @Test
    fun onlyTheOwnerRetriesAndARetryIsANewAttempt() {
        attach()
        pumpUntil(done = ::failed)
        val first = coordinator.uiState.failure!!
        val hitsAfterFailure = hits.size

        coordinator.retry(Any())
        pumpFor(2_000L)
        assertEquals(VideoPlaybackPhase.ERROR, coordinator.uiState.phase)
        assertEquals(first, coordinator.uiState.failure)
        assertEquals("a stranger's retry asks nothing: $hits", hitsAfterFailure, hits.size)

        coordinator.retry(owner)
        assertNull("a retry starts without the old failure", coordinator.uiState.failure)
        assertNotEquals(VideoPlaybackPhase.ERROR, coordinator.uiState.phase)

        pumpUntil(done = ::failed)
        val second = coordinator.uiState.failure!!
        assertTrue("the retry asked the first source again: $hits", hits.drop(hitsAfterFailure).contains("/a.mp4"))
        assertTrue("a new attempt: ${first.attemptId} -> ${second.attemptId}", second.attemptId > first.attemptId)
        assertEquals(listOf(0 to 403, 1 to 404), second.sources.map { it.sourceIndex to it.httpStatusCode })
    }

    @Test
    fun attachingTheSameFailedVideoAgainKeepsTheFailureAndAsksNothing() {
        attach()
        pumpUntil(done = ::failed)
        val failure = coordinator.uiState.failure!!
        val hitsAfterFailure = hits.size

        attach()
        pumpFor(3_000L)

        assertEquals(VideoPlaybackPhase.ERROR, coordinator.uiState.phase)
        assertEquals(failure, coordinator.uiState.failure)
        assertEquals("the failed sources are not replayed: $hits", hitsAfterFailure, hits.size)
    }
}
