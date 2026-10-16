package app.subshell.prototype.auth

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWriteFailureKind
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.reddit.ConfiguredRedditCommentSubmitter
import app.subshell.prototype.data.auth.reddit.mergeSubmittedCommentIntoThread
import app.subshell.prototype.data.network.FixtureResponse
import app.subshell.prototype.data.network.LocalJsonHttpsServer
import app.subshell.prototype.data.network.NetworkClock
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellOkHttpClientFactory
import app.subshell.prototype.data.network.reddit.EpochSecondsClock
import app.subshell.prototype.model.Comment
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Write01a3bCommentAuthSubmitInstrumentedTest {
    @Test
    fun localhostAuthorizationSubmitAndRoomWritebackStayPinnedAndFailClosed() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = requireNotNull(instrumentation.targetContext) {
                "WRITE01-A3B target context is unavailable"
            }
            val fixtureContext = targetContext.createPackageContext(
                instrumentation.componentName.packageName,
                0,
            )
            val server = LocalJsonHttpsServer(fixtureContext).also(LocalJsonHttpsServer::start)
            val accountId = authenticatedAccountId(
                provider = OAuthProvider.REDDIT,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                providerAccountId = WRITE01A3B_PROVIDER_ACCOUNT_ID,
            )
            val otherAccountId = authenticatedAccountId(
                provider = OAuthProvider.REDDIT,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                providerAccountId = "t2_other",
            )
            val directory = Write01a3bDirectory(accountId, otherAccountId)
            val capability = Write01a3bCapability(
                accountStableId = accountId,
                session = writeSession(),
            )
            val loader = Write01a3bCommentLoader(
                initial = listOf(
                    comment(
                        id = "t1_parent",
                        body = "Parent",
                        depth = 0,
                        parentId = "t3_post123",
                    ),
                ),
            )
            var visibleThread: List<Comment> = emptyList()
            val submitter = ConfiguredRedditCommentSubmitter(
                capability = capability,
                callFactory = SubshellOkHttpClientFactory.createNonRetryingWriteClient(),
                endpoint = NetworkEndpoint.https("https://127.0.0.1:${server.port}/"),
                userAgent = WRITE01A3B_USER_AGENT,
                networkClock = NetworkClock { 1_000_000L },
                epochSecondsClock = EpochSecondsClock { 10_000L },
            )
            val coordinator = CommentWriteCoordinator(
                capability = capability,
                submitter = submitter,
                commentLoader = loader,
                accountDirectory = directory,
                clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
                onThreadUpdated = { _, comments -> visibleThread = comments },
            )
            val request = CommentWriteRequest(
                accountStableId = accountId,
                postId = "t3_post123",
                targetStableKey = "t3_post123|t1_parent",
                parentFullname = "t1_parent",
                text = "Created after explicit authorization",
                sort = RedditCommentSort.NEW,
            )

            coordinator.submit(request)
            assertEquals(CommentWritePhase.AUTHORIZATION_REQUIRED, coordinator.uiState.value.phase)
            assertEquals(0, server.requestsSnapshot().size)

            val launch = coordinator.beginAuthorization()
            assertTrue(launch is AuthBrowserLaunchResult.Ready)
            launch as AuthBrowserLaunchResult.Ready
            assertEquals(WRITE01A3B_AUTHORIZATION_URL, launch.authorizationUrl)
            assertEquals(
                OAuthClientMode.REDREADER_COMPATIBILITY,
                launch.clientMode,
            )
            assertEquals(CommentWritePhase.AWAITING_CALLBACK, coordinator.uiState.value.phase)

            capability.acceptCallback()
            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = CREATED_REPLY_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "X-Ratelimit-Remaining" to "58",
                    ),
                ),
            )
            assertTrue(coordinator.handleCallbackResultTokenIfOwned("accepted"))

            val success = coordinator.uiState.value
            assertEquals(CommentWritePhase.SUCCEEDED, success.phase)
            assertTrue(success.threadUpdated)
            assertEquals("t1_createda3b", success.submittedComment?.id)
            assertEquals(1L, success.completionRevision)
            assertEquals("t1_createda3b", visibleThread.single().children.single().id)
            assertEquals(
                listOf(accountId, accountId),
                capability.requestedAccounts,
            )

            val posted = server.requestsSnapshot().single()
            assertEquals("POST /api/comment HTTP/1.1", posted.requestLine)
            assertEquals(
                mapOf(
                    "api_type" to "json",
                    "raw_json" to "1",
                    "thing_id" to "t1_parent",
                    "text" to "Created after explicit authorization",
                ),
                decodeForm(posted.body),
            )

            coordinator.reset()
            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = RATE_LIMIT_JSON,
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "X-Ratelimit-Reset" to "9",
                    ),
                ),
            )
            coordinator.submit(
                request.copy(
                    targetStableKey = "t3_post123|post",
                    parentFullname = "t3_post123",
                    text = "Rate limited",
                ),
            )
            val rateLimit = coordinator.uiState.value
            assertEquals(CommentWritePhase.FAILED, rateLimit.phase)
            assertEquals(CommentWriteFailureKind.RATE_LIMIT, rateLimit.failureKind)
            assertTrue(rateLimit.retryable)
            assertEquals(9_000L, rateLimit.retryAfterMs)

            coordinator.reset()
            server.enqueue(
                FixtureResponse(
                    status = "200 OK",
                    body = "{}",
                    headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                ),
            )
            coordinator.submit(
                request.copy(
                    targetStableKey = "t3_post123|post",
                    parentFullname = "t3_post123",
                    text = "Ambiguous response",
                ),
            )
            val unknown = coordinator.uiState.value
            assertEquals(CommentWritePhase.OUTCOME_UNKNOWN, unknown.phase)
            assertFalse(unknown.retryable)

            coordinator.reset()
            directory.selectAccount(otherAccountId)
            coordinator.submit(request.copy(text = "Must not reach localhost"))
            val changed = coordinator.uiState.value
            assertEquals(CommentWritePhase.FAILED, changed.phase)
            assertEquals(CommentWriteFailureKind.ACCOUNT_CHANGED, changed.failureKind)
            assertEquals(3, server.requestsSnapshot().size)

            server.close()
            server.assertHealthy()
            assertEquals(1, capability.completedCallbacks)
            assertEquals(1, loader.writebackCount)
            assertNotNull(loader.lastCacheKey)

            Log.i(
                WRITE01A3B_LOG_TAG,
                "WRITE01A3B_HTTPS_REQUESTS=3 EXPLICIT_AUTHORIZATION=true " +
                    "CALLBACK_RESUME=true PINNED_ACCOUNT=true T1_T3_TARGETS=true " +
                    "ROOM_THREAD_WRITEBACK=true RATE_LIMIT=true " +
                    "OUTCOME_UNKNOWN_NON_RETRYABLE=true ACCOUNT_SWITCH_FAIL_CLOSED=true " +
                    "APP_UI_LANGUAGE=ENGLISH LIVE_REDDIT_NETWORK=false " +
                    "REMOTE_COMMENT_WRITES=false",
            )
            Log.i(
                WRITE01A3B_LOG_TAG,
                "RESULT=SUBSHELL_WRITE01A3B_COMMENT_AUTH_SUBMIT_RUNTIME_PASS",
            )
        }
    }

    private fun writeSession(): StoredOAuthSession = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            accessToken = WRITE01A3B_ACCESS_TOKEN,
            refreshToken = "write01a3b-refresh",
            tokenType = "bearer",
            scope = "identity mysubreddits read submit",
            expiresAtEpochMs = 9_000_000L,
        ),
        identity = AuthAccountIdentity(
            providerAccountId = WRITE01A3B_PROVIDER_ACCOUNT_ID,
            username = "write01a3b_user",
        ),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        clientIdFingerprint = "write01a3b-fingerprint",
    )

    private fun decodeForm(body: String): Map<String, String> = body
        .split('&')
        .associate { field ->
            val separator = field.indexOf('=')
            require(separator > 0) { "Malformed WRITE01-A3B form field" }
            URLDecoder.decode(
                field.substring(0, separator),
                StandardCharsets.UTF_8.name(),
            ) to URLDecoder.decode(
                field.substring(separator + 1),
                StandardCharsets.UTF_8.name(),
            )
        }

    private fun comment(
        id: String,
        body: String,
        depth: Int,
        parentId: String,
    ): Comment = Comment(
        id = id,
        author = "write01a3b_user",
        age = "0m",
        body = body,
        score = 1,
        depth = depth,
        accent = 0xFFFF8A50L,
        parentId = parentId,
    )
}

