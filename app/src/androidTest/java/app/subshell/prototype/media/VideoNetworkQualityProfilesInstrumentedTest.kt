package app.subshell.prototype.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.ui.VideoQuality
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MEDIA04D_LOG_TAG = "subshell-network"

@RunWith(AndroidJUnit4::class)
class VideoNetworkQualityProfilesInstrumentedTest {
    @Test
    fun actualNetworkSnapshotAndDeterministicQualityProfilesRemainUsable() {
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
            val profiles = VideoNetworkQualityProfiles(
                unmetered = VideoQuality.HIGH,
                metered = VideoQuality.LOW,
            )

            assertEquals(
                VideoQuality.HIGH,
                automaticVideoQualityFor(true, VideoNetworkClass.UNMETERED, profiles),
            )
            assertEquals(
                VideoQuality.LOW,
                automaticVideoQualityFor(true, VideoNetworkClass.METERED, profiles),
            )
            assertEquals(
                VideoQuality.MEDIUM,
                automaticVideoQualityFor(
                    remoteSource = true,
                    networkClass = VideoNetworkClass.OFFLINE,
                    profiles = profiles,
                    offlineFallback = VideoQuality.MEDIUM,
                ),
            )
            VideoNetworkClass.entries.forEach { networkClass ->
                assertEquals(
                    VideoQuality.HIGH,
                    automaticVideoQualityFor(false, networkClass, profiles, VideoQuality.LOW),
                )
            }

            val effective = automaticVideoQualityFor(
                remoteSource = true,
                networkClass = snapshot.networkClass,
                profiles = profiles,
                offlineFallback = VideoQuality.MEDIUM,
            )
            Log.i(
                MEDIA04D_LOG_TAG,
                "MEDIA04D_NETWORK_CLASS=${snapshot.networkClass.name} " +
                    "UNMETERED_QUALITY=${profiles.unmetered.name} " +
                    "METERED_QUALITY=${profiles.metered.name} " +
                    "EFFECTIVE_QUALITY=${effective.name} " +
                    "OFFLINE_PRESERVES_ACTIVE=true LOCAL_NETWORK_INDEPENDENT=true",
            )
            Log.i(MEDIA04D_LOG_TAG, "RESULT=SUBSHELL_MEDIA04D_NETWORK_QUALITY_RUNTIME_PASS")
        } finally {
            instrumentation.runOnMainSync { monitor.close() }
        }
    }
}
