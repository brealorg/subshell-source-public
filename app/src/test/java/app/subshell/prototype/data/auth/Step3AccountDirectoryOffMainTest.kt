package app.subshell.prototype.data.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (search01c1a1): the account directory never decrypts or writes the
 * stored sessions on the main thread — creating it and reading its snapshot
 * touch nothing; loading it, switching account, reading a token and logging
 * out do their storage work elsewhere; a switch shows at once and is stored
 * afterwards; and a token asked for before the directory loaded loads it
 * first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3AccountDirectoryOffMainTest {
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
    private val a = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2a")
    private val b = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2b")
    private val cipher = ThreadRecordingCipher()
    private val store = EncryptedAuthSessionStore(application, cipher = cipher).also { it.clearAll() }

    private fun record(id: String, providerId: String, name: String) = StoredAuthAccountRecord(
        accountId = id,
        provider = OAuthProvider.REDDIT,
        providerAccountId = providerId,
        username = name,
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = 1L,
        clientMode = configuration.clientMode,
    )

    private fun session(token: String, providerId: String, name: String) = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(token, "refresh-$token", "bearer", RedditOAuth.coreReadScopes.joinToString(" "), Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = providerId, username = name),
        clientMode = configuration.clientMode,
        clientIdFingerprint = configuration.clientIdFingerprint,
    )

    /** A stored directory with A active and B signed in; storage runs on its default dispatcher. */
    private fun coldCoordinator(): AuthAccountSessionCoordinator {
        store.saveAccountRegistry(
            StoredAuthAccountRegistry(accounts = listOf(record(a, "t2a", "anna"), record(b, "t2b", "bert")), activeAccountId = a),
        )
        store.saveSession(a, session("token-a", "t2a", "anna"))
        store.saveSession(b, session("token-b", "t2b", "bert"))
        cipher.reset()
        return AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = OkHttpClient(), configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
        )
    }

    @Test
    fun creatingTheDirectoryAndReadingItsSnapshotTouchNoStorage() {
        assertTrue(Looper.myLooper() == Looper.getMainLooper())
        val coordinator = coldCoordinator()

        coordinator.snapshot()

        assertEquals(0, cipher.decrypts)
        assertEquals(0, cipher.encrypts)
    }

    @Test
    fun loadingTheDirectoryReadsStorageOffTheMainThread() = runBlocking {
        val coordinator = coldCoordinator()

        val loaded = coordinator.hydrateDirectory()

        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)
        assertTrue(loaded.accounts.map { it.stableId }.containsAll(listOf(a, b)))
        assertEquals(a, coordinator.snapshot().activeAccountId)
    }

    @Test
    fun aSwitchShowsAtOnceAndIsStoredAfterwardsOffTheMainThread() = runBlocking {
        val coordinator = coldCoordinator()
        coordinator.hydrateDirectory()
        cipher.reset()

        val switched = coordinator.selectAccount(b)

        assertEquals(b, switched.activeAccountId)
        coordinator.awaitDirectoryPersistence()
        assertEquals(b, store.accountRegistry()?.activeAccountId)
        assertFalse(cipher.encryptedOnMain)
    }

    @Test
    fun aTokenAskedForBeforeTheDirectoryLoadedLoadsItFirst() = runBlocking {
        val coordinator = coldCoordinator()

        val result = coordinator.ensureActiveAccessToken()

        assertEquals("token-a", (result as AuthSessionResult.Authenticated).session.tokens.accessToken)
        assertFalse(cipher.decryptedOnMain)
    }

    @Test
    fun readingATokenAndLoggingOutDoTheirStorageWorkOffTheMainThread() = runBlocking {
        val coordinator = coldCoordinator()
        coordinator.hydrateDirectory()
        cipher.reset()

        coordinator.ensureAccessTokenForAccount(b, RedditOAuth.coreReadScopes)
        coordinator.logoutActive()

        assertTrue(cipher.decrypts > 0)
        assertFalse(cipher.decryptedOnMain)
        assertFalse(cipher.encryptedOnMain)
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
