package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.OAuthClientMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth07b1DailyDriverAccountPolicyTest {
    @Test
    fun dailyDriverScopesAreExactCurrentProductGrant() {
        val expected = setOf(
            "account",
            "edit",
            "flair",
            "history",
            "identity",
            "modcontributors",
            "modlog",
            "modmail",
            "modothers",
            "modposts",
            "mysubreddits",
            "privatemessages",
            "read",
            "report",
            "save",
            "submit",
            "subscribe",
            "vote",
            "wikiread",
        )

        assertEquals(
            expected,
            RedditOAuth.dailyDriverScopes,
        )

        listOf(
            RedditOAuth.liveTextPostWriteScopes,
        ).plus(
            RedditOAuthCapability.entries.map(RedditOAuth::scopesFor),
        ).forEach { required ->
            assertTrue(
                RedditOAuth.dailyDriverScopes
                    .containsAll(required),
            )
        }

        assertTrue(
            RedditOAuth.dailyDriverScopes.contains("account"),
        )
        assertTrue(
            RedditOAuth.dailyDriverScopes.contains("save"),
        )
        assertTrue(
            RedditOAuth.dailyDriverScopes.contains("vote"),
        )
        assertTrue(
            RedditOAuth.dailyDriverScopes.contains("report"),
        )
    }

    @Test
    fun dailyDriverSelectionPreservesCompatibilityTransport() {
        val base =
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection =
                    RedditOAuth.redReaderCompatibilitySelection(
                        "auth07b1_fixture_client",
                    ),
                nativeConfigured = false,
                nativeClientId = "",
                nativeRedirectUri = "",
                nativeUserAgent = "",
                capability =
                    RedditOAuthCapability.CORE_READ,
            )
                ?: error(
                    "Compatibility selection unavailable"
                )

        val connect =
            RedditOAuth.dailyDriverAccountSelection(base)

        assertEquals(
            OAuthClientMode.REDREADER_COMPATIBILITY,
            connect.configuration.clientMode,
        )
        assertEquals(
            base.configuration.clientId,
            connect.configuration.clientId,
        )
        assertEquals(
            base.configuration.redirectUri,
            connect.configuration.redirectUri,
        )
        assertEquals(
            base.userAgent,
            connect.userAgent,
        )
        assertEquals(
            base.providerLabel,
            connect.providerLabel,
        )
        assertEquals(
            RedditOAuth.dailyDriverScopes,
            connect.configuration.scopes,
        )
    }
}
