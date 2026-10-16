package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityMembershipUiPolicyTest {
    @Test
    fun joinedMapsToLeaveAndUnjoinedMapsToJoin() {
        assertEquals(
            CommunityMembershipAction.LEAVE,
            communityMembershipActionForJoined(true),
        )
        assertEquals(
            CommunityMembershipAction.JOIN,
            communityMembershipActionForJoined(false),
        )
    }

    @Test
    fun successfulJoinProjectsJoinedUntilRefresh() {
        val state = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.SUCCEEDED,
            accountStableId = "reddit:account",
            communityName = "norge",
            action = CommunityMembershipAction.JOIN,
        )

        assertTrue(
            state.projectedJoined(
                accountStableId = "reddit:account",
                communityName = "r/norge",
                authoritativeJoined = false,
            ),
        )
        assertTrue(
            state.blocksActionFor(
                "reddit:account",
                "norge",
            ),
        )
    }

    @Test
    fun successfulLeaveProjectsUnjoinedUntilRefresh() {
        val state = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.SUCCEEDED,
            accountStableId = "reddit:account",
            communityName = "norge",
            action = CommunityMembershipAction.LEAVE,
        )

        assertFalse(
            state.projectedJoined(
                accountStableId = "reddit:account",
                communityName = "norge",
                authoritativeJoined = true,
            ),
        )
    }

    @Test
    fun projectionAndBlockingNeverLeakToAnotherTarget() {
        val state = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.SUCCEEDED,
            accountStableId = "reddit:account",
            communityName = "norge",
            action = CommunityMembershipAction.JOIN,
        )

        assertFalse(
            state.projectedJoined(
                accountStableId = "reddit:other",
                communityName = "norge",
                authoritativeJoined = false,
            ),
        )
        assertFalse(
            state.blocksActionFor(
                "reddit:account",
                "android",
            ),
        )
    }

    @Test
    fun outcomeUnknownBlocksSameTargetUntilReconciled() {
        val state = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.FAILED,
            accountStableId = "reddit:account",
            communityName = "norge",
            action = CommunityMembershipAction.JOIN,
            failureKind =
                CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
        )

        assertTrue(
            state.blocksActionFor(
                "reddit:account",
                "norge",
            ),
        )
        assertFalse(
            state.blocksActionFor(
                "reddit:account",
                "android",
            ),
        )
    }

    @Test
    fun failureMessageIsTargetScopedAndTyped() {
        val state = CommunityMembershipUiState(
            phase = CommunityMembershipPhase.FAILED,
            accountStableId = "reddit:account",
            communityName = "norge",
            action = CommunityMembershipAction.JOIN,
            failureKind = CommunityMembershipFailureKind.HTTP,
            failureStatusCode = 429,
        )

        assertEquals(
            "Reddit is rate limiting this action. Try again later.",
            state.failureMessageFor(
                "reddit:account",
                "norge",
            ),
        )
        assertNull(
            state.failureMessageFor(
                "reddit:account",
                "android",
            ),
        )
    }
}
