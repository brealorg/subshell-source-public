package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.account.QuarantineOptInResult
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.NsfwPreferencesNetwork
import app.subshell.prototype.data.network.reddit.sendQuarantineOptIn
import app.subshell.prototype.ui.error.quarantineOptInPresentation
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #211 follow-up: Reddit's api/quarantine_optin takes the `subscribe` scope
 * (ISSUE211_QUARANTINE_OPT_IN_NEEDS_SUBSCRIBE_V1), and "View anyway" says why
 * when it did not go through (ISSUE211_QUARANTINE_OPT_IN_RESULT_IS_SHOWN_V1).
 */
class Issue211QuarantineOptInTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client", redirectUri = "test://callback",
        scopes = setOf("identity", "subscribe"), permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")
    private fun stored(scopes: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", scopes, Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = config.clientMode, clientIdFingerprint = config.clientIdFingerprint,
    )
    private val unusedNetwork = object : NsfwPreferencesNetwork {
        override suspend fun read(): NsfwPreferencesResult = NsfwPreferencesResult.Failed
    }

    private fun client(sent: MutableList<String>) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            sent += chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )

    @Test
    fun aGrantWithoutSubscribeIsNotSent() = runBlocking {
        val sent = mutableListOf<String>()
        val asked = mutableListOf<Set<String>>()
        val service = ConfiguredRedditCommunityAccessService(
            configuration = config,
            activeAccountId = { account },
            ensureSession = { _, scopes -> asked += scopes; AuthSessionResult.Authenticated(stored("identity")) },
            storedSessionGrants = { _, scopes -> "subscribe" !in scopes },
            networkForSession = { unusedNetwork },
            writeClientForSession = { client(sent) },
        )

        assertEquals(QuarantineOptInResult.MissingPermission, service.optIn(account, "example") { true })
        assertTrue(sent.isEmpty())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aGrantWithSubscribeSendsTheOptIn() = runBlocking {
        val sent = mutableListOf<String>()
        val asked = mutableListOf<Set<String>>()
        val service = ConfiguredRedditCommunityAccessService(
            configuration = config,
            activeAccountId = { account },
            ensureSession = { _, scopes -> asked += scopes; AuthSessionResult.Authenticated(stored("identity subscribe")) },
            storedSessionGrants = { _, _ -> true },
            networkForSession = { unusedNetwork },
            writeClientForSession = { client(sent) },
        )

        assertEquals(QuarantineOptInResult.Sent, service.optIn(account, "example") { true })
        assertEquals(listOf(setOf("identity", "subscribe")), asked)
        assertEquals(listOf("/api/quarantine_optin"), sent)
    }

    @Test
    fun redditsRedirectAfterTheOptInIsSuccess() = runBlocking {
        fun answering(code: Int) = SubshellHttpClient(
            callFactory = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("x")
                    .header("Location", "https://oauth.reddit.test/r/example")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        )
        assertEquals(QuarantineOptInResult.Sent, answering(302).sendQuarantineOptIn("example"))
        assertEquals(QuarantineOptInResult.Refused(403), answering(403).sendQuarantineOptIn("example"))
        assertTrue(
            quarantineOptInPresentation(QuarantineOptInResult.Refused(403), "r/x")!!.message.contains("verified email"),
        )
    }

    @Test
    fun onlyAnOptInThatDidNotGoThroughIsReported() {
        assertNull(quarantineOptInPresentation(QuarantineOptInResult.Sent, "r/x"))
        assertNull(quarantineOptInPresentation(QuarantineOptInResult.OutcomeUnknown, "r/x"))
        val missing = assertNotNull(quarantineOptInPresentation(QuarantineOptInResult.MissingPermission, "r/x"))
        assertEquals("Couldn't open r/x", missing!!.title)
        assertTrue(missing.message.contains("permission"))
        assertNotNull(quarantineOptInPresentation(QuarantineOptInResult.Refused(403), "r/x"))
    }

    private fun <T> assertNotNull(value: T?): T? {
        org.junit.Assert.assertNotNull(value)
        return value
    }
}
