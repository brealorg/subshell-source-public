package app.subshell.prototype.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.R
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val MEDIA04C_LOG_TAG = "subshell-cache"
private const val MEDIA04C_CACHE_KEY = "subshell:media04c:clear-refill:v1"
private const val MEDIA04C_CACHE_DATABASE = "exoplayer_internal.db"

@RunWith(AndroidJUnit4::class)
class VideoMediaCacheClearInstrumentedTest {
    @Test
    fun cacheStatusClearAndRefillRemainUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = context.resources.openRawResource(R.raw.subshell_video_lab_a).use { it.readBytes() }
        val cacheDirectory = File(context.cacheDir, VIDEO_MEDIA_CACHE_DIRECTORY)

        assertTrue(cacheDirectory.deleteRecursively() || !cacheDirectory.exists())
        context.deleteDatabase(MEDIA04C_CACHE_DATABASE)

        val server = LocalHttpsFixtureServer(
            context = instrumentation.context,
            payload = fixture,
        ).also(LocalHttpsFixtureServer::start)

        try {
            val fixtureUri = "https://127.0.0.1:${server.port}/media04a1.mp4"
            val firstRead = readFully(
                factory = VideoMediaDataSource.factory(context),
                uri = fixtureUri,
                cacheKey = MEDIA04C_CACHE_KEY,
            )
            val requestsAfterFirstRead = server.requestCount
            val before = VideoMediaDataSource.cacheStatus(context)

            assertArrayEquals(fixture, firstRead)
            assertTrue("The seeded cache must contain bytes", before.bytesUsed >= fixture.size)
            assertTrue("The seeded cache must expose a resource", before.resourceCount > 0)

            val clearResult = VideoMediaDataSource.clearCache(context)
            val after = VideoMediaDataSource.cacheStatus(context)

            assertEquals(before, clearResult.before)
            assertEquals(after, clearResult.after)
            assertTrue("Clear must report at least one removed resource", clearResult.resourcesRemoved > 0)
            assertTrue("Clear must report removed bytes", clearResult.bytesRemoved >= fixture.size)
            assertEquals("Cache bytes must be zero after clear", 0L, after.bytesUsed)
            assertEquals("Cache resources must be zero after clear", 0, after.resourceCount)

            val refillRead = readFully(
                factory = VideoMediaDataSource.factory(context),
                uri = fixtureUri,
                cacheKey = MEDIA04C_CACHE_KEY,
            )
            val refill = VideoMediaDataSource.cacheStatus(context)

            assertArrayEquals(fixture, refillRead)
            assertTrue("The same process-local cache must accept new writes", refill.bytesUsed >= fixture.size)
            assertTrue("Refill must reach upstream again", server.requestCount > requestsAfterFirstRead)

            Log.i(
                MEDIA04C_LOG_TAG,
                "MEDIA04C_BEFORE_BYTES=${before.bytesUsed} " +
                    "AFTER_BYTES=${after.bytesUsed} " +
                    "REMOVED_RESOURCES=${clearResult.resourcesRemoved} " +
                    "REMOVED_BYTES=${clearResult.bytesRemoved} " +
                    "REFILL_BYTES=${refill.bytesUsed} " +
                    "HTTPS_REQUESTS=${server.requestCount}",
            )
            Log.i(MEDIA04C_LOG_TAG, "RESULT=SUBSHELL_MEDIA04C_CACHE_CLEAR_RUNTIME_PASS")
        } finally {
            server.close()
            server.assertHealthy()
        }
    }
}
