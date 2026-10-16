package app.subshell.prototype.data.auth

import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val AUTH01_LOG_TAG = "subshell-auth01"
private const val AUTH01_TEST_KEY_ALIAS = "app.subshell.prototype.auth01.runtime.v1"
private const val AUTH01_REDIRECT_URI = "subshell-auth-test://oauth/callback"

@RunWith(AndroidJUnit4::class)
class Auth01OAuthSessionInstrumentedTest {
    @Test
    fun writeEncryptedSessionBeforeExternalProcessRestart() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = MutableAuthClock(1_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH01_TEST_KEY_ALIAS),
            )
            store.clearAll(deleteKey = true)

            val server = LocalJsonHttpsServer(instrumentation.context).also(LocalJsonHttpsServer::start)
            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = TOKEN_ONE_JSON,
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                ),
            )
            val configuration = testConfiguration(server.port)
            val manager = OAuthSessionManager(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient(configuration, clock),
                clock = clock,
                stateGenerator = OAuthStateGenerator { "state-auth01-runtime-abcdefghijklmnopqrstuvwxyz" },
            )

            val authorizationUrl = manager.beginAuthorization().toHttpUrl()
            val state = requireNotNull(authorizationUrl.queryParameter("state"))
            assertEquals("code", authorizationUrl.queryParameter("response_type"))
            assertEquals("permanent", authorizationUrl.queryParameter("duration"))

            val callbackProcessor = OAuthCallbackProcessor(store = store, clock = clock)
            val wrongState = callbackProcessor.process(
                "$AUTH01_REDIRECT_URI?code=wrong&state=attacker".toUri(),
            )
            assertEquals(
                AuthCallbackFailureKind.STATE_MISMATCH,
                (wrongState as AuthCallbackResult.Rejected).kind,
            )

            val callbackUri =
                "$AUTH01_REDIRECT_URI?code=${urlEncode("one-time-code")}&state=${urlEncode(state)}".toUri()
            assertTrue(callbackProcessor.process(callbackUri) is AuthCallbackResult.Accepted)
            assertEquals(
                AuthCallbackFailureKind.NO_PENDING_AUTHORIZATION,
                (callbackProcessor.process(callbackUri) as AuthCallbackResult.Rejected).kind,
            )

            val completed = manager.completePendingCallback()
            assertTrue(completed is AuthSessionResult.Authenticated)
            completed as AuthSessionResult.Authenticated
            assertEquals("access-one", completed.session.tokens.accessToken)
            assertEquals("refresh-one", completed.session.tokens.refreshToken)
            assertEquals("access-one", manager.bearerToken())

            assertTrue(server.awaitRequestCount(expected = 1, timeoutMs = 5_000L))
            val request = server.requestsSnapshot().single()
            assertTrue(request.requestLine.startsWith("POST /api/v1/access_token"))
            assertTrue(request.headers["authorization"].orEmpty().startsWith("Basic "))
            assertTrue(request.headers["content-type"].orEmpty().startsWith("application/x-www-form-urlencoded"))
            assertTrue(request.body.contains("grant_type=authorization_code"))
            assertTrue(request.body.contains("code=one-time-code"))
            assertTrue(request.body.contains("redirect_uri="))

            val encrypted = store.encryptedBytesForTest().toString(Charsets.ISO_8859_1)
            listOf("access-one", "refresh-one", "one-time-code", state).forEach { secret ->
                assertFalse("Encrypted auth storage leaked plaintext", encrypted.contains(secret))
            }

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH01_LOG_TAG,
                "AUTH01_PHASE=WRITE STATE_MISMATCH_REJECTED=true STATE_REPLAY_REJECTED=true " +
                    "CODE_EXCHANGE=true ENCRYPTED_SESSION=true PLAINTEXT_SECRET_LEAK=false",
            )
            Log.i(AUTH01_LOG_TAG, "RESULT=SUBSHELL_AUTH01_WRITE_PHASE_PASS")
        }
    }

    @Test
    fun restoreRefreshAndRevokeAfterExternalProcessRestart() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = MutableAuthClock(2_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH01_TEST_KEY_ALIAS),
            )
            val restored = requireNotNull(store.session())
            assertEquals("access-one", restored.tokens.accessToken)
            assertEquals("refresh-one", restored.tokens.refreshToken)

            val server = LocalJsonHttpsServer(instrumentation.context).also(LocalJsonHttpsServer::start)
            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = TOKEN_TWO_JSON,
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                    delayMs = 150L,
                ),
            )
            server.enqueue(
                FixtureResponse(
                    status = "204 No Content",
                    body = "",
                    headers = emptyMap(),
                ),
            )
            val configuration = testConfiguration(server.port)
            val manager = OAuthSessionManager(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient(configuration, clock),
                clock = clock,
            )

            val refreshed = coroutineScope {
                listOf(
                    async { manager.ensureValidAccessToken() },
                    async { manager.ensureValidAccessToken() },
                ).awaitAll()
            }
            assertTrue(refreshed.all { it is AuthSessionResult.Authenticated })
            assertTrue(server.awaitRequestCount(expected = 1, timeoutMs = 5_000L))
            assertEquals(1, server.requestsSnapshot().size)
            assertEquals("access-two", manager.bearerToken())
            assertEquals("refresh-two", store.session()?.tokens?.refreshToken)
            assertEquals("identity read", store.session()?.tokens?.scope)

            val refreshRequest = server.requestsSnapshot().single()
            assertTrue(refreshRequest.body.contains("grant_type=refresh_token"))
            assertTrue(refreshRequest.body.contains("refresh_token=refresh-one"))

            val logout = manager.logout()
            assertTrue(logout.localSessionCleared)
            assertTrue(logout.remoteRevocationSucceeded)
            assertNull(manager.bearerToken())
            assertNull(store.session())
            assertTrue(server.awaitRequestCount(expected = 2, timeoutMs = 5_000L))

            val revokeRequest = server.requestsSnapshot().last()
            assertTrue(revokeRequest.requestLine.startsWith("POST /api/v1/revoke_token"))
            assertTrue(revokeRequest.body.contains("token=refresh-two"))
            assertTrue(revokeRequest.body.contains("token_type_hint=refresh_token"))

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH01_LOG_TAG,
                "AUTH01_PHASE=RESTORE SESSION_RESTORED=true SERIALIZED_REFRESH_REQUESTS=1 " +
                    "REFRESH_ROTATION=true BEARER_PROVIDER_UPDATED=true REMOTE_REVOKE=true LOCAL_LOGOUT=true",
            )
            Log.i(AUTH01_LOG_TAG, "RESULT=SUBSHELL_AUTH01_SECURE_OAUTH_RUNTIME_PASS")
        }
    }

    private fun testConfiguration(port: Int): OAuthProviderConfiguration =
        OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/api/v1/authorize.compact".toHttpUrl(),
            tokenEndpoint = "https://127.0.0.1:$port/api/v1/access_token".toHttpUrl(),
            revokeEndpoint = "https://127.0.0.1:$port/api/v1/revoke_token".toHttpUrl(),
            clientId = "auth01-test-client",
            clientSecret = "",
            redirectUri = AUTH01_REDIRECT_URI,
            scopes = setOf("identity", "read"),
            permanentAccess = true,
        )

    private fun tokenClient(
        configuration: OAuthProviderConfiguration,
        clock: AuthClock,
    ): OAuthTokenEndpointClient = OAuthTokenEndpointClient(
        callFactory = SubshellOkHttpClientFactory.create(),
        configuration = configuration,
        userAgent = "android:${BuildConfig.APPLICATION_ID}:0.48.0 (by /u/subshell-test)",
        clock = clock,
    )
}

private class MutableAuthClock(
    @Volatile var now: Long,
) : AuthClock {
    override fun epochMillis(): Long = now
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private const val TOKEN_ONE_JSON = """
{
  "access_token":"access-one",
  "refresh_token":"refresh-one",
  "token_type":"bearer",
  "expires_in":120,
  "scope":"identity read"
}
"""

private const val TOKEN_TWO_JSON = """
{
  "access_token":"access-two",
  "refresh_token":"refresh-two",
  "token_type":"bearer",
  "expires_in":3600
}
"""
