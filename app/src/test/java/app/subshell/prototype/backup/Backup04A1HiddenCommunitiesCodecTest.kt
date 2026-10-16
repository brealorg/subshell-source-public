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

class Backup04A1HiddenCommunitiesCodecTest {
    @Test
    fun schemaFiveRoundTripPreservesOpaqueStableIdOwnership() {
        val original = snapshot(
            hidden = linkedMapOf(
                " Auth-ABC " to setOf(" Android ", "subshell"),
                "local-demo" to emptySet(),
            ),
        )

        val encoded = SettingsSnapshotCodec.encode(original)
        val restored = SettingsSnapshotCodec.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"hiddenCommunities\""))
        assertEquals(
            mapOf(
                "Auth-ABC" to setOf("Android", "subshell"),
                "local-demo" to emptySet(),
            ),
            restored.hiddenCommunityNamesByAccount,
        )
        assertFalse(restored.hiddenCommunityNamesByAccount.orEmpty().containsKey("auth-abc"))
    }

    @Test
    fun optedOutNullModuleIsAbsentWhileExplicitEmptyRemainsAuthoritative() {
        val absentJson = SettingsSnapshotCodec.encode(snapshot(hidden = null))
        val explicit = SettingsSnapshotCodec.decode(
            SettingsSnapshotCodec.encode(snapshot(hidden = emptyMap())),
        )

        assertFalse(absentJson.contains("\"hiddenCommunities\""))
        assertNull(SettingsSnapshotCodec.decode(absentJson).hiddenCommunityNamesByAccount)
        assertNotNull(explicit.hiddenCommunityNamesByAccount)
        assertTrue(explicit.hiddenCommunityNamesByAccount.orEmpty().isEmpty())
    }

    @Test
    fun schemaFourIgnoresHiddenPayloadAsLegacy() {
        val schemaFive = SettingsSnapshotCodec.encode(
            snapshot(hidden = mapOf("Auth-ABC" to setOf("android"))),
        )
        val schemaFour = schemaFive.replace(
            "\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION",
            "\"schemaVersion\": 4",
        )

        val restored = SettingsSnapshotCodec.decode(schemaFour)

        assertNull(restored.hiddenCommunityNamesByAccount)
        assertNotNull(restored.favoriteCommunityNamesByAccount)
    }

    @Test
    fun malformedRowsAndValuesAreBoundedWithoutRejectingSnapshot() {
        val validRoot = Json.parseToJsonElement(
            SettingsSnapshotCodec.encode(snapshot(hidden = emptyMap())),
        ).jsonObject
        val malformed = Json.parseToJsonElement(
            "{\" Auth-ABC \": [\" Android \", null, 7, \"subshell\"], \"broken\": \"x\", \"empty\": []}",
        )
        val root = JsonObject(validRoot + ("hiddenCommunities" to malformed))

        val restored = SettingsSnapshotCodec.decode(root.toString())

        assertEquals(
            mapOf(
                "Auth-ABC" to setOf("Android", "subshell"),
                "empty" to emptySet(),
            ),
            restored.hiddenCommunityNamesByAccount,
        )
    }

    @Test
    fun importExportPreservesOptedInHiddenModuleAndSourceIdentity() {
        val directory = Files.createTempDirectory("backup04a1").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "target-version",
                deviceId = "target-device",
                now = { 200L },
            )
            val source = snapshot(
                hidden = mapOf("Auth-ABC" to setOf("android", "subshell")),
            )

            val imported = store.importSnapshot(SettingsSnapshotCodec.encode(source))
            val exported = SettingsSnapshotCodec.decode(store.exportSnapshot(imported.id))

            assertEquals(source.hiddenCommunityNamesByAccount, imported.hiddenCommunityNamesByAccount)
            assertEquals(imported.hiddenCommunityNamesByAccount, exported.hiddenCommunityNamesByAccount)
            assertEquals("source-device", imported.deviceId)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun opaqueStableIdNormalizerIsDeterministicAndBounded() {
        val oversized = linkedMapOf<String, Set<String>>()
        repeat(BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS + 5) { row ->
            oversized["Account-$row"] = buildSet {
                repeat(BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT + 5) { value ->
                    add("community-$value")
                }
            }
        }

        val normalized = normalizePortableOpaqueStableIdValuesByAccount(oversized)

        assertEquals(BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS, normalized.size)
        assertTrue(
            normalized.values.all {
                it.size <= BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT
            },
        )
        assertEquals(normalized, normalizePortableOpaqueStableIdValuesByAccount(oversized))
    }

    private fun snapshot(
        hidden: Map<String, Set<String>>?,
    ): SettingsSnapshot = SettingsSnapshot(
        id = "00000000-0000-0000-0000-0000000004a1",
        name = "BACKUP04A1",
        createdAtEpochMillis = 100L,
        appVersion = "source-version",
        kind = SnapshotKind.MANUAL,
        deviceId = "source-device",
        settings = SubshellSettings(),
        joinedCommunityIdsByAccount = mapOf("root" to setOf("android")),
        favoriteCommunityNamesByAccount = mapOf("auth-root" to setOf("android")),
        favoriteCustomFeedPathsByAccount = mapOf("auth-root" to setOf("/user/root/m/dev")),
        hiddenCommunityNamesByAccount = hidden,
    )
}
