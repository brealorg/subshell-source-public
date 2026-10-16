package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.auth.AuthAccountIdentity
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModmailResult
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthTokenSet
import app.subshell.prototype.data.auth.StoredOAuthSession
import app.subshell.prototype.data.auth.reddit.RedditModmailLoader
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CORRECT01: low-severity data/network correctness findings (OCB-509, 544,
 * 548, 555, 636, 638, 639).
 */
class Correct01Test {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun countsJustBelowAMillionRoundToTheNextUnit() {
        assertEquals("999.9k", 999_900L.compactCount())
        assertEquals("1m", 999_960L.compactCount())
        assertEquals("1b", 999_960_000L.compactCount())
        assertEquals("12.3k", 12_345L.compactCount())
    }

    @Test
    fun followedUsersAreNotListedAsJoinedCommunities() {
        val page = lenientJson.decodeFromString(
            RedditSubscriptionListingDto.serializer(),
            """{"data":{"children":[
                {"kind":"t5","data":{"name":"t5_a","display_name":"kotlin","subreddit_type":"public"}},
                {"kind":"t5","data":{"name":"t5_b","display_name":"u_someone","subreddit_type":"user"}}
            ]}}""",
        ).toNetworkPage()

        assertEquals(listOf("kotlin"), page.items.map { it.community.name })
    }

    @Test
    fun aModNoteOnlyRemovalSendsANullReasonId() {
        val request = redditAttachRemovalReasonRequest("t3_abc", reasonId = null, modNote = "note")
        val json = Json.parseToJsonElement(request.fields.toMap().getValue("json")).jsonObject

        assertEquals(JsonNull, json["reason_id"])
    }

    @Test
    fun aSubmittedCommentKeepsTheAuthorsOwnVote() {
        val payload = lenientJson.decodeFromString(
            RedditCommentSubmitResponseDto.serializer(),
            """{"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{
                "id":"c1","name":"t1_c1","author":"me","body":"hi","score":1,"likes":true,
                "saved":true,"created_utc":9940.0,"depth":0,"parent_id":"t3_p"}}]}}}""",
        ).toSubmitPayload(nowEpochSeconds = 10_000L) as RedditCommentSubmitPayload.Created

        assertEquals(1, payload.comment.vote)
        assertTrue(payload.comment.saved)
    }

    @Test
    fun oneUnreadableConversationDoesNotEndModmailPaging() {
        val conversations = (1..3).associate { n ->
            val id = "abc$n"
            id to RedditModmailConversationDto(
                id = id,
                subject = "Subject $n",
                owner = if (n == 2) null else RedditModmailSubredditDto(displayName = "kotlin"),
                lastUpdated = "2026-10-03T10:00:00.000000+00:00",
            )
        }
        val page = RedditModmailConversationsDto(
            conversationIds = listOf("abc1", "abc2", "abc3"),
            conversations = conversations,
        ).toConversationPage(limit = 3)

        assertEquals(2, page.items.size)
        assertEquals("abc3", page.nextCursor)
    }

    @Test
    fun aBadOrRepeatedFlairTemplateDoesNotFailManagePost() = runBlocking {
        val client = client { path ->
            when {
                path.endsWith("/api/info") ->
                    """{"data":{"children":[{"data":{"name":"t3_abc","author":"me","subreddit":"kotlin"}}]}}"""
                else ->
                    """[{"id":"f1","text":"${"x".repeat(80)}"},{"id":"f2","text":"Ok"},{"id":"f2","text":"Ok again"}]"""
            }
        }

        val result = RedditPostManageNetworkDataSource(client).load("t3_abc", flairScopeGranted = true)

        result as RedditPostManageLoadNetworkResult.Loaded
        assertEquals(listOf("f2"), result.snapshot.flairOptions.map { it.templateId })
    }

    @Test
    fun aFailedUnreadCountStillLoadsTheModmailList() = runBlocking {
        val calls = OkHttpClient.Builder().addInterceptor(
            answering { path ->
                when {
                    path.endsWith("/conversations/subreddits") ->
                        200 to """{"subreddits":{"t5_a":{"display_name":"kotlin"}}}"""
                    path.endsWith("/unread/count") -> 503 to "{}"
                    else -> 200 to """{"conversationIds":["abc1"],"conversations":{"abc1":{"id":"abc1",
                        "subject":"Hi","owner":{"displayName":"kotlin"},
                        "lastUpdated":"2026-10-03T10:00:00.000000+00:00"}}}"""
                }
            },
        ).build()
        val loader = RedditModmailLoader(
            capability = SignedIn,
            callFactory = calls,
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            userAgent = "subshell-test",
            ioDispatcher = Dispatchers.Unconfined,
        )

        val result = loader.load("reddit:me", after = null)

        result as AuthenticatedRedditModmailResult.Loaded
        assertEquals(1, result.conversations.size)
        assertNull(result.unreadCounts)
    }

    private fun client(body: (String) -> String) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor(answering { path -> 200 to body(path) }).build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )

    private fun answering(answer: (String) -> Pair<Int, String>) = Interceptor { chain ->
        val (code, body) = answer(chain.request().url.encodedPath)
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("answer")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private object SignedIn : AuthCommentWriteCapability {
        private val session = StoredOAuthSession(
            provider = OAuthProvider.REDDIT,
            tokens = OAuthTokenSet("token", null, "bearer", "modmail", Long.MAX_VALUE),
            identity = AuthAccountIdentity("me", "me"),
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )
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
