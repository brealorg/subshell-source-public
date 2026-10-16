package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.MessageReplyChannel
import app.subshell.prototype.data.account.MessageReplyFailureKind
import app.subshell.prototype.data.account.MessageReplyRequest
import app.subshell.prototype.data.account.OnlineVisibilityConfirmation
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.account.OnlineVisibilityWriteResult
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.OnlineVisibilityNetwork
import app.subshell.prototype.data.network.reddit.RedditModerationActionNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditModerationActionNetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CORRECT04: OCB-612 (reaction relations follow next_batch), OCB-513 (an
 * account switch after the visibility PATCH is an unknown outcome), OCB-616
 * (a reply failing before it is sent is not "may have been sent"), OCB-635
 * (distinguish reads json.errors).
 */
class Correct04Test {
    private val own = "@t2_me:reddit.com"
    private val target = "\$target"

    @Test
    fun reactionRelationsAreReadToTheLastPage() = runBlocking {
        val pages = mapOf<String?, String>(
            null to relations(listOf("\$r1"), next = "b2"),
            "b2" to relations(listOf("\$r2"), next = null),
        )

        val read = redditChatMatrixReadReactionRelationPages(target, own) { from ->
            RedditChatMatrixRelationsAnswer(200, pages.getValue(from))
        }

        assertTrue(read.complete)
        assertEquals(listOf("\$r1", "\$r2"), read.page!!.reactions.map { it.reactionEventId })
    }

    @Test
    fun anUnreadLaterPageLeavesTheTargetNotAuthoritative() = runBlocking {
        val read = redditChatMatrixReadReactionRelationPages(target, own) { from ->
            if (from == null) RedditChatMatrixRelationsAnswer(200, relations(listOf("\$r1"), next = "b2")) else null
        }

        assertFalse(read.complete)
        assertEquals(listOf("\$r1"), read.page!!.reactions.map { it.reactionEventId })
    }

    @Test
    fun anAccountSwitchAfterTheVisibilityChangeWasSentIsAnUnknownOutcome() = runBlocking {
        val configuration = OAuthProviderConfiguration(
            provider = OAuthProvider.REDDIT,
            authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
            revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
            clientId = "client-id-123",
            redirectUri = "subshell-auth-test://oauth/callback",
            scopes = RedditOAuth.coreReadScopes,
            permanentAccess = true,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )
        val account = authenticatedAccountId(OAuthProvider.REDDIT, configuration.clientMode, "t2me")
        var switched = false
        val service = ConfiguredRedditOnlineVisibilityService(
            configuration = configuration,
            activeAccountId = { account },
            ensureSession = { _, scopes ->
                AuthSessionResult.Authenticated(
                    StoredOAuthSession(
                        provider = OAuthProvider.REDDIT,
                        tokens = OAuthTokenSet("t", null, "bearer", scopes.joinToString(" "), Long.MAX_VALUE),
                        identity = AuthAccountIdentity("t2me", "me"),
                        clientMode = configuration.clientMode,
                        clientIdFingerprint = configuration.clientIdFingerprint,
                    ),
                )
            },
            networkForSession = {
                object : OnlineVisibilityNetwork {
                    override suspend fun read() = OnlineVisibilityResult.Known(true)
                    override suspend fun patch(value: Boolean): OnlineVisibilityResult {
                        switched = true
                        return OnlineVisibilityResult.Known(value)
                    }
                }
            },
        )

        val result = service.change(OnlineVisibilityConfirmation(account, true, false, 1)) { !switched }

        assertEquals(OnlineVisibilityIssue.OUTCOME_UNKNOWN, (result as OnlineVisibilityWriteResult.Failed).issue)
    }

    @Test
    fun aReplyThatFailsBeforeItIsSentIsRetryable() = runBlocking {
        val writer = RedditMessageReplyWriter(
            callFactory = OkHttpClient(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            capability = object : AuthInboxReadCapability {
                override suspend fun beginAuthorization(accountStableId: String): String = error("unused")
                override suspend fun hasScopeGrant(accountStableId: String) = true
                override fun hasPendingAuthorization() = false
                override fun hasStoredCallback() = false
                override fun cancelPendingAuthorization() = Unit
                override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult = error("unused")
                override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
                    throw IllegalStateException("store fault before sending")
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = writer.send(
            MessageReplyRequest(
                accountStableId = "reddit:me",
                inboxItemId = "t4_abc",
                parentFullname = "t4_abc",
                channel = MessageReplyChannel.PRIVATE_MESSAGE_THREAD,
                text = "hello",
            ),
        )

        result as AuthenticatedRedditMessageReplyResult.Failed
        assertEquals(MessageReplyFailureKind.UNAVAILABLE, result.kind)
        assertTrue(result.retryable)
    }

    @Test
    fun aRefusedDistinguishIsARefusalNotASuccess() = runBlocking {
        val source = RedditModerationActionNetworkDataSource(
            SubshellHttpClient(
                callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body("""{"json":{"errors":[["NO_THING_ID","not found","id"]]}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }.build(),
                endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test",
            ),
        )

        val result = source.mutate(
            RedditModeratorActionRequest(
                accountStableId = "reddit:me",
                communityName = "kotlin",
                thingFullname = "t1_abc",
                action = RedditModeratorAction.DISTINGUISH,
            ),
        )

        assertEquals("NO_THING_ID", (result as RedditModerationActionNetworkResult.Rejected).code)
    }

    private fun relations(ids: List<String>, next: String?): String {
        val chunk = ids.joinToString(",") { id ->
            """{"type":"m.reaction","event_id":"$id","sender":"@t2_peer:reddit.com",
               "content":{"m.relates_to":{"rel_type":"m.annotation","event_id":"$target","key":"8r21ukpfa7081.gif"}}}"""
        }
        val nextPart = next?.let { ""","next_batch":"$it"""" }.orEmpty()
        return """{"chunk":[$chunk]$nextPart}"""
    }
}