private class Write01a3bCapability(
    private val accountStableId: String,
    private val session: StoredOAuthSession,
) : AuthCommentWriteCapability {
    var authorized = false
    var pending = false
    var callback = false
    var completedCallbacks = 0
    val requestedAccounts = mutableListOf<String>()

    override suspend fun beginAuthorization(accountStableId: String): String {
        require(accountStableId == this.accountStableId)
        pending = true
        callback = false
        return WRITE01A3B_AUTHORIZATION_URL
    }

    override fun hasPendingAuthorization(): Boolean = pending

    override fun hasStoredCallback(): Boolean = callback

    override fun cancelPendingAuthorization() {
        pending = false
        callback = false
    }

    fun acceptCallback() {
        require(pending)
        pending = false
        callback = true
    }

    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult {
        require(callback)
        callback = false
        authorized = true
        completedCallbacks += 1
        return AuthCommentWriteCapabilityResult.Upgraded(
            accountStableId = accountStableId,
            username = requireNotNull(session.identity).username,
        )
    }

    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
        requestedAccounts += accountStableId
        return if (authorized && accountStableId == this.accountStableId) {
            AuthSessionResult.Authenticated(session)
        } else {
            AuthSessionResult.ReauthenticationRequired
        }
    }
}

private class Write01a3bDirectory(
    private val accountId: String,
    private val otherAccountId: String,
) : AccountSessionDirectory {
    private var activeId = accountId
    private val accounts = listOf(
        account(accountId, "write01a3b_user"),
        account(otherAccountId, "other_user"),
    )

    override fun snapshot(): AccountSessionDirectorySnapshot =
        AccountSessionDirectorySnapshot(
            accounts = accounts,
            activeAccountId = activeId,
        )

    override fun selectAccount(stableId: String): AccountSessionDirectorySnapshot {
        require(accounts.any { it.stableId == stableId })
        activeId = stableId
        return snapshot()
    }

    private fun account(stableId: String, username: String): AccountSessionSummary =
        AccountSessionSummary(
            stableId = stableId,
            username = username,
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit · RedReader compatibility",
        )
}

