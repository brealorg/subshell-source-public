package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import org.junit.Assert.assertEquals
import org.junit.Test

class Mod01A21RemovalReasonAuthorizationPolicyTest {
    @Test
    fun removalReasonsHaveDedicatedAuthorizationOwner() {
        val owner = AuthCapabilityAuthorizationOwner.MODERATION_REASON
        assertEquals("moderation_reason", owner.wireKey)
        assertEquals(owner, AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey))
    }
}
