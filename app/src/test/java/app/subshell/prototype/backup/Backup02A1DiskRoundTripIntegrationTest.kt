package app.subshell.prototype.backup

import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.SubshellState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup02A1DiskRoundTripIntegrationTest {
    @Test
    fun exportedBackupRestoresJoinedCommunitiesAfterSimulatedDataLossWithoutCrossAccountLeakage() {
        val sourceDirectory = Files.createTempDirectory("backup02a1-source").toFile()
        val targetDirectory = Files.createTempDirectory("backup02a1-target").toFile()
        val persisted = mutableMapOf<String, Set<String>>(
            "root" to setOf("android", "subshell"),
            "packetmoss" to emptySet(),
        )

        fun state(): SubshellState = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(
                listOf("root", "packetmoss"),
            ),
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { username ->
                persisted[username.trim().lowercase()] ?: setOf("subshell")
            },
            persistJoinedCommunityIds = { username, ids ->
                persisted[username.trim().lowercase()] = ids.toSet()
            },
        )

        try {
            val sourceState = state()
            val captured = sourceState.captureJoinedCommunityIdsByAccount()
            assertEquals(
                mapOf(
                    "packetmoss" to emptySet(),
                    "root" to setOf("android", "subshell"),
                ),
                captured,
            )

            val sourceStore = SettingsBackupStore(
                directory = sourceDirectory,
                appVersion = "source-version",
                deviceId = "source-device",
                now = { 1_800_000_000_000L },
            )
            val saved = sourceStore.saveCurrent(
                name = "Issue 15 roundtrip",
                settings = sourceState.captureSettings(),
                joinedCommunityIdsByAccount = captured,
            )
            val exported = sourceStore.exportSnapshot(saved.id)

            // Simulate app-data loss: account-scoped persisted state is gone.
            // The load callback now falls back to the same fixture/default set
            // that production uses when joined_community_ids.<username> is absent.
            persisted.clear()
            val afterLoss = state()
            assertEquals(setOf("subshell"), afterLoss.joinedCommunityIds.toSet())
            afterLoss.switchAccount("packetmoss")
            assertEquals(setOf("subshell"), afterLoss.joinedCommunityIds.toSet())
            afterLoss.switchAccount("root")

            // Simulate import on another device/store.
            val targetStore = SettingsBackupStore(
                directory = targetDirectory,
                appVersion = "target-version",
                deviceId = "target-device",
                now = { 1_800_000_000_100L },
            )
            val imported = targetStore.importSnapshot(exported)
            val beforeRestoreCommunities =
                afterLoss.captureJoinedCommunityIdsByAccount()
            val target = targetStore.prepareRestore(
                id = imported.id,
                currentSettings = afterLoss.captureSettings(),
                currentJoinedCommunityIdsByAccount = beforeRestoreCommunities,
            )

            // The safety snapshot must contain the pre-restore membership state.
            val safety = targetStore.listSnapshots()
                .first { snapshot -> snapshot.kind == SnapshotKind.SAFETY }
            assertEquals(
                beforeRestoreCommunities,
                safety.joinedCommunityIdsByAccount,
            )

            afterLoss.restoreSettings(target.settings)
            afterLoss.restoreJoinedCommunityIdsByAccount(
                target.joinedCommunityIdsByAccount,
            )

            assertEquals(
                setOf("android", "subshell"),
                afterLoss.joinedCommunityIds.toSet(),
            )
            assertEquals(
                setOf("android", "subshell"),
                persisted.getValue("root"),
            )
            assertEquals(
                emptySet<String>(),
                persisted.getValue("packetmoss"),
            )

            afterLoss.switchAccount("packetmoss")
            assertTrue(afterLoss.joinedCommunityIds.isEmpty())
            afterLoss.switchAccount("root")
            assertEquals(
                setOf("android", "subshell"),
                afterLoss.joinedCommunityIds.toSet(),
            )

            // Unknown/deleted communities are non-fatal and are filtered by
            // the existing local repository normalizer.
            afterLoss.restoreJoinedCommunityIdsByAccount(
                mapOf(
                    "root" to setOf("android", "does_not_exist"),
                    "packetmoss" to emptySet(),
                ),
            )
            assertEquals(setOf("android"), afterLoss.joinedCommunityIds.toSet())
            assertEquals(setOf("android"), persisted.getValue("root"))
            assertTrue(persisted.getValue("packetmoss").isEmpty())

            // Legacy schema 2 has no joined-community payload. Null must be a
            // no-op rather than becoming authoritative empty/default state.
            persisted["root"] = setOf("linux")
            afterLoss.switchAccount("packetmoss")
            afterLoss.switchAccount("root")
            assertEquals(setOf("linux"), afterLoss.joinedCommunityIds.toSet())

            val legacyJson = SettingsSnapshotCodec.encode(
                target.copy(joinedCommunityIdsByAccount = null),
            ).replace(
                "\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION",
                "\"schemaVersion\": 2",
            )
            val legacy = SettingsSnapshotCodec.decode(legacyJson)
            assertNull(legacy.joinedCommunityIdsByAccount)

            afterLoss.restoreJoinedCommunityIdsByAccount(
                legacy.joinedCommunityIdsByAccount,
            )
            assertEquals(setOf("linux"), afterLoss.joinedCommunityIds.toSet())
            assertEquals(setOf("linux"), persisted.getValue("root"))
        } finally {
            sourceDirectory.deleteRecursively()
            targetDirectory.deleteRecursively()
        }
    }
}
