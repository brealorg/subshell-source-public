package app.subshell.prototype.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.ui.SubshellSettings
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (settings02c): automatic history keeps ten changes by default, a
 * stricter limit trims at once, a vault merge can be told not to trim, and the
 * repository keeps the reader's retention choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3BackupRetentionTest {
    private var clock = 1_800_000_000_000L

    private fun store(deviceId: String = "device") = SettingsBackupStore(
        directory = Files.createTempDirectory("backup-retention").toFile(),
        appVersion = "test",
        deviceId = deviceId,
        now = { clock },
    )

    private fun SettingsBackupStore.recordChanges(count: Int, prefix: String = "c") = repeat(count) { index ->
        clock += 1_000L
        recordSettingsChange(
            settings = SubshellSettings(),
            joinedCommunityIdsByAccount = mapOf("local:root" to setOf("$prefix$index")),
        )
    }

    private fun SettingsBackupStore.changes() = listSnapshots().count { it.kind == SnapshotKind.CHANGE }

    @Test
    fun automaticHistoryKeepsTenChangesByDefault() {
        val store = store()

        store.recordChanges(12)

        assertEquals(10, store.changes())
    }

    @Test
    fun aStricterLimitTrimsAtOnce() {
        val store = store()
        store.recordChanges(10)

        store.setRetentionPolicy(BackupRetentionPolicy(automaticHistory = BackupRetention.FIVE))

        assertEquals(5, store.pruneHistory())
        assertEquals(5, store.changes())
    }

    // A device trims only its own history (BACKUP09); a merge from the vault
    // trims it unless the caller says not to (a retention change reads the
    // vault first, then trims once).
    @Test
    fun aMergeTrimsOwnHistoryUnlessToldNotTo() {
        val other = store(deviceId = "other")
        other.recordChanges(7, prefix = "o")
        val incoming = other.listSnapshots()

        fun ownWithEightChanges() = store().apply {
            recordChanges(8)
            setRetentionPolicy(BackupRetentionPolicy(automaticHistory = BackupRetention.FIVE))
        }

        val kept = ownWithEightChanges()
        kept.mergeSnapshots(incoming, pruneAfterMerge = false)
        assertEquals(15, kept.changes())

        val trimmed = ownWithEightChanges()
        trimmed.mergeSnapshots(incoming)
        assertEquals(12, trimmed.changes())
    }

    @Test
    fun theRepositoryKeepsTheRetentionChoiceAndTrimsWithIt() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = SettingsBackupRepository(context, "test", now = { clock })
        assertEquals(BackupRetention.TEN, repository.automaticHistoryRetention())
        assertEquals(BackupRetention.FIVE, repository.safetySnapshotRetention())
        repeat(8) { index ->
            clock += 1_000L
            repository.recordSettingsChange(
                settings = SubshellSettings(),
                joinedCommunityIdsByAccount = mapOf("local:root" to setOf("r$index")),
            )
        }

        repository.setAutomaticHistoryRetention(BackupRetention.FIVE)

        assertEquals(5, repository.listSnapshots().count { it.kind == SnapshotKind.CHANGE })
        assertEquals(BackupRetention.FIVE, SettingsBackupRepository(context, "test").automaticHistoryRetention())
    }
}
