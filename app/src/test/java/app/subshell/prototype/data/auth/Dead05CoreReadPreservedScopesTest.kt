package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditOAuthCapability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DEAD05 (OCB-511): CORE_READ kept the preserved scopes in RedReader
 * compatibility mode but dropped them in native mode.
 */
class Dead05CoreReadPreservedScopesTest {
    private val preserved = setOf("history")

    @Test
    fun bothClientModesKeepPreservedScopesForCoreRead() {
        val native = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = null,
            nativeConfigured = true,
            nativeClientId = "dead05_native_client",
            nativeRedirectUri = "subshell-auth-test://oauth/callback",
            nativeUserAgent = "subshell-test",
            capability = RedditOAuthCapability.CORE_READ,
            preservedScopes = preserved,
        )
        val compatibility = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection("dead05_compat_client"),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "unused",
            capability = RedditOAuthCapability.CORE_READ,
            preservedScopes = preserved,
        )

        assertEquals(RedditOAuth.coreReadScopes + preserved, native?.configuration?.scopes)
        assertEquals(RedditOAuth.coreReadScopes + preserved, compatibility?.configuration?.scopes)
    }
}
