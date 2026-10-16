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

class Backup03C1FavoritesCodecTest {
    @Test
    fun schemaFourRoundTripPreservesStableIdOwnershipAndExplicitEmptyRows() {
        val original = snapshot(
            favoriteCommunityNamesByAccount = linkedMapOf(
                " Auth-ABC " to setOf(" Android ", "subshell"),
                "local-demo" to emptySet(),
            ),
            favoriteCustomFeedPathsByAccount = linkedMapOf(
                " AUTH-ABC " to setOf(" /user/root/m/dev ", "/user/root/m/news"),
                "local-demo" to emptySet(),
            ),
        )

        val encoded = SettingsSnapshotCodec.encode(original)
        val restored = SettingsSnapshotCodec.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"favoriteCommunities\""))
        assertTrue(encoded.contains("\"favoriteCustomFeeds\""))
        assertEquals(
            mapOf(
                "auth-abc" to setOf("Android", "subshell"),
                "local-demo" to emptySet(),
            ),
            restored.favoriteCommunityNamesByAccount,
        )
        assertEquals(
            mapOf(
                "auth-abc" to setOf("/user/root/m/dev", "/user/root/m/news"),
                "local-demo" to emptySet(),
            ),
            restored.favoriteCustomFeedPathsByAccount,
        )
        assertFalse(restored.favoriteCommunityNamesByAccount.orEmpty().containsKey("root"))
    }

    @Test
    fun schemaThreeRemainsLegacyForFavoritesWhileJoinedCommunitiesStillDecode() {
        val current = snapshot(
            joinedCommunityIdsByAccount = mapOf("root" to setOf("android")),
            favoriteCommunityNamesByAccount = null,
            favoriteCustomFeedPathsByAccount = null,
        )
        val schemaThree = SettingsSnapshotCodec.encode(current).replace(
            "\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION",
            "\"schemaVersion\": 3",
        )

        val restored = SettingsSnapshotCodec.decode(schemaThree)

        assertEquals(mapOf("root" to setOf("android")), restored.joinedCommunityIdsByAccount)
        assertNull(restored.favoriteCommunityNamesByAccount)
        assertNull(restored.favoriteCustomFeedPathsByAccount)
    }

    @Test
    fun explicitEmptyFavoriteModulesStayDistinctFromAbsentModules() {
        val explicit = SettingsSnapshotCodec.decode(
            SettingsSnapshotCodec.encode(
                snapshot(
                    favoriteCommunityNamesByAccount = emptyMap(),
                    favoriteCustomFeedPathsByAccount = emptyMap(),
                ),
            ),
        )

        assertNotNull(explicit.favoriteCommunityNamesByAccount)
        assertNotNull(explicit.favoriteCustomFeedPathsByAccount)
        assertTrue(explicit.favoriteCommunityNamesByAccount.orEmpty().isEmpty())
        assertTrue(explicit.favoriteCustomFeedPathsByAccount.orEmpty().isEmpty())

        val absent = SettingsSnapshotCodec.decode(
            SettingsSnapshotCodec.encode(
                snapshot(
                    favoriteCommunityNamesByAccount = null,
                    favoriteCustomFeedPathsByAccount = null,
                ),
            ),
        )
        assertNull(absent.favoriteCommunityNamesByAccount)
        assertNull(absent.favoriteCustomFeedPathsByAccount)
    }

    @Test
    fun malformedRowsAndArrayItemsAreIgnoredWithoutRejectingSnapshot() {
        val validRoot = Json.parseToJsonElement(
            SettingsSnapshotCodec.encode(
                snapshot(
                    favoriteCommunityNamesByAccount = emptyMap(),
                    favoriteCustomFeedPathsByAccount = emptyMap(),
                ),
            ),
        ).jsonObject

        val malformedFavorites = Json.parseToJsonElement(
            """
            {
              " Auth-ABC ": [" Android ", null, 7, "subshell"],
              "broken": "not-an-array",
              "empty": []
            }
            """.trimIndent(),
        )
        val malformedFeeds = Json.parseToJsonElement(
            """
            {
              " Auth-ABC ": ["/user/root/m/dev", false, null],
              "broken": 9,
              "empty": []
            }
            """.trimIndent(),
        )
        val malformedRoot = JsonObject(
            validRoot +
                ("favoriteCommunities" to malformedFavorites) +
                ("favoriteCustomFeeds" to malformedFeeds),
        )

        val restored = SettingsSnapshotCodec.decode(malformedRoot.toString())

        assertEquals(
            mapOf(
                "auth-abc" to setOf("Android", "subshell"),
                "empty" to emptySet(),
            ),
            restored.favoriteCommunityNamesByAccount,
        )
        assertEquals(
            mapOf(
                "auth-abc" to setOf("/user/root/m/dev"),
                "empty" to emptySet(),
            ),
            restored.favoriteCustomFeedPathsByAccount,
        )
    }

    @Test
    fun importExportPreservesV4FavoritesAndSourceDeviceIdentity() {
        val directory = Files.createTempDirectory("backup03c1").toFile()
        try {
            val store = SettingsBackupStore(
                directory = directory,
                appVersion = "target-version",
                deviceId = "target-device",
                now = { 200L },
            )
            val source = snapshot(
                favoriteCommunityNamesByAccount =
                    mapOf("auth-abc" to setOf("android", "subshell")),
                favoriteCustomFeedPathsByAccount =
                    mapOf("auth-abc" to setOf("/user/root/m/dev")),
            )

            val imported = store.importSnapshot(SettingsSnapshotCodec.encode(source))
            val exportedAgain = SettingsSnapshotCodec.decode(store.exportSnapshot(imported.id))

            assertEquals(source.favoriteCommunityNamesByAccount, imported.favoriteCommunityNamesByAccount)
            assertEquals(source.favoriteCustomFeedPathsByAccount, imported.favoriteCustomFeedPathsByAccount)
            assertEquals(imported.favoriteCommunityNamesByAccount, exportedAgain.favoriteCommunityNamesByAccount)
            assertEquals(imported.favoriteCustomFeedPathsByAccount, exportedAgain.favoriteCustomFeedPathsByAccount)
            assertEquals("source-device", imported.deviceId)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun portableStableIdNormalizerBoundsRowsAndValuesDeterministically() {
        val oversized = linkedMapOf<String, Set<String>>()
        repeat(BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS + 5) { row ->
            oversized["account-$row"] = buildSet {
                repeat(BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT + 5) { value ->
                    add("value-$value")
                }
            }
        }

        val normalized = normalizePortableStableIdValuesByAccount(oversized)

        assertEquals(BACKUP03C_MAX_PORTABLE_ACCOUNT_ROWS, normalized.size)
        assertTrue(
            normalized.values.all {
                it.size <= BACKUP03C_MAX_PORTABLE_VALUES_PER_ACCOUNT
            },
        )
        assertEquals(normalized, normalizePortableStableIdValuesByAccount(oversized))
    }

    private fun snapshot(
        joinedCommunityIdsByAccount: Map<String, Set<String>>? =
            mapOf("root" to setOf("android")),
        favoriteCommunityNamesByAccount: Map<String, Set<String>>? = null,
        favoriteCustomFeedPathsByAccount: Map<String, Set<String>>? = null,
    ): SettingsSnapshot = SettingsSnapshot(
        id = "00000000-0000-0000-0000-00000000003c",
        name = "BACKUP03C1",
        createdAtEpochMillis = 100L,
        appVersion = "source-version",
        kind = SnapshotKind.MANUAL,
        deviceId = "source-device",
        settings = SubshellSettings(),
        joinedCommunityIdsByAccount = joinedCommunityIdsByAccount,
        favoriteCommunityNamesByAccount = favoriteCommunityNamesByAccount,
        favoriteCustomFeedPathsByAccount = favoriteCustomFeedPathsByAccount,
    )
}
