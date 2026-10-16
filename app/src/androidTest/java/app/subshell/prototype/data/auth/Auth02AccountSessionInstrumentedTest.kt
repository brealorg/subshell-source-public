package app.subshell.prototype.data.auth

import android.util.Log
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.localFixtureAccountId
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
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

private const val AUTH02_LOG_TAG = "subshell-auth02"
private const val AUTH02_TEST_KEY_ALIAS = "app.subshell.prototype.auth02.runtime.v1"
private const val AUTH02_REDIRECT_URI = "subshell-auth-test://oauth/callback"

@RunWith(AndroidJUnit4::class)
class Auth02AccountSessionInstrumentedTest {
    @Test
    fun writeTwoAccountRegistryAndRejectFixtureCollisionBeforeExternalRestart() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = Auth02MutableClock(1_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH02_TEST_KEY_ALIAS),
            )
            store.clearAll(deleteKey = true)

            val server = LocalJsonHttpsServer(instrumentation.context).also(LocalJsonHttpsServer::start)
            enqueueTokenAndIdentity(server, TOKEN_ALICE_JSON, IDENTITY_ALICE_JSON)
            enqueueTokenAndIdentity(server, TOKEN_BOB_JSON, IDENTITY_BOB_JSON)
            enqueueTokenAndIdentity(server, TOKEN_COLLISION_JSON, IDENTITY_ROOT_JSON)

            val configuration = auth02Configuration(server.port)
            val tokenClient = auth02TokenClient(configuration, clock)
            val identityClient = RedditAccountIdentityClient(
                callFactory = SubshellOkHttpClientFactory.create(),
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = AUTH02_USER_AGENT,
            )
            val coordinator = AuthAccountSessionCoordinator(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient,
                identityClient = identityClient,
                localFixtureUsernames = listOf("root", "packetmoss"),
                clock = clock,
            )

            val alice = registerAccount(
                store = store,
                configuration = configuration,
                tokenClient = tokenClient,
                coordinator = coordinator,
                clock = clock,
                code = "alice-code",
                state = "state-auth02-alice-abcdefghijklmnopqrstuvwxyz",
            )
            assertEquals("alice", (alice as AuthAccountRegistrationResult.Registered).account.username)

            val bob = registerAccount(
                store = store,
                configuration = configuration,
                tokenClient = tokenClient,
                coordinator = coordinator,
                clock = clock,
                code = "bob-code",
                state = "state-auth02-bob-abcdefghijklmnopqrstuvwxyzab",
            )
            val bobAccount = (bob as AuthAccountRegistrationResult.Registered).account
            assertEquals("bob", bobAccount.username)
            assertEquals("access-bob", coordinator.bearerToken())

            val collision = registerAccount(
                store = store,
                configuration = configuration,
                tokenClient = tokenClient,
                coordinator = coordinator,
                clock = clock,
                code = "root-code",
                state = "state-auth02-root-abcdefghijklmnopqrstuvwxyz",
            )
            assertEquals(
                AuthAccountRegistrationFailureKind.LOCAL_FIXTURE_USERNAME_COLLISION,
                (collision as AuthAccountRegistrationResult.Failed).kind,
            )

            val snapshot = coordinator.snapshot()
            assertEquals(4, snapshot.accounts.size)
            assertEquals(bobAccount.stableId, snapshot.activeAccountId)
            assertEquals(2, snapshot.accounts.count { it.source == AccountSessionSource.LOCAL_FIXTURE })
            assertEquals(2, requireNotNull(store.accountRegistry()).accounts.size)
            assertNull(store.session())

            assertTrue(server.awaitRequestCount(expected = 6, timeoutMs = 5_000L))
            val requests = server.requestsSnapshot()
            assertEquals(6, requests.size)
            assertTrue(requests[1].requestLine.startsWith("GET /api/v1/me"))
            assertEquals("Bearer access-alice", requests[1].headers["authorization"])
            assertEquals("Bearer access-bob", requests[3].headers["authorization"])

            val encrypted = store.encryptedBytesForTest().toString(Charsets.ISO_8859_1)
            listOf("access-alice", "refresh-alice", "access-bob", "refresh-bob", "alice", "bob")
                .forEach { secret ->
                    assertFalse("AUTH02 private storage leaked plaintext", encrypted.contains(secret))
                }

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH02_LOG_TAG,
                "AUTH02_PHASE=WRITE AUTHENTICATED_ACCOUNTS=2 LOCAL_FIXTURES=2 " +
                    "ACTIVE_ACCOUNT=bob FIXTURE_COLLISION_REJECTED=true METADATA_ONLY_REGISTRY=true " +
                    "PLAINTEXT_SECRET_LEAK=false",
            )
            Log.i(AUTH02_LOG_TAG, "RESULT=SUBSHELL_AUTH02_WRITE_PHASE_PASS")
        }
    }

    @Test
    fun coldDirectorySnapshotDoesNotDecryptBeforeAsyncHydration() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().context
            val cipher = Auth02CountingPassthroughCipher()
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = cipher,
            )
            store.clearAll(deleteKey = true)
            store.saveAccountRegistry(
                StoredAuthAccountRegistry(
                    accounts = emptyList(),
                    activeAccountId = localFixtureAccountId("root"),
                ),
            )
            cipher.decryptCalls.set(0)

            val configuration = auth02Configuration(port = 6553)
            val coordinator = AuthAccountSessionCoordinator(
                configuration = configuration,
                store = store,
                tokenClient = auth02TokenClient(configuration, Auth02MutableClock(1L)),
                identityClient = OAuthAccountIdentityClient {
                    error("Identity resolution must not run during AUTH02 cold directory hydration test")
                },
                localFixtureUsernames = listOf("root", "packetmoss"),
                clock = Auth02MutableClock(1L),
            )

            val cold = coordinator.snapshot()
            assertEquals(localFixtureAccountId("root"), cold.activeAccountId)
            assertEquals(0, cipher.decryptCalls.get())

            coordinator.hydrateDirectory()
            assertTrue(cipher.decryptCalls.get() > 0)
            store.clearAll(deleteKey = true)
        }
    }

    @Test
    fun restoreActiveAccountRefreshSwitchAndScopedLogoutAfterExternalRestart() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val clock = Auth02MutableClock(2_000_000L)
            val store = EncryptedAuthSessionStore(
                context = context,
                cipher = AndroidKeystoreAesGcmCipher(AUTH02_TEST_KEY_ALIAS),
            )
            val server = LocalJsonHttpsServer(instrumentation.context).also(LocalJsonHttpsServer::start)
            server.enqueue(jsonFixture(TOKEN_BOB_REFRESHED_JSON, delayMs = 150L))
            server.enqueue(
                FixtureResponse(
                    status = "204 No Content",
                    body = "",
                    headers = emptyMap(),
                ),
            )

            val configuration = auth02Configuration(server.port)
            val coordinator = AuthAccountSessionCoordinator(
                configuration = configuration,
                store = store,
                tokenClient = auth02TokenClient(configuration, clock),
                identityClient = OAuthAccountIdentityClient {
                    error("Identity resolution must not run during AUTH02 restore")
                },
                localFixtureUsernames = listOf("root", "packetmoss"),
                clock = clock,
            )

            val coldSnapshot = coordinator.snapshot()
            assertEquals(2, coldSnapshot.accounts.size)
            assertTrue(
                coldSnapshot.accounts.all { it.source == AccountSessionSource.LOCAL_FIXTURE },
            )

            val restored = coordinator.hydrateDirectory()
            assertEquals("bob", restored.accounts.first { it.stableId == restored.activeAccountId }.username)

            val refreshed = coroutineScope {
                listOf(
                    async { coordinator.ensureActiveAccessToken() },
                    async { coordinator.ensureActiveAccessToken() },
                ).awaitAll()
            }
            assertTrue(refreshed.all { it is AuthSessionResult.Authenticated })
            assertTrue(server.awaitRequestCount(expected = 1, timeoutMs = 5_000L))
            assertEquals(1, server.requestsSnapshot().size)
            assertEquals("access-bob-rotated", coordinator.bearerToken())

            val bobId = restored.activeAccountId
            val logout = coordinator.logoutActive()
            assertTrue(logout.localSessionCleared)
            assertTrue(logout.remoteRevocationSucceeded)
            assertNull(store.session(bobId))
            assertTrue(server.awaitRequestCount(expected = 2, timeoutMs = 5_000L))

            val afterLogout = coordinator.snapshot()
            assertEquals(localFixtureAccountId("root"), afterLogout.activeAccountId)
            assertTrue(afterLogout.accounts.none { it.stableId == bobId })
            val alice = afterLogout.accounts.single { it.username == "alice" }
            coordinator.selectAccount(alice.stableId)
            assertEquals("access-alice", coordinator.bearerToken())
            coordinator.selectAccount(localFixtureAccountId("root"))
            assertNull(coordinator.bearerToken())
            coordinator.selectAccount(alice.stableId)
            coordinator.awaitDirectoryPersistence()
            assertEquals(alice.stableId, requireNotNull(store.accountRegistry()).activeAccountId)

            val requests = server.requestsSnapshot()
            assertTrue(requests.first().body.contains("grant_type=refresh_token"))
            assertTrue(requests.first().body.contains("refresh_token=refresh-bob"))
            assertTrue(requests.last().body.contains("token=refresh-bob-rotated"))
            assertTrue(requests.last().body.contains("token_type_hint=refresh_token"))

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH02_LOG_TAG,
                "AUTH02_PHASE=RESTORE ACTIVE_ACCOUNT_RESTORED=bob SERIALIZED_REFRESH_REQUESTS=1 " +
                    "BEARER_SWITCHING=true SCOPED_LOGOUT=true UNRELATED_ACCOUNT_PRESERVED=true " +
                    "LOCAL_ACCOUNT_BEARER_NULL=true",
            )
            Log.i(AUTH02_LOG_TAG, "RESULT=SUBSHELL_AUTH02_ACCOUNT_SESSION_RUNTIME_PASS")
        }
    }

    private suspend fun registerAccount(
        store: EncryptedAuthSessionStore,
        configuration: OAuthProviderConfiguration,
        tokenClient: OAuthTokenEndpointClient,
        coordinator: AuthAccountSessionCoordinator,
        clock: AuthClock,
        code: String,
        state: String,
    ): AuthAccountRegistrationResult {
        val manager = OAuthSessionManager(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            clock = clock,
            stateGenerator = OAuthStateGenerator { state },
        )
        val authorizationUrl = manager.beginAuthorization().toHttpUrl()
        val returnedState = requireNotNull(authorizationUrl.queryParameter("state"))
        val callback = "$AUTH02_REDIRECT_URI?code=${urlEncodeAuth02(code)}&state=${urlEncodeAuth02(returnedState)}"
            .toUri()
        assertTrue(OAuthCallbackProcessor(store, clock).process(callback) is AuthCallbackResult.Accepted)
        return coordinator.completePendingAccount()
    }
}

