package app.subshell.prototype.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class Reddit01c3c1AuthorizationContextPolicyTest {
    @Test
    fun authorizationContextKeysNormalizeDeterministically() {
        assertNull(normalizeAuthCapabilityAuthorizationContextKey(null))
        assertEquals(
            "moderation",
            normalizeAuthCapabilityAuthorizationContextKey(" MODERATION "),
        )
        assertEquals(
            "post_manage_flair",
            normalizeAuthCapabilityAuthorizationContextKey("post_manage_flair"),
        )
    }

    @Test
    fun malformedAuthorizationContextFailsClosed() {
        try {
            normalizeAuthCapabilityAuthorizationContextKey("bad-context!")
            fail("Malformed authorization context should be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    // STEP3 (reddit01c3c1): a blank key and one over 64 characters are
    // refused; 64 characters fit.
    @Test
    fun blankAndOverlongAuthorizationContextsFailClosed() {
        for (bad in listOf("  ", "a".repeat(65))) {
            try {
                normalizeAuthCapabilityAuthorizationContextKey(bad)
                fail("\"$bad\" should be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals("a".repeat(64), normalizeAuthCapabilityAuthorizationContextKey("a".repeat(64)))
    }

    @Test
    fun legacyPendingAndCallbackRemainContextlessByDefault() {
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

        assertNull(pending.authorizationContextKey)
        assertNull(callback.authorizationContextKey)
    }

    @Test
    fun authorizationRouteIncludesOwnerAndOptionalContext() {
        assertEquals(
            AuthCapabilityAuthorizationRoute(
                owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                contextKey = "flair",
            ),
            AuthCapabilityAuthorizationRoute(
                owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                contextKey = "flair",
            ),
        )
    }
}
