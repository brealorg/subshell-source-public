package app.subshell.prototype.data.auth.reddit

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import app.subshell.prototype.data.network.reddit.EpochSecondsClock
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
class Write01a2CommentSubmitTransportInstrumentedTest {
    @Test
    fun localhostCommentTransportProvesExactPostFailuresAndDuplicateGuard() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = requireNotNull(instrumentation.targetContext) {
                "WRITE01-A2 target context is unavailable"
            }
            val fixtureContext = targetContext.createPackageContext(
                instrumentation.componentName.packageName,
                0,
            )
            val server = LocalJsonHttpsServer(fixtureContext).also(LocalJsonHttpsServer::start)
            val accountId = authenticatedAccountId(
                provider = OAuthProvider.REDDIT,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                providerAccountId = WRITE01A2_PROVIDER_ACCOUNT_ID,
            )
            val capability = Write01a2FakeCapability(
                accountStableId = accountId,
                session = StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet(
                        accessToken = WRITE01A2_ACCESS_TOKEN,
                        refreshToken = "write01a2-refresh",
                        tokenType = "bearer",
                        scope = "identity mysubreddits read submit",
                        expiresAtEpochMs = 9_000_000L,
                    ),
                    identity = AuthAccountIdentity(
                        providerAccountId = WRITE01A2_PROVIDER_ACCOUNT_ID,
                        username = "write01a2_user",
                    ),
                    clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                    clientIdFingerprint = "write01a2-fingerprint",
                ),
            )
            val callFactory = SubshellOkHttpClientFactory.createNonRetryingWriteClient()
            val submitter = ConfiguredRedditCommentSubmitter(
                capability = capability,
                callFactory = callFactory,
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = WRITE01A2_USER_AGENT,
                networkClock = NetworkClock { 1_000_000L },
                epochSecondsClock = EpochSecondsClock { 10_000L },
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = CREATED_COMMENT_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "X-Ratelimit-Remaining" to "59",
                        "X-Ratelimit-Used" to "1",
                        "X-Ratelimit-Reset" to "60",
                    ),
                ),
            )
            val created = submitter.submit(
                AuthenticatedCommentSubmitRequest(
                    accountStableId = accountId,
                    parentFullname = "t3_post123",
                    text = "  **Created through localhost**  ",
                ),
            )
            assertTrue(created is AuthenticatedCommentSubmitResult.Submitted)
            created as AuthenticatedCommentSubmitResult.Submitted
            assertEquals(accountId, created.accountStableId)
            assertEquals("t1_created123", created.comment.id)
            assertEquals("t3_post123", created.comment.parentId)
            assertEquals("**Created through localhost**", created.comment.body)
            assertEquals(60_000L, created.metadata.rateLimit.resetAfterMs)

            val exactRequest = server.requestsSnapshot().single()
            assertEquals("POST /api/comment HTTP/1.1", exactRequest.requestLine)
            assertEquals("Bearer $WRITE01A2_ACCESS_TOKEN", exactRequest.headers["authorization"])
            assertEquals(WRITE01A2_USER_AGENT, exactRequest.headers["user-agent"])
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
                    "thing_id" to "t3_post123",
                    "text" to "**Created through localhost**",
                ),
                decodeForm(exactRequest.body),
            )

            val wrongAccount = submitter.submit(
                AuthenticatedCommentSubmitRequest(
                    accountStableId = "reddit:redreader_compatibility:t2_other",
                    parentFullname = "t1_parent",
                    text = "Must not reach the network",
                ),
            )
            assertFailure(
                result = wrongAccount,
                kind = AuthenticatedCommentSubmitFailureKind.AUTHORIZATION_REQUIRED,
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
                AuthenticatedCommentSubmitRequest(
                    accountStableId = accountId,
                    parentFullname = "t1_parent",
                    text = "Rate limited",
                ),
            )
            assertFailure(
                result = rateLimited,
                kind = AuthenticatedCommentSubmitFailureKind.RATE_LIMIT,
                retryable = true,
                retryAfterMs = 12_000L,
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = "{}",
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                ),
            )
            val unknownOutcome = submitter.submit(
                AuthenticatedCommentSubmitRequest(
                    accountStableId = accountId,
                    parentFullname = "t3_post123",
                    text = "Ambiguous response",
                ),
            )
            assertFailure(
                result = unknownOutcome,
                kind = AuthenticatedCommentSubmitFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
            )

            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = SECOND_CREATED_COMMENT_JSON,
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                    delayMs = 400L,
                ),
            )
            val inFlight = async(Dispatchers.IO) {
                submitter.submit(
                    AuthenticatedCommentSubmitRequest(
                        accountStableId = accountId,
                        parentFullname = "t1_parent",
                        text = "Only once",
                    ),
                )
            }
            assertTrue(
                "WRITE01-A2 delayed request did not reach localhost",
                server.awaitRequestCount(expected = 4, timeoutMs = 5_000L),
            )
            val duplicate = submitter.submit(
                AuthenticatedCommentSubmitRequest(
                    accountStableId = accountId,
                    parentFullname = "t1_parent",
                    text = "Duplicate",
                ),
            )
            assertFailure(
                result = duplicate,
                kind = AuthenticatedCommentSubmitFailureKind.ALREADY_SUBMITTING,
                retryable = false,
            )
            assertTrue(inFlight.await() is AuthenticatedCommentSubmitResult.Submitted)

            server.close()
            server.assertHealthy()
            val recordedRequests = server.requestsSnapshot()
            assertEquals(4, recordedRequests.size)
            assertEquals("t1_parent", decodeForm(recordedRequests[1].body)["thing_id"])
            assertEquals("t1_parent", decodeForm(recordedRequests[3].body)["thing_id"])
            assertEquals(
                listOf(
                    accountId,
                    "reddit:redreader_compatibility:t2_other",
                    accountId,
                    accountId,
                    accountId,
                ),
                capability.requestedAccounts,
            )
            assertFalse(callFactory.retryOnConnectionFailure)

            Log.i(
                WRITE01A2_LOG_TAG,
                "WRITE01A2_HTTPS_REQUESTS=4 EXACT_FORM_POST=true " +
                    "PARENT_T1_T3=true PINNED_ACCOUNT=true API_RATE_LIMIT=true " +
                    "OUTCOME_UNKNOWN_NON_RETRYABLE=true DUPLICATE_GUARD=true " +
                    "AUTOMATIC_CONNECTION_RETRY=false TARGET_APP_AUTH_VAULT_TOUCHED=false " +
                    "LIVE_REDDIT_NETWORK=false REMOTE_COMMENT_WRITES=false",
            )
            Log.i(
                WRITE01A2_LOG_TAG,
                "RESULT=SUBSHELL_WRITE01A2_COMMENT_SUBMIT_TRANSPORT_RUNTIME_PASS",
            )
        }
    }

    private fun assertFailure(
        result: AuthenticatedCommentSubmitResult,
        kind: AuthenticatedCommentSubmitFailureKind,
        retryable: Boolean,
        retryAfterMs: Long? = null,
    ) {
        assertTrue(result is AuthenticatedCommentSubmitResult.Failed)
        result as AuthenticatedCommentSubmitResult.Failed
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

private class Write01a2FakeCapability(
    private val accountStableId: String,
    private val session: StoredOAuthSession,
) : AuthCommentWriteCapability {
    val requestedAccounts = CopyOnWriteArrayList<String>()

    override suspend fun beginAuthorization(accountStableId: String): String =
        error("WRITE01-A2 transport proof does not start OAuth authorization")

    override fun hasPendingAuthorization(): Boolean = false

    override fun hasStoredCallback(): Boolean = false

    override fun cancelPendingAuthorization() = Unit

    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        error("WRITE01-A2 transport proof does not consume OAuth callbacks")

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
        requestedAccounts += accountStableId
        return if (accountStableId == this.accountStableId) {
            AuthSessionResult.Authenticated(session)
        } else {
            AuthSessionResult.ReauthenticationRequired
        }
    }
}