private class Auth02CountingPassthroughCipher : AuthCipher {
    val decryptCalls = AtomicInteger(0)

    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.copyOf()

    override fun decrypt(envelope: ByteArray): ByteArray {
        decryptCalls.incrementAndGet()
        return envelope.copyOf()
    }

    override fun deleteKey() = Unit
}

private class Auth02MutableClock(
    @Volatile var now: Long,
) : AuthClock {
    override fun epochMillis(): Long = now
}

private fun auth02Configuration(port: Int): OAuthProviderConfiguration = OAuthProviderConfiguration(
    provider = OAuthProvider.REDDIT,
    authorizationEndpoint = "https://example.test/api/v1/authorize.compact".toHttpUrl(),
    tokenEndpoint = "https://127.0.0.1:$port/api/v1/access_token".toHttpUrl(),
    revokeEndpoint = "https://127.0.0.1:$port/api/v1/revoke_token".toHttpUrl(),
    clientId = "auth02-test-client",
    clientSecret = "",
    redirectUri = AUTH02_REDIRECT_URI,
    scopes = setOf("identity", "read"),
    permanentAccess = true,
)

private fun auth02TokenClient(
    configuration: OAuthProviderConfiguration,
    clock: AuthClock,
): OAuthTokenEndpointClient = OAuthTokenEndpointClient(
    callFactory = SubshellOkHttpClientFactory.create(),
    configuration = configuration,
    userAgent = AUTH02_USER_AGENT,
    clock = clock,
)

