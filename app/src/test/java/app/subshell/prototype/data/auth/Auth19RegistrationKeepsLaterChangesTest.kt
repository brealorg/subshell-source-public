package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RepositoryQueryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH19 (OCB-468): a login captured the account registry before its feed sync
 * (seconds of network) and saved that copy afterwards, so a logout made in
 * between was undone and the removed account came back. The registry is now
 * read again when the login saves it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth19RegistrationKeepsLaterChangesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = EncryptedAuthSessionStore(application, cipher = PlainCipher)
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
    private val existingId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val newId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2new")

    @Test
    fun aLogoutDuringTheLoginFeedSyncIsNotUndone() = runBlocking {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = existingId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = existingId,
            ),
        )
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
        val endpoint = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(
                    """{"access_token":"access","token_type":"bearer","expires_in":3600,"scope":"read identity","refresh_token":"refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        lateinit var coordinator: AuthAccountSessionCoordinator
        coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = endpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient {
                AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2new", username = "newuser"))
            },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
            redditBootstrapper = AuthenticatedRedditBootstrapper {
                // The user logs out of the existing account while the new
                // account's first feed page loads.
                coordinator.logoutActive()
                AuthenticatedRedditBootstrapResult.Synchronized(RepositoryQueryKey("feed"), 0, 0)
            },
        )
        coordinator.hydratedSnapshot()

        val result = coordinator.completePendingAccount()

        assertTrue(result.toString(), result is AuthAccountRegistrationResult.Registered)
        assertEquals(listOf(newId), store.accountRegistry()!!.accounts.map { it.accountId })
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
