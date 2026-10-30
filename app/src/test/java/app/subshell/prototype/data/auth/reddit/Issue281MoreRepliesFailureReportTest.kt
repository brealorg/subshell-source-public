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
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import app.subshell.prototype.data.diagnostics.READ_EVENT_MORE_REPLIES_FAILED
import app.subshell.prototype.data.diagnostics.RedditReadDiagnostics
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE281_THREAD_READS_IN_THE_REPORT_V1 (#281: a mod comment's replies
 * showed "Couldn't load replies" and the report said nothing): a failed
 * replies load is recorded with the branch that failed it, Reddit's error
 * code and the shape of the node; a load that works records nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue281MoreRepliesFailureReportTest {
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
    private val database = Room.inMemoryDatabaseBuilder(application, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database, clock = MutableCacheClock(1_000L))

    private val networkRequests = mutableListOf<String>()
    private val recorded = mutableListOf<Pair<String, Map<String, Any?>>>()

    private val thread = listOf(
        Comment(
            id = "t1_mod",
            author = "automod",
            age = "1h",
            body = "Announcement",
            score = 1,
            depth = 0,
            accent = 0L,
            isModerator = true,
            children = listOf(
                Comment(
                    id = "more:t3_abc:r1",
                    author = "",
                    age = "",
                    body = "",
                    score = 0,
                    depth = 1,
                    accent = 0L,
                    parentId = "t1_mod",
                    moreChildIds = listOf("r1", "r2", "r3"),
                    moreCount = 3,
                ),
            ),
        ),
    )

    @Before
    fun record() {
        RedditReadDiagnostics.installForTest { kind, fields -> recorded += kind to fields.toMap() }
    }

    @After
    fun close() {
        RedditReadDiagnostics.installForTest(null)
        cache.close()
    }

    @Test
    fun aRepliesLoadRedditRefusedIsRecordedWithItsCodeAndNode() = runBlocking {
        val result = loader(
            """{"json":{"errors":[["RATELIMIT","you are doing that too much","ratelimit"]],"data":{"things":[]}}}""",
        ).loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t3_abc:r1",
            currentComments = thread,
        )

        assertTrue(result is AuthenticatedRedditCommentResult.Failed)
        val (kind, fields) = recorded.single { (kind, _) -> kind == READ_EVENT_MORE_REPLIES_FAILED }
        assertEquals(READ_EVENT_MORE_REPLIES_FAILED, kind)
        assertEquals("rejected_by_reddit", fields["branch"])
        assertEquals("ratelimit", fields["code"])
        assertEquals(3, fields["requested"])
        assertEquals(3, fields["of"])
        assertEquals(0, fields["returned"])
        assertEquals(true, fields["parent_mod"])
        assertEquals(0, fields["parent_depth"])
        assertEquals(false, fields["continue"])
        // The answer's rate-limit headers were seen too.
        assertEquals(0, RedditReadDiagnostics.rateLimitSnapshot()?.remaining)
    }

    @Test
    fun aRepliesLoadThatWorksRecordsNoFailure() = runBlocking {
        val result = loader(
            """{"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{"id":"r1","name":"t1_r1",
              "author":"bob","body":"reply","parent_id":"t1_mod","link_id":"t3_abc","created_utc":1.0,
              "depth":1,"score":1}}]}}}""",
        ).loadMoreFromSnapshot(
            accountStableId = accountId,
            postId = "abc",
            sort = RedditCommentSort.BEST,
            moreNodeId = "more:t3_abc:r1",
            currentComments = thread,
        )

        assertTrue("$result", result is AuthenticatedRedditCommentResult.Loaded)
        assertTrue("$recorded", recorded.none { (kind, _) -> kind == READ_EVENT_MORE_REPLIES_FAILED })
    }

    private suspend fun loader(body: String): RedditCommentLoader {
        val store = EncryptedAuthSessionStore(application, cipher = IdentityCipher())
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
        val noToken = OkHttpClient.Builder().addInterceptor { error("no token request in this test") }.build()
        val coordinator = AuthAccountSessionCoordinator(
            configuration = configuration,
            store = store,
            tokenClient = OAuthTokenEndpointClient(callFactory = noToken, configuration = configuration, userAgent = "subshell-test/1.0"),
            identityClient = OAuthAccountIdentityClient { error("no network in this test") },
            localFixtureUsernames = listOf("subshell"),
            storageDispatcher = Dispatchers.Unconfined,
        )
        coordinator.hydratedSnapshot()
        return RedditCommentLoader(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                networkRequests += chain.request().url.encodedPath
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .header("X-Ratelimit-Remaining", "0.0")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            configuration = configuration,
            cacheStore = cache,
            accountCoordinator = coordinator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private class IdentityCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }
}
