package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class Reddit01aCoreReadPolicyTest {
    @Test
    fun coreReadIsExactAndIdenticalAcrossClientModes() {
        val expected = setOf(
            "identity",
            "mysubreddits",
            "read",
        )

        val native = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.nativeProductionSelection(),
            nativeConfigured = true,
            nativeClientId = "native-client",
            nativeRedirectUri = "https://example.com/oauth",
            nativeUserAgent = "subshell-test",
            capability = RedditOAuthCapability.CORE_READ,
        )

        val compatibility = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection("compat-client"),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "",
            capability = RedditOAuthCapability.CORE_READ,
        )

        assertNotNull(native)
        assertNotNull(compatibility)
        assertEquals(expected, RedditOAuth.coreReadScopes)
        assertEquals(expected, RedditOAuth.coreReadScopes)
        assertEquals(expected, RedditOAuth.scopesFor(RedditOAuthCapability.CORE_READ))
        assertEquals(expected, native!!.configuration.scopes)
        assertEquals(expected, compatibility!!.configuration.scopes)
    }

    @Test
    fun coreReadContainsNoMutationOrExpandedHistoryScopes() {
        val excluded = setOf(
            "history",
            "save",
            "subscribe",
            "vote",
            "submit",
            "edit",
            "flair",
            "modposts",
            "privatemessages",
        )

        assertFalse(RedditOAuth.coreReadScopes.any(excluded::contains))
    }
}
