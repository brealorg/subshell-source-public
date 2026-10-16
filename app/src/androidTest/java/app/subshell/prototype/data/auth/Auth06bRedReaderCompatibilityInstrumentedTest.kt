package app.subshell.prototype.data.auth

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Auth06bRedReaderCompatibilityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = EncryptedAuthSessionStore(context)

    @After
    fun cleanup() {
        runCatching { store.clearAll(deleteKey = true) }
    }

    @Test
    fun encryptedLocalSelectionExactCallbackAndLocalhostIdentityRuntime() {
        runBlocking {
        store.clearAll(deleteKey = true)
        val clientId = "auth06b_fixture_client"
        val selection = RedditOAuth.redReaderCompatibilitySelection(clientId)
        store.saveOAuthClientSelection(selection)

        val encryptedConfiguration = store.encryptedConfigurationBytesForTest()
        assertTrue(encryptedConfiguration.isNotEmpty())
        assertFalse(
            encryptedConfiguration.toString(Charsets.ISO_8859_1).contains(clientId),
        )
        val reopened = EncryptedAuthSessionStore(context).oauthClientSelection()
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, reopened?.mode)
        assertEquals(clientId, reopened?.clientId)

        val server = LocalJsonHttpsServer(instrumentation.context)
        server.start()
        server.enqueue(jsonFixture(TOKEN_JSON))
        server.enqueue(jsonFixture(IDENTITY_JSON))

        val configuration = OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint =
                "https://example.test/api/v1/authorize.compact".toHttpUrl(),
            tokenEndpoint =
                "https://127.0.0.1:${server.port}/api/v1/access_token".toHttpUrl(),
            revokeEndpoint =
                "https://127.0.0.1:${server.port}/api/v1/revoke_token".toHttpUrl(),
            clientId = clientId,
            clientSecret = "",
            redirectUri = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            scopes = setOf("identity", "read"),
            permanentAccess = true,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val tokenClient = OAuthTokenEndpointClient(
            callFactory = SubshellOkHttpClientFactory.create(),
            configuration = configuration,
            userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
            clock = FixedAuth06bClock,
        )
        val sessionManager = OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            clock = FixedAuth06bClock,
            stateGenerator = OAuthStateGenerator { AUTH06B_STATE },
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = RedditAccountIdentityClient(
                callFactory = SubshellOkHttpClientFactory.create(),
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
            ),
            localFixtureUsernames = listOf("root", "packetmoss"),
            clock = FixedAuth06bClock,
        )

        val authorizationUrl = sessionManager.beginAuthorization().toHttpUrl()
        assertEquals(clientId, authorizationUrl.queryParameter("client_id"))
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            authorizationUrl.queryParameter("redirect_uri"),
        )
        val pending = store.pendingAuthorization()
        assertNotNull(pending)
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, pending?.clientMode)
        assertEquals(configuration.clientIdFingerprint, pending?.clientIdFingerprint)

        val nativeCallback =
            "https://brealorg.github.io/oauth/callback?code=wrong&state=$AUTH06B_STATE".toUri()
        val mismatch = OAuthCallbackProcessor(store, FixedAuth06bClock).process(nativeCallback)
        assertTrue(mismatch is AuthCallbackResult.Rejected)
        assertEquals(
            AuthCallbackFailureKind.REDIRECT_URI_MISMATCH,
            (mismatch as AuthCallbackResult.Rejected).kind,
        )
        assertNotNull(store.pendingAuthorization())

        val callback = Uri.parse(
            RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI +
                "?code=${encodeAuth06b("compatibility-code")}&state=$AUTH06B_STATE#_",
        )
        assertTrue(
            OAuthCallbackProcessor(store, FixedAuth06bClock).process(callback) is
                AuthCallbackResult.Accepted,
        )
        val storedCallback = store.callback()
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, storedCallback?.clientMode)
        assertEquals(configuration.clientIdFingerprint, storedCallback?.clientIdFingerprint)

        val registered = coordinator.completePendingAccount()
        assertTrue(registered is AuthAccountRegistrationResult.Registered)
        registered as AuthAccountRegistrationResult.Registered
        assertEquals("auth06b-user", registered.account.username)
        assertTrue(
            registered.account.stableId.startsWith(
                "reddit:redreader_compatibility:",
            ),
        )

        assertTrue(server.awaitRequestCount(expected = 2, timeoutMs = 5_000L))
        val requests = server.requestsSnapshot()
        assertEquals(2, requests.size)
        assertEquals(
            Credentials.basic(clientId, ""),
            requests[0].headers["authorization"],
        )
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
            requests[0].headers["user-agent"],
        )
        assertTrue(requests[0].body.contains("grant_type=authorization_code"))
        assertTrue(requests[0].body.contains("redirect_uri=redreader%3A%2F%2Frr_oauth_redir"))
        assertEquals("Bearer auth06b-access", requests[1].headers["authorization"])
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
            requests[1].headers["user-agent"],
        )

        val authenticatedSession = store.session(registered.account.stableId)
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, authenticatedSession?.clientMode)
        assertEquals(configuration.clientIdFingerprint, authenticatedSession?.clientIdFingerprint)
        assertNull(store.session())

        store.deleteOAuthClientSelection()
        assertNull(store.oauthClientSelection())
        assertEquals(0, store.encryptedConfigurationBytesForTest().size)

        server.close()
        server.assertHealthy()
        Log.i(
            AUTH06B_LOG_TAG,
            "LOCAL_CLIENT_ID_ENCRYPTED=true PROCESS_REOPEN=true " +
                "NATIVE_CALLBACK_REJECTED=true EXACT_REDREADER_CALLBACK=true " +
                "MODE_FINGERPRINT_BOUND=true TOKEN_USER_AGENT_EXACT=true " +
                "IDENTITY_USER_AGENT_EXACT=true CLIENT_ID_REMOVED=true HTTPS_REQUESTS=2",
        )
        Log.i(AUTH06B_LOG_TAG, "RESULT=SUBSHELL_AUTH06B_REDREADER_COMPATIBILITY_RUNTIME_PASS")
        }
    }
}

private object FixedAuth06bClock : AuthClock {
    override fun epochMillis(): Long = 1_800_000_000_000L
}

private fun jsonFixture(body: String): FixtureResponse = FixtureResponse(
    status = "200 OK",
    body = body,
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
)

private fun encodeAuth06b(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private const val AUTH06B_STATE =
    "auth06b-state-value-with-at-least-thirty-two-characters"
private const val AUTH06B_LOG_TAG = "subshell-auth06b"
private const val TOKEN_JSON = """
{"access_token":"auth06b-access","refresh_token":"auth06b-refresh","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val IDENTITY_JSON = """{"id":"t2_auth06b","name":"auth06b-user"}"""
