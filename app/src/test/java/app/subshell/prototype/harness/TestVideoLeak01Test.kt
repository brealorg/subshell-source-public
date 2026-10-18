package app.subshell.prototype.harness

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * TESTVIDEOLEAK01: tests make video coordinators through
 * testVideoPlaybackCoordinator, which releases the coordinators of finished
 * tests (Robolectric gives each test a new Application) and never those of
 * the running test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TestVideoLeak01Test {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theRunningTestsCoordinatorsAreKept() {
        val first = testVideoPlaybackCoordinator(context)
        val second = testVideoPlaybackCoordinator(context)

        assertFalse(first.player.isReleased)
        assertFalse(second.player.isReleased)
        first.releaseWithoutHanging()
        second.releaseWithoutHanging()
    }

    @Test
    fun aFinishedTestsCoordinatorIsReleasedWhenTheNextTestMakesOne() {
        val earlier = testVideoPlaybackCoordinator(context)
        val running = currentTestApplication
        // The next test runs under a new Application.
        val next = Any()
        currentTestApplication = { next }
        try {
            val nextTests = testVideoPlaybackCoordinator(context)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(earlier.player.isReleased)
            assertFalse(nextTests.player.isReleased)
            nextTests.releaseWithoutHanging()
        } finally {
            currentTestApplication = running
        }
    }
}
