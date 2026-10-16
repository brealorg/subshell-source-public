package app.subshell.prototype.data.sync

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.cache.CacheWriteTransactionObserver
import app.subshell.prototype.data.cache.MutableCacheClock
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import app.subshell.prototype.data.network.jsonResponse
import app.subshell.prototype.data.network.reddit.RedditListingNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.data.network.reddit.RedditPostFeedNetworkSource
import app.subshell.prototype.model.Post
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val NET02_LOG_TAG = "subshell-net02"

@RunWith(AndroidJUnit4::class)
class Net02PostFeedSyncInstrumentedTest {
    @Test
    fun localhostPullSyncCommitsOnlyThroughRoomAndRollsBackAtomically() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            context.deleteDatabase(RUNTIME_DATABASE)

            val transactionObserver = FailingCacheWriteTransactionObserver()
            val cacheStore = RoomSubshellCacheStore(
                database = SubshellCacheDatabase.open(
                    context = context,
                    databaseName = RUNTIME_DATABASE,
                ),
                clock = MutableCacheClock(CACHE_CLOCK_EPOCH_MS),
                freshForMs = 1_000_000L,
                transactionObserver = transactionObserver,
            )
            val server = LocalJsonHttpsServer(instrumentation.context).also(
                LocalJsonHttpsServer::start,
            )
            val httpClient = SubshellHttpClient(
                callFactory = SubshellOkHttpClientFactory.create(),
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "android:${BuildConfig.APPLICATION_ID}:0.47.0 (by /u/subshell-test)",
                clock = NetworkClock { NETWORK_RECEIVED_EPOCH_MS },
            )
            val synchronizer = OfflineFirstPostFeedSynchronizer(
                networkSource = RedditPostFeedNetworkSource(
                    dataSource = RedditListingNetworkDataSource(
                        httpClient = httpClient,
                        clock = { 10_000L },
                    ),
                    community = "android",
                    sort = RedditListingSort.HOT,
                ),
                cacheStore = cacheStore,
            )

            server.enqueue(jsonResponse(PAGE_ONE_JSON))
            val initial = synchronizer.synchronize(
                RepositoryPageRequest(
                    limit = 2,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.INITIAL,
                ),
            )
            assertTrue(initial is PostFeedSyncResult.Applied)
            initial as PostFeedSyncResult.Applied
            assertEquals(2, initial.networkItemCount)
            assertEquals(2, initial.storedItemCount)
            assertEquals("t3_net02_page_2", initial.remoteNextCursor)
            assertFalse(initial.terminal)
            assertEquals(NETWORK_RECEIVED_EPOCH_MS, initial.receivedAtEpochMs)

            val initialSnapshot = requireNotNull(
                cacheStore.readPostFeedSnapshot(QUERY_KEY),
            )
            assertEquals(
                listOf("t3_net02_a", "t3_net02_b"),
                initialSnapshot.posts.map(Post::id),
            )
            assertEquals("t3_net02_page_2", initialSnapshot.remoteNextCursor)
            assertEquals(NETWORK_RECEIVED_EPOCH_MS, initialSnapshot.lastSuccessfulRefreshEpochMs)
            assertFalse(initialSnapshot.stale)
            assertNull(
                cacheStore.readPostPage(
                    RepositoryPageRequest(
                        cursor = "t3_net02_page_2",
                        limit = 2,
                        queryKey = QUERY_KEY,
                        operation = RepositoryLoadOperation.APPEND,
                    ),
                ),
            )

            val requestsBeforeMismatch = server.requestsSnapshot().size
            val mismatch = synchronizer.synchronize(
                RepositoryPageRequest(
                    cursor = "t3_stale_cursor",
                    limit = 2,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.APPEND,
                ),
            )
            assertEquals(
                PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_MISMATCH,
                (mismatch as PostFeedSyncResult.PreconditionFailure).kind,
            )
            assertEquals(requestsBeforeMismatch, server.requestsSnapshot().size)
            assertEquals(initialSnapshot, cacheStore.readPostFeedSnapshot(QUERY_KEY))

