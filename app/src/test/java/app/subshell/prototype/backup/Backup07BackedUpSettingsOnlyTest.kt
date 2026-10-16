package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.ui.theme.ThemeMode
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BACKUP07 (review of the pre-Claude backup package, findings 2 and 3): a
 * restore applies only the settings the backup contains.
 * - A backup from an older build lacks the settings added since (text size,
 *   pure black, back twice to exit…); those stay as they are instead of
 *   resetting to their defaults.
 * - A backup from a newer build (an unknown key, or a new enum value) is
 *   no longer rejected: what this build cannot read is left unchanged on
 *   restore and kept verbatim when the snapshot is written again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Backup07BackedUpSettingsOnlyTest {
    private val current = SubshellSettings(
        appTextScale = AppTextScale.LARGER,
        pureBlackDarkTheme = true,
        backTwiceToExit = true,
        themeMode = ThemeMode.LIGHT,
    )

    @Test
    fun anOlderBackupLeavesNewerSettingsAsTheyAre() {
        val backedUp = SubshellSettings(themeMode = ThemeMode.DARK)
        val old = decode(
            encode(backedUp) { settings ->
                settings - SubshellSettings.APP_TEXT_SCALE -
                    SubshellSettings.PURE_BLACK_DARK_THEME -
                    SubshellSettings.BACK_TWICE_TO_EXIT
            },
        )

        val restored = old.settingsRestoredOver(current)
        assertEquals(AppTextScale.LARGER, restored.appTextScale)
        assertTrue(restored.pureBlackDarkTheme)
        assertTrue(restored.backTwiceToExit)
        assertEquals(ThemeMode.DARK, restored.themeMode)
    }

    @Test
    fun aNewerBackupIsReadAndWhatThisBuildCannotReadIsKept() {
        val newer = decode(
            encode(SubshellSettings(appTextScale = AppTextScale.SMALL)) { settings ->
                settings + (SubshellSettings.THEME_MODE to JsonPrimitive("SEPIA")) +
                    ("future_setting" to JsonPrimitive("on"))
            },
        )

        assertFalse(SubshellSettings.THEME_MODE in newer.settingsKeysIncluded)
        assertEquals(mapOf(SubshellSettings.THEME_MODE to "SEPIA", "future_setting" to "on"), newer.unreadSettings)
        val restored = newer.settingsRestoredOver(current)
        assertEquals(ThemeMode.LIGHT, restored.themeMode)
        assertEquals(AppTextScale.SMALL, restored.appTextScale)

        // Written again (import, rename): the unread values survive as written.
        val rewritten = settingsOf(SettingsSnapshotCodec.encode(newer.copy(name = "Renamed")))
        assertEquals("SEPIA", rewritten.getValue(SubshellSettings.THEME_MODE).jsonPrimitive.content)
        assertEquals("on", rewritten.getValue("future_setting").jsonPrimitive.content)
    }

    @Test
    fun anImportedOlderBackupKeepsSayingNothingAboutNewerSettings() {
        val directory = createTempDirectory("backup07").toFile()
        try {
            val store = SettingsBackupStore(directory, appVersion = "t", deviceId = "d", now = { 5L })
            val file = encode(SubshellSettings()) { settings -> settings - SubshellSettings.APP_TEXT_SCALE }
            val imported = store.importSnapshot(file)
            val target = store.prepareRestore(imported.id, current)
            assertEquals(AppTextScale.LARGER, target.settingsRestoredOver(current).appTextScale)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aLegacyKeyCountsForTheSettingsItMapsTo() {
        val included = SubshellSettings.includedStorageKeys(
            mapOf(SubshellSettings.LEGACY_COMMENT_ACTION_PLACEMENT to "LEFT"),
        )
        assertTrue(SubshellSettings.INTERACTION_SIDE in included)
        assertTrue(SubshellSettings.COMMENT_ACTION_PREFERENCE in included)
    }

    private fun encode(
        settings: SubshellSettings,
        edit: (Map<String, kotlinx.serialization.json.JsonElement>) -> Map<String, kotlinx.serialization.json.JsonElement>,
    ): String {
        val encoded = SettingsSnapshotCodec.encode(
            SettingsSnapshot(
                id = "backup-7",
                name = "Backup",
                createdAtEpochMillis = 1L,
                appVersion = "0.78.0",
                kind = SnapshotKind.MANUAL,
                deviceId = "device",
                settings = settings,
            ),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject
        return JsonObject(root + ("settings" to JsonObject(edit(root.getValue("settings").jsonObject)))).toString()
    }

    private fun decode(json: String) = SettingsSnapshotCodec.decode(json)

    private fun settingsOf(json: String): JsonObject =
        Json.parseToJsonElement(json).jsonObject.getValue("settings").jsonObject
}
