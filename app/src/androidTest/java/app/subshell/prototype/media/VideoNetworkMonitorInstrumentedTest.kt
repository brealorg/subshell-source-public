package app.subshell.prototype.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MEDIA04B_LOG_TAG = "subshell-network"

@RunWith(AndroidJUnit4::class)
class VideoNetworkMonitorInstrumentedTest {
    @Test
    fun defaultNetworkCallbackPublishesCurrentClassAndPolicyMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val latch = CountDownLatch(1)
        val observed = AtomicReference<VideoNetworkSnapshot>()
        lateinit var monitor: AndroidVideoNetworkMonitor

        instrumentation.runOnMainSync {
            monitor = AndroidVideoNetworkMonitor(context) { snapshot ->
                observed.set(snapshot)
                latch.countDown()
            }
            monitor.start()
        }

        try {
            assertTrue("Default network snapshot was not published", latch.await(5, TimeUnit.SECONDS))
            val snapshot = requireNotNull(observed.get()) { "Network snapshot missing" }

            when (snapshot.networkClass) {
                VideoNetworkClass.OFFLINE ->
                    assertTrue(!snapshot.hasInternetCapability || !snapshot.validated)
                VideoNetworkClass.METERED -> {
                    assertTrue(snapshot.hasInternetCapability)
                    assertTrue(snapshot.validated)
                    assertTrue(snapshot.metered)
                }
                VideoNetworkClass.UNMETERED -> {
                    assertTrue(snapshot.hasInternetCapability)
                    assertTrue(snapshot.validated)
                    assertTrue(!snapshot.metered)
                }
            }

            val unmetered = VideoNetworkSnapshot(true, true, false)
            val metered = VideoNetworkSnapshot(true, true, true)
            val offline = VideoNetworkSnapshot()
            assertEquals(
                VideoPreloadTarget.RANGE_3_SECONDS,
                videoPreloadTarget(true, 0, unmetered, FeedViewportMotion.IDLE),
            )
            assertEquals(
                VideoPreloadTarget.SOURCE_PREPARED,
                videoPreloadTarget(true, 0, metered, FeedViewportMotion.IDLE),
            )
            assertEquals(
                VideoPreloadTarget.NONE,
                videoPreloadTarget(true, 0, offline, FeedViewportMotion.IDLE),
            )
            assertEquals(
                VideoPreloadTarget.NONE,
                videoPreloadTarget(true, 0, unmetered, FeedViewportMotion.FLINGING),
            )

            Log.i(
                MEDIA04B_LOG_TAG,
                "MEDIA04B_NETWORK_CLASS=${snapshot.networkClass.name} " +
                    "VALIDATED=${snapshot.validated} METERED=${snapshot.metered} " +
                    "DATA_SAVER=${snapshot.dataSaverActive}",
            )
            Log.i(MEDIA04B_LOG_TAG, "RESULT=SUBSHELL_MEDIA04B_NETWORK_POLICY_RUNTIME_PASS")
        } finally {
            instrumentation.runOnMainSync { monitor.close() }
        }
    }
}
