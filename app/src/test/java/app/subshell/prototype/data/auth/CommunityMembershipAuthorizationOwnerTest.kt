package app.subshell.prototype.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CommunityMembershipAuthorizationOwnerTest {
    @Test
    fun communityMembershipHasDedicatedPersistedOwner() {
        assertEquals(
            AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP,
            AuthCapabilityAuthorizationOwner.fromWireKey(
                "community_membership",
            ),
        )
        assertNotEquals(
            AuthCapabilityAuthorizationOwner.CUSTOM_FEED_WRITE,
            AuthCapabilityAuthorizationOwner.COMMUNITY_MEMBERSHIP,
        )
    }
}
