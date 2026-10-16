package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryQueryKey
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.HiddenHistoryReconciliation
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdateResult
import app.subshell.prototype.data.auth.AuthenticatedRedditAccountHistoryUpdater
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (safety01a2): Hide, Unhide and Report go to Reddit once each, as the
 * account that asked. A confirmed hide or post report re-reads that account's
 * Hidden history; a comment report does not. A report Reddit may have stored
 * (a lost answer, a gateway error, a body that cannot be read) is "outcome
 * unknown" and never offered again; a hide that did not arrive may be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3ContentSafetyWriterTest {
    private val session = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("access", "refresh", "bearer", "report history", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "me1", username = "me"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String>()
    private val authorizedAccounts = mutableListOf<String>()
    private val historyReads = mutableListOf<String>()
    private var answer: () -> Pair<Int, String> = { 200 to """{"json":{"errors":[]}}""" }
    private var lostConnection = false

    private val updater = AuthenticatedRedditAccountHistoryUpdater { account, username, kind, operation ->
        historyReads += "$account:$username:$kind:$operation"
        AuthenticatedRedditAccountHistoryUpdateResult.Applied(
            queryKey = RepositoryQueryKey("hidden"),
            operation = operation,
            networkItemCount = 1,
            storedItemCount = 1,
            endReached = true,
        )
    }

    private fun writer() = ConfiguredRedditContentSafetyWriter(
        capability = AuthorizedCapability(session, authorizedAccounts),
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
        hiddenHistoryUpdater = updater,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun form(index: Int): Map<String, String> = bodies[index]
        .split("&")
        .filter(String::isNotEmpty)
        .associate { field ->
            val (name, value) = field.split("=", limit = 2)
            URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    private fun postReport() = RedditContentReportRequest(
        accountStableId = "reddit:me",
        thingFullname = "t3_x",
        reason = "Spam",
        communityName = "homelab",
    )

    @Test
    fun aHideIsOnePostAndReReadsTheAccountsHiddenHistory() = runBlocking {
        // Reddit answers api/hide with an empty object; the hide reads no body.
        answer = { 200 to "" }

        val result = writer().setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_x", hidden = true))

        assertEquals(listOf("reddit:me"), authorizedAccounts)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/hide", request.url.encodedPath)
        assertEquals(mapOf("id" to "t3_x"), form(0))
        assertEquals(
            AuthenticatedContentSafetyResult.Updated(
                accountStableId = "reddit:me",
                thingFullname = "t3_x",
                hidden = true,
                hiddenHistoryReconciliation = HiddenHistoryReconciliation.REFRESHED,
            ),
            result,
        )
        assertEquals(
            listOf("reddit:me:me:${RedditAccountHistoryKind.HIDDEN}:${RepositoryLoadOperation.REFRESH}"),
            historyReads,
        )

        writer().setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_x", hidden = false))
        assertEquals("/api/unhide", requests[1].url.encodedPath)
        assertEquals(mapOf("id" to "t3_x"), form(1))
    }

    @Test
    fun aPostReportIsOnePostWithItsCommunityAndRedditsRefusalIsKept() = runBlocking {
        val result = writer().report(postReport())

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/report", request.url.encodedPath)
        assertEquals(
            mapOf("api_type" to "json", "id" to "t3_x", "reason" to "Spam", "sr_name" to "homelab"),
            form(0),
        )
        result as AuthenticatedContentSafetyResult.Updated
        assertEquals(true, result.hidden)
        assertEquals(HiddenHistoryReconciliation.REFRESHED, result.hiddenHistoryReconciliation)
        assertEquals(1, historyReads.size)

        answer = { 200 to """{"json":{"errors":[["SUBREDDIT_NOTALLOWED","you aren't allowed","sr_name"]]}}""" }
        val refused = writer().report(postReport())
        refused as AuthenticatedContentSafetyResult.Failed
        assertEquals(ContentSafetyFailureKind.API, refused.kind)
        assertFalse(refused.retryable)
        assertEquals("SUBREDDIT_NOTALLOWED", refused.failureDetails?.providerCode?.value)
        assertEquals("you aren't allowed", refused.failureDetails?.providerMessage?.value)
    }

    @Test
    fun aCommentReportHidesNothingAndReadsNoHistory() = runBlocking {
        val result = writer().report(RedditContentReportRequest("reddit:me", "t1_c", "Spam"))

        assertEquals(
            mapOf("api_type" to "json", "id" to "t1_c", "reason" to "Spam"),
            form(0),
        )
        result as AuthenticatedContentSafetyResult.Updated
        assertEquals(null, result.hidden)
        assertEquals(HiddenHistoryReconciliation.NOT_APPLICABLE, result.hiddenHistoryReconciliation)
        assertTrue(historyReads.isEmpty())
    }

    @Test
    fun aReportRedditMayHaveStoredIsOutcomeUnknownAndNotRetryable() = runBlocking {
        for (status in listOf(500, 408)) {
            requests.clear()
            answer = { status to "{}" }

            val result = writer().report(postReport())

            result as AuthenticatedContentSafetyResult.Failed
            assertEquals("$status", ContentSafetyFailureKind.OUTCOME_UNKNOWN, result.kind)
            assertFalse("$status", result.retryable)
            assertEquals("$status", 1, requests.size)
        }

        requests.clear()
        answer = { 200 to "this is not json" }
        val unreadable = writer().report(postReport()) as AuthenticatedContentSafetyResult.Failed
        assertEquals(ContentSafetyFailureKind.OUTCOME_UNKNOWN, unreadable.kind)
        assertEquals(1, requests.size)
        assertTrue(historyReads.isEmpty())
    }

    @Test
    fun aLostAnswerIsUnknownForAReportButMayBeRetriedForAHide() = runBlocking {
        lostConnection = true

        val report = writer().report(postReport()) as AuthenticatedContentSafetyResult.Failed
        assertEquals(ContentSafetyFailureKind.OUTCOME_UNKNOWN, report.kind)
        assertFalse(report.retryable)
        assertEquals(1, requests.size)

        requests.clear()
        val hide = writer().setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_x", hidden = true))
        hide as AuthenticatedContentSafetyResult.Failed
        assertEquals(ContentSafetyFailureKind.TRANSPORT, hide.kind)
        assertTrue(hide.retryable)
        assertEquals(1, requests.size)
        assertTrue(historyReads.isEmpty())
    }

    @Test
    fun aRefusedHideAsksToSignInAgain() = runBlocking {
        for (status in listOf(401, 403)) {
            answer = { status to "{}" }

            val result = writer().setPostHidden(RedditPostVisibilityRequest("reddit:me", "t3_x", hidden = true))

            assertEquals("$status", AuthenticatedContentSafetyResult.AuthorizationRequired, result)
        }
        assertTrue(historyReads.isEmpty())
    }

    private class AuthorizedCapability(
        private val session: StoredOAuthSession,
        private val authorizedAccounts: MutableList<String>,
    ) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult = error("unused")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            authorizedAccounts += accountStableId
            return AuthSessionResult.Authenticated(session)
        }
    }
}