            server.enqueue(jsonResponse(PAGE_TWO_JSON))
            val append = synchronizer.synchronize(
                RepositoryPageRequest(
                    cursor = "t3_net02_page_2",
                    limit = 2,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.APPEND,
                ),
            )
            assertTrue(append is PostFeedSyncResult.Applied)
            append as PostFeedSyncResult.Applied
            assertEquals(2, append.networkItemCount)
            assertEquals(3, append.storedItemCount)
            assertTrue(append.terminal)

            val appendedSnapshot = requireNotNull(
                cacheStore.readPostFeedSnapshot(QUERY_KEY),
            )
            assertEquals(
                listOf("t3_net02_a", "t3_net02_b", "t3_net02_c"),
                appendedSnapshot.posts.map(Post::id),
            )
            assertEquals(
                "NET02 B updated by append",
                appendedSnapshot.posts.single { post -> post.id == "t3_net02_b" }.title,
            )

            server.enqueue(jsonResponse(REFRESH_JSON))
            val refresh = synchronizer.synchronize(
                RepositoryPageRequest(
                    limit = 2,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.REFRESH,
                ),
            )
            assertTrue(refresh is PostFeedSyncResult.Applied)
            refresh as PostFeedSyncResult.Applied
            assertEquals(1, refresh.generation)
            assertEquals(2, refresh.storedItemCount)
            assertTrue(refresh.terminal)

            val refreshedSnapshot = requireNotNull(
                cacheStore.readPostFeedSnapshot(QUERY_KEY),
            )
            assertEquals(
                listOf("t3_net02_a", "t3_net02_d"),
                refreshedSnapshot.posts.map(Post::id),
            )
            assertEquals("NET02 A refreshed", refreshedSnapshot.posts.first().title)
            assertEquals(2, cacheStore.diagnostics().postCount)

            server.enqueue(
                FixtureResponse(
                    status = "503 Service Unavailable",
                    body = "{}",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Retry-After" to "4",
                    ),
                ),
            )
            val httpFailure = synchronizer.synchronize(
                RepositoryPageRequest(
                    limit = 2,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.REFRESH,
                ),
            )
            assertTrue(httpFailure is PostFeedSyncResult.HttpFailure)
            httpFailure as PostFeedSyncResult.HttpFailure
            assertEquals(503, httpFailure.statusCode)
            assertTrue(httpFailure.retryable)
            assertEquals(4_000L, httpFailure.retryAfterMs)
            assertEquals(refreshedSnapshot, cacheStore.readPostFeedSnapshot(QUERY_KEY))

            transactionObserver.failNextTransaction = true
            server.enqueue(jsonResponse(ROLLBACK_JSON))
            val storageFailure = synchronizer.synchronize(
                RepositoryPageRequest(
                    limit = 1,
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.REFRESH,
                ),
            )
            assertEquals(PostFeedSyncResult.StorageFailure, storageFailure)
            assertEquals(refreshedSnapshot, cacheStore.readPostFeedSnapshot(QUERY_KEY))
            assertEquals(2, cacheStore.diagnostics().postCount)

            val requestLines = server.requestsSnapshot().map { request ->
                request.requestLine
            }
            assertEquals(5, requestLines.size)
            assertTrue(requestLines.first().contains("/r/android/hot?"))
            assertTrue(requestLines[1].contains("after=t3_net02_page_2"))
            assertFalse(requestLines.any { line -> line.contains("t3_stale_cursor") })

            server.close()
            server.assertHealthy()
            cacheStore.close()
            context.deleteDatabase(RUNTIME_DATABASE)

            Log.i(
                NET02_LOG_TAG,
                "NET02_HTTPS_REQUESTS=5 INITIAL_STORED=2 APPEND_DEDUP_STORED=3 " +
                    "REFRESH_REPLACED=2 GENERATION=1 CURSOR_MISMATCH_NO_NETWORK=true " +
                    "HTTP_FAILURE_CACHE_PRESERVED=true TRANSACTION_ROLLBACK=true " +
                    "ROOM_READBACK_ONLY=true OPAQUE_CURSOR_CACHE_REPLAY=false",
            )
            Log.i(NET02_LOG_TAG, "RESULT=SUBSHELL_NET02_NETWORK_TO_ROOM_RUNTIME_PASS")
        }
    }

    companion object {
        private const val RUNTIME_DATABASE = "subshell-net02-runtime.db"
        private const val CACHE_CLOCK_EPOCH_MS = 2_000_000L
        private const val NETWORK_RECEIVED_EPOCH_MS = 1_500_000L
        private val QUERY_KEY = RepositoryQueryKey("net02|runtime|r/android|hot")
    }
}

