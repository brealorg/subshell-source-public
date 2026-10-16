package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup02A1JoinedCommunitiesCodecTest {
    @Test
    fun schemaThreeRoundTripPreservesAccountsExplicitEmptyAndUnknownCommunityIds() {
        val snapshot = snapshot(
            joinedCommunityIdsByAccount = linkedMapOf(
                " ROOT " to setOf("Android", "subshell", "does_not_exist"),
                "packetmoss" to emptySet(),
            ),
        )

        val encoded = SettingsSnapshotCodec.encode(snapshot)
        val restored = SettingsSnapshotCodec.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertEquals(
            mapOf(
                "packetmoss" to emptySet(),
                "root" to setOf("android", "does_not_exist", "subshell"),
            ),
            restored.joinedCommunityIdsByAccount,
        )
        assertTrue(encoded.indexOf("\"packetmoss\"") < encoded.indexOf("\"root\""))
    }

    @Test
    fun legacySchemaTwoWithoutCommunityPayloadRemainsAbsentInsteadOfBecomingEmpty() {
        val schemaThree = SettingsSnapshotCodec.encode(
            snapshot(joinedCommunityIdsByAccount = null),
        )
        val schemaTwo = schemaThree.replace(
            "\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION",
            "\"schemaVersion\": 2",
        )

        val restored = SettingsSnapshotCodec.decode(schemaTwo)

        assertNull(restored.joinedCommunityIdsByAccount)
    }

    @Test
    fun explicitEmptyPayloadStaysDistinctFromLegacyAbsence() {
        val restored = SettingsSnapshotCodec.decode(
            SettingsSnapshotCodec.encode(
                snapshot(joinedCommunityIdsByAccount = emptyMap()),
            ),
        )

        assertNotNull(restored.joinedCommunityIdsByAccount)
        assertTrue(restored.joinedCommunityIdsByAccount.orEmpty().isEmpty())
    }

    @Test
    fun malformedAccountRowsAreIgnoredWithoutMakingTheBackupUnrestorable() {
        val validRoot = Json.parseToJsonElement(
            SettingsSnapshotCodec.encode(
                snapshot(joinedCommunityIdsByAccount = emptyMap()),
            ),
        ).jsonObject
        val malformedJoinedCommunities = Json.parseToJsonElement(
            """
            {
              " ROOT ": ["Android", "does_not_exist", null, 7],
              "broken": "not-an-array",
              "packetmoss": []
            }
            """.trimIndent(),
        )
        val malformedRoot = JsonObject(
            validRoot + ("joinedCommunities" to malformedJoinedCommunities),
        )

        val restored = SettingsSnapshotCodec.decode(malformedRoot.toString())

        assertEquals(
            mapOf(
                "packetmoss" to emptySet(),
                "root" to setOf("android", "does_not_exist"),
            ),
            restored.joinedCommunityIdsByAccount,
        )
    }

    @Test
    fun importingSnapshotPreservesCommunityPayloadInsteadOfStrippingIt() {
        val directory = Files.createTempDirectory("backup02a1").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "target-version",
                deviceId = "target-device",
                now = { 200L },
            )
            val original = snapshot(
                joinedCommunityIdsByAccount = mapOf(
                    "root" to setOf("android", "subshell"),
                    "packetmoss" to emptySet(),
                ),
            )

            val imported = store.importSnapshot(SettingsSnapshotCodec.encode(original))
            val exportedAgain = SettingsSnapshotCodec.decode(store.exportSnapshot(imported.id))

            assertEquals(original.joinedCommunityIdsByAccount, imported.joinedCommunityIdsByAccount)
            assertEquals(imported.joinedCommunityIdsByAccount, exportedAgain.joinedCommunityIdsByAccount)
            assertEquals("source-device", imported.deviceId)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun joinedCommunityPayloadDoesNotCreateAnAuthenticationSecretSurface() {
        val encoded = SettingsSnapshotCodec.encode(
            snapshot(
                joinedCommunityIdsByAccount = mapOf(
                    "root" to setOf("android", "subshell"),
                ),
            ),
        )

        assertFalse(encoded.contains("accessToken", ignoreCase = true))
        assertFalse(encoded.contains("refreshToken", ignoreCase = true))
        assertFalse(encoded.contains("clientSecret", ignoreCase = true))
    }

    private fun snapshot(
        joinedCommunityIdsByAccount: Map<String, Set<String>>?,
    ): SettingsSnapshot = SettingsSnapshot(
        id = "00000000-0000-0000-0000-000000000015",
        name = "Issue 15",
        createdAtEpochMillis = 100L,
        appVersion = "source-version",
        kind = SnapshotKind.MANUAL,
        deviceId = "source-device",
        settings = SubshellSettings(),
        joinedCommunityIdsByAccount = joinedCommunityIdsByAccount,
    )
}
