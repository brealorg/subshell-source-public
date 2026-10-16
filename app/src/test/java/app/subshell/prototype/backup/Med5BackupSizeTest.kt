package app.subshell.prototype.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MED5 (OCB-455): a backup this build writes with the saved-responses opt-in
 * full of Norwegian text passed the 1 000 000-byte read limit and became
 * unreadable in the backup folder for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Med5BackupSizeTest {
    @Test
    fun aBackupWithAllSavedResponsesReadsBackFromTheFolder() {
        val responses = (1..BACKUP04A4_MAX_SAVED_RESPONSES).map { index ->
            PortableModeratorSavedResponse(
                id = "r$index",
                title = "Svar $index",
                body = "ø".repeat(BACKUP04A4_MAX_BODY_CHARS),
                updatedAtEpochMs = index.toLong(),
            )
        }
        val snapshot = SettingsSnapshot(
            id = "big",
            name = "Big",
            createdAtEpochMillis = 1L,
            appVersion = "test",
            kind = SnapshotKind.MANUAL,
            deviceId = "device",
            settings = SubshellSettings(),
            moderatorSavedResponses = responses,
        )
        val encoded = SettingsSnapshotCodec.encode(snapshot)
        assertTrue("${encoded.toByteArray().size} bytes", encoded.toByteArray().size > 1_000_000)

        val folder = ByteSizedFolder().apply { files["big.subshell-settings.json"] = encoded }
        val read = SettingsBackupVault(folder).readSnapshots()

        assertEquals(0, read.unreadableFiles)
        assertEquals(BACKUP04A4_MAX_SAVED_RESPONSES, read.snapshots.single().moderatorSavedResponses?.size)
    }

    @Test
    fun theFolderDescriptionIsReadFromPreferencesNotWorkedOutAgain() {
        // MED5 (OCB-453): status() runs in composition; the description of a
        // connected folder comes from preferences, not provider IPC.
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.getSharedPreferences("subshell_backup_preferences", Context.MODE_PRIVATE).edit()
            .putString("tree_uri", "content://com.example.cloud.documents/tree/root%3ASubshell")
            .putString("folder_location", "Example Cloud › Subshell")
            .commit()

        val status = SettingsBackupRepository(application, appVersion = "test").status()

        assertEquals("Example Cloud › Subshell", status.folderLocation)
    }

    /** Reports sizes in UTF-8 bytes, as a document provider does. */
    private class ByteSizedFolder : BackupVaultFolder {
        val files = linkedMapOf<String, String>()
        override fun list() = files.map { (name, content) ->
            VaultDocument(name, content.toByteArray().size.toLong(), 1L, name)
        }
        override fun read(document: VaultDocument, maxChars: Int): String {
            val content = files.getValue(document.name)
            require(content.length <= maxChars) { "Backup file is too large" }
            return content
        }
        override fun create(name: String, mimeType: String): VaultDocument {
            files[name] = ""
            return VaultDocument(name, 0L, 1L, name)
        }
        override fun write(document: VaultDocument, content: String) {
            files[document.handle as String] = content
        }
        override fun delete(document: VaultDocument) {
            files.remove(document.handle as String)
        }
        override fun rename(document: VaultDocument, newName: String): VaultDocument? {
            val content = files.remove(document.handle as String) ?: return null
            files[newName] = content
            return VaultDocument(newName, content.toByteArray().size.toLong(), 1L, newName)
        }
    }
}
