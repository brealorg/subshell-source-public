package app.subshell.prototype.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.PortableBackupState
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.app.recordAutomaticBackup
import java.io.File
import java.io.Reader
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP05 (review of the pre-Claude backup package, findings 4 and 8):
 * - a failing backup store no longer escapes the automatic history effect
 *   (it crashed the app on every start);
 * - backup files are read with a bound, so a huge file is refused before it
 *   is loaded into memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup05CrashAndMemoryTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val snapshotsPath = File(application.filesDir, "settings_snapshots")

    @After
    fun restoreStorage() {
        if (snapshotsPath.isFile) snapshotsPath.delete()
    }

    @Test
    fun aFailingStoreDoesNotEscapeTheAutomaticHistory() = runBlocking {
        // A file where the snapshot directory should be: the store cannot
        // create it, as with a full or broken disk.
        snapshotsPath.deleteRecursively()
        snapshotsPath.writeText("not a directory")
        val repository = SettingsBackupRepository(application, appVersion = "test")

        try {
            repository.recordSettingsChange(settings = SubshellSettings())
            fail("the store should fail in this setup")
        } catch (_: java.io.IOException) {
            // Expected (FileNotFoundException, like a full disk): this is
            // what used to escape the effect and crash the app.
        }

        recordAutomaticBackup(repository, portableState())
    }

    @Test
    fun aBackupWithinTheLimitIsReadWhole() {
        assertEquals("x".repeat(100), StringReader("x".repeat(100)).readBackupTextBounded(100))
    }

    @Test
    fun aLargerBackupIsRefusedWithoutReadingItAll() {
        val endless = CountingEndlessReader()
        try {
            endless.readBackupTextBounded(1_000)
            fail("an oversized backup should be refused")
        } catch (tooLarge: IllegalArgumentException) {
            assertEquals("Backup file is too large", tooLarge.message)
        }
        assertTrue("read ${endless.charsRead} chars", endless.charsRead <= 1_000 + 8 * 1024)
    }

    private fun portableState() = PortableBackupState(
        settings = SubshellSettings(),
        joinedCommunityIdsByAccount = emptyMap(),
        favoriteCommunityNamesByAccount = emptyMap(),
        favoriteCustomFeedPathsByAccount = emptyMap(),
        hiddenCommunityNamesByAccount = emptyMap(),
        communityMutesByAccount = emptyMap(),
        postComposerDraftsByAccount = emptyMap(),
        moderatorSavedResponses = emptyList(),
        userTagsByAccount = emptyMap(),
    )

    /** A file that never ends, like a 500 MB video picked for import. */
    private class CountingEndlessReader : Reader() {
        var charsRead = 0L
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            buffer.fill('x', offset, offset + length)
            charsRead += length
            return length
        }
        override fun close() = Unit
    }
}
