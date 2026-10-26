package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommentResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationActionResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedRedditModerationWriter
import app.subshell.prototype.data.cache.SubshellCacheCodec
import app.subshell.prototype.data.network.reddit.RedditCommentListingDto
import app.subshell.prototype.data.network.reddit.marksRemoval
import app.subshell.prototype.data.network.reddit.toComments
import app.subshell.prototype.model.Comment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE226_REMOVED_COMMENT_IS_MARKED_V1 (#226, owner-reported): a comment a
 * moderator removed stayed in the thread unmarked. Reddit's `removed` /
 * `banned_by` are read and cached; a confirmed Remove/Spam marks the comment
 * in the open thread at once, Approve unmarks it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue226RemovedCommentTest {
    private val account = "reddit:test"
    private val post = "abc123"
    private val sort = RedditCommentSort.BEST
    private val json = Json { ignoreUnknownKeys = true }

    // Parsing.

    private fun parse(extra: String): Comment {
        val body = """
            [{"kind":"Listing","data":{"children":[]}},
             {"kind":"Listing","data":{"children":[{"kind":"t1","data":{
               "id":"c1","name":"t1_c1","author":"someone","body":"hi","created_utc":1700000000.0$extra
             }}]}}]
        """.trimIndent()
        return json.decodeFromString<List<RedditCommentListingDto>>(body)
            .toComments(json = json, nowEpochSeconds = 1_700_000_100L, limit = 100, maxDepth = 8)
            .single()
    }

    @Test
    fun redditsRemovalFieldsMarkTheComment() {
        assertFalse(parse("").removedByModerator)
        assertTrue(parse(""","removed":true""").removedByModerator)
        assertTrue(parse(""","banned_by":"a_mod"""").removedByModerator)
        assertTrue(parse(""","banned_by":true""").removedByModerator)
        assertFalse(parse(""","banned_by":null,"removed":false""").removedByModerator)
    }

    @Test
    fun onlyARealRemoverMarksRemoval() {
        assertFalse(null.marksRemoval())
        assertFalse(JsonNull.marksRemoval())
        assertFalse(JsonPrimitive(false).marksRemoval())
        assertFalse(JsonPrimitive(" ").marksRemoval())
        assertTrue(JsonPrimitive(true).marksRemoval())
        assertTrue(JsonPrimitive("a_mod").marksRemoval())
    }

    // Cache codec.

    @Test
    fun theMarkSurvivesTheCacheAndOlderPayloadsReadAsNotRemoved() {
        val removed = comment("c1").copy(removedByModerator = true)
        assertTrue(SubshellCacheCodec.decodeComment(SubshellCacheCodec.encodeComment(removed)).removedByModerator)

        val current = SubshellCacheCodec.encodeComment(comment("c1"))
        assertTrue(current.endsWith("5:false"))
        val beforeIssue226 = current.removeSuffix("5:false")
        val decoded = SubshellCacheCodec.decodeComment(beforeIssue226)
        assertFalse(decoded.removedByModerator)
        assertEquals(comment("c1"), decoded)
    }

    // The open thread.

    @Test
    fun aConfirmedRemoveMarksTheNestedCommentAndApproveUnmarksIt() {
        val writer = FakeWriter(AuthenticatedRedditModerationActionResult.Updated())
        val viewModel = viewModel(writer)
        viewModel.reading().loadRedditComments(account, post, sort)
        idle()

        moderation.moderateRedditQueueItem(request("t1_c2", RedditModeratorAction.REMOVE))
        idle()
        assertTrue(child().removedByModerator)
        assertFalse(viewModel.reading().redditCommentsUiState.value.comments.single().removedByModerator)

        moderation.moderateRedditQueueItem(request("t1_c2", RedditModeratorAction.APPROVE))
        idle()
        assertFalse(child().removedByModerator)

        moderation.moderateRedditQueueItem(request("t1_c2", RedditModeratorAction.SPAM))
        idle()
        assertTrue(child().removedByModerator)
    }

    @Test
    fun anUnknownOutcomeOrAPostLeavesTheThreadAlone() {
        val writer = FakeWriter(
            AuthenticatedRedditModerationActionResult.Failed(
                kind = RedditModeratorActionFailureKind.OUTCOME_UNKNOWN,
                retryable = false,
            ),
        )
        val viewModel = viewModel(writer)
        viewModel.reading().loadRedditComments(account, post, sort)
        idle()

        moderation.moderateRedditQueueItem(request("t1_c2", RedditModeratorAction.REMOVE))
        idle()
        assertFalse(child().removedByModerator)

        writer.result = AuthenticatedRedditModerationActionResult.Updated()
        moderation.moderateRedditQueueItem(request("t3_$post", RedditModeratorAction.REMOVE))
        idle()
        assertFalse(child().removedByModerator)
    }

    private lateinit var current: AuthConnectViewModel
    private lateinit var moderation: ModerationViewModel
    private fun child() = current.reading().redditCommentsUiState.value.comments.single().children.single()

    private fun request(thing: String, action: RedditModeratorAction) = RedditModeratorActionRequest(
        accountStableId = account,
        communityName = "subshell",
        thingFullname = thing,
        action = action,
    )

    private fun viewModel(writer: AuthenticatedRedditModerationWriter): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                redditCommentLoader = ThreadLoader(),
                moderationActionWriter = writer,
                accountSessionDirectory = ActiveDirectory(account),
            ),
        ).also {
            current = it
            it.withAreaViewModels() // AUTHSPLIT05: the thread lives in ReadingViewModel
            // AUTHSPLIT01: the action is ModerationViewModel's; the thread
            // learns of it through the shared runtime.
            moderation = ModerationViewModel(it.sharedRuntime)
        }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun comment(id: String, children: List<Comment> = emptyList()) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "body $id",
        score = 1,
        depth = 0,
        accent = 0L,
        children = children,
    )

    private inner class ThreadLoader : AuthenticatedRedditCommentLoader {
        private val thread = listOf(comment("c1", children = listOf(comment("c2"))))
        override suspend fun cached(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = true)
        override suspend fun synchronize(accountStableId: String, postId: String, sort: RedditCommentSort) =
            AuthenticatedRedditCommentResult.Loaded(comments = thread, fromCache = false)
        override suspend fun loadMore(accountStableId: String, postId: String, sort: RedditCommentSort, moreNodeId: String): AuthenticatedRedditCommentResult =
            error("not used")
        override suspend fun cacheSubmittedComment(
            accountStableId: String,
            postId: String,
            sort: RedditCommentSort,
            parentFullname: String,
            comment: Comment,
        ): AuthenticatedRedditCommentResult = error("not used")
    }

    private class FakeWriter(var result: AuthenticatedRedditModerationActionResult) : AuthenticatedRedditModerationWriter {
        override suspend fun mutate(request: RedditModeratorActionRequest): AuthenticatedRedditModerationActionResult = result
    }

    private class ActiveDirectory(private val active: String) : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(
            accounts = listOf(
                AccountSessionSummary(
                    stableId = active,
                    username = "mod_user",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            activeAccountId = active,
        )

        override fun selectAccount(stableId: String) = snapshot()
    }
}
