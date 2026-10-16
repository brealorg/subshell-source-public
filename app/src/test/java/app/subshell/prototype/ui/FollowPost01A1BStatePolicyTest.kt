package app.subshell.prototype.ui

import app.subshell.prototype.data.account.PostFollowUiState
import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowPost01A1BStatePolicyTest {
    @Test
    fun unknownServerStateStaysUnknownButOffersAbsoluteFollow() {
        assertNull(PostFollowUiState().followed)
        assertTrue(postFollowDesiredState(null))
        assertEquals(
            "Follow post",
            postFollowActionLabel(null),
        )
    }

    @Test
    fun acknowledgedFollowedStateOffersAbsoluteUnfollow() {
        assertFalse(postFollowDesiredState(true))
        assertEquals(
            "Unfollow post",
            postFollowActionLabel(true),
        )
        assertEquals(
            "Follow post",
            postFollowActionLabel(false),
        )
    }

    @Test
    fun controlSerializesDifferentTargetsButAllowsSameTargetLastIntent() {
        assertTrue(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = null,
                targetFullname = "t3_alpha",
            ),
        )
        assertTrue(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_alpha",
                targetFullname = "t3_alpha",
            ),
        )
        assertFalse(
            postFollowTargetControlEnabled(
                activeAccountIsAuthenticated = true,
                inFlightFullname = "t3_other",
                targetFullname = "t3_alpha",
            ),
        )
    }

    @Test
    fun successDispatchesOnlyQueuedStateThatStillDiffers() {
        assertEquals(
            false,
            queuedPostFollowFollowUp(
                acknowledgedFollowed = true,
                queuedDesiredFollowed = false,
            ),
        )
        assertNull(
            queuedPostFollowFollowUp(
                acknowledgedFollowed = true,
                queuedDesiredFollowed = true,
            ),
        )
    }

    @Test
    fun postFollowHasDedicatedPersistedAuthorizationOwner() {
        val owner =
            AuthCapabilityAuthorizationOwner.POST_FOLLOW

        assertEquals(
            "post_follow",
            owner.wireKey,
        )
        assertEquals(
            owner,
            AuthCapabilityAuthorizationOwner.fromWireKey(
                owner.wireKey,
            ),
        )
    }
}
