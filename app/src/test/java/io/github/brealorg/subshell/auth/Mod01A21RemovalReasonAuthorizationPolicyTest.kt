package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.auth.AuthCapabilityAuthorizationOwner
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
