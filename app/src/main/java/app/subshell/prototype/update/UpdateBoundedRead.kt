package app.subshell.prototype.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/** The update manifest or bundled changelog is larger than its limit. */
internal class UpdateBodyTooLargeException : IOException("Update data exceeds its byte limit")

/**
 * UPDATE05_ONE_BOUNDED_UPDATE_READER_V1: the one bounded UTF-8 read behind
 * the update manifest and the bundled changelog (it was written out twice).
 */
internal fun InputStream.readUpdateUtf8Bounded(maxBytes: Int): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw UpdateBodyTooLargeException()
        output.write(buffer, 0, count)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}
