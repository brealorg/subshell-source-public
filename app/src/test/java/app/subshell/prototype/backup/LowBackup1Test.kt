package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP1 (low backup findings): two keys that trim to one account merge
 * (OCB-451), and an unreadable deletion list in the folder is left as it is
 * and applies no deletions (OCB-456).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowBackup1Test {
    @Test
    fun keysThatTrimToOneAccountMerge() {
        val mutes = normalizePortableCommunityMutesByAccount(
            mapOf(
                " acct" to listOf(PortableCommunityMute("first")),
                "acct" to listOf(PortableCommunityMute("second")),
            ),
        )
        assertEquals(listOf("first", "second"), mutes.getValue("acct").map(PortableCommunityMute::communityName))

        val drafts = normalizePortablePostComposerDraftsByAccount(
            mapOf(
                "acct " to listOf(draft("d1", 2_000L)),
                "acct" to listOf(draft("d2", 3_000L)),
            ),
        )
        assertEquals(setOf("d1", "d2"), drafts.getValue("acct").map(PortablePostComposerDraft::id).toSet())
    }

    @Test
    fun anUnreadableDeletionListIsLeftAloneAndAppliesNothing() {
        val folder = MemoryFolder()
        val stale = snapshot("00000000-0000-4000-8000-000000000001")
        folder.files["deleted-backups.json"] = "{\"deleted\": [ truncated"
        folder.files["${stale.id}.subshell-settings.json"] = SettingsSnapshotCodec.encode(stale)
        val vault = SettingsBackupVault(folder)

        val read = vault.readSnapshots()
        assertTrue(read.tombstonesUnreadable)

        vault.sync(
            snapshots = emptyList(),
            existingSnapshots = mapOf(stale.id to stale),
            generatedAtEpochMillis = 5_000L,
            tombstones = mapOf(stale.id to 4_000L),
            vaultTombstonesUnreadable = read.tombstonesUnreadable,
        )

        assertEquals("{\"deleted\": [ truncated", folder.files["deleted-backups.json"])
        assertTrue(folder.files.keys.any { it.startsWith(stale.id) })
    }

    @Test
    fun aDeviceSilentFor30DaysLosesItsAutomaticHistoryButNotItsManualBackups() {
        val day = 24L * 60L * 60L * 1_000L
        val now = 100L * day
        fun snap(id: Int, device: String, kind: SnapshotKind, createdDaysAgo: Long) = SettingsSnapshot(
            id = "00000000-0000-4000-8000-%012d".format(id),
            name = "s$id",
            createdAtEpochMillis = now - createdDaysAgo * day,
            appVersion = "test",
            kind = kind,
            deviceId = device,
            settings = SubshellSettings(),
        )
        val orphanChange = snap(1, "old-install", SnapshotKind.CHANGE, createdDaysAgo = 40)
        val orphanManual = snap(2, "old-install", SnapshotKind.MANUAL, createdDaysAgo = 45)
        val activeChange = snap(3, "tablet", SnapshotKind.CHANGE, createdDaysAgo = 2)
        val own = snap(4, "phone", SnapshotKind.CHANGE, createdDaysAgo = 1)

        val pruned = snapshotIdsToPrune(
            listOf(orphanChange, orphanManual, activeChange, own),
            ownDeviceId = "phone",
            nowEpochMillis = now,
        )

        assertEquals(setOf(orphanChange.id), pruned)
    }

    private fun draft(id: String, updated: Long) = PortablePostComposerDraft(
        id = id,
        community = "subshell",
        type = "TEXT",
        title = "Title $id",
        body = "",
        linkUrl = "",
        inputMode = "MARKDOWN",
        nsfw = false,
        spoiler = false,
        sendReplies = true,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = updated,
    )

    private fun snapshot(id: String) = SettingsSnapshot(
        id = id,
        name = "Kept",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
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
