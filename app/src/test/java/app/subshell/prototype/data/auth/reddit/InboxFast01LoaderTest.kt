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
import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOXFAST01_A_REFRESH_READS_ONLY_THE_FIRST_PAGES_V1 through the loader: the
 * first load (nothing stored) balances the inbox and sent history; a refresh
 * of a stored inbox sends only the two first-page requests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InboxFast01LoaderTest {
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

    private val inboxPage = """
        {"kind":"Listing","data":{"after":"t4_i2","children":[
          {"kind":"t4","data":{"id":"root","name":"t4_root","author":"other_user","dest":"test-user",
           "subject":"Old thread","body":"Old question","created_utc":1000}}]}}
    """.trimIndent()

    private fun sentPage(after: String?, createdUtc: Int) = """
        {"kind":"Listing","data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[
          {"kind":"t4","data":{"id":"s$createdUtc","name":"t4_s$createdUtc","author":"test-user","dest":"someone",
           "subject":"Thread $createdUtc","body":"Sent $createdUtc","created_utc":$createdUtc}}]}}
    """.trimIndent()

    private val requests = Collections.synchronizedList(mutableListOf<String>())

    private val callFactory = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        // INBOXORDER01: a first page also reads the unread list; not part of this test.
        if (request.url.encodedPath.contains("/message/unread")) {
            return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"kind":"Listing","data":{"after":null,"children":[]}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val after = request.url.queryParameter("after")
        val sent = request.url.encodedPath.contains("/message/sent")
        requests += (if (sent) "sent:" else "inbox:") + (after ?: "first")
        val body = when {
            sent && after == null -> sentPage(after = "t4_s2", createdUtc = 5000)
            sent -> sentPage(after = null, createdUtc = 900)
            else -> inboxPage
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
        capability = FastTestCapability(session),
    )

    @After
    fun close() {
        cache.close()
    }

    @Test
    fun theFirstLoadBalancesAndARefreshReadsOnlyTheFirstPages() = runBlocking {
        val first = loader().synchronize(account)
        assertTrue(first.toString(), first is AuthenticatedRedditInboxResult.Loaded)
        assertTrue(requests.toString(), "sent:t4_s2" in requests)

        requests.clear()
        val refreshed = loader().synchronize(account)

        assertTrue(refreshed.toString(), refreshed is AuthenticatedRedditInboxResult.Loaded)
        assertEquals(setOf("inbox:first", "sent:first"), requests.toSet())
        assertEquals(2, requests.size)
    }
}

private class FastTestCapability(private val session: StoredOAuthSession) : AuthInboxReadCapability {
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
