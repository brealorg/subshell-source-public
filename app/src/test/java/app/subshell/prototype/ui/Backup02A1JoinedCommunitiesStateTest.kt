package app.subshell.prototype.ui

import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.screens.joinedCommunitiesRestoreChangeCount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup02A1JoinedCommunitiesStateTest {
    private val sessions = localFixtureAccountSummaries(
        listOf("root", "packetmoss"),
    )

    @Test
    fun captureIncludesEveryAccountAndPreservesExplicitEmptyMembership() {
        val stored = mutableMapOf(
            "root" to setOf("subshell", "android"),
            "packetmoss" to emptySet(),
        )
        val state = state(stored)

        assertEquals(
            mapOf(
                "packetmoss" to emptySet(),
                "root" to setOf("android", "subshell"),
            ),
            state.captureJoinedCommunityIdsByAccount(),
        )
    }

    @Test
    fun restoreKeepsAccountsSeparateAndExplicitEmptyWinsOverFixtureDefaults() {
        val stored = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = state(stored) { username, ids ->
            stored[username] = ids
            writes += username to ids
        }

        state.restoreJoinedCommunityIdsByAccount(
            mapOf(
                "root" to setOf("Android", "does_not_exist"),
                "packetmoss" to emptySet(),
            ),
        )

        assertEquals(setOf("android"), stored.getValue("root"))
        assertEquals(emptySet<String>(), stored.getValue("packetmoss"))
        assertEquals(listOf("android"), state.joinedCommunityIds)
        assertEquals(
            setOf("packetmoss", "root"),
            writes.map { it.first }.toSet(),
        )

        state.switchAccount("packetmoss")
        assertTrue(state.joinedCommunityIds.isEmpty())
    }

    @Test
    fun legacyAbsentPayloadDoesNotWriteOrReplaceCurrentMemberships() {
        val stored = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        var writes = 0
        val state = state(stored) { _, _ -> writes += 1 }

        state.restoreJoinedCommunityIdsByAccount(null)

        assertEquals(0, writes)
        assertEquals(listOf("subshell"), state.joinedCommunityIds)
    }

    @Test
    fun disconnectedAccountRowStaysPinnedToItsOwnUsernameForLaterReconnect() {
        val stored = mutableMapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = state(stored) { username, ids ->
            stored[username] = ids
            writes += username to ids
        }

        state.restoreJoinedCommunityIdsByAccount(
            mapOf("future_user" to setOf("subshell")),
        )

        assertEquals(
            listOf("future_user" to setOf("subshell")),
            writes,
        )
        assertEquals(listOf("subshell"), state.joinedCommunityIds)
        assertEquals(setOf("subshell"), stored.getValue("future_user"))
    }

    @Test
    fun restorePreviewCountsOnlyAccountRowsThatWouldActuallyBeApplied() {
        val current = mapOf(
            "root" to setOf("subshell"),
            "packetmoss" to setOf("linux"),
        )

        assertEquals(0, joinedCommunitiesRestoreChangeCount(current, null))
        assertEquals(
            0,
            joinedCommunitiesRestoreChangeCount(
                current,
                mapOf("root" to setOf("subshell")),
            ),
        )
        assertEquals(
            2,
            joinedCommunitiesRestoreChangeCount(
                current,
                mapOf(
                    "root" to emptySet(),
                    "future_user" to emptySet(),
                ),
            ),
        )
    }

    @Test
    fun productWiringCarriesCommunityStateThroughAutomaticManualSafetyAndRestorePaths() {
        val app = source("ui/app/AutomaticBackupHistoryEffect.kt")
        val screen = source("ui/screens/BackupRestoreScreen.kt")

        assertTrue(
            app.contains(
                "state.capturePortableBackupState()",
            ),
        )
        assertTrue(
            app.contains(
                "joinedCommunityIdsByAccount = portableState.joinedCommunityIdsByAccount",
            ),
        )
        assertTrue(screen.contains("state.captureJoinedCommunityIdsByAccount()"))
        assertTrue(screen.contains("currentJoinedCommunityIdsByAccount ="))
        assertTrue(
            screen.contains(
                "repository.saveCurrent(\n" +
                    "                        name,\n" +
                    "                        currentSettings,\n" +
                    "                        currentJoinedCommunities,",
            ),
        )
        assertTrue(
            screen.contains(
                "pending.currentJoinedCommunityIdsByAccount,",
            ),
        )
        assertTrue(
            screen.contains("state.restoreBackupState(") ||
                screen.contains(
                    "state.restoreJoinedCommunityIdsByAccount(\n" +
                        "                                    target.joinedCommunityIdsByAccount,",
                ),
        )
        assertTrue(
            screen.contains(
                "Joined communities are not included in this older backup and will be kept unchanged.",
            ),
        )
    }

    private fun state(
        stored: MutableMap<String, Set<String>>,
        persist: (String, Set<String>) -> Unit = { username, ids ->
            stored[username] = ids
        },
    ): SubshellState = SubshellState(
        initialAccountSessions = sessions,
        initialActiveAccountUsername = "root",
        loadJoinedCommunityIds = { username -> stored[username].orEmpty() },
        persistJoinedCommunityIds = persist,
    )

    private fun source(relative: String): String =
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
