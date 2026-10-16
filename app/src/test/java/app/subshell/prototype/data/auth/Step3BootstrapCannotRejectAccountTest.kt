package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth06c, auth07a1): the first feed sync after a sign-in is a
 * convenience, not a condition. When it fails — by an exception or by a
 * failed result — the account is still registered, active and holding its
 * session. A cancellation stops the sign-in instead: no account is
 * registered and none of its session is kept. The real coordinator and
 * EncryptedAuthSessionStore (pass-through cipher), with a token endpoint and
 * identity answered locally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3BootstrapCannotRejectAccountTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher).also { it.clearAll() }
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = setOf("read", "identity"),
        permanentAccess = true,
    )
    private val newId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2new")

    private fun coordinator(bootstrapper: AuthenticatedRedditBootstrapper): AuthAccountSessionCoordinator {
        store.saveCallback(
            StoredOAuthCallback(
                provider = OAuthProvider.REDDIT,
                state = "state",
                code = "code",
                error = null,
                receivedAtEpochMs = 1L,
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
                scopeFingerprint = configuration.scopeFingerprint,
            ),
        )
        val endpoint = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(
                    """{"access_token":"access","token_type":"bearer","expires_in":3600,"scope":"read identity","refresh_token":"refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = endpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient {
                AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2new", username = "newuser"))
            },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
            redditBootstrapper = bootstrapper,
        )
    }

    private fun assertRegisteredAndActive(result: AuthAccountRegistrationResult) {
        assertTrue(result.toString(), result is AuthAccountRegistrationResult.Registered)
        val registry = requireNotNull(store.accountRegistry())
        assertEquals(listOf(newId), registry.accounts.map { it.accountId })
        assertEquals(newId, registry.activeAccountId)
        assertNotNull("the account keeps its session", store.session(newId))
    }

    @Test
    fun aFirstSyncThatThrowsStillRegistersTheAccount() = runBlocking {
        val coordinator = coordinator { error("the first page could not be loaded") }
        coordinator.hydratedSnapshot()

        assertRegisteredAndActive(coordinator.completePendingAccount())
    }

    @Test
    fun aFirstSyncThatFailsStillRegistersTheAccount() = runBlocking {
        val coordinator = coordinator {
            AuthenticatedRedditBootstrapResult.Failed(AuthenticatedRedditBootstrapFailureKind.TRANSPORT, retryable = true)
        }
        coordinator.hydratedSnapshot()

        assertRegisteredAndActive(coordinator.completePendingAccount())
    }

    @Test
    fun aCancelledFirstSyncStopsTheSignIn() {
        val coordinator = coordinator { throw CancellationException("the sign-in was left") }
        runBlocking { coordinator.hydratedSnapshot() }

        val thrown = runCatching { runBlocking { coordinator.completePendingAccount() } }.exceptionOrNull()

        assertTrue("$thrown", thrown is CancellationException)
        assertTrue(
            "no account is registered",
            store.accountRegistry()?.accounts.orEmpty().none { it.accountId == newId },
        )
        assertNull("no session is kept for it", store.session(newId))
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
