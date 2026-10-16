package app.subshell.prototype.ui

import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterisation of the persisted surface tab selections inside
 * SubshellState (SurfaceTabState). The process round trip stays covered by
 * SubshellProcessStatePolicyTest. The library, You and profile-activity tabs
 * were retired by LOWUI4 (no screen read them).
 */
class SurfaceTabStateCharacterisationTest {
    @Test
    fun profileTabsAreKeyedByNormalisedUsernameAndClampedAtZero() {
        val state = state()
        assertEquals(0, state.userProfileTab("Someone"))

        state.selectUserProfileTab("  Some One ", 2)
        assertEquals(2, state.userProfileTab("some one"))
        assertEquals(2, state.userProfileTab("SOME   ONE"))
        assertEquals(0, state.userProfileTab("someone"))

        state.selectUserProfileTab("some one", -4)
        assertEquals(0, state.userProfileTab("some one"))
        state.selectUserProfileTab("some one", 99)
        assertEquals(99, state.userProfileTab("some one"))
    }

    @Test
    fun snapshotCarriesTheSelectionsAndARestoredStateStartsFromThem() {
        val state = state()
        state.selectUserProfileTab("alice", 1)

        val snapshot = state.processStateSnapshot()
        assertEquals(mapOf("alice" to 1), snapshot.userProfileTabs)

        // The codec clamps profile tabs to 0..2 on encode, so the live state
        // may hold values the snapshot cannot carry.
        val restored = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialProcessSnapshotToken = encodeSubshellProcessSnapshot(snapshot),
        )
        assertEquals(1, restored.userProfileTab("Alice"))
        assertEquals(0, restored.userProfileTab("bob"))
    }

    private fun state(): SubshellState = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
    )
}
