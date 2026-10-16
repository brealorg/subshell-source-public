package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionState
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
 * STEP3 (auth08a2): Reddit's 401 for a session that cannot refresh ends only
 * that session — the account then needs a new sign-in and the directory says
 * so (its revision moves, so the app reads it again); a 401 for a token no
 * account holds changes nothing; and signing an account in again must come
 * back as the same Reddit account, otherwise nothing is stored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3SessionRecoveryTest {
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
    private val me = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val other = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2other")

    private fun record(accountId: String, providerAccountId: String, username: String) = StoredAuthAccountRecord(
        accountId = accountId,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerAccountId,
        username = username,
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = configuration.clientMode,
    )

    private fun session(token: String, refresh: String?, providerAccountId: String, username: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, refresh, "bearer", "read identity", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = providerAccountId, username = username),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    private val tokenEndpoint = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(
                """{"access_token":"access-new","token_type":"bearer","expires_in":3600,"scope":"read identity","refresh_token":"refresh-new"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
            .build()
    }.build()

    private suspend fun coordinator(identity: AuthAccountIdentity? = null): AuthAccountSessionCoordinator {
        store.clearAll()
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(
                accounts = listOf(record(me, "t2me", "me"), record(other, "t2other", "other")),
                activeAccountId = me,
            ),
        )
        store.saveSession(me, session("access-me", refresh = null, providerAccountId = "t2me", username = "me"))
        store.saveSession(other, session("access-other", refresh = "refresh-other", providerAccountId = "t2other", username = "other"))
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = tokenEndpoint, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient {
                identity?.let { AuthIdentityResult.Success(it) } ?: error("no identity in this test")
            },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return coordinator
    }

    private fun AuthAccountSessionCoordinator.stateOf(accountId: String) =
        snapshot().accounts.single { it.stableId == accountId }.state

    @Test
    fun a401ForASessionThatCannotRefreshEndsOnlyThatSession() = runBlocking {
        val coordinator = coordinator()
        val revisionBefore = coordinator.revision.value

        coordinator.sessionBoundBearerTokenProvider("access-me").onUnauthorized()

        assertNull(store.session(me))
        assertEquals(AccountSessionState.REAUTHENTICATION_REQUIRED, coordinator.stateOf(me))
        assertTrue(coordinator.revision.value > revisionBefore)
        assertEquals("access-other", store.session(other)?.tokens?.accessToken)
        assertEquals(AccountSessionState.CONNECTED, coordinator.stateOf(other))
    }

    @Test
    fun a401ForATokenNoAccountHoldsChangesNothing() = runBlocking {
        val coordinator = coordinator()
        val revisionBefore = coordinator.revision.value

        coordinator.sessionBoundBearerTokenProvider("access-stranger").onUnauthorized()

        assertNotNull(store.session(me))
        assertNotNull(store.session(other))
        assertEquals(revisionBefore, coordinator.revision.value)
        assertEquals(AccountSessionState.CONNECTED, coordinator.stateOf(me))
    }

    @Test
    fun signingInAgainAsAnotherRedditAccountStoresNothing() = runBlocking {
        val coordinator = coordinator(identity = AuthAccountIdentity(providerAccountId = "t2stranger", username = "stranger"))
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

        val result = coordinator.completePendingAccount(authorizationSessionManager = null, expectedAccountStableId = me)

        assertEquals(
            AuthAccountRegistrationResult.Failed(AuthAccountRegistrationFailureKind.REAUTHENTICATION_ACCOUNT_MISMATCH),
            result,
        )
        assertNull(store.session())
        assertEquals("access-me", store.session(me)?.tokens?.accessToken)
        assertEquals(listOf(me, other), store.accountRegistry()!!.accounts.map { it.accountId })
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
