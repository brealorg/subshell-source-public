package app.subshell.prototype.data.auth

import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.account.ConnectAccountFailureKind
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val AUTH03_LOG_TAG = "subshell-auth03"
private const val AUTH03_TEST_KEY_ALIAS = "app.subshell.prototype.auth03.runtime.v1"
private const val AUTH03_REDIRECT_URI = "subshell-auth-test://oauth/callback"

@RunWith(AndroidJUnit4::class)
class Auth03ConnectAccountInstrumentedTest {
    @Test
    fun writePendingConnectFlowWithCancelMismatchProviderErrorAndProcessSafeCallback() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = Auth03MutableClock(3_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH03_TEST_KEY_ALIAS),
            )
            store.clearAll(deleteKey = true)

            val server = LocalJsonHttpsServer(instrumentation.context)
                .also(LocalJsonHttpsServer::start)
            val runtime = auth03Runtime(server, store, clock)

            assertEquals(ConnectAccountPhase.IDLE, runtime.flow.uiState.value.phase)
            assertEquals(2, runtime.coordinator.snapshot().accounts.size)

            assertTrue(runtime.flow.beginAuthorization() is AuthBrowserLaunchResult.Ready)
            runtime.flow.browserLaunchFailed()
            assertEquals(ConnectAccountPhase.ERROR, runtime.flow.uiState.value.phase)
            assertEquals(
                ConnectAccountFailureKind.BROWSER_LAUNCH,
                runtime.flow.uiState.value.failureKind,
            )
            assertTrue(runtime.flow.uiState.value.retryable)

            assertTrue(runtime.flow.beginAuthorization() is AuthBrowserLaunchResult.Ready)
            runtime.flow.cancel()
            assertEquals(ConnectAccountPhase.CANCELLED, runtime.flow.uiState.value.phase)
            assertNull(store.pendingAuthorization())
            assertNull(store.callback())

            val mismatchLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val mismatchState = requireNotNull(
                mismatchLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            val mismatch = OAuthCallbackProcessor(store, clock).process(
                "$AUTH03_REDIRECT_URI?code=mismatch&state=${encodeAuth03(mismatchState + "-wrong")}"
                    .toUri(),
            )
            assertEquals(
                AuthCallbackFailureKind.STATE_MISMATCH,
                (mismatch as AuthCallbackResult.Rejected).kind,
            )
            runtime.flow.handleCallbackResultToken("rejected:STATE_MISMATCH")
            assertEquals(ConnectAccountPhase.ERROR, runtime.flow.uiState.value.phase)
            assertEquals(
                ConnectAccountFailureKind.CALLBACK_REJECTED,
                runtime.flow.uiState.value.failureKind,
            )
            assertEquals(2, runtime.coordinator.snapshot().accounts.size)
            assertNull(store.pendingAuthorization())
            assertNull(store.callback())
            assertNull(store.session())

            val providerLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val providerState = requireNotNull(
                providerLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            val providerCallback = OAuthCallbackProcessor(store, clock).process(
                "$AUTH03_REDIRECT_URI?error=temporarily_unavailable&state=${encodeAuth03(providerState)}"
                    .toUri(),
            )
            assertTrue(providerCallback is AuthCallbackResult.Accepted)
            runtime.flow.handleCallbackResultToken("denied")
            assertEquals(ConnectAccountPhase.ERROR, runtime.flow.uiState.value.phase)
            assertEquals(
                ConnectAccountFailureKind.PROVIDER,
                runtime.flow.uiState.value.failureKind,
            )
            assertTrue(runtime.flow.uiState.value.retryable)
            assertEquals(2, runtime.coordinator.snapshot().accounts.size)

            val successLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val successState = requireNotNull(
                successLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            val accepted = OAuthCallbackProcessor(store, clock).process(
                "$AUTH03_REDIRECT_URI?code=alice-code&state=${encodeAuth03(successState)}"
                    .toUri(),
            )
            assertTrue(accepted is AuthCallbackResult.Accepted)
            assertNotNull(store.callback())
            assertTrue(runtime.flow.hasStoredCallback())
            assertEquals(0, server.requestsSnapshot().size)

            val privateBytes = store.encryptedBytesForTest().toString(Charsets.ISO_8859_1)
            assertFalse(privateBytes.contains("alice-code"))
            assertFalse(privateBytes.contains(successState))

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH03_LOG_TAG,
                "AUTH03_PHASE=WRITE BROWSER_FAILURE=true EXPLICIT_CANCEL=true " +
                    "STATE_MISMATCH=true PROVIDER_ERROR=true EXISTING_ACCOUNTS_PRESERVED=true " +
                    "CALLBACK_PERSISTED_FOR_RESTART=true PLAINTEXT_CALLBACK_LEAK=false",
            )
            Log.i(AUTH03_LOG_TAG, "RESULT=SUBSHELL_AUTH03_WRITE_PHASE_PASS")
        }
    }

    @Test
    fun restoreConnectThenPreserveAccountAcrossExchangeIdentityErrorsAndRetry() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = Auth03MutableClock(4_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH03_TEST_KEY_ALIAS),
            )
            val server = LocalJsonHttpsServer(instrumentation.context)
                .also(LocalJsonHttpsServer::start)

            enqueueSuccess(server, TOKEN_ALICE_JSON, IDENTITY_ALICE_JSON)
            val runtime = auth03Runtime(server, store, clock)
            assertEquals(
                ConnectAccountPhase.AWAITING_CALLBACK,
                runtime.flow.uiState.value.phase,
            )

            runtime.flow.resumeStoredCallbackIfPresent()
            assertEquals(ConnectAccountPhase.CONNECTED, runtime.flow.uiState.value.phase)
            assertEquals("alice", runtime.flow.uiState.value.username)
            assertEquals(1L, runtime.flow.uiState.value.directoryRevision)
            val aliceSnapshot = runtime.coordinator.snapshot()
            val alice = aliceSnapshot.accounts.single { account ->
                account.username == "alice"
            }
            assertEquals(alice.stableId, aliceSnapshot.activeAccountId)
            assertEquals(AccountSessionSource.AUTHENTICATED, alice.source)

            runtime.flow.handleCallbackResultToken("accepted")
            assertEquals(ConnectAccountPhase.CONNECTED, runtime.flow.uiState.value.phase)
            assertEquals("alice", runtime.flow.uiState.value.username)
            assertTrue(server.awaitRequestCount(expected = 2, timeoutMs = 5_000L))
            assertEquals(2, server.requestsSnapshot().size)

            val exchangeLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val exchangeState = requireNotNull(
                exchangeLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            server.enqueue(httpFailureFixture())
            assertTrue(
                OAuthCallbackProcessor(store, clock).process(
                    "$AUTH03_REDIRECT_URI?code=exchange-error&state=${encodeAuth03(exchangeState)}"
                        .toUri(),
                ) is AuthCallbackResult.Accepted,
            )
            runtime.flow.handleCallbackResultToken("accepted")
            assertEquals(ConnectAccountPhase.ERROR, runtime.flow.uiState.value.phase)
            assertEquals(
                ConnectAccountFailureKind.TOKEN_EXCHANGE,
                runtime.flow.uiState.value.failureKind,
            )
            assertTrue(runtime.flow.uiState.value.retryable)
            assertTrue(runtime.coordinator.snapshot().accounts.any { it.stableId == alice.stableId })

            val identityLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val identityState = requireNotNull(
                identityLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            server.enqueue(jsonFixture(TOKEN_IDENTITY_FAILURE_JSON))
            server.enqueue(httpFailureFixture())
            assertTrue(
                OAuthCallbackProcessor(store, clock).process(
                    "$AUTH03_REDIRECT_URI?code=identity-error&state=${encodeAuth03(identityState)}"
                        .toUri(),
                ) is AuthCallbackResult.Accepted,
            )
            runtime.flow.handleCallbackResultToken("accepted")
            assertEquals(ConnectAccountPhase.ERROR, runtime.flow.uiState.value.phase)
            assertEquals(
                ConnectAccountFailureKind.IDENTITY,
                runtime.flow.uiState.value.failureKind,
            )
            assertTrue(runtime.flow.uiState.value.retryable)
            assertTrue(runtime.coordinator.snapshot().accounts.any { it.stableId == alice.stableId })
            assertNull(store.session())

            val retryLaunch =
                runtime.flow.beginAuthorization() as AuthBrowserLaunchResult.Ready
            val retryState = requireNotNull(
                retryLaunch.authorizationUrl.toHttpUrl().queryParameter("state"),
            )
            enqueueSuccess(server, TOKEN_BOB_JSON, IDENTITY_BOB_JSON)
            assertTrue(
                OAuthCallbackProcessor(store, clock).process(
                    "$AUTH03_REDIRECT_URI?code=bob-code&state=${encodeAuth03(retryState)}"
                        .toUri(),
                ) is AuthCallbackResult.Accepted,
            )
            runtime.flow.handleCallbackResultToken("accepted")
            assertEquals(ConnectAccountPhase.CONNECTED, runtime.flow.uiState.value.phase)
            assertEquals("bob", runtime.flow.uiState.value.username)
            assertEquals(2L, runtime.flow.uiState.value.directoryRevision)

            val finalSnapshot = runtime.coordinator.snapshot()
            assertEquals(4, finalSnapshot.accounts.size)
            assertEquals("bob", finalSnapshot.accounts.first {
                it.stableId == finalSnapshot.activeAccountId
            }.username)
            assertTrue(finalSnapshot.accounts.any { it.stableId == alice.stableId })
            assertEquals(2, finalSnapshot.accounts.count {
                it.source == AccountSessionSource.AUTHENTICATED
            })

            assertTrue(server.awaitRequestCount(expected = 7, timeoutMs = 5_000L))
            val requests = server.requestsSnapshot()
            assertEquals(7, requests.size)
            assertTrue(requests[0].requestLine.startsWith("POST /api/v1/access_token"))
            assertTrue(requests[1].requestLine.startsWith("GET /api/v1/me"))
            assertEquals("Bearer access-alice-auth03", requests[1].headers["authorization"])
            assertEquals("Bearer access-bob-auth03", requests.last().headers["authorization"])

            val uiText = runtime.flow.uiState.value.toString()
            listOf(
                "access-alice-auth03",
                "refresh-alice-auth03",
                "access-bob-auth03",
                "refresh-bob-auth03",
                "alice-code",
                "bob-code",
            ).forEach { secret ->
                assertFalse(
                    "Sanitized AUTH03 UI state leaked private material",
                    uiText.contains(secret),
                )
            }

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH03_LOG_TAG,
                "AUTH03_PHASE=RESTORE CONNECTED_AFTER_PROCESS_RESTART=alice " +
                    "DUPLICATE_RESULT_IDEMPOTENT=true TOKEN_EXCHANGE_ERROR=true " +
                    "IDENTITY_ERROR=true RETRY_SUCCESS=bob EXISTING_ACCOUNT_PRESERVED=true " +
                    "ACTIVE_ACCOUNT=bob HTTPS_REQUESTS=7 " +
                    "SANITIZED_UI_STATE=true",
            )
            Log.i(AUTH03_LOG_TAG, "RESULT=SUBSHELL_AUTH03_CONNECT_ACCOUNT_RUNTIME_PASS")
        }
    }
}

