package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditRemovalMessage
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalReasonFailureKind
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.network.NetworkEndpoint
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The removal transaction is three writes at most — remove, attach reason,
 * send response — in that order, over the non-retrying client, each reported
 * on its own. Only the removal decides success; a failed response or reason
 * is a warning on a successful removal, never a retry and never a silent pass.
 */
class ModResponse01A1RemovalMessageWriterTest {
    private val config = OAuthProviderConfiguration(
        provider = OAuthProvider.REDDIT,
        authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
        tokenEndpoint = "https://example.test/token".toHttpUrl(),
        revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
        clientId = "test-client",
        redirectUri = "test://callback",
        scopes = setOf("identity", "modposts"),
        permanentAccess = true,
    )
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, config.clientMode, "me")

    @Test
    fun removeThenReasonThenPublicMessageInOrderAndAllReported() = runBlocking {
        val network = ScriptedCallFactory { _ -> 200 to "{}" }
        val result = writer(network).mutate(
            request(
                reasonId = "reason-1",
                message = RedditRemovalMessage(RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT, "  Rule 1.  "),
            ),
        )

        assertEquals(
            AuthenticatedRedditModerationActionResult.Updated(
                removalReasonWarning = false,
                removalMessageOutcome = RedditRemovalStepOutcome.SENT,
            ),
            result,
        )
        assertEquals(
            listOf("api/remove", "api/v1/modactions/removal_reasons", "api/v1/modactions/removal_link_message"),
            network.paths(),
        )
        val body = network.bodies().last()
        assertTrue(body.contains("api_type=json"))
        assertTrue(body.contains("%22item_id%22%3A%5B%22t3_abc%22%5D"))
        assertTrue(body.contains("%22message%22%3A%22Rule+1.%22"))
        assertTrue(body.contains("%22type%22%3A%22public_as_subreddit%22"))
    }

    @Test
    fun commentTargetsUseTheCommentEndpointAndNoMessageMeansNoRequest() = runBlocking {
        val network = ScriptedCallFactory { _ -> 200 to "{}" }
        val sent = writer(network).mutate(
            request(fullname = "t1_xyz", message = RedditRemovalMessage(RedditRemovalMessageType.PRIVATE, "body", "title")),
        )
        assertEquals(RedditRemovalStepOutcome.SENT, (sent as AuthenticatedRedditModerationActionResult.Updated).removalMessageOutcome)
        assertEquals(listOf("api/remove", "api/v1/modactions/removal_comment_message"), network.paths())

        val quiet = ScriptedCallFactory { _ -> 200 to "{}" }
        val plain = writer(quiet).mutate(request())
        assertEquals(RedditRemovalStepOutcome.NOT_REQUESTED, (plain as AuthenticatedRedditModerationActionResult.Updated).removalMessageOutcome)
        assertEquals(listOf("api/remove"), quiet.paths())
    }

    @Test
    fun failedResponseIsAWarningOnASuccessfulRemovalAndIsNotRetried() = runBlocking {
        val network = ScriptedCallFactory { request ->
            if (request.url.encodedPath.endsWith("removal_link_message")) 403 to "{}" else 200 to "{}"
        }
        val result = writer(network).mutate(
            request(message = RedditRemovalMessage(RedditRemovalMessageType.PUBLIC, "body")),
        )
        assertEquals(
            AuthenticatedRedditModerationActionResult.Updated(
                removalReasonWarning = false,
                removalMessageOutcome = RedditRemovalStepOutcome.FAILED,
            ),
            result,
        )
        assertEquals(listOf("api/remove", "api/v1/modactions/removal_link_message"), network.paths())
    }

    @Test
    fun failedRemovalSendsNothingElse() = runBlocking {
        val network = ScriptedCallFactory { _ -> 500 to "{}" }
        val result = writer(network).mutate(
            request(reasonId = "r", message = RedditRemovalMessage(RedditRemovalMessageType.PUBLIC, "body")),
        )
        assertTrue(result is AuthenticatedRedditModerationActionResult.Failed)
        assertEquals(listOf("api/remove"), network.paths())
    }

    // STEP3 (mod01a2, mod01a21): a removal reason that Reddit refuses is a
    // warning on a removal that already happened; nothing is sent again.
    @Test
    fun aRefusedRemovalReasonIsAWarningAndNothingIsRepeated() = runBlocking {
        val network = ScriptedCallFactory { request ->
            if (request.url.encodedPath.endsWith("removal_reasons")) 500 to "{}" else 200 to "{}"
        }

        val result = writer(network).mutate(request(reasonId = "reason-1"))

        assertEquals(
            AuthenticatedRedditModerationActionResult.Updated(
                removalReasonWarning = true,
                removalMessageOutcome = RedditRemovalStepOutcome.NOT_REQUESTED,
            ),
            result,
        )
        assertEquals(listOf("api/remove", "api/v1/modactions/removal_reasons"), network.paths())
    }

    // STEP3 (mod01a2): a removal Reddit answers with 401 asks to authorize, 403
    // is a refusal that is not retryable, a 5xx may have happened; each is one
    // request.
    @Test
    fun aRemovalAnswerIsReadOnceAndNamedByItsStatus() = runBlocking {
        val unauthorized = ScriptedCallFactory { _ -> 401 to "{}" }
        assertEquals(
            AuthenticatedRedditModerationActionResult.AuthorizationRequired,
            writer(unauthorized).mutate(request()),
        )
        assertEquals(listOf("api/remove"), unauthorized.paths())

        val forbidden = ScriptedCallFactory { _ -> 403 to "{}" }
        val refused = writer(forbidden).mutate(request()) as AuthenticatedRedditModerationActionResult.Failed
        assertEquals(RedditModeratorActionFailureKind.AUTHORIZATION, refused.kind)
        assertEquals(false, refused.retryable)
        assertEquals(listOf("api/remove"), forbidden.paths())

        val unavailable = ScriptedCallFactory { _ -> 503 to "{}" }
        val unknown = writer(unavailable).mutate(request()) as AuthenticatedRedditModerationActionResult.Failed
        assertEquals(RedditModeratorActionFailureKind.OUTCOME_UNKNOWN, unknown.kind)
        assertEquals(listOf("api/remove"), unavailable.paths())
    }

    // STEP3 (mod01a21): a removal reason belongs to a removal only.
    @Test
    fun anApprovalCannotCarryARemovalReason() {
        assertThrows {
            RedditModeratorActionRequest(
                accountStableId = account,
                communityName = "norge",
                thingFullname = "t3_abc",
                action = RedditModeratorAction.APPROVE,
                removalReasonId = "reason-1",
            )
        }
    }

    // STEP3 (mod01a21): reading removal reasons asks to authorize on 401 and is
    // a refusal (not an authorization prompt) on 403.
    @Test
    fun removalReasonsAskToAuthorizeOn401AndAreRefusedOn403() = runBlocking {
        val unauthorized = ScriptedCallFactory { _ -> 401 to "{}" }
        assertEquals(
            AuthenticatedRedditRemovalReasonResult.AuthorizationRequired,
            reasonLoader(unauthorized).load(account, "norge"),
        )
        assertEquals(listOf("api/v1/norge/removal_reasons"), unauthorized.paths())

        val forbidden = ScriptedCallFactory { _ -> 403 to "{}" }
        val refused = reasonLoader(forbidden).load(account, "norge") as AuthenticatedRedditRemovalReasonResult.Failed
        assertEquals(RedditRemovalReasonFailureKind.AUTHORIZATION, refused.kind)
        assertEquals(1, forbidden.paths().size)
    }

    private fun reasonLoader(callFactory: Call.Factory) = ConfiguredRedditRemovalReasonLoader(
        capability = AuthorizedCapability(config, account),
        callFactory = callFactory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )

    @Test
    fun messageIsOnlyValidOnRemoveAndNeedsABody() {
        assertThrows { RedditRemovalMessage(RedditRemovalMessageType.PUBLIC, "   ") }
        assertThrows { RedditRemovalMessage(RedditRemovalMessageType.PUBLIC, "x".repeat(10_001)) }
        assertThrows { RedditRemovalMessage(RedditRemovalMessageType.PRIVATE, "body", "t".repeat(101)) }
        assertThrows {
            RedditModeratorActionRequest(
                accountStableId = account,
                communityName = "norge",
                thingFullname = "t3_abc",
                action = RedditModeratorAction.APPROVE,
                removalMessage = RedditRemovalMessage(RedditRemovalMessageType.PUBLIC, "body"),
            )
        }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private fun request(
        fullname: String = "t3_abc",
        reasonId: String? = null,
        message: RedditRemovalMessage? = null,
    ) = RedditModeratorActionRequest(
        accountStableId = account,
        communityName = "norge",
        thingFullname = fullname,
        action = RedditModeratorAction.REMOVE,
        removalReasonId = reasonId,
        removalMessage = message,
    )

    private fun writer(callFactory: Call.Factory) = ConfiguredRedditModerationWriter(
        capability = AuthorizedCapability(config, account),
        callFactory = callFactory,
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        userAgent = "subshell-test",
        activeAccountStableId = { account },
    )
}

