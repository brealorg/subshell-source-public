package app.subshell.prototype.media

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1 (OCB-108): with the download
 * system retired, Settings can still see and delete what an older build
 * downloaded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowUi4cLegacyVideoDownloadsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val folder = File(application.noBackupFilesDir, LEGACY_VIDEO_DOWNLOAD_DIRECTORY)

    @Before
    @After
    fun clean() {
        folder.deleteRecursively()
    }

    @Test
    fun oldDownloadsAreCountedAndDeleted() {
        ensureWorkManagerInitialized(application)
        folder.mkdirs()
        File(folder, "a.media").writeBytes(ByteArray(3))
        File(folder, "b.media").writeBytes(ByteArray(5))
        File(folder, "c.part").writeBytes(ByteArray(7))
        val downloads = LegacyVideoDownloads(application)

        val before = downloads.status()
        assertEquals(2, before.videoCount)
        assertEquals(8L, before.videoBytes)
        assertTrue(before.hasDownloads)

        val deleted = downloads.deleteAll()

        assertEquals(2, deleted.videosRemoved)
        assertEquals(8L, deleted.bytesRemoved)
        assertFalse(folder.exists())
        assertFalse(downloads.status().hasDownloads)
    }

    @Test
    fun aLeftoverPartialFileStillOffersTheDelete() {
        folder.mkdirs()
        File(folder, "c.part").writeBytes(ByteArray(7))

        val status = LegacyVideoDownloads(application).status()

        assertEquals(0, status.videoCount)
        assertTrue(status.hasDownloads)
    }
}
