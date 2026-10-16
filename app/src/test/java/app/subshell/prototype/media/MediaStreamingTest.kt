package app.subshell.prototype.media

import java.io.File
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ISSUE35_MEDIA_STREAMING_V1: saving and sharing stream to a file with no size cap. */
class MediaStreamingTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** A generated stream of [size] bytes (index mod 251), never held in memory. */
    private class GeneratedStream(private val size: Long) : InputStream() {
        private var position = 0L
        override fun read(): Int = if (position >= size) -1 else ((position++ % 251).toInt())
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            val count = minOf(length.toLong(), size - position).toInt()
            for (index in 0 until count) buffer[offset + index] = ((position + index) % 251).toByte()
            position += count
            return count
        }
    }

    @Test
    fun aFileFarAboveTheOldTwelveMegabyteCapIsStreamedWhole() {
        val size = 50L * 1_024L * 1_024L
        val file = temporary.newFile("large.partial")

        val streamed = streamToFileKeepingLeadingBytes(GeneratedStream(size), file, leadingCount = 16)

        assertEquals(size, streamed.totalBytes)
        assertEquals(size, file.length())
        assertArrayEquals(ByteArray(16) { it.toByte() }, streamed.leadingBytes)
    }

    @Test
    fun aShortBodyKeepsOnlyTheBytesItHas() {
        val file = temporary.newFile("gif.partial")
        val gif = "GIF89a".toByteArray()

        val streamed = streamToFileKeepingLeadingBytes(gif.inputStream(), file, leadingCount = 16)

        assertEquals(6L, streamed.totalBytes)
        assertArrayEquals(gif, streamed.leadingBytes)
        assertArrayEquals(gif, File(file.path).readBytes())
    }

    @Test
    fun anEmptyBodyIsReportedAsZeroBytes() {
        val file = temporary.newFile("empty.partial")
        assertEquals(0L, streamToFileKeepingLeadingBytes(ByteArray(0).inputStream(), file, 16).totalBytes)
    }
}
