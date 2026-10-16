package app.subshell.prototype.data.auth

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.auth.reddit.RedditAccountIdentityClient
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.auth.reddit.RedditBootstrapper
import app.subshell.prototype.data.auth.reddit.RedditFeedUpdater
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Auth06cLiveReadOnlyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = EncryptedAuthSessionStore(context)
    private val databaseName = "subshell-auth06c-runtime.db"

    @After
    fun cleanup() {
        runCatching { store.clearAll(deleteKey = true) }
        runCatching { context.deleteDatabase(databaseName) }
    }

    @Test
    fun localhostOAuthIdentityAndBestFeedAreRegisteredAtomicallyInRoom() {
        runBlocking {
            store.clearAll(deleteKey = true)
            context.deleteDatabase(databaseName)
            val clientId = "auth06c_fixture_client"
            store.saveOAuthClientSelection(RedditOAuth.redReaderCompatibilitySelection(clientId))

            val server = LocalJsonHttpsServer(instrumentation.context)
            server.start()
            server.enqueue(auth06cJsonFixture(TOKEN_JSON))
            server.enqueue(auth06cJsonFixture(IDENTITY_JSON))
            server.enqueue(auth06cJsonFixture(BEST_LISTING_JSON))
            server.enqueue(auth06cJsonFixture(REFRESH_LISTING_JSON))
            server.enqueue(auth06cJsonFixture(APPEND_LISTING_JSON))

            val configuration = OAuthProviderConfiguration(
                provider = OAuthProvider.REDDIT,
                authorizationEndpoint = "https://example.test/api/v1/authorize.compact".toHttpUrl(),
                tokenEndpoint = "https://127.0.0.1:${server.port}/api/v1/access_token".toHttpUrl(),
                revokeEndpoint = "https://127.0.0.1:${server.port}/api/v1/revoke_token".toHttpUrl(),
                clientId = clientId,
                clientSecret = "",
                redirectUri = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
                scopes = RedditOAuth.coreReadScopes,
                permanentAccess = true,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            )
            val callFactory = SubshellOkHttpClientFactory.create()
            val tokenClient = OAuthTokenEndpointClient(
                callFactory = callFactory,
                configuration = configuration,
                userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                clock = FixedAuth06cClock,
            )
            val sessionManager = OAuthSessionManager(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient,
                clock = FixedAuth06cClock,
                stateGenerator = OAuthStateGenerator { AUTH06C_STATE },
            )
            val cacheStore = RoomSubshellCacheStore(
                database = SubshellCacheDatabase.open(context, databaseName),
            )
            val coordinator = AuthAccountSessionCoordinator(
                configuration = configuration,
                store = store,
                tokenClient = tokenClient,
                identityClient = RedditAccountIdentityClient(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                    userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                ),
                redditBootstrapper = RedditBootstrapper(
                    callFactory = callFactory,
                    endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                    userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                    configuration = configuration,
                    cacheStore = cacheStore,
                ),
                localFixtureUsernames = listOf("root", "packetmoss"),
                clock = FixedAuth06cClock,
            )

            val authorizationUrl = sessionManager.beginAuthorization().toHttpUrl()
            assertEquals(
                "identity mysubreddits read",
                authorizationUrl.queryParameter("scope"),
            )
            assertEquals("permanent", authorizationUrl.queryParameter("duration"))
            assertEquals(clientId, authorizationUrl.queryParameter("client_id"))

            val callback = Uri.parse(
                RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI +
                    "?code=${encodeAuth06c("live-code")}&state=$AUTH06C_STATE",
            )
            assertTrue(
                OAuthCallbackProcessor(store, FixedAuth06cClock).process(callback) is
                    AuthCallbackResult.Accepted,
            )

            val stages = mutableListOf<AuthAccountRegistrationStage>()
            val registered = coordinator.completePendingAccount(stages::add)
            assertTrue(registered is AuthAccountRegistrationResult.Registered)
            registered as AuthAccountRegistrationResult.Registered
            assertEquals(
                listOf(
                    AuthAccountRegistrationStage.TOKEN_EXCHANGE,
                    AuthAccountRegistrationStage.IDENTITY_RESOLUTION,
                    AuthAccountRegistrationStage.REDDIT_FEED_SYNC,
                ),
                stages,
            )
            assertEquals("auth06c-user", registered.account.username)

            val liveHomeKey = auth06cHomeBestQueryKey(registered.account.stableId)
            val snapshot = cacheStore.readPostFeedSnapshot(liveHomeKey)
            assertNotNull(snapshot)
            assertEquals(2, snapshot?.posts?.size)
            assertEquals("t3_auth06c_one", snapshot?.posts?.first()?.id)
            assertEquals(
                "https://i.redd.it/auth06c.png",
                snapshot?.posts?.get(1)?.remoteImageUri,
            )

            val feedUpdater = RedditFeedUpdater(
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                configuration = configuration,
                cacheStore = cacheStore,
                accountCoordinator = coordinator,
            )
            val refreshed = feedUpdater.synchronize(
                accountStableId = registered.account.stableId,
                operation = RepositoryLoadOperation.REFRESH,
                target = AuthenticatedRedditFeedTarget.HOME_BEST,
            )
            assertTrue(refreshed is AuthenticatedRedditFeedUpdateResult.Applied)
            assertEquals(
                2,
                (refreshed as AuthenticatedRedditFeedUpdateResult.Applied).storedItemCount,
            )
            val appended = feedUpdater.synchronize(
                accountStableId = registered.account.stableId,
                operation = RepositoryLoadOperation.APPEND,
                target = AuthenticatedRedditFeedTarget.HOME_BEST,
            )
            assertTrue(appended is AuthenticatedRedditFeedUpdateResult.Applied)
            assertEquals(
                3,
                (appended as AuthenticatedRedditFeedUpdateResult.Applied).storedItemCount,
            )
            assertEquals(
                listOf(
                    "t3_auth06c_refresh_one",
                    "t3_auth06c_refresh_two",
                    "t3_auth06c_append_one",
                ),
                cacheStore.readPostFeedSnapshot(liveHomeKey)?.posts?.map { it.id },
            )

            assertTrue(server.awaitRequestCount(expected = 5, timeoutMs = 5_000L))
            val requests = server.requestsSnapshot()
            assertEquals(5, requests.size)
            assertEquals(Credentials.basic(clientId, ""), requests[0].headers["authorization"])
            assertEquals("POST /api/v1/access_token HTTP/1.1", requests[0].requestLine)
            assertEquals("GET /api/v1/me HTTP/1.1", requests[1].requestLine)
            assertEquals(
                "GET /best?raw_json=1&limit=25 HTTP/1.1",
                requests[2].requestLine,
            )
            assertEquals(
                "GET /best?raw_json=1&limit=25 HTTP/1.1",
                requests[3].requestLine,
            )
            assertEquals(
                "GET /best?raw_json=1&limit=25&after=t3_auth06c_refresh_after HTTP/1.1",
                requests[4].requestLine,
            )
            requests.forEach { request ->
                assertEquals(
                    RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
                    request.headers["user-agent"],
                )
            }
            assertEquals("Bearer auth06c-access", requests[1].headers["authorization"])
            assertEquals("Bearer auth06c-access", requests[2].headers["authorization"])
            assertEquals("Bearer auth06c-access", requests[3].headers["authorization"])
            assertEquals("Bearer auth06c-access", requests[4].headers["authorization"])

            cacheStore.close()
            val reopenedStore = EncryptedAuthSessionStore(context)
            val reopenedRegistry = reopenedStore.accountRegistry()
            assertEquals(1, reopenedRegistry?.accounts?.size)
            assertEquals(registered.account.stableId, reopenedRegistry?.activeAccountId)
            assertNotNull(reopenedStore.session(registered.account.stableId))
            val reopenedCache = RoomSubshellCacheStore(
                database = SubshellCacheDatabase.open(context, databaseName),
            )
            assertEquals(
                3,
                reopenedCache.readPostFeedSnapshot(liveHomeKey)
                    ?.posts
                    ?.size,
            )
            reopenedCache.close()

            server.close()
            server.assertHealthy()
            Log.i(
                AUTH06C_LOG_TAG,
                "READ_ONLY_SCOPES_EXACT=true TOKEN_EXCHANGE=true IDENTITY_RESOLVED=true " +
                    "BEST_LIMIT_10=true ROOM_ITEMS=3 EXPLICIT_REFRESH=true EXPLICIT_APPEND=true " +
                    "ACCOUNT_REGISTERED=true PROCESS_REOPEN=true USER_AGENT_EXACT=true HTTPS_REQUESTS=5",
            )
            Log.i(AUTH06C_LOG_TAG, "RESULT=SUBSHELL_AUTH06C_LOCALHOST_READONLY_RUNTIME_PASS")
        }
    }


}

