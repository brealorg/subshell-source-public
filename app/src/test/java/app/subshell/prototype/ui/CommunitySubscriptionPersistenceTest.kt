package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitySubscriptionPersistenceTest {
    @Test
    fun togglePersistsCanonicalCommunityIdsForActiveAccount() {
        val stored = mutableMapOf(
            "root" to setOf("subshell"),
        )
        val writes = mutableListOf<Pair<String, Set<String>>>()
        val state = stateWithSubscriptions(stored) { username, ids ->
            stored[username] = ids
            writes += username to ids
        }

        state.toggleCommunity("SUBSHELL_VIDEO_LAB")

        assertTrue("subshell_video_lab" in state.joinedCommunityIds)
        assertEquals(
            "root" to setOf("subshell", "subshell_video_lab"),
            writes.single(),
        )
    }

    @Test
    fun accountSwitchLoadsIndependentSubscriptionSet() {
        val stored = mutableMapOf(
            "root" to setOf("subshell", "android"),
            "packetmoss" to setOf("linux"),
        )
        val state = stateWithSubscriptions(stored) { username, ids ->
            stored[username] = ids
        }

        state.switchAccount("packetmoss")

        assertEquals("packetmoss", state.activeAccountUsername)
        assertEquals(listOf("linux"), state.joinedCommunityIds)

        state.toggleCommunity("homelab")
        state.switchAccount("root")

        assertEquals(setOf("subshell", "android"), state.joinedCommunityIds.toSet())
        assertEquals(setOf("linux", "homelab"), stored.getValue("packetmoss"))
    }

    @Test
    fun emptyPersistedSubscriptionSetDoesNotRestoreFixtureDefaults() {
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { emptySet() },
        )

        assertTrue(state.joinedCommunityIds.isEmpty())
    }

    @Test
    fun unknownCommunityIsIgnoredWithoutPersistenceWrite() {
        var writes = 0
        val state = SubshellState(
            initialActiveAccountUsername = "root",
            loadJoinedCommunityIds = { setOf("subshell") },
            persistJoinedCommunityIds = { _, _ -> writes += 1 },
        )

        state.toggleCommunity("does_not_exist")

        assertEquals(listOf("subshell"), state.joinedCommunityIds)
        assertEquals(0, writes)
        assertFalse("does_not_exist" in state.joinedCommunityIds)
    }

    @Test
    fun preferenceKeysAreStableAndAccountScoped() {
        assertEquals("joined_community_ids.root", joinedCommunitiesPreferenceKey("ROOT"))
        assertEquals(
            "joined_community_ids.packetmoss",
            joinedCommunitiesPreferenceKey(" packetmoss "),
        )
    }

    private fun stateWithSubscriptions(
        stored: MutableMap<String, Set<String>>,
        persist: (String, Set<String>) -> Unit,
    ): SubshellState = SubshellState(
        initialActiveAccountUsername = "root",
        loadJoinedCommunityIds = { username -> stored[username].orEmpty() },
        persistJoinedCommunityIds = persist,
    )
}