private fun enqueueTokenAndIdentity(
    server: LocalJsonHttpsServer,
    tokenJson: String,
    identityJson: String,
) {
    server.enqueue(jsonFixture(tokenJson))
    server.enqueue(jsonFixture(identityJson))
}

private fun jsonFixture(body: String, delayMs: Long = 0L): FixtureResponse = FixtureResponse(
    status = "200 OK",
    body = body,
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
    delayMs = delayMs,
)

private fun urlEncodeAuth02(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private const val AUTH02_USER_AGENT =
    "android:${BuildConfig.APPLICATION_ID}:0.49.0 (by /u/subshell-test)"

private const val TOKEN_ALICE_JSON = """
{"access_token":"access-alice","refresh_token":"refresh-alice","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val TOKEN_BOB_JSON = """
{"access_token":"access-bob","refresh_token":"refresh-bob","token_type":"bearer","expires_in":120,"scope":"identity read"}
"""
private const val TOKEN_COLLISION_JSON = """
{"access_token":"access-root","refresh_token":"refresh-root","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val TOKEN_BOB_REFRESHED_JSON = """
{"access_token":"access-bob-rotated","refresh_token":"refresh-bob-rotated","token_type":"bearer","expires_in":3600,"scope":"identity read"}
"""
private const val IDENTITY_ALICE_JSON = """{"id":"t2_alice","name":"alice"}"""
private const val IDENTITY_BOB_JSON = """{"id":"t2_bob","name":"bob"}"""
private const val IDENTITY_ROOT_JSON = """{"id":"t2_root","name":"root"}"""