private const val WRITE01A2_LOG_TAG = "subshell-write01a2"
private const val WRITE01A2_PROVIDER_ACCOUNT_ID = "t2_write01a2"
private const val WRITE01A2_ACCESS_TOKEN = "write01a2-access"
private const val WRITE01A2_USER_AGENT =
    "android:io.github.brealorg.subshell.debug.dev2:0.65.1 (localhost fixture)"
private const val CREATED_COMMENT_JSON = """
{
  "json": {
    "errors": [],
    "data": {
      "things": [
        {
          "kind": "t1",
          "data": {
            "id": "created123",
            "name": "t1_created123",
            "author": "write01a2_user",
            "body": "**Created through localhost**",
            "score": 1,
            "created_utc": 9940,
            "depth": 0,
            "is_submitter": true,
            "parent_id": "t3_post123"
          }
        }
      ]
    }
  }
}
"""
private const val SECOND_CREATED_COMMENT_JSON = """
{
  "json": {
    "errors": [],
    "data": {
      "things": [
        {
          "kind": "t1",
          "data": {
            "id": "created124",
            "name": "t1_created124",
            "author": "write01a2_user",
            "body": "Only once",
            "score": 1,
            "created_utc": 9950,
            "depth": 1,
            "parent_id": "t1_parent"
          }
        }
      ]
    }
  }
}
"""
private const val RATE_LIMIT_JSON = """
{
  "json": {
    "errors": [
      ["RATELIMIT", "provider message must remain outside UI state", "text"]
    ],
    "data": {
      "things": []
    }
  }
}
"""
