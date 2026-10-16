package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.ui.error.contentSafetyErrorPresentation
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (useraction01a1, useraction01a2): blocking and unblocking a user go
 * to Reddit once each, as the account itself, never for the account's own
 * name; a refusal Reddit keeps for approved clients says so instead of asking
 * to sign in again, and a lost answer is never sent a second time. The
 * blocked list is Reddit's own `/prefs/blocked`, page by page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3UserBlockWriterTest {
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "account", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "me1", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String>()
    private var answer: () -> Pair<Int, String> = { 200 to """{"json":{"errors":[]}}""" }
    private var lostConnection = false

    private fun writer() = ConfiguredRedditContentSafetyWriter(
        capability = AuthorizedCapability(session),
        callFactory = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                bodies += request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }.orEmpty()
                if (lostConnection) throw IOException("connection lost")
                val (code, body) = answer()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(code).message("answer")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        ).build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        hiddenHistoryUpdater = null,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun form(index: Int): Map<String, String> = bodies[index]
        .split("&")
        .filter(String::isNotEmpty)
        .associate { field ->
            val (name, value) = field.split("=", limit = 2)
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun aBlockIsOnePostToBlockUser() = runBlocking {
        val result = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "target_user", blocked = true))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/block_user", request.url.encodedPath)
        assertEquals(mapOf("api_type" to "json", "name" to "target_user"), form(0))
        assertEquals(
            AuthenticatedContentSafetyResult.Updated(
                accountStableId = "reddit:me",
                username = "target_user",
                blocked = true,
            ),
            result,
        )
    }

    @Test
    fun anUnblockIsOnePostToUnfriendFromTheAccountItself() = runBlocking {
        answer = { 200 to "{}" }

        val result = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "target_user", blocked = false))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/unfriend", request.url.encodedPath)
        assertEquals(
            mapOf("container" to "t2_me1", "name" to "target_user", "type" to "enemy"),
            form(0),
        )
        assertEquals(false, (result as AuthenticatedContentSafetyResult.Updated).blocked)
    }

    @Test
    fun theAccountCannotBlockItself() = runBlocking {
        val result = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "me", blocked = true))

        assertEquals(
            ContentSafetyFailureKind.VALIDATION,
            (result as AuthenticatedContentSafetyResult.Failed).kind,
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun refusalsAreToldApartAndALostAnswerIsNeverSentAgain() = runBlocking {
        answer = { 403 to "{}" }
        val forbidden = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "target_user", blocked = true))
        assertEquals(
            ContentSafetyFailureKind.PROVIDER_RESTRICTION,
            (forbidden as AuthenticatedContentSafetyResult.Failed).kind,
        )

        answer = { 401 to "{}" }
        val unauthorized = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "target_user", blocked = true))
        assertEquals(AuthenticatedContentSafetyResult.AuthorizationRequired, unauthorized)

        requests.clear()
        lostConnection = true
        val lost = writer().setUserBlocked(RedditUserBlockRequest("reddit:me", "target_user", blocked = true))
        assertTrue("$lost", lost is AuthenticatedContentSafetyResult.Failed)
        assertEquals(1, requests.size)
    }

    @Test
    fun theBlockedListIsRedditsOwnPageByPage() = runBlocking {
        answer = {
            200 to """{"kind":"UserList","data":{"after":"c2","children":[
                {"date":1700846613.0,"rel_id":"r9_x","name":"oiz92","id":"t2_1"}]}}"""
        }
        val writer = writer()

        val first = writer.loadBlockedAccounts("reddit:me", null)

        assertEquals("GET", requests[0].method)
        assertEquals("/prefs/blocked", requests[0].url.encodedPath)
        assertEquals("100", requests[0].url.queryParameter("limit"))
        assertNull(requests[0].url.queryParameter("after"))
        first as AuthenticatedBlockedAccountsResult.Loaded
        assertEquals(
            listOf(RedditBlockedAccount("oiz92", "t2_1", relationshipId = "r9_x", blockedAtEpochSeconds = 1700846613L)),
            first.items,
        )
        assertEquals("c2", first.nextCursor)

        writer.loadBlockedAccounts("reddit:me", "c2")
        assertEquals("c2", requests[1].url.queryParameter("after"))

        answer = { 401 to "{}" }
        assertEquals(
            AuthenticatedBlockedAccountsResult.AuthorizationRequired,
            writer.loadBlockedAccounts("reddit:me", null),
        )

        answer = { 403 to "{}" }
        val forbidden = writer.loadBlockedAccounts("reddit:me", null)
        forbidden as AuthenticatedBlockedAccountsResult.Failed
        assertEquals(ContentSafetyFailureKind.HTTP, forbidden.kind)
        assertEquals(403, forbidden.statusCode)
    }

    @Test
    fun aBlockRedditKeepsForApprovedClientsSaysSo() {
        val presentation = contentSafetyErrorPresentation(
            ContentSafetyUiState(
                phase = ContentSafetyPhase.FAILED,
                operation = ContentSafetyOperation.SET_USER_BLOCKED,
                username = "target_user",
                blocked = true,
                failureKind = ContentSafetyFailureKind.PROVIDER_RESTRICTION,
            ),
        )

        assertEquals("Couldn't block user", presentation.title)
        assertTrue(presentation.message, presentation.message.contains("approved OAuth clients"))
        assertNull(presentation.primaryAction)
    }

    private class AuthorizedCapability(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }
}
