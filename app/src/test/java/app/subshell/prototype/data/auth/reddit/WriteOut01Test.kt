package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.ModeratorPermissionUpdateFailureKind
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.authenticatedAccountId
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.reddit.RedditModeratorPermissionNetworkDataSource
import app.subshell.prototype.data.network.reddit.RedditModeratorPermissionNetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WRITEOUT01: writes whose answer is lost or refused report what really
 * happened (OCB-488, 489, 490, 510, 541, 542, 551).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WriteOut01Test {
    private val account = authenticatedAccountId(OAuthProvider.REDDIT, OAuthClientMode.REDREADER_COMPATIBILITY, "me")
    private val endpoint = NetworkEndpoint.https("https://oauth.reddit.test/")
    private val stillThere =
        """{"data":{"children":[{"kind":"t1","data":{"name":"t1_abc","author":"me","body":"hello"}}]}}"""
    private val deletedPlaceholder =
        """{"data":{"children":[{"kind":"t1","data":{"name":"t1_abc","author":"[deleted]","body":"[deleted]"}}]}}"""

    @Test
    fun aDeleteWhoseAnswerWasLostIsReadBackAndFoundDeleted() = runBlocking {
        val result = deleter(deleteAnswer = 503 to "{}", info = deletedPlaceholder).delete(request())

        assertTrue("$result", result is AuthenticatedCommentDeleteResult.Deleted)
    }

    @Test
    fun aDeleteWhoseAnswerWasLostAndTheCommentIsStillThereMayBeRetried() = runBlocking {
        val result = deleter(deleteAnswer = 503 to "{}", info = stillThere).delete(request())

        result as AuthenticatedCommentDeleteResult.Failed
        assertEquals(AuthenticatedCommentDeleteFailureKind.HTTP, result.kind)
        assertTrue(result.retryable)
    }

    @Test
    fun aRefusedDeleteIsARefusalNotAnUnknownOutcome() = runBlocking {
        val result = deleter(
            deleteAnswer = 200 to """{"json":{"errors":[["NOT_AUTHOR","not yours","id"]]}}""",
            info = stillThere,
        ).delete(request())

        result as AuthenticatedCommentDeleteResult.Failed
        assertEquals(AuthenticatedCommentDeleteFailureKind.HTTP, result.kind)
        assertFalse(result.retryable)
        assertEquals("NOT_AUTHOR", result.failureDetails?.providerCode?.value)
    }

    @Test
    fun modmailArchiveAndReadStateFlagAnUnknownOutcomeOn5xx() = runBlocking {
        val writer = ConfiguredRedditModmailWriter(
            capability = SignedIn(session()),
            callFactory = client { _ -> 503 to "{}" },
            endpoint = endpoint,
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )

        val archive = writer.setArchived(AuthenticatedRedditModmailArchiveRequest(account, "abc1", archived = true))
        val read = writer.setReadState(AuthenticatedRedditModmailReadStateRequest(account, "abc1", unread = false))

        assertTrue("$archive", (archive as AuthenticatedRedditModmailArchiveResult.Failed).outcomeUnknown)
        assertTrue("$read", (read as AuthenticatedRedditModmailReadStateResult.Failed).outcomeUnknown)
    }

    @Test
    fun aModeratorPermissionChangeAnswered5xxHasAnUnknownOutcome() = runBlocking {
        val source = RedditModeratorPermissionNetworkDataSource(
            SubshellHttpClient(
                callFactory = client { _ -> 502 to "{}" },
                endpoint = endpoint,
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test",
            ),
        )

        val result = source.update(
            RedditModeratorPermissionUpdateRequest(
                accountStableId = account,
                communityName = "kotlin",
                username = "someone",
                allPermissions = true,
            ),
        )

        assertEquals(
            ModeratorPermissionUpdateFailureKind.OUTCOME_UNKNOWN,
            (result as RedditModeratorPermissionNetworkResult.Failed).kind,
        )
    }

    @Test
    fun anAcceptedChatSendWithoutAReadableEventIdHasNoEventId() {
        assertNull(redditChatMatrixAcceptedEventId(Json.parseToJsonElement("""{"event_id":{"x":1}}""").jsonObject))
        assertNull(redditChatMatrixAcceptedEventId(null))
        assertEquals(
            "\$abc:reddit.com",
            redditChatMatrixAcceptedEventId(Json.parseToJsonElement("""{"event_id":"${'$'}abc:reddit.com"}""").jsonObject),
        )
    }

    private fun request() = AuthenticatedCommentDeleteRequest(accountStableId = account, commentFullname = "t1_abc")

    private fun deleter(deleteAnswer: Pair<Int, String>, info: String) = ConfiguredRedditCommentDeleter(
        capability = SignedIn(session()),
        callFactory = client { path -> if (path.endsWith("/api/del")) deleteAnswer else 200 to info },
        endpoint = endpoint,
        userAgent = "subshell-test",
    )

    private fun client(answer: (String) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor(
        Interceptor { chain ->
            val (code, body) = answer(chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("answer")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        },
    ).build()

    private fun session() = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet(
            "test-token",
            null,
            "bearer",
            (RedditOAuth.liveCommentEditScopes + RedditOAuth.coreReadScopes).joinToString(" "),
            Long.MAX_VALUE,
        ),
        identity = AuthAccountIdentity("me", "test-user"),
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
    )

    private class SignedIn(private val session: StoredOAuthSession) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String = throw UnsupportedOperationException()
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.Authenticated(session)
    }
}
