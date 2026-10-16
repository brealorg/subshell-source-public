package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP09 (review of the pre-Claude backup package, finding 5, and the
 * rest of 6): deletions travel as tombstones, each device trims only its
 * own automatic history, and the later of two changed copies wins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup09TombstonesAndRenamesTest {
    private val directory = createTempDirectory("backup09").toFile()
    private var clock = 1_000L

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aDeletedBackupDoesNotComeBackFromTheFolder() {
        val store = store()
        val kept = store.saveCurrent("Kept", SubshellSettings())
        val deleted = store.saveCurrent("Deleted", SubshellSettings())
        store.deleteSnapshot(deleted.id)

        // The folder still has the deleted one (it was offline at the time).
        store.mergeSnapshots(listOf(kept, deleted))

        assertEquals(listOf(kept.id), store.listSnapshots().map(SettingsSnapshot::id))
        assertTrue(deleted.id in store.tombstones())
    }

    @Test
    fun aDeletionOnAnotherDeviceRemovesTheLocalCopy() {
        val store = store()
        val snapshot = store.saveCurrent("Shared", SubshellSettings())

        store.mergeSnapshots(emptyList(), tombstones = mapOf(snapshot.id to 5_000L))

        assertTrue(store.listSnapshots().isEmpty())
    }

    @Test
    fun theLaterRenameWinsOnBothSides() {
        val store = store()
        val original = store.saveCurrent("Before", SubshellSettings())
        val renamedElsewhere = original.copy(name = "Renamed on the tablet", updatedAtEpochMillis = 9_000L)

        store.mergeSnapshots(listOf(renamedElsewhere))
        assertEquals("Renamed on the tablet", store.listSnapshots().single().name)

        clock = 20_000L
        store.renameSnapshot(original.id, "Renamed here later")
        store.mergeSnapshots(listOf(renamedElsewhere))
        assertEquals("Renamed here later", store.listSnapshots().single().name)
    }

    @Test
    fun aDeviceTrimsOnlyItsOwnAutomaticHistory() {
        val store = store(retention = BackupRetentionPolicy(automaticHistory = BackupRetention.FIVE))
        val tabletHistory = (1..8).map { index ->
            automatic("tablet-$index", deviceId = "tablet", createdAt = index.toLong())
        }
        val phoneHistory = (1..8).map { index ->
            automatic("phone-$index", deviceId = "phone", createdAt = index.toLong())
        }

        store.mergeSnapshots(tabletHistory + phoneHistory)

        val ids = store.listSnapshots().map(SettingsSnapshot::id).toSet()
        assertEquals(8, ids.count { it.startsWith("tablet") })
        assertEquals(5, ids.count { it.startsWith("phone") })
        assertTrue("phone-1" in store.tombstones())
        assertFalse("tablet-1" in store.tombstones())
    }

    @Test
    fun theFolderCarriesTombstonesAndDropsTombstonedFiles() {
        val folder = MemoryFolder()
        val gone = automatic("gone", deviceId = "tablet", createdAt = 1L)
        folder.files["gone.subshell-settings.json"] = SettingsSnapshotCodec.encode(gone)
        val vault = SettingsBackupVault(folder)

        vault.sync(emptyList(), existingSnapshots = emptyMap(), generatedAtEpochMillis = 1L, tombstones = mapOf("gone" to 7L))

        assertFalse("gone.subshell-settings.json" in folder.files)
        assertEquals(mapOf("gone" to 7L), vault.readSnapshots().tombstones)
    }

    @Test
    fun theChangeTimeSurvivesTheCodecAndOldFilesHaveNone() {
        val renamed = automatic("x", deviceId = "d", createdAt = 1L).copy(updatedAtEpochMillis = 42L)
        assertEquals(42L, SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(renamed)).updatedAtEpochMillis)
        val old = automatic("y", deviceId = "d", createdAt = 1L)
        assertFalse(SettingsSnapshotCodec.encode(old).contains("updatedAtEpochMillis"))
        assertEquals(0L, SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(old)).updatedAtEpochMillis)
    }

    private fun store(retention: BackupRetentionPolicy = BackupRetentionPolicy()) = SettingsBackupStore(
        directory = directory,
        appVersion = "0.78.0",
        deviceId = "phone",
        now = { clock++ },
        retentionPolicy = retention,
    )

    private fun automatic(id: String, deviceId: String, createdAt: Long) = SettingsSnapshot(
        id = id,
        name = "Automatic settings history",
        createdAtEpochMillis = createdAt,
        appVersion = "0.78.0",
        kind = SnapshotKind.CHANGE,
        deviceId = deviceId,
        settings = SubshellSettings(),
    )

    private class MemoryFolder : BackupVaultFolder {
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
        override fun rename(document: VaultDocument, newName: String): VaultDocument? {
            val content = files.remove(document.handle as String) ?: return null
            files[newName] = content
            return VaultDocument(newName, content.length.toLong(), 1L, newName)
        }
    }
}
