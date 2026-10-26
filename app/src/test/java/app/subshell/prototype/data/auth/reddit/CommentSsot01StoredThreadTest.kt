package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthAccountIdentityClient
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredAuthAccountRecord
import app.subshell.prototype.data.auth.StoredAuthAccountRegistry
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.live03CommentCacheKey
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTSSOT01 (#244) through the real loader and Room: a confirmed edit
 * reaches every stored sort of the thread (each sort is its own stored
 * thread), and whoever writes a stored thread, its observer sees it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentSsot01StoredThreadTest {
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
    private val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
    private val accountId = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))
    private val noNetwork = Call.Factory { error("no request in this test") }
    private lateinit var loader: RedditCommentLoader

    @Before
    fun signedIn() = runBlocking {
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
            tokenClient = OAuthTokenEndpointClient(callFactory = noNetwork, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        loader = RedditCommentLoader(
            callFactory = noNetwork,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun close() {
        cache.close()
    }

    private fun key(sort: RedditCommentSort) = live03CommentCacheKey(accountId, "abc", sort)

    private fun comment(id: String, body: String) = Comment(
        id = id,
        author = "me",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0L,
    )

    @Test
    fun anEditReachesEveryStoredSortOfTheThread() = runBlocking {
        cache.cacheComments(key(RedditCommentSort.BEST), listOf(comment("t1_a", "old"), comment("t1_b", "other")))
        cache.cacheComments(key(RedditCommentSort.TOP), listOf(comment("t1_b", "other"), comment("t1_a", "old")))

        loader.updateStoredThread(accountId, "abc") { stored ->
            stored.map { if (it.id == "t1_a") it.copy(body = "edited") else it }
        }

        assertEquals(listOf("edited", "other"), cache.allComments(key(RedditCommentSort.BEST)).map(Comment::body))
        assertEquals(listOf("other", "edited"), cache.allComments(key(RedditCommentSort.TOP)).map(Comment::body))
        // A sort never read stays unstored.
        assertTrue(cache.allComments(key(RedditCommentSort.NEW)).isEmpty())
    }

    @Test
    fun theObserverSeesAThreadStoredAfterItStarted() = runBlocking {
        cache.cacheComments(key(RedditCommentSort.BEST), listOf(comment("t1_a", "first")))
        val seen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                loader.observeStoredThread(accountId, "abc", RedditCommentSort.BEST).first { thread -> thread.size == 2 }
            }
        }
        Thread.sleep(200)

        cache.cacheComments(key(RedditCommentSort.BEST), listOf(comment("t1_a", "first"), comment("t1_b", "reply")))

        assertEquals(listOf("first", "reply"), seen.await().map(Comment::body))
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
