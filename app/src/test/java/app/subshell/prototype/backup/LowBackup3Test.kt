package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOW_BACKUP_DECODED_SNAPSHOTS_CACHED_V1 (OCB-454): an unchanged snapshot
 * file is not decoded again within a minute; a changed one is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowBackup3Test {
    private val directory = createTempDirectory("backup3").toFile()
    private var clock = 1_000_000L

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun anUnchangedFileIsServedFromTheDecodedCache() {
        val store = SettingsBackupStore(directory, appVersion = "test", deviceId = "phone", now = { clock })
        val saved = store.saveCurrent("Kept", SubshellSettings())
        assertEquals(listOf(saved.id), store.listSnapshots().map(SettingsSnapshot::id))

        // Same size and time, other bytes: only a fresh decode would notice.
        val file = directory.listFiles().orEmpty().single { it.name.startsWith(saved.id) }
        val stamp = file.lastModified()
        file.writeText(" ".repeat(file.length().toInt()))
        file.setLastModified(stamp)
        assertEquals(listOf(saved.id), store.listSnapshots().map(SettingsSnapshot::id))

        clock += 61_000L
        assertEquals(emptyList<String>(), store.listSnapshots().map(SettingsSnapshot::id))
    }

    @Test
    fun theFolderSignatureChangesWithItsFiles() {
        val folder = Folder()
        val vault = SettingsBackupVault(folder)
        val before = vault.listingSignature()
        assertEquals(before, vault.listingSignature())
        folder.files["a.subshell-settings.json"] = "x"
        assertNotEquals(before, vault.listingSignature())
    }

    private class Folder : BackupVaultFolder {
        val files = linkedMapOf<String, String>()
        override fun list() = files.map { (name, content) -> VaultDocument(name, content.length.toLong(), 1L, name) }
        override fun read(document: VaultDocument, maxChars: Int) = files.getValue(document.name)
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
        override fun rename(document: VaultDocument, newName: String): VaultDocument? = null
    }
}
