package app.subshell.prototype.ui

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Media I/O folds ordinary failures into null but never swallows cancellation. */
class MediaActionsCancellationTest {
    @Test
    fun aValueIsReturned() {
        assertEquals("saved", nullUnlessCancelled { "saved" })
    }

    @Test
    fun anOrdinaryFailureBecomesNull() {
        assertNull(nullUnlessCancelled<String> { throw IOException("disk full") })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsRethrown() {
        nullUnlessCancelled<String> { throw CancellationException("left the screen") }
    }
}
