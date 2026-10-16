package app.subshell.prototype.share

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.components.pastedImageKeptInApp
import java.io.File
import androidx.core.content.FileProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER16_PASTED_IMAGE_COPIED_V1 (ui review round 3 P11): a pasted image is
 * copied into app storage at once, so a draft still has it after the paste's
 * temporary read grant is gone (restart); a failed copy keeps the old address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Composer16PastedImageCopyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    // androidx FileProvider caches its path strategy statically per authority,
    // with the files directory of the first test that used it; Robolectric
    // gives every test a new one. Cleared before and after, so this test and
    // ShareIntoSubshellTest pass in either order in one JVM.
    @Before
    @After
    fun clearFileProviderCache() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { field -> (field.get(null) as MutableMap<*, *>).clear() }
    }

    /** A keyboard's provider lending out one pasted PNG. */
    class KeyboardProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String = "image/png"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = File.createTempFile("pasted", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @Test
    fun aPastedImageIsCopiedIntoAppStorageAndKeepsItsSourceLink() {
        Robolectric.buildContentProvider(KeyboardProvider::class.java).create("keyboard.app.files")
        val pasted = ComposerMediaAttachment(
            uri = "content://keyboard.app.files/sticker.png",
            mimeType = "image/png",
            kind = ComposerMediaKind.IMAGE,
            sourceLinkUri = "https://example.com/source",
        )

        val kept = pastedImageKeptInApp(context, pasted)

        assertTrue(kept.uri, kept.uri.startsWith("content://${context.packageName}.files/"))
        assertEquals("https://example.com/source", kept.sourceLinkUri)
        val copy = File(sharedMediaDirectory(context), Uri.parse(kept.uri).lastPathSegment!!)
        assertTrue(copy.exists())
        assertEquals(4L, copy.length())
    }

    @Test
    fun aPasteThatCannotBeCopiedKeepsItsAddress() {
        val unreadable = ComposerMediaAttachment(
            uri = "content://nobody.provides.this/x.png",
            mimeType = "image/png",
            kind = ComposerMediaKind.IMAGE,
        )
        assertEquals(unreadable, pastedImageKeptInApp(context, unreadable))
    }
}
