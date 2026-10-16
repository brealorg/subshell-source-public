package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AUTH14 (#132): the custom feed writer used the stored session handed to it
 * without checking that its access token was still usable, so an app left open
 * past the token's lifetime sent an expired token to Reddit and drew a 401.
 * An expired session is now not used as is: the writer refreshes it through
 * ensureAccessTokenForAccount instead.
 */
class Auth14CustomFeedSessionExpiryTest {
    private val now = 10_000_000L

    private fun session(expiresAt: Long) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresAtEpochMs = expiresAt,
            scope = "identity mysubreddits read subscribe",
        ),
        identity = AuthAccountIdentity(providerAccountId = "account-id", username = "tester"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        clientIdFingerprint = "fingerprint",
    )

    @Test
    fun anExpiredOrExpiringSessionIsNotUsedAsIs() {
        for (expiresAt in listOf(1L, now, now + OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS)) {
            assertNull(
                "$expiresAt",
                authorizedCustomFeedSession(session(expiresAt), RedditOAuth.liveCustomFeedWriteScopes, now),
            )
        }
    }

    @Test
    fun aUsableSessionIsStillUsedDirectly() {
        val usable = session(now + OAuthSessionManager.DEFAULT_REFRESH_SKEW_MS + 1)
        assertEquals(usable, authorizedCustomFeedSession(usable, RedditOAuth.liveCustomFeedWriteScopes, now))
    }
}
