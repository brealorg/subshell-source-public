package app.subshell.prototype.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mod01A1ModerationUiStatePolicyTest {
    @Test
    fun moderatorDirectoryIsAccountScopedAndCommunityNormalized() {
        val state = RedditModerationUiState(
            accountStableId = "account-a",
            moderatedCommunities = setOf("norge", "android"),
            directoryLoaded = true,
        )

        assertTrue(state.matchesAccount("account-a"))
        assertFalse(state.matchesAccount("account-b"))
        assertTrue(state.moderates("r/Norge"))
        assertTrue(state.moderates(" /r/android "))
        assertFalse(state.moderates("space"))
    }

    @Test
    fun modQueueIdentityIncludesAccountAndCommunity() {
        val state = RedditModerationUiState(
            accountStableId = "account-a",
            queueCommunityName = "norge",
            queueLoaded = true,
        )

        assertTrue(state.queueMatches("account-a", "r/Norge"))
        assertFalse(state.queueMatches("account-b", "norge"))
        assertFalse(state.queueMatches("account-a", "android"))
    }
}
