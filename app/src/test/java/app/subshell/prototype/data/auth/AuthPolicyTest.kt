package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPolicyTest {
    @Test
    fun secureStateIsUrlSafeUniqueAndAtLeast256Bits() {
        val generator = SecureOAuthStateGenerator()
        val states = (0 until 64).map { generator.generate() }

        assertEquals(states.size, states.toSet().size)
        assertTrue(states.all { state -> state.length >= 43 })
        assertTrue(states.all { state -> state.all { it.isLetterOrDigit() || it == '-' || it == '_' } })
    }

    @Test
    fun authorizationUrlContainsCodeFlowPermanentDurationAndSortedScopes() {
        val configuration = OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
            revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
            clientId = "client-id",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = setOf("read", "identity"),
            permanentAccess = true,
        )

        val url = configuration.authorizationUrl("state-value")

        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("state-value", url.queryParameter("state"))
        assertEquals("permanent", url.queryParameter("duration"))
        assertEquals("identity read", url.queryParameter("scope"))
        assertFalse(url.toString().contains("secret"))
    }

    @Test
    fun providerEndpointsRequireHttpsAndRedditInstalledAppsHaveNoSecret() {
        assertThrows(IllegalArgumentException::class.java) {
            OAuthProviderConfiguration(
                provider = OAuthProvider.REDDIT,
                authorizationEndpoint = "http://example.test/authorize".toHttpUrl(),
                tokenEndpoint = "https://example.test/token".toHttpUrl(),
                revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
                clientId = "client-id",
                redirectUri = "subshell-auth-test://oauth/callback",
                scopes = setOf("read"),
                permanentAccess = true,
            )
        }

        val reddit = RedditOAuth.installedApplication(
            clientId = "client-id",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = RedditOAuth.coreReadScopes,
        )
        assertEquals("", reddit.clientSecret)
        assertEquals("https://oauth.reddit.com/", RedditOAuth.apiEndpoint.toString())
    }

    @Test
    fun commentWriteCapabilityIsExplicitScopeBoundAndMinimal() {
        assertEquals(
            setOf("identity", "mysubreddits", "read", "submit"),
            RedditOAuth.liveCommentWriteScopes,
        )
        assertEquals(
            setOf("identity", "mysubreddits", "read", "submit", "flair"),
            RedditOAuth.liveComposerFlairScopes,
        )
        assertTrue(RedditOAuth.liveCommentWriteScopes.containsAll(RedditOAuth.coreReadScopes))
        assertTrue(
            RedditOAuth.liveComposerFlairScopes.containsAll(
                RedditOAuth.liveCommentWriteScopes,
            ),
        )
        assertFalse(RedditOAuth.coreReadScopes.contains("submit"))

        val readOnly = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection("auth-policy-client"),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "unused",
        )
        val write = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection("auth-policy-client"),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "unused",
            capability = app.subshell.prototype.data.auth.reddit.RedditOAuthCapability.COMMENT_WRITE,
        )
        assertEquals(RedditOAuth.coreReadScopes, readOnly?.configuration?.scopes)
        assertEquals(RedditOAuth.liveCommentWriteScopes, write?.configuration?.scopes)
        assertTrue(readOnly?.configuration?.scopeFingerprint != write?.configuration?.scopeFingerprint)
    }

    @Test
    fun tokenScopeGrantsSupportReadOnlySupersetsButRejectMissingSubmit() {
        val writeToken = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            scope = "submit identity mysubreddits read",
            expiresAtEpochMs = 100_000L,
        )
        val readToken = writeToken.copy(scope = "identity mysubreddits read")

        assertTrue(writeToken.grants(RedditOAuth.coreReadScopes))
        assertTrue(writeToken.grants(RedditOAuth.liveCommentWriteScopes))
        assertFalse(writeToken.grants(RedditOAuth.liveComposerFlairScopes))
        assertFalse(readToken.grants(RedditOAuth.liveCommentWriteScopes))
    }

    @Test
    fun tokenUsabilityAppliesRefreshSkew() {
        val token = OAuthTokenSet(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "bearer",
            scope = "read",
            expiresAtEpochMs = 100_000L,
        )

        assertTrue(token.isUsableAt(nowEpochMs = 10_000L, refreshSkewMs = 60_000L))
        assertFalse(token.isUsableAt(nowEpochMs = 50_000L, refreshSkewMs = 60_000L))
    }

    @Test
    fun storedSessionAcceptsConfiguredScopeSupersetsButRejectsMissingScopes() {
        val configuration = RedditOAuth.installedApplication(
            clientId = "client-id",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = setOf("identity", "mysubreddits", "read"),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val oldSession = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet(
                accessToken = "access",
                refreshToken = "refresh",
                tokenType = "bearer",
                scope = "identity read",
                expiresAtEpochMs = 100_000L,
            ),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            clientIdFingerprint = configuration.clientIdFingerprint,
        )

        assertFalse(oldSession.matches(configuration))
        val readSession = oldSession.copy(
            tokens = oldSession.tokens.copy(
                scope = "identity mysubreddits read",
            ),
        )
        assertTrue(readSession.matches(configuration))
        assertTrue(
            readSession.copy(
                tokens = readSession.tokens.copy(
                    scope = "flair identity mysubreddits read submit",
                ),
            ).matches(configuration),
        )
        assertTrue(
            oldSession.copy(
                tokens = oldSession.tokens.copy(
                    scope = "identity mysubreddits privatemessages read subscribe",
                ),
            ).matches(configuration),
        )
    }
}