private class AuthorizedCapability(
    private val config: OAuthProviderConfiguration,
    private val account: String,
) : AuthCommentWriteCapability {
    override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
    override fun hasPendingAuthorization(): Boolean = false
    override fun hasStoredCallback(): Boolean = false
    override fun cancelPendingAuthorization() = Unit
    override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
        throw UnsupportedOperationException()
    override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
        AuthSessionResult.Authenticated(
            StoredOAuthSession(
                provider = OAuthProvider.REDDIT,
                tokens = OAuthTokenSet("test-token", null, "bearer", "identity modposts", Long.MAX_VALUE),
                identity = AuthAccountIdentity("me", "test-user"),
                clientMode = config.clientMode,
                clientIdFingerprint = config.clientIdFingerprint,
            ),
        )
}

private class ScriptedCallFactory(
    private val script: (Request) -> Pair<Int, String>,
) : Call.Factory {
    private val requests = CopyOnWriteArrayList<Pair<Request, String>>()
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { buffer -> request.body?.writeTo(buffer) }.readUtf8()
            requests += request to body
            val (code, response) = script(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(response.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    fun paths(): List<String> = requests.map { (request, _) -> request.url.encodedPath.trim('/') }
    fun bodies(): List<String> = requests.map { (_, body) -> body }

    override fun newCall(request: Request): Call = client.newCall(request)
}
