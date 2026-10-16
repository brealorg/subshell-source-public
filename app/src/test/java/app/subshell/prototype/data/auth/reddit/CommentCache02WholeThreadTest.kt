package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthCipher
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
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
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMMENTCACHE02 (#137): every cached read used a 100-root page, so a thread
 * that seamless loading had grown past 100 roots came back cut (and without its
 * trailing "more" node); a reply then cached and showed the cut thread. Reads
 * now return every stored root.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentCache02WholeThreadTest {
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

    private val cacheKey get() = live03CommentCacheKey(accountId, "abc", RedditCommentSort.BEST)

    private fun comment(id: String, body: String, parent: String = "t3_abc", depth: Int = 0) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = depth,
        accent = 0xFF000000,
        parentId = parent,
    )

    private fun roots(count: Int): List<Comment> =
        (1..count).map { index -> comment("t1_r$index", "root $index") } +
            Comment(
                id = "more_t3_abc",
                author = "",
                age = "",
                body = "",
                score = 0,
                depth = 0,
                accent = 0xFF000000,
                parentId = "t3_abc",
                moreChildIds = listOf("t1_later"),
            )

    @Test
    fun aReplyToAThreadPastOneHundredRootsKeepsEveryRootAndTheMoreNode() = runBlocking {
        cache.cacheComments(cacheKey, roots(130))

        val result = loader.cacheSubmittedComment(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            parentFullname = "t3_abc",
            comment = comment("t1_new", "my reply"),
        )

        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals(132, comments.size)
        assertEquals("more_t3_abc", comments.last().id)
        assertEquals(132, cache.allComments(cacheKey).size)
    }

    @Test
    fun theCachedOpenReadsEveryRootAcrossPages() = runBlocking {
        cache.cacheComments(cacheKey, roots(1_205))

        val result = loader.cached(accountId, "abc", RedditCommentSort.BEST)

        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals(1_206, comments.size)
        assertEquals("t1_r1001", comments[1_000].id)
        assertEquals("more_t3_abc", comments.last().id)
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
