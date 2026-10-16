package app.subshell.prototype.data.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LAUNCHASYNC01 (OCB-474): an authorize tap ran its store work on the main
 * thread: the scope check decrypted the session and beginning the
 * authorization encrypted and wrote the pending record. Both run on IO now;
 * the tap starts them from a coroutine on Main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchAsync01StoreWorkOffMainTest {
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
    private val cipher = ThreadRecordingCipher()
    private val store = EncryptedAuthSessionStore(application, cipher = cipher).also { it.clearAll() }
    private val tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0")

    @Test
    fun beginningAnAuthorizationWritesThePendingRecordOffTheMainThread() = runBlocking<Unit> {
        val manager = OAuthSessionManager(configuration = configuration, store = store, tokenClient = tokenClient)
        cipher.reset()

        manager.beginAuthorization()

        assertNotNull(store.pendingAuthorization())
        assertTrue(cipher.encrypts > 0)
        assertFalse(cipher.encryptedOnMain)
    }

    @Test
    fun theInboxAndCustomFeedScopeChecksReadTheSessionOffTheMainThread() = runBlocking<Unit> {
        val coordinator = signedInCoordinator()
        cipher.reset()

        ConfiguredAuthInboxReadCapability(configuration, store, coordinator).hasScopeGrant(accountId)
        ConfiguredAuthCustomFeedWriteCapability(configuration, store, coordinator).authorizedSession(accountId)

        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)
    }

    private suspend fun signedInCoordinator(): AuthAccountSessionCoordinator {
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
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = tokenClient,
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        ).also { it.hydratedSnapshot() }
    }

    private class ThreadRecordingCipher : AuthCipher {
        @Volatile var encrypts = 0
        @Volatile var decrypts = 0
        @Volatile var encryptedOnMain = false
        @Volatile var decryptedOnMain = false

        fun reset() {
            encrypts = 0; decrypts = 0; encryptedOnMain = false; decryptedOnMain = false
        }

        override fun encrypt(plaintext: ByteArray): ByteArray {
            encrypts += 1
            if (Looper.myLooper() == Looper.getMainLooper()) encryptedOnMain = true
            return plaintext
        }

        override fun decrypt(envelope: ByteArray): ByteArray {
            decrypts += 1
            if (Looper.myLooper() == Looper.getMainLooper()) decryptedOnMain = true
            return envelope
        }

        override fun deleteKey() = Unit
    }
}
