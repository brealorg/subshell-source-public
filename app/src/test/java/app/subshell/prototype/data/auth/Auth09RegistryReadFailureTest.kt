package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
 * AUTH09 (#61, audit OCB-335/336): an account registry that exists but
 * cannot be read (Keystore/AES-GCM failure, IO error, newer format) is never
 * overwritten by the in-memory fallback — not at hydration, not by an account
 * switch, not by a registration — and the accounts come back once it reads.
 * A fresh install (no file) still starts with a stored fallback registry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth09RegistryReadFailureTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val cipher = SwitchableCipher()
    private val store = EncryptedAuthSessionStore(application, cipher = cipher)
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
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")

    @Before
    fun storeOneAccount() {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(
                    StoredAuthAccountRecord(
                        accountId = accountId,
                        provider = OAuthProvider.REDDIT,
                        providerAccountId = "t2me",
                        username = "me",
                        createdAtEpochMs = 1L,
                        lastUsedAtEpochMs = 1L,
                        clientMode = configuration.clientMode,
                    ),
                ),
                activeAccountId = accountId,
            ),
        )
    }

    private fun coordinator(
        callFactory: Call.Factory = NoNetwork,
        identityClient: OAuthAccountIdentityClient = OAuthAccountIdentityClient { error("no network in this test") },
    ) = AuthAccountSessionCoordinator(
        configuration = configuration,
        store = store,
        tokenClient = OAuthTokenEndpointClient(
            callFactory = callFactory,
            configuration = configuration,
            userAgent = "subshell-test/1.0",
        ),
        identityClient = identityClient,
        localFixtureUsernames = listOf("subshell"),
        storageDispatcher = Dispatchers.Unconfined,
    )

    /** A login whose callback and token exchange succeed, for u/newuser. */
    private fun loginCoordinator(): AuthAccountSessionCoordinator {
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
        val tokenEndpoint = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(
                    """{"access_token":"access","token_type":"bearer","expires_in":3600,"scope":"read identity","refresh_token":"refresh"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                .build()
        }.build()
        return coordinator(
            callFactory = tokenEndpoint,
            identityClient = OAuthAccountIdentityClient {
                AuthIdentityResult.Success(AuthAccountIdentity(providerAccountId = "t2new", username = "newuser"))
            },
        )
    }

    private fun storedAccountIds(): List<String> {
        cipher.failing = false
        cipher.failRegistry = false
        return store.accountRegistry()!!.accounts.map { it.accountId }
    }

    @Test
    fun anUnreadableRegistryIsNotOverwrittenAtStartUp() = runBlocking {
        cipher.failing = true
        val directory = coordinator()

        directory.hydratedSnapshot()

        assertEquals(listOf(accountId), storedAccountIds())
    }

    @Test
    fun anAccountSwitchWhileUnreadableDoesNotWriteTheFallback() = runBlocking {
        cipher.failing = true
        val directory = coordinator()
        val fallback = directory.hydratedSnapshot()

        directory.selectAccount(fallback.accounts.first().stableId)
        directory.awaitDirectoryPersistence()

        assertEquals(listOf(accountId), storedAccountIds())
    }

    @Test
    fun theAccountsComeBackOnceTheRegistryReadsAgain() = runBlocking {
        cipher.failing = true
        val directory = coordinator()
        directory.hydratedSnapshot()

        cipher.failing = false
        val recovered = directory.hydratedSnapshot()

        assertTrue(recovered.accounts.any { it.stableId == accountId })
    }

    @Test
    fun aLoginWhileTheRegistryIsUnreadableFailsWithStorageAndKeepsTheStoredAccounts() = runBlocking {
        cipher.failRegistry = true
        val directory = loginCoordinator()
        directory.hydratedSnapshot()

        val result = directory.completePendingAccount()

        assertEquals(
            AuthAccountRegistrationFailureKind.STORAGE,
            (result as AuthAccountRegistrationResult.Failed).kind,
        )
        assertEquals(listOf(accountId), storedAccountIds())
    }

    @Test
    fun aLoginAfterTheRegistryReadsAgainKeepsBothAccounts() = runBlocking {
        val directory = loginCoordinator()

        val result = directory.completePendingAccount()

        assertTrue(result.toString(), result is AuthAccountRegistrationResult.Registered)
        assertEquals(
            setOf(accountId, authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2new")),
            storedAccountIds().toSet(),
        )
    }

    @Test
    fun aFreshInstallStillStoresTheFallbackRegistry() = runBlocking {
        store.clearAll()
        coordinator().hydratedSnapshot()

        assertNotNull(store.accountRegistry())
    }

    private object NoNetwork : Call.Factory {
        override fun newCall(request: Request): Call = error("no network in this test")
    }

    private class SwitchableCipher : AuthCipher {
        @Volatile var failing = false
        /** Fails only the registry file, so callbacks and sessions still read. */
        @Volatile var failRegistry = false
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            if (failing) throw java.security.GeneralSecurityException("keystore unavailable")
            if (failRegistry && String(envelope, Charsets.UTF_8).contains("\"activeAccountId\"")) {
                throw java.security.GeneralSecurityException("registry unreadable")
            }
            return envelope
        }
        override fun deleteKey() = Unit
    }
}