private data class Auth03Runtime(
    val coordinator: AuthAccountSessionCoordinator,
    val flow: ConfiguredAuthConnectFlowController,
)

private class Auth03MutableClock(
    @Volatile var now: Long,
) : AuthClock {
    override fun epochMillis(): Long = now
}

private fun auth03Runtime(
    server: LocalJsonHttpsServer,
    store: EncryptedAuthSessionStore,
    clock: AuthClock,
): Auth03Runtime {
    val configuration = auth03Configuration(server.port)
    val tokenClient = OAuthTokenEndpointClient(
        callFactory = SubshellOkHttpClientFactory.create(),
        configuration = configuration,
        userAgent = AUTH03_USER_AGENT,
        clock = clock,
    )
    val coordinator = AuthAccountSessionCoordinator(
        configuration = configuration,
        store = store,
        tokenClient = tokenClient,
        identityClient = RedditAccountIdentityClient(
            callFactory = SubshellOkHttpClientFactory.create(),
            endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
            userAgent = AUTH03_USER_AGENT,
        ),
        localFixtureUsernames = listOf("root", "packetmoss"),
        clock = clock,
    )
    return Auth03Runtime(
        coordinator = coordinator,
        flow = ConfiguredAuthConnectFlowController(
            sessionManager = OAuthSessionManager(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient,
                clock = clock,
            ),
            accountCoordinator = coordinator,
            store = store,
            providerLabel = "Reddit",
            clientMode = configuration.clientMode,
        ),
    )
}

