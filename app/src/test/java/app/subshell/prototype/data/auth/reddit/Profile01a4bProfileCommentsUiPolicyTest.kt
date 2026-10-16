package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthCapabilityAuthorizationOwner
import app.subshell.prototype.data.auth.profileHistoryAuthorizationContextKey
import app.subshell.prototype.data.auth.profileHistoryUsernameFromAuthorizationContextKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a4bProfileCommentsUiPolicyTest {
    @Test
    fun profileHistoryAuthorizationOwnerHasStableWireIdentity() {
        assertEquals(
            AuthCapabilityAuthorizationOwner.PROFILE_HISTORY,
            AuthCapabilityAuthorizationOwner.fromWireKey("profile_history"),
        )
    }

    @Test
    fun profileHistoryAuthorizationContextRoundTripsNormalizedUsername() {
        val key = profileHistoryAuthorizationContextKey("Some-User")

        assertTrue(key.matches(Regex("[a-z0-9_]+")))
        assertTrue(key.length <= 64)
        assertEquals(
            "some-user",
            profileHistoryUsernameFromAuthorizationContextKey(key),
        )
    }

    @Test
    fun invalidProfileHistoryAuthorizationContextIsRejected() {
        assertNull(
            profileHistoryUsernameFromAuthorizationContextKey("user_xyz"),
        )
        assertNull(
            profileHistoryUsernameFromAuthorizationContextKey("other_616263"),
        )
    }
}