private class Write01a3bCommentLoader(
    initial: List<Comment>,
) : AuthenticatedRedditCommentLoader {
    private var comments = initial
    var writebackCount = 0
    var lastCacheKey: String? = null

    override suspend fun cached(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult = loaded()

    override suspend fun synchronize(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
    ): AuthenticatedRedditCommentResult = loaded()

    override suspend fun loadMore(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        moreNodeId: String,
    ): AuthenticatedRedditCommentResult = loaded()

    override suspend fun cacheSubmittedComment(
        accountStableId: String,
        postId: String,
        sort: RedditCommentSort,
        parentFullname: String,
        comment: Comment,
    ): AuthenticatedRedditCommentResult {
        comments = requireNotNull(
            mergeSubmittedCommentIntoThread(
                current = comments,
                parentFullname = parentFullname,
                submitted = comment,
            ),
        )
        writebackCount += 1
        lastCacheKey = "$accountStableId|$postId|${sort.apiValue}"
        return loaded()
    }

    private fun loaded(): AuthenticatedRedditCommentResult =
        AuthenticatedRedditCommentResult.Loaded(
            comments = comments,
            fromCache = false,
        )
}

private const val WRITE01A3B_LOG_TAG = "subshell-write01a3b"
private const val WRITE01A3B_PROVIDER_ACCOUNT_ID = "t2_write01a3b"
private const val WRITE01A3B_ACCESS_TOKEN = "write01a3b-access"
private const val WRITE01A3B_AUTHORIZATION_URL =
    "https://www.reddit.com/api/v1/authorize.compact?state=write01a3b"
private const val WRITE01A3B_USER_AGENT =
    "android:io.github.brealorg.subshell.debug.dev2:0.65.3 (localhost fixture)"
private const val CREATED_REPLY_JSON = """
{
  "json": {
    "errors": [],
    "data": {
      "things": [
        {
          "kind": "t1",
          "data": {
            "id": "createda3b",
            "name": "t1_createda3b",
            "author": "write01a3b_user",
            "body": "Created after explicit authorization",
            "score": 1,
            "created_utc": 9970,
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