private fun auth03Configuration(port: Int): OAuthProviderConfiguration =
    OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint =
            "https://example.test/api/v1/authorize.compact".toHttpUrl(),
        tokenEndpoint =
            "https://127.0.0.1:$port/api/v1/access_token".toHttpUrl(),
        revokeEndpoint =
            "https://127.0.0.1:$port/api/v1/revoke_token".toHttpUrl(),
        clientId = "auth03-test-client",
        clientSecret = "",
        redirectUri = AUTH03_REDIRECT_URI,
        scopes = setOf("identity", "read"),
        permanentAccess = true,
    )

private fun enqueueSuccess(
    server: LocalJsonHttpsServer,
    tokenJson: String,
    identityJson: String,
) {
    server.enqueue(jsonFixture(tokenJson))
    server.enqueue(jsonFixture(identityJson))
}

private fun jsonFixture(body: String): FixtureResponse = FixtureResponse(
    status = "200 OK",
    body = body,
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
)

private fun httpFailureFixture(): FixtureResponse = FixtureResponse(
    status = "503 Service Unavailable",
    body = "{}",
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
)

private fun encodeAuth03(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private const val AUTH03_USER_AGENT =
    "android:${BuildConfig.APPLICATION_ID}:0.50.0 (by /u/subshell-test)"

private const val TOKEN_ALICE_JSON = """
{"access_token":"access-alice-auth03","refresh_token":"refresh-alice-auth03","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val TOKEN_IDENTITY_FAILURE_JSON = """
{"access_token":"access-identity-failure","refresh_token":"refresh-identity-failure","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val TOKEN_BOB_JSON = """
{"access_token":"access-bob-auth03","refresh_token":"refresh-bob-auth03","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val IDENTITY_ALICE_JSON = """{"id":"t2_auth03_alice","name":"alice"}"""
private const val IDENTITY_BOB_JSON = """{"id":"t2_auth03_bob","name":"bob"}"""
