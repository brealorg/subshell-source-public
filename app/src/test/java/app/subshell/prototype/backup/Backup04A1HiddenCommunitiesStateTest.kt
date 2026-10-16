package app.subshell.prototype.backup

import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A1HiddenCommunitiesStateTest {
    private val sessions = localFixtureAccountSummaries(listOf("root", "packetmoss"))
    private val root = account("root")
    private val packet = account("packetmoss")
    private val rootId = root.stableId
    private val packetId = packet.stableId

    @Test
    fun captureUsesNativeStableIdsForEveryKnownAccountAndExplicitEmptyRows() {
        val hidden = mutableMapOf(
            rootId to setOf("android"),
            packetId to emptySet(),
        )
        val state = state(hidden = hidden)

        state.toggleHiddenCommunity("subshell")

        assertEquals(
            mapOf(
                packetId to emptySet(),
                rootId to setOf("android", "subshell"),
            ),
            state.captureHiddenCommunityNamesByAccount(),
        )
    }

    @Test
    fun nullHiddenModuleIsLegacyOrOptedOutNoOp() {
        var seen: Map<String, Set<String>>? = emptyMap()
        val state = state(
            hidden = mutableMapOf(rootId to setOf("android")),
            persistBackupRestoreState = { _, _, _, _, hiddenCommunities, _, _, _, _, _ ->
                seen = hiddenCommunities
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = null,
        )

        assertEquals(setOf("android"), state.hiddenCommunityNames.toSet())
        assertNull(seen)
    }

    @Test
    fun explicitEmptyActiveRowClearsOnlyAfterTransaction() {
        var calls = 0
        val state = state(
            hidden = mutableMapOf(rootId to setOf("android")),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ -> calls += 1 },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = mapOf(rootId to emptySet()),
        )

        assertEquals(1, calls)
        assertTrue(state.hiddenCommunityNames.isEmpty())
    }

    @Test
    fun explicitEmptyModuleClearsActiveHiddenState() {
        var seen: Map<String, Set<String>>? = null
        val state = state(
            hidden = mutableMapOf(rootId to setOf("android")),
            persistBackupRestoreState = { _, _, _, _, hiddenCommunities, _, _, _, _, _ ->
                seen = hiddenCommunities
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = emptyMap(),
        )

        assertEquals(emptyMap<String, Set<String>>(), seen)
        assertTrue(state.hiddenCommunityNames.isEmpty())
    }

    @Test
    fun disconnectedOpaqueStableIdRowDoesNotProjectIntoActiveAccount() {
        val futureId = "Future-Stable-ID"
        var seen: Map<String, Set<String>>? = null
        val state = state(
            hidden = mutableMapOf(rootId to setOf("android")),
            persistBackupRestoreState = { _, _, _, _, hiddenCommunities, _, _, _, _, _ ->
                seen = hiddenCommunities
            },
        )

        state.restoreBackupState(
            settings = state.captureSettings(),
            joinedCommunityIdsByAccount = null,
            hiddenCommunityNamesByAccount = mapOf(futureId to setOf("subshell")),
        )

        assertEquals(mapOf(futureId to setOf("subshell")), seen)
        assertEquals(setOf("android"), state.hiddenCommunityNames.toSet())
    }

    @Test
    fun failedDurableTransactionPreventsHiddenAndSettingsProjection() {
        val state = state(
            hidden = mutableMapOf(rootId to setOf("android")),
            persistBackupRestoreState = { _, _, _, _, _, _, _, _, _, _ ->
                error("simulated durable commit failure")
            },
        )
        val beforeSettings = state.captureSettings()
        val targetSettings = beforeSettings.copy(dynamicColor = !beforeSettings.dynamicColor)

        val result = runCatching {
            state.restoreBackupState(
                settings = targetSettings,
                joinedCommunityIdsByAccount = null,
                hiddenCommunityNamesByAccount = emptyMap(),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(beforeSettings, state.captureSettings())
        assertEquals(setOf("android"), state.hiddenCommunityNames.toSet())
    }

    private fun state(
        hidden: MutableMap<String, Set<String>>,
        persistBackupRestoreState: (
            Map<String, String>,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
            Map<String, Set<String>>?,
                    Map<String, Set<String>>?,
                    List<app.subshell.prototype.ui.PostComposerDraft>?,
                    List<app.subshell.prototype.data.moderation.ModeratorSavedResponse>?,
                    Map<String, Map<String, app.subshell.prototype.ui.UserTag>>?,
                    Map<String, List<app.subshell.prototype.ui.SavedSearch>>?,
) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    ): SubshellState = SubshellState(
        initialAccountSessions = sessions,
        initialActiveAccountUsername = "root",
        loadHiddenCommunityNames = { id -> hidden[id].orEmpty() },
        persistHiddenCommunityNames = { id, names -> hidden[id] = names.toSet() },
        persistBackupRestoreState = persistBackupRestoreState,
    )

    private fun account(username: String): AccountSessionSummary =
        sessions.first { it.username.equals(username, ignoreCase = true) }
}
