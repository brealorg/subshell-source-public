package app.subshell.prototype.share

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.ComposerPostType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #33: reading a share, copying shared images in, and the toggle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareIntoSubshellTest {
    @org.junit.Before
    @org.junit.After
    fun forgetFileProviderRoots() = app.subshell.prototype.harness.forgetFileProviderRoots()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** Another app's provider: a PNG and a HEIC it lends out. */
    class OtherAppProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String = if (uri.path!!.endsWith(".heic")) "image/heic" else "image/png"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = File.createTempFile("shared", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @Test
    fun aSharedUrlBecomesALinkPost() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/story")
            .putExtra(Intent.EXTRA_SUBJECT, "A story")

        val post = readSharedPost(context, intent)!!

        assertEquals(ComposerPostType.LINK, post.type)
        assertEquals("A story", post.title)
        assertEquals("https://example.com/story", post.linkUrl)
    }

    @Test
    fun sharedImagesAreCopiedInAndUnsupportedOnesAreCounted() {
        Robolectric.buildContentProvider(OtherAppProvider::class.java).create("other.app.files")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/*")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(
                    Uri.parse("content://other.app.files/one.png"),
                    Uri.parse("content://other.app.files/two.heic"),
                ),
            )

        val post = readSharedPost(context, intent)!!

        assertEquals(ComposerPostType.MEDIA, post.type)
        assertEquals(1, post.media.size)
        assertEquals(1, post.skippedCount)
        val media = post.media.single()
        assertEquals(ComposerMediaKind.IMAGE, media.kind)
        assertTrue(media.uri, media.uri.startsWith("content://${context.packageName}.files/shared_into_subshell/"))
        val copies = sharedMediaDirectory(context).listFiles().orEmpty()
        assertTrue(copies.any { it.readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)) })
    }

    @Test
    fun theShareTargetIsOffUntilTurnedOn() {
        assertFalse(ShareIntoSubshellComponent.isEnabled(context))
        ShareIntoSubshellComponent.setEnabled(context, true)
        assertTrue(ShareIntoSubshellComponent.isEnabled(context))
        ShareIntoSubshellComponent.setEnabled(context, false)
        assertFalse(ShareIntoSubshellComponent.isEnabled(context))
    }

    @Test
    fun onlyOldUnreferencedCopiesArePruned() {
        val directory = sharedMediaDirectory(context).apply { mkdirs() }
        val kept = File(directory, "kept.png").apply { writeText("k"); setLastModified(0L) }
        val orphan = File(directory, "orphan.png").apply { writeText("o"); setLastModified(0L) }
        val fresh = File(directory, "fresh.png").apply { writeText("f") }
        val keptUri = "content://${context.packageName}.files/shared_into_subshell/kept.png"

        pruneSharedMedia(context, referencedUris = setOf(keptUri))

        assertTrue(kept.exists())
        assertFalse(orphan.exists())
        assertTrue(fresh.exists())
    }
}
