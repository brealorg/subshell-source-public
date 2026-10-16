package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFeedWritePolicyTest {
    @Test
    fun customFeedScopeIsAnExplicitSupersetWithoutWideningReadOnly() {
        assertTrue(RedditOAuth.liveCustomFeedWriteScopes.containsAll(RedditOAuth.coreReadScopes))
        assertTrue("subscribe" in RedditOAuth.liveCustomFeedWriteScopes)
        assertFalse("subscribe" in RedditOAuth.coreReadScopes)
    }

    @Test
    fun scopeSupersetSatisfiesReadOnlyAndCustomFeedRequirements() {
        val token = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            expiresAtEpochMs = Long.MAX_VALUE,
            scope = "read subscribe identity mysubreddits",
        )
        assertTrue(token.grants(RedditOAuth.coreReadScopes))
        assertTrue(token.grants(RedditOAuth.liveCustomFeedWriteScopes))
    }

    @Test
    fun customFeedSlugIsDeterministicAndBounded() {
        assertEquals("volvo_and_music", customFeedSlug("Volvo & Music"))
        assertTrue(customFeedSlug("x".repeat(80))!!.length <= 50)
    }
    @Test
    fun authorizedSessionHandoffKeepsFreshWriteGrant() {
        val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet(
                accessToken = "access",
                refreshToken = "refresh",
                tokenType = "bearer",
                expiresAtEpochMs = Long.MAX_VALUE,
                scope = "identity mysubreddits read subscribe",
            ),
            identity = AuthAccountIdentity(
                providerAccountId = "account-id",
                username = "tester",
            ),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            clientIdFingerprint = "fingerprint",
        )

        assertEquals(
            session,
            authorizedCustomFeedSession(
                candidate = session,
                requiredScopes = RedditOAuth.liveCustomFeedWriteScopes,
            ),
        )
    }

    @Test
    fun httpFailureKeepsSanitizedStatusCode() {
        val failure = CustomFeedCreateResult.Failed(
            kind = app.subshell.prototype.data.account.CustomFeedCreateFailureKind.HTTP,
            retryable = false,
            statusCode = 400,
        )
        assertEquals(400, failure.statusCode)
    }

    @Test
    fun preferredWriteScopeSessionCanBackReadOnlyCustomFeedLoad() {
        val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet(
                accessToken = "access",
                refreshToken = "refresh",
                tokenType = "bearer",
                expiresAtEpochMs = 120_000L,
                scope = "identity mysubreddits read subscribe",
            ),
            identity = AuthAccountIdentity(
                providerAccountId = "account-id",
                username = "tester",
            ),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            clientIdFingerprint = "fingerprint",
        )

        assertEquals(
            session,
            preferredCustomFeedReadSession(
                candidate = session,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientIdFingerprint = "fingerprint",
                requiredScopes = RedditOAuth.coreReadScopes,
                nowEpochMs = 1_000L,
                refreshSkewMs = 60_000L,
            ),
        )
    }

    @Test
    fun preferredWriteScopeSessionMustStillBeFreshForRead() {
        val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet(
                accessToken = "access",
                refreshToken = "refresh",
                tokenType = "bearer",
                expiresAtEpochMs = 61_000L,
                scope = "identity mysubreddits read subscribe",
            ),
            identity = AuthAccountIdentity(
                providerAccountId = "account-id",
                username = "tester",
            ),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            clientIdFingerprint = "fingerprint",
        )

        assertEquals(
            null,
            preferredCustomFeedReadSession(
                candidate = session,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                clientIdFingerprint = "fingerprint",
                requiredScopes = RedditOAuth.coreReadScopes,
                nowEpochMs = 1_000L,
                refreshSkewMs = 60_000L,
            ),
        )
    }

}
