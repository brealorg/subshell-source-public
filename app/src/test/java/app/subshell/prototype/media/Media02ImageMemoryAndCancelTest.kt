package app.subshell.prototype.media

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * MEDIA02 (review of the pre-Claude media package, findings 7 and 8):
 * - a share/save download stops as soon as it is no longer wanted instead of
 *   transferring the whole body first;
 * - display loads read a known-length body straight into one array, stay
 *   bounded when the server sends more than it declared, and stop when
 *   cancelled;
 * - an encoded image too large for its cache is not put in it.
 */
class Media02ImageMemoryAndCancelTest {
    @Test
    fun aKnownLengthBodyIsReadExactly() {
        val body = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(body, readBoundedMediaBytes(ByteArrayInputStream(body), body.size.toLong(), 20_000))
    }

    @Test
    fun anUnknownOrWrongLengthStaysCorrectAndBounded() {
        val body = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(body, readBoundedMediaBytes(ByteArrayInputStream(body), -1L, 20_000))
        // Declared shorter than sent is read on; a body that ends before its
        // declared length is a failed read (OCB-647), not a complete image.
        assertArrayEquals(body, readBoundedMediaBytes(ByteArrayInputStream(body), 4_000L, 20_000))
        assertThrows(java.io.EOFException::class.java) {
            readBoundedMediaBytes(ByteArrayInputStream(body), 12_000L, 20_000)
        }
        // Too large, declared or not.
        assertNull(readBoundedMediaBytes(ByteArrayInputStream(body), 30_000L, 20_000))
        assertNull(readBoundedMediaBytes(ByteArrayInputStream(body), -1L, 5_000))
        assertNull(readBoundedMediaBytes(ByteArrayInputStream(body), 4_000L, 5_000))
    }

    @Test
    fun aDisplayLoadStopsWhenCancelled() {
        val endless = EndlessStream()
        var reads = 0
        try {
            readBoundedMediaBytes(endless, -1L, Long.MAX_VALUE) { ++reads < 3 }
            fail("a cancelled load should stop")
        } catch (_: CancellationException) {
        }
        assertTrue("read ${endless.bytesRead} bytes", endless.bytesRead < 64 * 1024)
    }

    @Test
    fun aShareDownloadStopsWhenNoLongerWanted() {
        val endless = EndlessStream()
        val file = File.createTempFile("media02", ".bin")
        var checks = 0
        try {
            streamToFileKeepingLeadingBytes(endless, file, 16) { ++checks < 3 }
            fail("a cancelled download should stop")
        } catch (_: CancellationException) {
        } finally {
            file.delete()
        }
        assertTrue("read ${endless.bytesRead} bytes", endless.bytesRead < 64 * 1024)
    }

    @Test
    fun onlyImagesThatFitTheCacheAreCached() {
        assertTrue(RemoteImageLoader.shouldCacheEncodedImage(5 * 1024 * 1024))
        assertFalse(RemoteImageLoader.shouldCacheEncodedImage(7 * 1024 * 1024))
        assertFalse(RemoteImageLoader.shouldCacheEncodedImage(40 * 1024 * 1024))
    }

    /** A body that never ends, like a 500 MB video. */
    private class EndlessStream : InputStream() {
        var bytesRead = 0L
        override fun read(): Int {
            bytesRead += 1
            return 0
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            bytesRead += length
            return length
        }
    }
}
