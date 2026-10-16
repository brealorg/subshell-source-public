package app.subshell.prototype.backup

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Settings02BBackupBulkDeletePolicyTest {
    @Test
    fun delete_all_removes_every_snapshot_file_without_decoding_contents() {
        val directory = Files.createTempDirectory("subshell-settings02b").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "test",
                deviceId = "device",
            )
            val snapshotIds = listOf(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
            )
            snapshotIds.forEachIndexed { index, id ->
                directory.resolve("$id.json").writeText("not-decoded-by-bulk-delete-$index")
            }
            val temporary = directory.resolve("stale-write.tmp").apply { writeText("partial") }
            val unrelated = directory.resolve("keep-me.txt").apply { writeText("unrelated") }

            assertEquals(3, store.deleteAllSnapshots())
            assertTrue(snapshotIds.none { id -> directory.resolve("$id.json").exists() })
            assertFalse(temporary.exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun delete_all_on_empty_store_is_a_noop() {
        val directory = Files.createTempDirectory("subshell-settings02b-empty").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "test",
                deviceId = "device",
            )

            assertEquals(0, store.deleteAllSnapshots())
            assertTrue(directory.listFiles().orEmpty().none { it.extension == "json" })
        } finally {
            directory.deleteRecursively()
        }
    }
}
