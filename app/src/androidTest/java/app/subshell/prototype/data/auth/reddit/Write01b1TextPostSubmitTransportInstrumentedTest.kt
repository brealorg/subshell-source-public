package app.subshell.prototype.data.auth.reddit

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Write01b1TextPostSubmitTransportInstrumentedTest {
    @Test
    fun localhostTextPostTransportProvesExactSubmitFailuresAndDuplicateGuard() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = requireNotNull(instrumentation.targetContext) {
                "WRITE01-B1 target context is unavailable"
            }
            val fixtureContext = targetContext.createPackageContext(
                instrumentation.componentName.packageName,
                0,
            )
            val server = LocalJsonHttpsServer(fixtureContext)
                .also(LocalJsonHttpsServer::start)
            val accountId = authenticatedAccountId(
                provider = OAuthProvider.REDDIT,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                providerAccountId = WRITE01B1_PROVIDER_ACCOUNT_ID,
            )
            val capability = Write01b1FakeCapability(
                accountStableId = accountId,
                session = StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet(
                        accessToken = WRITE01B1_ACCESS_TOKEN,
                        refreshToken = "write01b1-refresh",
                        tokenType = "bearer",
                        scope = "identity mysubreddits read submit",
                        expiresAtEpochMs = 9_000_000L,
                    ),
                    identity = AuthAccountIdentity(
                        providerAccountId = WRITE01B1_PROVIDER_ACCOUNT_ID,
                        username = "write01b1_user",
                    ),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                    clientIdFingerprint = "write01b1-fingerprint",
                ),
            )
            val callFactory = SubshellOkHttpClientFactory.createNonRetryingWriteClient()
            val submitter = ConfiguredRedditTextPostSubmitter(
                capability = capability,
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = WRITE01B1_USER_AGENT,
                networkClock = NetworkClock { 1_000_000L },
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = CREATED_TEXT_POST_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "X-Ratelimit-Remaining" to "59",
                        "X-Ratelimit-Used" to "1",
                        "X-Ratelimit-Reset" to "60",
                    ),
                ),
            )
            val created = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = accountId,
                    community = " r/SubShell ",
                    title = "  WRITE01-B1 localhost title  ",
                    text = "  **Created through localhost**\r\n\r\nProof.  ",
                    sendReplies = true,
                    nsfw = false,
                    spoiler = true,
                ),
            )
            assertTrue(created is AuthenticatedTextPostSubmitResult.Submitted)
            created as AuthenticatedTextPostSubmitResult.Submitted
            assertEquals(accountId, created.accountStableId)
            assertEquals("t3_created123", created.post.fullname)
            assertEquals("created123", created.post.id)
            assertEquals("subshell", created.post.community)
            assertEquals("WRITE01-B1 localhost title", created.post.title)
            assertEquals(
                "**Created through localhost**\n\nProof.",
                created.post.text,
            )
            assertEquals(
                "/r/subshell/comments/created123/write01b1/",
                created.post.permalink,
            )
            assertEquals(60_000L, created.metadata.rateLimit.resetAfterMs)

            val exactRequest = server.requestsSnapshot().single()
            assertEquals("POST /api/submit HTTP/1.1", exactRequest.requestLine)
            assertEquals(
                "Bearer $WRITE01B1_ACCESS_TOKEN",
                exactRequest.headers["authorization"],
            )
            assertEquals(WRITE01B1_USER_AGENT, exactRequest.headers["user-agent"])
            assertEquals("application/json", exactRequest.headers["accept"])
            assertTrue(
                exactRequest.headers["content-type"]
                    .orEmpty()
                    .startsWith("application/x-www-form-urlencoded"),
            )
            assertEquals(
                mapOf(
                    "api_type" to "json",
                    "raw_json" to "1",
                    "kind" to "self",
                    "sr" to "subshell",
                    "title" to "WRITE01-B1 localhost title",
                    "text" to "**Created through localhost**\n\nProof.",
                    "sendreplies" to "true",
                    "nsfw" to "false",
                    "spoiler" to "true",
                ),
                decodeForm(exactRequest.body),
            )

            val wrongAccount = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = "reddit:redreader_compatibility:t2_other",
                    community = "subshell",
                    title = "Must not reach the network",
                    text = "Wrong account",
                ),
            )
            assertFailure(
                result = wrongAccount,
                kind = AuthenticatedTextPostSubmitFailureKind.AUTHORIZATION_REQUIRED,
                retryable = false,
            )
            assertEquals(1, server.requestsSnapshot().size)

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = RATE_LIMIT_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "X-Ratelimit-Reset" to "12",
                    ),
                ),
            )
            val rateLimited = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = accountId,
                    community = "subshell",
                    title = "Rate limited",
                    text = "Rate limited body",
                ),
            )
            assertFailure(
                result = rateLimited,
                kind = AuthenticatedTextPostSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = 12_000L,
            )

            server.enqueue(
                FixtureResponse(
                    status = "503 Service Unavailable",
                    body = "{}",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                    ),
                ),
            )
            val httpUnknown = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = accountId,
                    community = "subshell",
                    title = "HTTP unknown outcome",
                    text = "Do not automatically retry",
                ),
            )
            assertFailure(
                result = httpUnknown,
                kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = "{}",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                    ),
                ),
            )
            val malformedUnknown = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = accountId,
                    community = "subshell",
                    title = "Malformed unknown outcome",
                    text = "Do not automatically retry",
                ),
            )
            assertFailure(
                result = malformedUnknown,
                kind = AuthenticatedTextPostSubmitFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = SECOND_CREATED_TEXT_POST_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                    ),
                    delayMs = 400L,
                ),
            )
            val inFlight = async(Dispatchers.IO) {
                submitter.submit(
                    AuthenticatedTextPostSubmitRequest(
                        accountStableId = accountId,
                        community = "subshell",
                        title = "Only once",
                        text = "Delayed successful post",
                    ),
                )
            }
            assertTrue(
                "WRITE01-B1 delayed request did not reach localhost",
                server.awaitRequestCount(expected = 5, timeoutMs = 5_000L),
            )
            val duplicate = submitter.submit(
                AuthenticatedTextPostSubmitRequest(
                    accountStableId = accountId,
                    community = "subshell",
                    title = "Duplicate",
                    text = "Must fail locally",
                ),
            )
            assertFailure(
                result = duplicate,
                kind = AuthenticatedTextPostSubmitFailureKind.ALREADY_SUBMITTING,
                retryable = false,
            )
            assertTrue(inFlight.await() is AuthenticatedTextPostSubmitResult.Submitted)

            server.close()
            server.assertHealthy()
            val recordedRequests = server.requestsSnapshot()
            assertEquals(5, recordedRequests.size)
            assertEquals("Rate limited", decodeForm(recordedRequests[1].body)["title"])
            assertEquals(
                "HTTP unknown outcome",
                decodeForm(recordedRequests[2].body)["title"],
            )
            assertEquals(
                "Malformed unknown outcome",
                decodeForm(recordedRequests[3].body)["title"],
            )
            assertEquals("Only once", decodeForm(recordedRequests[4].body)["title"])
            assertEquals(
                listOf(
                    accountId,
                    "reddit:redreader_compatibility:t2_other",
                    accountId,
                    accountId,
                    accountId,
                    accountId,
                ),
                capability.requestedAccounts,
            )
            assertFalse(callFactory.retryOnConnectionFailure)

            Log.i(
                WRITE01B1_LOG_TAG,
                "WRITE01B1_HTTPS_REQUESTS=5 EXACT_API_SUBMIT_FORM=true " +
                    "KIND_SELF=true PINNED_ACCOUNT=true API_RATE_LIMIT=true " +
                    "HTTP_OUTCOME_UNKNOWN=true MALFORMED_OUTCOME_UNKNOWN=true " +
                    "DUPLICATE_GUARD=true AUTOMATIC_CONNECTION_RETRY=false " +
                    "TARGET_APP_AUTH_VAULT_TOUCHED=false LIVE_REDDIT_NETWORK=false " +
                    "REMOTE_POST_WRITES=false",
            )
            Log.i(
                WRITE01B1_LOG_TAG,
                "RESULT=SUBSHELL_WRITE01B1_TEXT_POST_SUBMIT_TRANSPORT_RUNTIME_PASS",
            )
        }
    }

    private fun assertFailure(
        result: AuthenticatedTextPostSubmitResult,
        kind: AuthenticatedTextPostSubmitFailureKind,
        retryable: Boolean,
        retryAfterMs: Long? = null,
    ) {
        assertTrue(result is AuthenticatedTextPostSubmitResult.Failed)
        result as AuthenticatedTextPostSubmitResult.Failed
        assertEquals(kind, result.kind)
        assertEquals(retryable, result.retryable)
        assertEquals(retryAfterMs, result.retryAfterMs)
    }

    private fun decodeForm(body: String): Map<String, String> = body
        .split('&')
        .associate { field ->
            val separator = field.indexOf('=')
            require(separator > 0) { "Malformed form field in localhost request" }
            URLDecoder.decode(
                field.substring(0, separator),
                StandardCharsets.UTF_8.name(),
            ) to URLDecoder.decode(
                field.substring(separator + 1),
                StandardCharsets.UTF_8.name(),
            )
        }
}

