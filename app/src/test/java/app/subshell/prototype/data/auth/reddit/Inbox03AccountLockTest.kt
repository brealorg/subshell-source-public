package app.subshell.prototype.data.auth.reddit

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthInboxReadMutationResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditInboxResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.cache.RoomSubshellCacheStore
import app.subshell.prototype.data.cache.SubshellCacheDatabase
import app.subshell.prototype.data.network.NetworkEndpoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOX03: the loader and the read writer share one lock per account, also
 * across separate instances (the background worker builds its own runtime).
 * A refresh whose page was fetched before a read finishes first; the read
 * then marks the stored item read, so the stale page cannot undo it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Inbox03AccountLockTest {
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

    @Volatile private var holdInbox = false
    private val inboxRequested = CountDownLatch(1)
    private val releaseInbox = CountDownLatch(1)

    private val unreadReply = """{"kind":"Listing","data":{"after":null,"children":[{"kind":"t1","data":{
        "id":"c1","name":"t1_c1","author":"peer","dest":"test-user","body":"hello","new":true,
        "created_utc":1700000000.0,"subject":"comment reply","was_comment":true,
        "context":"/r/test/comments/p1/_/c1/?context=3","link_title":"A post","subreddit":"test"}}]}}"""
    private val emptyListing = """{"kind":"Listing","data":{"after":null,"children":[]}}"""

    private val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("/message/inbox") || path.endsWith("/message/inbox.json") -> {
                if (holdInbox) {
                    inboxRequested.countDown()
                    releaseInbox.await(10, TimeUnit.SECONDS)
                }
                unreadReply
            }
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
        capability = LockTestCapability(session),
    )

    private fun writer() = RedditInboxReadMutationWriter(
        callFactory = callFactory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        cacheStore = cache,
        capability = LockTestCapability(session),
    )

    @After
    fun close() {
        cache.close()
    }

    @Test
    fun aReadDuringARefreshFromAnotherInstanceIsNotUndoneByTheStalePage() = runBlocking {
        val seeded = loader().synchronize(account) as AuthenticatedRedditInboxResult.Loaded
        val item = seeded.snapshot.items.single()
        assertTrue(item.unread)

        // The worker's loader starts a refresh; its page (item unread) is in flight.
        holdInbox = true
        val refresh = async(Dispatchers.IO) { loader().synchronize(account) }
        assertTrue(inboxRequested.await(10, TimeUnit.SECONDS))

        // The app's writer marks the item read meanwhile.
        val read = async(Dispatchers.IO) { writer().markRead(account, item) }
        Thread.sleep(300)
        releaseInbox.countDown()

        refresh.await()
        val readResult = read.await()
        assertTrue(readResult.toString(), readResult is AuthInboxReadMutationResult.Applied)
        assertFalse(cache.liveInboxSnapshot(account)!!.items.single().unread)
    }
    // STEP3 (modmail01a1, mod01a22): a grant without the inbox permission
    // asks for it and sends nothing; a refused sign-in is a session failure.
    @Test
    fun aMissingInboxPermissionAsksForItAndARefusalIsASessionFailure() = runBlocking {
        var requests = 0
        val counting = OkHttpClient.Builder().addInterceptor { chain ->
            requests += 1
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(emptyListing.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val item = app.subshell.prototype.model.InboxItem(
            id = "t1_c1",
            kind = app.subshell.prototype.model.ActivityKind.REPLY,
            title = "comment reply",
            sender = "peer",
            age = "now",
            preview = "",
            unread = true,
        )
        fun loaderWith(denied: String) = RedditInboxLoader(
            callFactory = counting,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            cacheStore = cache,
            capability = DeniedCapability(denied),
        )
        fun writerWith(denied: String) = RedditInboxReadMutationWriter(
            callFactory = counting,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            cacheStore = cache,
            capability = DeniedCapability(denied),
        )

        assertEquals(
            AuthenticatedRedditInboxResult.AuthorizationRequired,
            loaderWith("missing_incremental_scope").synchronize(account),
        )
        assertEquals(
            AuthInboxReadMutationResult.AuthorizationRequired,
            writerWith("missing_incremental_scope").markRead(account, item),
        )
        val refused = loaderWith("access_denied").synchronize(account)
        assertEquals(
            refused.toString(),
            app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind.SESSION,
            (refused as AuthenticatedRedditInboxResult.Failed).kind,
        )
        val refusedRead = writerWith("access_denied").markRead(account, item)
        assertEquals(
            refusedRead.toString(),
            app.subshell.prototype.data.auth.AuthenticatedRedditInboxFailureKind.SESSION,
            (refusedRead as AuthInboxReadMutationResult.Failed).kind,
        )
        assertEquals(0, requests)
    }
}

private class DeniedCapability(private val error: String) : AuthInboxReadCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override suspend fun hasScopeGrant(accountStableId: String): Boolean = false
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.AuthorizationDenied(error)
}

private class LockTestCapability(private val session: StoredOAuthSession) : AuthInboxReadCapability {
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
