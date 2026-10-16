package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP08 (review of the pre-Claude backup package, findings 1, 6 and 7):
 * the shared backup folder sync writes through a temporary file, is not
 * stopped by an unreadable file, deletes only files it read and then
 * removed, and trims history per device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup08VaultSyncSafetyTest {
    @Test
    fun anUnreadableFileIsCountedAndLeftAlone() {
        val folder = FakeFolder()
        folder.put("broken.subshell-settings.json", "{ truncated")
        val vault = SettingsBackupVault(folder)

        assertEquals(1, vault.readSnapshots().unreadableFiles)
        vault.sync(listOf(snapshot("a")), existingSnapshots = emptyMap(), generatedAtEpochMillis = 1L)

        assertTrue("broken.subshell-settings.json" in folder.files)
        assertTrue("a.subshell-settings.json" in folder.files)
    }

    @Test
    fun onlyFilesThisDeviceReadAndRemovedAreDeleted() {
        val folder = FakeFolder()
        val known = snapshot("known")
        folder.put("known.subshell-settings.json", SettingsSnapshotCodec.encode(known))
        // Added by another device after this device read the folder.
        folder.put("newcomer.subshell-settings.json", SettingsSnapshotCodec.encode(snapshot("newcomer")))

        SettingsBackupVault(folder).sync(
            snapshots = emptyList(),
            existingSnapshots = mapOf(known.id to known),
            generatedAtEpochMillis = 1L,
        )

        assertFalse("known.subshell-settings.json" in folder.files)
        assertTrue("newcomer.subshell-settings.json" in folder.files)
    }

    @Test
    fun aWriteGoesThroughATemporaryFileAndLeavesNoneBehind() {
        listOf(true, false).forEach { canRename ->
            val folder = FakeFolder(canRename = canRename)
            folder.put("a.subshell-settings.json", "old")
            val updated = snapshot("a")

            SettingsBackupVault(folder).sync(listOf(updated), existingSnapshots = emptyMap(), generatedAtEpochMillis = 1L)

            assertEquals(SettingsSnapshotCodec.encode(updated), folder.files["a.subshell-settings.json"])
            assertTrue("rename=$canRename ${folder.files.keys}", folder.files.keys.none { ".tmp-" in it })
        }
    }

    @Test
    fun aFailedWriteLeavesTheExistingFileIntact() {
        val folder = FakeFolder(failWritesTo = ".tmp-")
        folder.put("a.subshell-settings.json", "previous")
        try {
            SettingsBackupVault(folder).sync(listOf(snapshot("a")), existingSnapshots = emptyMap(), generatedAtEpochMillis = 1L)
            fail("the write should fail")
        } catch (_: IllegalStateException) {
        }
        assertEquals("previous", folder.files["a.subshell-settings.json"])
        assertTrue(folder.files.keys.none { ".tmp-" in it })
    }

    @Test
    fun staleTemporaryFilesAreRemovedAndFreshOnesKept() {
        val folder = FakeFolder()
        folder.put("x.subshell-settings.json.tmp-old", "a", lastModified = 1_000L)
        folder.put("y.subshell-settings.json.tmp-new", "b", lastModified = 10_000_000L - 1_000L)

        SettingsBackupVault(folder, now = { 10_000_000L }).sync(emptyList(), emptyMap(), 1L)

        assertFalse("x.subshell-settings.json.tmp-old" in folder.files)
        assertTrue("y.subshell-settings.json.tmp-new" in folder.files)
    }

    @Test
    fun historyIsTrimmedPerDevice() {
        val snapshots = (1..8).flatMap { index ->
            listOf(
                snapshot("a$index", deviceId = "phone", createdAt = index.toLong(), kind = SnapshotKind.CHANGE),
                snapshot("b$index", deviceId = "tablet", createdAt = index.toLong(), kind = SnapshotKind.CHANGE),
            )
        }
        val pruned = snapshotIdsToPrune(snapshots, BackupRetentionPolicy(automaticHistory = BackupRetention.FIVE))

        assertEquals(6, pruned.size)
        assertEquals(3, pruned.count { it.startsWith("a") })
        assertEquals(3, pruned.count { it.startsWith("b") })
    }

    private fun snapshot(
        id: String,
        deviceId: String = "device",
        createdAt: Long = 1L,
        kind: SnapshotKind = SnapshotKind.MANUAL,
    ) = SettingsSnapshot(
        id = id,
        name = "Backup $id",
        createdAtEpochMillis = createdAt,
        appVersion = "0.78.0",
        kind = kind,
        deviceId = deviceId,
        settings = SubshellSettings(),
    )

    private class FakeFolder(
        private val canRename: Boolean = true,
        private val failWritesTo: String? = null,
    ) : BackupVaultFolder {
        val files = linkedMapOf<String, String>()
        private val modified = mutableMapOf<String, Long>()

        fun put(name: String, content: String, lastModified: Long = 1L) {
            files[name] = content
            modified[name] = lastModified
        }

        override fun list(): List<VaultDocument> = files.map { (name, content) ->
            VaultDocument(name, content.length.toLong(), modified[name] ?: 1L, handle = name)
        }

        override fun read(document: VaultDocument, maxChars: Int): String = files.getValue(document.name)

        override fun create(name: String, mimeType: String): VaultDocument {
            put(name, "")
            return VaultDocument(name, 0L, 1L, handle = name)
        }

        override fun write(document: VaultDocument, content: String) {
            val name = document.handle as String
            if (failWritesTo != null && failWritesTo in name) error("disk full")
            files[name] = content
        }

        override fun delete(document: VaultDocument) {
            files.remove(document.handle as String)
        }

        override fun rename(document: VaultDocument, newName: String): VaultDocument? {
            if (!canRename) return null
            val content = files.remove(document.handle as String) ?: return null
            put(newName, content)
            return VaultDocument(newName, content.length.toLong(), 1L, handle = newName)
        }
    }
}
