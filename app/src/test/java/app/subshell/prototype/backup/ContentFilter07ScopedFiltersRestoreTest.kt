package app.subshell.prototype.backup

import app.subshell.prototype.ui.ContentFilterType
import app.subshell.prototype.ui.ContentFilters
import app.subshell.prototype.ui.ScopedContentFilter
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.screens.settingsRestoreChangeCount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

/**
 * CONTENTFILTER07_SCOPED_FILTERS_MISSING_KEEPS_CURRENT_V1: a backup written
 * before community-scoped filters existed carries no
 * `content_filter_<type>_scoped` key. Restoring it leaves the current scoped
 * filters in place (missing = unchanged), while a newer backup whose scoped
 * lists are empty clears them (present but empty = authoritatively empty).
 * Every other setting still comes from the backup.
 */
class ContentFilter07ScopedFiltersRestoreTest {
    private val currentScoped = setOf(
        ScopedContentFilter(ContentFilterType.WORD, "spoiler", "movies"),
    )
    private val current = SubshellSettings(
        contentFilters = ContentFilters(scoped = currentScoped),
    )
    private val backedUp = SubshellSettings(
        contentFilters = ContentFilters(words = setOf("crypto")),
    )

    @Test
    fun aBackupFromBeforeScopedFiltersKeepsTheCurrentOnes() {
        val old = SettingsSnapshotCodec.decode(withoutScopedKeys(SettingsSnapshotCodec.encode(snapshot(backedUp))))

        assertFalse(old.scopedContentFiltersIncluded)
        val restored = old.settingsRestoredOver(current)
        assertEquals(currentScoped, restored.contentFilters.scoped)
        assertEquals(setOf("crypto"), restored.contentFilters.words)
    }

    @Test
    fun aBackupWithEmptyScopedFiltersClearsTheCurrentOnes() {
        val newer = SettingsSnapshotCodec.decode(SettingsSnapshotCodec.encode(snapshot(backedUp)))

        assertTrue(newer.scopedContentFiltersIncluded)
        assertEquals(emptySet<ScopedContentFilter>(), newer.settingsRestoredOver(current).contentFilters.scoped)
    }

    @Test
    fun aBackupWithScopedFiltersRestoresThem() {
        val backedUpScoped = setOf(ScopedContentFilter(ContentFilterType.USER, "someone", "news"))
        val newer = SettingsSnapshotCodec.decode(
            SettingsSnapshotCodec.encode(
                snapshot(backedUp.copy(contentFilters = backedUp.contentFilters.copy(scoped = backedUpScoped))),
            ),
        )

        assertEquals(backedUpScoped, newer.settingsRestoredOver(current).contentFilters.scoped)
    }

    @Test
    fun reEncodingAnOldBackupDoesNotMakeItsScopedFiltersAuthoritativelyEmpty() {
        val old = SettingsSnapshotCodec.decode(withoutScopedKeys(SettingsSnapshotCodec.encode(snapshot(backedUp))))
        // Renaming a backup re-encodes it.
        val reEncoded = SettingsSnapshotCodec.encode(old.copy(name = "Renamed"))

        assertFalse(reEncoded.contains("_scoped"))
        assertFalse(SettingsSnapshotCodec.decode(reEncoded).scopedContentFiltersIncluded)
    }

    @Test
    fun theRestorePreviewDoesNotCountKeptScopedFiltersAsChanges() {
        val old = SettingsSnapshotCodec.decode(withoutScopedKeys(SettingsSnapshotCodec.encode(snapshot(current))))

        assertEquals(0, settingsRestoreChangeCount(current, old.settingsRestoredOver(current)))
    }

    @Test
    fun anImportedBackupFromBeforeScopedFiltersKeepsTheCurrentOnes() {
        val directory = createTempDirectory("contentfilter08").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "target-version",
                deviceId = "target-device",
                now = { 200L },
            )
            val oldFile = withoutScopedKeys(SettingsSnapshotCodec.encode(snapshot(backedUp)))

            val imported = store.importSnapshot(oldFile)
            assertFalse(imported.scopedContentFiltersIncluded)

            // The import is stored and read back from disk before a restore.
            val target = store.prepareRestore(imported.id, current)
            assertFalse(target.scopedContentFiltersIncluded)
            val restored = target.settingsRestoredOver(current)
            assertEquals(currentScoped, restored.contentFilters.scoped)
            assertEquals(setOf("crypto"), restored.contentFilters.words)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun snapshot(settings: SubshellSettings) = SettingsSnapshot(
        id = "backup-1",
        name = "Backup",
        createdAtEpochMillis = 1L,
        appVersion = "0.77.0",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = settings,
    )

    private fun withoutScopedKeys(encoded: String): String {
        val root = Json.parseToJsonElement(encoded).jsonObject
        val settings = root.getValue("settings").jsonObject
        assertTrue(settings.keys.any { it.endsWith("_scoped") })
        return JsonObject(
            root + ("settings" to JsonObject(settings.filterKeys { !it.endsWith("_scoped") })),
        ).toString()
    }
}
