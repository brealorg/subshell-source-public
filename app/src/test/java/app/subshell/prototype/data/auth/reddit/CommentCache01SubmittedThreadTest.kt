package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.RepositoryPageRequest
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
 * COMMENTCACHE01 (#138): a posted comment was merged into the Room-cached
 * thread and that replaced what the reader saw. The cache lags the screen
 * (edits and deletes change only the screen, a focused load is not cached),
 * so a reply put an edited comment's old text back, or was lost when its
 * parent was only on screen. The reply now goes into the displayed thread,
 * and that result is cached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentCache01SubmittedThreadTest {
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

    private fun cached(): List<Comment> =
        cache.comments(cacheKey, RepositoryPageRequest(limit = 100))

    @Test
    fun aReplyKeepsAnEditTheCacheNeverSaw() = runBlocking {
        cache.cacheComments(cacheKey, listOf(comment("t1_x", "old text")))
        val displayed = listOf(comment("t1_x", "edited text"))

        val result = loader.cacheSubmittedCommentOnto(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            parentFullname = "t3_abc",
            comment = comment("t1_new", "my reply"),
            currentComments = displayed,
        )

        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals(listOf("my reply", "edited text"), comments.map { it.body })
        assertEquals(listOf("my reply", "edited text"), cached().map { it.body })
    }

    @Test
    fun aReplyToACommentOnlyOnScreenIsShown() = runBlocking {
        // A focused load showed t1_focus; it was never cached.
        val displayed = listOf(comment("t1_focus", "focused"))

        val result = loader.cacheSubmittedCommentOnto(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            parentFullname = "t1_focus",
            comment = comment("t1_new", "my reply", parent = "t1_focus", depth = 1),
            currentComments = displayed,
        )

        val comments = (result as AuthenticatedRedditCommentResult.Loaded).comments
        assertEquals(listOf("t1_focus"), comments.map { it.id })
        assertEquals(listOf("t1_new"), comments.single().children.map { it.id })
    }

    @Test
    fun withoutADisplayedThreadTheCacheIsTheBase() = runBlocking {
        cache.cacheComments(cacheKey, listOf(comment("t1_x", "cached")))

        val result = loader.cacheSubmittedComment(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            parentFullname = "t3_abc",
            comment = comment("t1_new", "my reply"),
        )

        assertEquals(
            listOf("my reply", "cached"),
            (result as AuthenticatedRedditCommentResult.Loaded).comments.map { it.body },
        )
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
