package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOXSSOT01_REFRESH_KEEPS_THE_OLDER_PAGES_V1 through the real loader: a
 * refresh keeps the stored pages below Reddit's first page and says how many
 * items are fresh, which is all the background sync may notify about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InboxSsot01LoaderRefreshTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, SubshellCacheDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cache = RoomSubshellCacheStore(database)
    private val account = "reddit:redreader_compatibility:me"
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("test-token", null, "bearer", "identity read privatemessages", Long.MAX_VALUE),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    // More inbox pages follow, so the fresh page is complete only down to c1.
    private val unreadReply = """{"kind":"Listing","data":{"after":"t1_c1","children":[{"kind":"t1","data":{
        "id":"c1","name":"t1_c1","author":"peer","dest":"test-user","body":"hello","new":true,
        "created_utc":1700000000.0,"subject":"comment reply","was_comment":true,
        "context":"/r/test/comments/p1/_/c1/?context=3","link_title":"A post","subreddit":"test"}}]}}"""
    private val emptyListing = """{"kind":"Listing","data":{"after":null,"children":[]}}"""

    private val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("/message/inbox") || path.endsWith("/message/inbox.json") -> unreadReply
            path.contains("/message/sent") -> emptyListing
            else -> "{}"
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun loader() = RedditInboxLoader(
        callFactory = callFactory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        cacheStore = cache,
        capability = SsotTestCapability(session),
    )

    @After
    fun close() {
        cache.close()
    }

    @Test
    fun aRefreshKeepsTheAppendedPageAndCountsOnlyRedditsItemsAsFresh() = runBlocking {
        val first = loader().synchronize(account) as AuthenticatedRedditInboxResult.Loaded
        val replyId = first.snapshot.items.single().id
        // An older page the reader scrolled to, still unread from weeks ago.
        cache.replaceLiveInboxSnapshot(
            accountStableId = account,
            items = listOf(first.snapshot.items.single(), oldUnread("t1_old")),
            nextCursor = "t1_old",
        )

        val refreshed = loader().synchronize(account) as AuthenticatedRedditInboxResult.Loaded

        assertEquals(listOf(replyId, "t1_old"), refreshed.snapshot.items.map(InboxItem::id))
        assertEquals("t1_old", refreshed.snapshot.nextCursor)
        assertEquals(setOf(replyId), refreshed.snapshot.freshItemIds)
        assertEquals(listOf(replyId, "t1_old"), cache.liveInboxSnapshot(account)!!.items.map(InboxItem::id))
    }

    private fun oldUnread(id: String) = InboxItem(
        id = id,
        kind = ActivityKind.REPLY,
        title = "Old reply",
        sender = "peer",
        age = "3w",
        preview = "old",
        unread = true,
        createdEpochSeconds = 1_600_000_000,
    )
}

private class SsotTestCapability(private val session: StoredOAuthSession) : AuthInboxReadCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override suspend fun hasScopeGrant(accountStableId: String): Boolean = true
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(session)
}
