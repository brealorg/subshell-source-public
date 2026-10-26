package app.subshell.prototype.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SECAUDIT23 (security audit #23): a share is read inside Subshell's process,
 * so it could name Subshell's own files (copied shares, diagnostics reports)
 * or a file:// path. Only another app's content URI is taken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecAudit23SharedStreamOriginTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    @After
    fun forgetFileProviderRoots() = app.subshell.prototype.harness.forgetFileProviderRoots()

    @Test
    fun aShareOfSubshellsOwnFilesIsSkipped() {
        // A real file behind Subshell's own FileProvider, so without the rule the copy would succeed.
        java.io.File(context.cacheDir, "diagnostics_reports").apply { mkdirs() }
            .resolve("report.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        Robolectric.buildContentProvider(ShareIntoSubshellTest.OtherAppProvider::class.java)
            .create("other.app.files")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/*")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(
                    Uri.parse("content://${context.packageName}.files/diagnostics_reports/report.png"),
                    Uri.parse("file:///data/data/${context.packageName}/files/report.png"),
                    Uri.parse("content://other.app.files/one.png"),
                ),
            )

        val post = readSharedPost(context, intent)!!

        assertEquals(1, post.media.size)
        assertEquals(2, post.skippedCount)
    }

    @Test
    fun onlyAnotherAppsContentUriIsForeign() {
        assertTrue(isForeignSharedStream(context, Uri.parse("content://other.app.files/one.png")))
        assertFalse(isForeignSharedStream(context, Uri.parse("content://${context.packageName}.files/x.png")))
        assertFalse(isForeignSharedStream(context, Uri.parse("file:///sdcard/x.png")))
    }
}
