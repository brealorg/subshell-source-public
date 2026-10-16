package app.subshell.prototype.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UPDATE05 (review of the pre-Claude update package, finding 10): the
 * bundled changelog is parsed once per process (composables load it through
 * rememberBundledChangelog on an IO thread), and the manifest and changelog
 * share one bounded reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update05ChangelogLoadTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theBundledChangelogIsParsedOncePerProcess() {
        val first = UpdateChangelogRepository(application).load()
        val second = UpdateChangelogRepository(application).load()
        assertSame(first, second)
    }

    @Test
    fun theSharedReaderIsBounded() {
        assertEquals("abc", ByteArrayInputStream("abc".toByteArray()).readUpdateUtf8Bounded(3))
        try {
            ByteArrayInputStream("abcd".toByteArray()).readUpdateUtf8Bounded(3)
            fail("a body over the limit should be refused")
        } catch (_: UpdateBodyTooLargeException) {
        }
    }
}
