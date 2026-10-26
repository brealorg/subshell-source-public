package app.subshell.prototype

import android.os.StrictMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STRICTMODE01_DEBUG_LOGS_MAIN_THREAD_IO_V1 (#243): debug builds watch the main
 * thread for disk and network work; release builds leave the policy alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StrictMode01MainThreadCheckTest {
    @After
    fun tearDown() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    }

    @Test
    fun aDebugBuildWatchesDiskAndNetworkOnTheMainThread() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)

        installDebugMainThreadCheck(debug = true)

        val expected = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build()
        assertEquals(expected.toString(), StrictMode.getThreadPolicy().toString())
        assertNotEquals(StrictMode.ThreadPolicy.LAX.toString(), StrictMode.getThreadPolicy().toString())
    }

    @Test
    fun aReleaseBuildLeavesThePolicyAlone() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)

        installDebugMainThreadCheck(debug = false)

        assertEquals(StrictMode.ThreadPolicy.LAX.toString(), StrictMode.getThreadPolicy().toString())
    }
}
