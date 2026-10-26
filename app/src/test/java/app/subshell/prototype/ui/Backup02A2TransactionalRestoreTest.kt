package app.subshell.prototype.ui

import app.subshell.prototype.data.account.localFixtureAccountSummaries
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup02A2TransactionalRestoreTest {
    private val sessions = localFixtureAccountSummaries(
        listOf("root", "packetmoss"),
    )

    @Test
    fun successfulBackupRestoreUsesOnePersistenceBoundaryBeforeUpdatingLiveState() {
        val durableCommunities = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        val durableSettings = linkedMapOf<String, String>()
        var legacySettingsWrites = 0
        var legacyCommunityWrites = 0
        var transactionCalls = 0
        var seenCommunities: Map<String, Set<String>>? = null

        val state = SubshellState(
            initialAccountSessions = sessions,
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { username ->
                durableCommunities[username.trim().lowercase()].orEmpty()
            },
            persistJoinedCommunityIds = { _, _ -> legacyCommunityWrites += 1 },
            persistSettings = { legacySettingsWrites += 1 },
            persistBackupRestoreState = { values, communities, _, _, _, _, _, _, _, _, _ ->
                transactionCalls += 1
                durableSettings.clear()
                durableSettings.putAll(values)
                seenCommunities = communities
                communities?.forEach { (username, ids) ->
                    durableCommunities[username] = ids.toSet()
                }
            },
        )
        val targetSettings = state.captureSettings().copy(dynamicColor = true)

        state.restoreBackupState(
            settings = targetSettings,
            joinedCommunityIdsByAccount = mapOf(
                " ROOT " to setOf("Android", "does_not_exist"),
                "packetmoss" to emptySet(),
            ),
        )

        assertEquals(1, transactionCalls)
        assertEquals(0, legacySettingsWrites)
        assertEquals(0, legacyCommunityWrites)
        assertEquals(SubshellSettings.storageKeys, durableSettings.keys)
        assertEquals(
            mapOf(
                "packetmoss" to emptySet(),
                "root" to setOf("android"),
            ),
            seenCommunities,
        )
        assertEquals(targetSettings, state.captureSettings())
        assertEquals(listOf("android"), state.joinedCommunityIds)

        state.switchAccount("packetmoss")
        assertTrue(state.joinedCommunityIds.isEmpty())
    }

    @Test
    fun failedTransactionalCommitLeavesSettingsAndCommunitiesLiveStateUntouched() {
        val durableCommunities = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        var legacySettingsWrites = 0
        var legacyCommunityWrites = 0

        val state = SubshellState(
            initialAccountSessions = sessions,
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { username ->
                durableCommunities[username.trim().lowercase()].orEmpty()
            },
            persistJoinedCommunityIds = { _, _ -> legacyCommunityWrites += 1 },
            persistSettings = { legacySettingsWrites += 1 },
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _, _ ->
                throw IllegalStateException("simulated atomic commit failure")
            },
        )
        val settingsBefore = state.captureSettings()
        val communitiesBefore = state.joinedCommunityIds.toList()

        val result = runCatching {
            state.restoreBackupState(
                settings = settingsBefore.copy(dynamicColor = !settingsBefore.dynamicColor),
                joinedCommunityIdsByAccount = mapOf(
                    "root" to setOf("android"),
                    "packetmoss" to emptySet(),
                ),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, legacySettingsWrites)
        assertEquals(0, legacyCommunityWrites)
        assertEquals(settingsBefore, state.captureSettings())
        assertEquals(communitiesBefore, state.joinedCommunityIds)
        assertEquals(setOf("subshell"), durableCommunities.getValue("root"))
        assertEquals(setOf("linux"), durableCommunities.getValue("packetmoss"))
    }

    @Test
    fun legacyNullPayloadAtomicallyRestoresSettingsWithoutTouchingCommunities() {
        val durableCommunities = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        var seenCommunities: Map<String, Set<String>>? = emptyMap()
        val durableSettings = linkedMapOf<String, String>()

        val state = SubshellState(
            initialAccountSessions = sessions,
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { username ->
                durableCommunities[username.trim().lowercase()].orEmpty()
            },
            persistBackupRestoreState = { values, communities, _, _, _, _, _, _, _, _, _ ->
                durableSettings.putAll(values)
                seenCommunities = communities
            },
        )
        val targetSettings = state.captureSettings().copy(dynamicColor = true)

        state.restoreBackupState(
            settings = targetSettings,
            joinedCommunityIdsByAccount = null,
        )

        assertNull(seenCommunities)
        assertEquals(targetSettings, state.captureSettings())
        assertEquals(listOf("subshell"), state.joinedCommunityIds)
        assertEquals(setOf("subshell"), durableCommunities.getValue("root"))
        assertEquals(setOf("linux"), durableCommunities.getValue("packetmoss"))
        assertEquals(SubshellSettings.storageKeys, durableSettings.keys)
    }

    @Test
    fun productionWiringUsesSingleEditorCommitAndUiUsesTransactionalBoundary() {
        // The persistence wiring lives in RememberSubshellState.kt.
        val stateSource = source("ui/SubshellState.kt") + source("ui/RememberSubshellState.kt")
        val screenSource = source("ui/screens/BackupRestoreScreen.kt")

        assertTrue(
            stateSource.contains(
                "// BACKUP02A2_TRANSACTIONAL_RESTORE_V1",
            ),
        )
        assertTrue(
            stateSource.contains(
                "persistBackupRestoreState = ::persistBackupRestoreState",
            ),
        )
        assertTrue(
            stateSource.contains(
                "val editor = preferences.edit()",
            ),
        )
        assertTrue(
            stateSource.contains(
                "Could not atomically persist restored backup state",
            ),
        )
        // COMMIT01 (#243): the screen prepares on Main, commits on IO and
        // applies after the commit.
        assertTrue(screenSource.contains("val prepared = state.prepareBackupRestore("))
        assertTrue(
            screenSource.contains(
                "withContext(Dispatchers.IO) { state.persistBackupRestore(prepared) }\n" +
                    "                                        state.applyBackupRestore(prepared)",
            ),
        )
        assertFalse(
            screenSource.contains(
                "state.restoreSettings(target.settings)\n" +
                    "                                state.restoreJoinedCommunityIdsByAccount(",
            ),
        )
        // Indentation-free: the restore moved into withContext(NonCancellable)
        // when coroutines 1.11 deprecated launch(NonCancellable).
        assertTrue(
            Regex("""runCatching \{\s*// [^\n]*\n\s*val prepared = state\.prepareBackupRestore\(""")
                .containsMatchIn(screenSource),
        )
    }

    private fun source(relative: String): String =
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
