package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP10 (#131): restoring first saves "Before restoring <name>"; with a
 * backup name of 64-80 characters (the UI allows 80) that safety name broke
 * the 80-character limit and the restore failed before it began.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup10LongNameRestoreTest {
    @Test
    fun aBackupWithAnEightyCharacterNameCanBeRestored() {
        val directory = createTempDirectory("backup10").toFile()
        try {
            val store = SettingsBackupStore(directory, appVersion = "t", deviceId = "d", now = { 5L })
            val longName = "A".repeat(80)
            val saved = store.saveCurrent(longName, SubshellSettings())

            store.prepareRestore(saved.id, SubshellSettings())

            val safety = store.listSnapshots().single { it.id != saved.id }
            assertTrue(safety.name.startsWith("Before restoring "))
            assertEquals(80, safety.name.length)
            assertTrue(safety.name.endsWith("…"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aShortNameIsKeptWhole() {
        assertEquals("Before restoring Home setup", safetySnapshotName("Home setup"))
    }
}