private class FailingCacheWriteTransactionObserver : CacheWriteTransactionObserver {
    var failNextTransaction: Boolean = false

    override fun beforeCommit(queryKey: RepositoryQueryKey) {
        if (failNextTransaction) {
            failNextTransaction = false
            error("Injected NET02 transaction failure")
        }
    }
}

private const val PAGE_ONE_JSON = """
{
  "kind": "Listing",
  "data": {
    "after": "t3_net02_page_2",
    "children": [
      {
        "kind": "t3",
        "data": {
          "id": "net02_a",
          "name": "t3_net02_a",
          "subreddit": "android",
          "author": "alice",
          "created_utc": 9900,
          "title": "NET02 A",
          "selftext": "Initial A",
          "score": 10,
          "num_comments": 2
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "net02_b",
          "name": "t3_net02_b",
          "subreddit": "android",
          "author": "bob",
          "created_utc": 9800,
          "title": "NET02 B",
          "selftext": "Initial B",
          "score": 20,
          "num_comments": 3
        }
      }
    ]
  }
}
"""

private const val PAGE_TWO_JSON = """
{
  "kind": "Listing",
  "data": {
    "after": null,
    "children": [
      {
        "kind": "t3",
        "data": {
          "id": "net02_b",
          "name": "t3_net02_b",
          "subreddit": "android",
          "author": "bob",
          "created_utc": 9800,
          "title": "NET02 B updated by append",
          "selftext": "Duplicate ID with fresh payload",
          "score": 21,
          "num_comments": 4
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "net02_c",
          "name": "t3_net02_c",
          "subreddit": "android",
          "author": "carol",
          "created_utc": 9700,
          "title": "NET02 C",
          "selftext": "Append C",
          "score": 30,
          "num_comments": 5
        }
      }
    ]
  }
}
"""

private const val REFRESH_JSON = """
{
  "kind": "Listing",
  "data": {
    "after": null,
    "children": [
      {
        "kind": "t3",
        "data": {
          "id": "net02_a",
          "name": "t3_net02_a",
          "subreddit": "android",
          "author": "alice",
          "created_utc": 9950,
          "title": "NET02 A refreshed",
          "selftext": "Refresh replaces the query generation",
          "score": 99,
          "num_comments": 9
        }
      },
      {
        "kind": "t3",
        "data": {
          "id": "net02_d",
          "name": "t3_net02_d",
          "subreddit": "android",
          "author": "dave",
          "created_utc": 9940,
          "title": "NET02 D",
          "selftext": "Refresh D",
          "score": 40,
          "num_comments": 6
        }
      }
    ]
  }
}
"""

private const val ROLLBACK_JSON = """
{
  "kind": "Listing",
  "data": {
    "after": null,
    "children": [
      {
        "kind": "t3",
        "data": {
          "id": "net02_rollback",
          "name": "t3_net02_rollback",
          "subreddit": "android",
          "author": "rollback",
          "created_utc": 9990,
          "title": "This transaction must roll back",
          "selftext": "The prior Room snapshot must survive",
          "score": 1,
          "num_comments": 0
        }
      }
    ]
  }
}
"""
