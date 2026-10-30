package app.subshell.prototype.data.auth

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.CustomFeedCoordinator
import app.subshell.prototype.data.account.CustomFeedPostsFailureKind
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE32_CUSTOM_FEED_POSTS_ARE_STORED_V1 (#32, owner: "ta det som står
 * igjen"): a custom feed kept its posts in memory only, so after a restart
 * while Reddit refused access it was empty. Its pages are now stored like
 * Home's (account-scoped rows, no new table); a feed with nothing in memory
 * shows them while Reddit is asked, and keeps them when the read fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue32CustomFeedPostsStoredTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cacheStore = RoomSubshellCacheStore(database)
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
    private val feedPath = "/user/me/m/celebs/"

    @After
    fun close() {
        database.close()
        store.clearAll()
    }

    @Test
    fun aReadPageIsStoredForTheAccountAndFoundAfterARestart() = runBlocking {
        val coordinator = signedIn()
        val reader = reader(coordinator)

        val loaded = reader.loadPosts(accountId, feedPath)

        assertEquals(listOf("t3_abc"), (loaded as CustomFeedPostReadResult.Loaded).posts.map(Post::id))
        // A new reader over the same storage, as after a restart.
        val stored = reader(coordinator).storedPosts(accountId, feedPath)
        assertEquals(listOf("t3_abc"), stored?.posts?.map(Post::id))
        assertEquals("t3_abc", stored?.nextCursor)
        assertNull(reader(coordinator).storedPosts("reddit:someone-else", feedPath))
        assertNull(reader(coordinator).storedPosts(accountId, "/user/me/m/other/"))
    }

    @Test
    fun aFeedThatCannotBeReadShowsItsStoredPosts() {
        val refused = CLIENT_ACCESS_REFUSED_RESULT.toFailureDetails()
        val feeds = object : CustomFeedReader {
            override suspend fun load(accountStableId: String, preferredSession: StoredOAuthSession?) =
                error("not used")

            override suspend fun loadPosts(
                accountStableId: String,
                feedPath: String,
                after: String?,
                preferredSession: StoredOAuthSession?,
            ): CustomFeedPostReadResult =
                CustomFeedPostReadResult.Failed(CustomFeedPostsFailureKind.SESSION, details = refused)

            override suspend fun loadFeed(accountStableId: String, feedPath: String): CustomFeedSummary? = null

            override suspend fun storedPosts(accountStableId: String, feedPath: String) =
                CustomFeedPostReadResult.Loaded(listOf(post("t3_stored")), nextCursor = "t3_stored")
        }
        val coordinator = CustomFeedCoordinator(
            feedReader = feeds,
            feedCreator = null,
            writeCapability = null,
            writeClientMode = null,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        coordinator.loadCustomFeedPosts("acct", feedPath, forceRefresh = false)

        val state = coordinator.customFeedPostsUiState.value
        assertEquals(listOf("t3_stored"), state.posts.map(Post::id))
        assertEquals(CustomFeedPostsFailureKind.SESSION, state.failureKind)
        assertEquals(refused, state.failureDetails)
    }

    private fun reader(coordinator: AuthAccountSessionCoordinator) = ConfiguredCustomFeedReader(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(LISTING.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test/1.0",
        configuration = configuration,
        accountCoordinator = coordinator,
        ioDispatcher = Dispatchers.Unconfined,
        postStore = cacheStore,
    )

    private suspend fun signedIn(): AuthAccountSessionCoordinator {
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
                tokens = OAuthTokenSet(
                    "access",
                    "refresh",
                    "bearer",
                    RedditOAuth.coreReadScopes.joinToString(" "),
                    Long.MAX_VALUE,
                ),
                identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                clientMode = configuration.clientMode,
                clientIdFingerprint = configuration.clientIdFingerprint,
            ),
        )
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(
                callFactory = OkHttpClient.Builder().addInterceptor { error("no token request expected") }.build(),
                configuration = configuration,
                userAgent = "subshell-test/1.0",
            ),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return coordinator
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        const val LISTING =
            """{"kind":"Listing","data":{"after":"t3_abc","children":[{"kind":"t3","data":{"id":"abc","name":"t3_abc","author":"me","title":"A post","subreddit":"celebs","subreddit_name_prefixed":"r/celebs","created_utc":1700000000,"score":1,"num_comments":0,"permalink":"/r/celebs/comments/abc/a_post/"}}]}}"""

        fun post(id: String) = Post(
            id = id,
            community = "r/test",
            author = "me",
            age = "1h",
            title = id,
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )
    }
}
