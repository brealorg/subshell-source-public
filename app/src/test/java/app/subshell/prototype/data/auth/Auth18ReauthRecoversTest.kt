package app.subshell.prototype.data.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH18 (OCB-470): a session read that failed at start (a transient decrypt
 * failure) marked the account "Sign in again" for the rest of the process,
 * although every later request worked. A working token now restores CONNECTED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Auth18ReauthRecoversTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val configuration = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "client-id-123",
        redirectUri = "subshell-auth-test://oauth/callback",
        scopes = RedditOAuth.coreReadScopes,
        permanentAccess = true,
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")

    @Test
    fun aWorkingTokenRestoresAnAccountMarkedForSignInAtStart() = runBlocking {
        val cipher = FlakyCipher()
        val store = EncryptedAuthSessionStore(application, cipher = cipher)
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
        store.saveSession(
            accountId,
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("access", "refresh", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )

        cipher.failSessionReads = true
        coordinator.hydratedSnapshot()
        assertEquals(AccountSessionState.REAUTHENTICATION_REQUIRED, state(coordinator))

        cipher.failSessionReads = false
        val result = coordinator.ensureAccessTokenForAccount(accountId, RedditOAuth.coreReadScopes)

        assertTrue("$result", result is AuthSessionResult.Authenticated)
        assertEquals(AccountSessionState.CONNECTED, state(coordinator))
    }

    private fun state(coordinator: AuthAccountSessionCoordinator) =
        coordinator.snapshot().accounts.single { it.stableId == accountId }.state

    /** Fails decrypting a session (not the registry) while [failSessionReads] is set. */
    private class FlakyCipher : AuthCipher {
        var failSessionReads = false
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray {
            if (failSessionReads && String(envelope).contains("access")) error("transient decrypt failure")
            return envelope
        }
        override fun deleteKey() = Unit
    }
}
