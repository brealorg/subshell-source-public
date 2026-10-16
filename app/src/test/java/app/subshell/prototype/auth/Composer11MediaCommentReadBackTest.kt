package app.subshell.prototype.auth

import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWriteMediaAttachment
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommentSubmitResult
import app.subshell.prototype.data.network.reddit.redditCommentReplyDiagnostic
import app.subshell.prototype.data.network.reddit.redditCommentSubmitResponse
import app.subshell.prototype.data.network.reddit.redditCommentSubmitReplyJson
import app.subshell.prototype.data.network.NetworkRateLimit
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitDataDto
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitJsonDto
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitPayload
import app.subshell.prototype.data.network.reddit.RedditCommentSubmitResponseDto
import app.subshell.prototype.data.network.reddit.RedditCommentThingDto
import app.subshell.prototype.data.network.reddit.redditCommentBodyStillProcessing
import app.subshell.prototype.data.network.reddit.toSubmitPayload
import app.subshell.prototype.model.Comment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMPOSER11_MEDIA_COMMENT_READ_BACK_V1: an image comment Reddit accepted
 * while the image was still processing is read back until the image is
 * resolved, then replaced in the thread; it used to stay "Processing img"
 * until a manual refresh.
 */
class Composer11MediaCommentReadBackTest {
    private val account = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private var activeAccount: String = account.stableId
    private val directory = object : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(
            listOf(account, account.copy(stableId = "reddit:other", username = "other")),
            activeAccount,
        )
        override fun selectAccount(stableId: String) = snapshot()
    }
    private val request = CommentWriteRequest(
        accountStableId = account.stableId,
        postId = "abc",
        targetStableKey = "post:abc",
        parentFullname = "t3_abc",
        text = "",
        sort = RedditCommentSort.BEST,
        media = CommentWriteMediaAttachment("content://media/1", "image/png"),
    )
    private val processing = "*Processing img k2x9...*"
    private val resolved = "![img](k2x9 \"subshell-media https://i.redd.it/k2x9.png\")"

    private var submittedBody = processing
    private val readBacks = ArrayDeque<String?>()
    private var reads = 0
    private val cachedComments = mutableListOf<Comment>()
    private val shownThreads = mutableListOf<List<Comment>>()

    private val loader = object : AuthenticatedRedditCommentLoader {
        private fun loaded() = AuthenticatedRedditCommentResult.Loaded(comments = cachedComments.toList(), fromCache = false)
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) = loaded()
        override suspend fun loadMore(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            moreNodeId: String,
        ) = loaded()
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ): AuthenticatedRedditCommentResult {
            cachedComments.removeAll { it.id == comment.id }
            cachedComments.add(comment)
            return loaded()
        }
        override suspend fun readCommentBody(accountStableId: String, commentFullname: String): String? {
            reads += 1
            return readBacks.removeFirstOrNull()
        }
    }

    private fun coordinator() = CommentWriteCoordinator(
        capability = null,
        submitter = { _ ->
            AuthenticatedCommentSubmitResult.Submitted(
                accountStableId = account.stableId,
                comment = Comment(
                    id = "t1_new",
                    author = "alice",
                    age = "now",
                    body = submittedBody,
                    score = 1,
                    depth = 0,
                    accent = 0xFF000000,
                    parentId = "t3_abc",
                ),
                metadata = NetworkResponseMetadata(
                    statusCode = 200,
                    receivedAtEpochMs = 0L,
                    rateLimit = NetworkRateLimit(remaining = null, used = null, resetAfterMs = null),
                    retryAfterMs = null,
                ),
            )
        },
        commentLoader = loader,
        accountDirectory = directory,
        clientMode = null,
        onThreadUpdated = { _, comments -> shownThreads += comments },
    )

    @Test
    fun aProcessingImageIsReadBackUntilResolvedAndThenShown() = runBlocking {
        readBacks += listOf(null, processing, resolved)
        val writes = coordinator()
        writes.submit(request)
        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)
        assertEquals(processing, shownThreads.last().single().body)

        writes.followProcessingMediaComment(delaysMs = listOf(0L, 0L, 0L, 0L))

        assertEquals(3, reads)
        assertEquals(resolved, shownThreads.last().single().body)
        assertEquals(resolved, cachedComments.single().body)
    }

    @Test
    fun anImageResolvedAtOnceOrATextCommentIsNotReadBack() = runBlocking {
        submittedBody = resolved
        val writes = coordinator()
        writes.submit(request)
        writes.followProcessingMediaComment(delaysMs = listOf(0L))

        submittedBody = "Just text"
        writes.submit(request.copy(text = "Just text", media = null))
        writes.followProcessingMediaComment(delaysMs = listOf(0L))

        assertEquals(0, reads)
    }

    @Test
    fun itStopsAfterTheLastTryOrWhenTheAccountChanges() = runBlocking {
        readBacks += listOf(processing, processing)
        val writes = coordinator()
        writes.submit(request)
        writes.followProcessingMediaComment(delaysMs = listOf(0L, 0L))
        assertEquals(2, reads)
        assertEquals(processing, shownThreads.last().single().body)

        readBacks += resolved
        writes.submit(request.copy(targetStableKey = "post:abc:2"))
        activeAccount = "reddit:other"
        writes.followProcessingMediaComment(delaysMs = listOf(0L))
        assertEquals(2, reads)
    }

    @Test
    fun anImageOnlyCommentWithAnEmptyBodyIsPostedAndReadBack() = runBlocking {
        submittedBody = ""
        readBacks += listOf("", resolved)
        val writes = coordinator()
        writes.submit(request)
        assertEquals(CommentWritePhase.SUCCEEDED, writes.uiState.value.phase)

        writes.followProcessingMediaComment(delaysMs = listOf(0L, 0L, 0L))

        assertEquals(2, reads)
        assertEquals(resolved, shownThreads.last().single().body)
    }

    @Test
    fun redditsReplyWithACommentIdButNoBodyYetIsACreatedComment() {
        val response = RedditCommentSubmitResponseDto(
            json = RedditCommentSubmitJsonDto(
                data = RedditCommentSubmitDataDto(
                    things = listOf(
                        RedditCommentThingDto(
                            kind = "t1",
                            data = RedditCommentDataDto(id = "new", name = "t1_new", body = ""),
                        ),
                    ),
                ),
            ),
        )
        val payload = response.toSubmitPayload(10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.Created)
        assertEquals("t1_new", (payload as RedditCommentSubmitPayload.Created).comment.id)
    }

    @Test
    fun aReplyWithNullsWhereAFinishedCommentHasValuesIsStillReadable() {
        val reply = """{"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{
            "id":"new","name":"t1_new","author":"alice","body":null,"score":null,"depth":null,
            "media_metadata":{"k2x9":{"status":"unprocessed","p":null}}}}]}}}"""
        val json = redditCommentSubmitReplyJson(SubshellNetworkJson.providerJson)
        val payload = json.decodeFromString(RedditCommentSubmitResponseDto.serializer(), reply)
            .toSubmitPayload(10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.Created)
        assertEquals("t1_new", (payload as RedditCommentSubmitPayload.Created).comment.id)
        assertTrue(
            runCatching {
                SubshellNetworkJson.providerJson.decodeFromString(RedditCommentSubmitResponseDto.serializer(), reply)
            }.isFailure,
        )
    }

    @Test
    fun anUnreadableReplyIsDescribedByKeysAndCountsNeverValues() {
        val reply = SubshellNetworkJson.providerJson.parseToJsonElement(
            """{"json":{"errors":[],"data":{"things":[]}},"secret_value":"do-not-show"}""",
        )
        val diagnostic = redditCommentReplyDiagnostic(reply, null)
        assertTrue(diagnostic, diagnostic.contains("keys [json, secret_value]"))
        assertTrue(diagnostic, diagnostic.contains("things 0"))
        assertFalse(diagnostic, diagnostic.contains("do-not-show"))
    }

    @Test
    fun aRichtextCommentAnsweredWithTheFlatCommentIsCreated() {
        val json = redditCommentSubmitReplyJson(SubshellNetworkJson.providerJson)
        val flat = SubshellNetworkJson.providerJson.parseToJsonElement(
            """{"all_awardings":[],"approved":false,"author":"alice","body":"","id":"new",
            "name":"t1_new","parent_id":"t3_abc","media_metadata":{"k2x9":{"status":"unprocessed"}},
            "rtjson":{"document":[]}}""",
        )
        val payload = redditCommentSubmitResponse(json, flat).toSubmitPayload(10_000L)
        assertTrue(payload is RedditCommentSubmitPayload.Created)
        assertEquals("t1_new", (payload as RedditCommentSubmitPayload.Created).comment.id)

        val wrapped = SubshellNetworkJson.providerJson.parseToJsonElement(
            """{"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{"id":"txt","name":"t1_txt","body":"Hi"}}]}}}""",
        )
        assertEquals(
            "t1_txt",
            (redditCommentSubmitResponse(json, wrapped).toSubmitPayload(10_000L) as RedditCommentSubmitPayload.Created)
                .comment.id,
        )
    }

    @Test
    fun processingIsRecognisedOnItsOwnLine() {
        assertTrue(redditCommentBodyStillProcessing("Look\n\n*Processing img k2x9...*"))
        assertFalse(redditCommentBodyStillProcessing(resolved))
        assertFalse(redditCommentBodyStillProcessing("I was Processing img files today"))
    }
}
