package app.subshell.prototype.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCapabilityAuthorizationOwnerPolicyTest {
    @Test
    fun ownerWireKeysAreUniqueAndRoundTrip() {
        val owners = AuthCapabilityAuthorizationOwner.entries

        assertEquals(
            owners.size,
            owners.map(AuthCapabilityAuthorizationOwner::wireKey).distinct().size,
        )

        owners.forEach { owner ->
            assertEquals(
                owner,
                AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey),
            )
        }
    }

    @Test
    fun unknownOwnerFailsClosed() {
        assertNull(AuthCapabilityAuthorizationOwner.fromWireKey(null))
        assertNull(AuthCapabilityAuthorizationOwner.fromWireKey("unknown_owner"))
    }

    @Test
    fun legacyPendingAndCallbackRemainOwnerlessByDefault() {
        val pending = PendingOAuthAuthorization(
            provider = OAuthProvider.REDDIT,
            state = "abcdefghijklmnopqrstuvwxyz012345",
            redirectUri = "https://example.com/callback",
            createdAtEpochMs = 1L,
        )
        val callback = StoredOAuthCallback(
            provider = OAuthProvider.REDDIT,
            state = "abcdefghijklmnopqrstuvwxyz012345",
            code = "code",
            error = null,
            receivedAtEpochMs = 2L,
        )

        assertNull(pending.authorizationOwnerKey)
        assertNull(callback.authorizationOwnerKey)
        assertTrue(
            AuthCapabilityAuthorizationOwner.entries.all {
                it.wireKey.length <= 64
            },
        )
    }
}