private class Write01b1FakeCapability(
    private val accountStableId: String,
    private val session: StoredOAuthSession,
) : AuthCommentWriteCapability {
    val requestedAccounts = CopyOnWriteArrayList<String>()

    override suspend fun beginAuthorization(accountStableId: String): String =
        error("WRITE01-B1 transport proof does not start OAuth authorization")

    override fun hasPendingAuthorization(): Boolean = false

    override fun hasStoredCallback(): Boolean = false

    override fun cancelPendingAuthorization() = Unit

    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        error("WRITE01-B1 transport proof does not consume OAuth callbacks")

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
        requestedAccounts += accountStableId
        return if (accountStableId == this.accountStableId) {
            AuthSessionResult.Authenticated(session)
        } else {
            AuthSessionResult.ReauthenticationRequired
        }
    }
}

private const val WRITE01B1_LOG_TAG = "subshell-write01b1"
private const val WRITE01B1_PROVIDER_ACCOUNT_ID = "t2_write01b1"
private const val WRITE01B1_ACCESS_TOKEN = "write01b1-access"
private const val WRITE01B1_USER_AGENT =
    "android:io.github.brealorg.subshell.debug.dev2:0.65.5 (localhost fixture)"
private const val CREATED_TEXT_POST_JSON = """
{
  "json": {
    "errors": [],
    "data": {
      "id": "created123",
      "name": "t3_created123",
      "url": "https://www.reddit.com/r/subshell/comments/created123/write01b1/?utm_source=test"
    }
  }
}
"""
private const val SECOND_CREATED_TEXT_POST_JSON = """
{
  "json": {
    "errors": [],
    "data": {
      "id": "created124",
      "name": "t3_created124",
      "url": "https://www.reddit.com/r/subshell/comments/created124/only_once/"
    }
  }
}
"""
private const val RATE_LIMIT_JSON = """
{
  "json": {
    "errors": [
      ["RATELIMIT", "provider message must remain outside result state", "title"]
    ],
    "data": {}
  }
}
"""