private object FixedAuth06cClock : AuthClock {
    override fun epochMillis(): Long = 1_800_000_000_000L
}

private fun auth06cJsonFixture(body: String): FixtureResponse = FixtureResponse(
    status = "200 OK",
    body = body,
    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
)

private fun encodeAuth06c(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private const val AUTH06C_STATE =
    "auth06c-state-value-with-at-least-thirty-two-characters"
private const val AUTH06C_LOG_TAG = "subshell-auth06c"
private const val AUTH06C_LIVE_LOG_TAG = "subshell-auth06c-live"
private const val TOKEN_JSON =
    """{"access_token":"auth06c-access","refresh_token":"auth06c-refresh","token_type":"bearer","expires_in":3600,"scope":"identity mysubreddits read"}"""
private const val IDENTITY_JSON = """{"id":"t2_auth06c","name":"auth06c-user"}"""
private const val BEST_LISTING_JSON = """
{
  "kind":"Listing",
  "data":{
    "after":"t3_auth06c_after",
    "children":[
      {"kind":"t3","data":{"id":"auth06c_one","name":"t3_auth06c_one","subreddit":"android","author":"fixture","created_utc":1799999900,"title":"AUTH06C first","selftext":"read only","score":12,"num_comments":3}},
      {"kind":"t3","data":{"id":"auth06c_two","name":"t3_auth06c_two","subreddit":"kotlin","author":"fixture2","created_utc":1799999800,"title":"AUTH06C second","post_hint":"image","domain":"i.redd.it","url_overridden_by_dest":"https://i.redd.it/auth06c.png","score":8,"num_comments":2}}
    ]
  }
}
"""
private const val REFRESH_LISTING_JSON = """
{
  "kind":"Listing",
  "data":{
    "after":"t3_auth06c_refresh_after",
    "children":[
      {"kind":"t3","data":{"id":"auth06c_refresh_one","name":"t3_auth06c_refresh_one","subreddit":"android","author":"fixture","created_utc":1799999700,"title":"LIVE02 refresh first","selftext":"read only","score":14,"num_comments":4}},
      {"kind":"t3","data":{"id":"auth06c_refresh_two","name":"t3_auth06c_refresh_two","subreddit":"kotlin","author":"fixture2","created_utc":1799999600,"title":"LIVE02 refresh second","selftext":"read only","score":9,"num_comments":1}}
    ]
  }
}
"""
private const val APPEND_LISTING_JSON = """
{
  "kind":"Listing",
  "data":{
    "after":null,
    "children":[
      {"kind":"t3","data":{"id":"auth06c_append_one","name":"t3_auth06c_append_one","subreddit":"androiddev","author":"fixture3","created_utc":1799999500,"title":"LIVE02 append","selftext":"read only","score":6,"num_comments":2}}
    ]
  }
}
"""
