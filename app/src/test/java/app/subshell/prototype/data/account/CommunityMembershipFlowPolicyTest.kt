package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMembershipFlowPolicyTest {
    @Test
    fun requestNormalizationPinsAccountAndCanonicalCommunityName() {
        val request = CommunityMembershipRequest(
            accountStableId = "  reddit:account  ",
            communityName = "r/Norge",
            action = CommunityMembershipAction.JOIN,
        ).normalizedCommunityMembershipRequest()

        assertEquals("reddit:account", request.accountStableId)
        assertEquals("norge", request.communityName)
        assertEquals(CommunityMembershipAction.JOIN, request.action)
    }

    @Test
    fun uiStateMatchesOnlySameAccountAndCommunity() {
        val state = CommunityMembershipUiState(
            accountStableId = "reddit:account",
            communityName = "Norge",
            action = CommunityMembershipAction.LEAVE,
        )

        assertTrue(state.matches("reddit:account", "r/norge"))
        assertFalse(state.matches("reddit:other", "r/norge"))
        assertFalse(state.matches("reddit:account", "r/android"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankAccountIsRejected() {
        CommunityMembershipRequest(
            accountStableId = " ",
            communityName = "norge",
            action = CommunityMembershipAction.JOIN,
        ).normalizedCommunityMembershipRequest()
    }
}
