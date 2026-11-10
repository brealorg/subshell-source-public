package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a5): a reply nested deeper than the indentation shows says
 * whom it answers ("↳ name", past the sixth level since REPLYCONTEXT02; #285 first used the third), and replies still to come load by
 * themselves — the thread has no "Continue thread" row and no "N more" count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3DeepRepliesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val post: Post = SampleData.posts.first().copy(id = "t3_deep", comments = 9)

    private fun comment(id: String, author: String, depth: Int, children: List<Comment> = emptyList()) = Comment(
        id = id,
        author = author,
        age = "1h",
        body = "Comment by $author",
        score = 1,
        depth = depth,
        accent = 0xFF336699,
        children = children,
    )

    private val moreNode = Comment(
        id = "more_deep",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = "t1_a",
        moreChildIds = listOf("t1_x", "t1_y", "t1_z", "t1_w", "t1_v"),
        moreCount = 5,
    )

    private val thread = listOf(
        comment(
            "t1_a", "anna", 0,
            listOf(
                comment(
                    "t1_b", "bert", 1,
                    listOf(
                        comment(
                            "t1_c", "carl", 2,
                            listOf(
                                comment(
                                    "t1_d", "dina", 3,
                                    listOf(
                                        comment(
                                            "t1_e", "eve", 4,
                                            listOf(
                                                comment(
                                                    "t1_f", "finn", 5,
                                                    listOf(
                                                        comment(
                                                            "t1_g", "gus", 6,
                                                            listOf(
                                                                comment(
                                                                    "t1_h", "hal", 7,
                                                                    listOf(comment("t1_i", "ida", 8)),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                moreNode,
            ),
        ),
    )

    @Test
    fun aDeepReplySaysWhomItAnswersAndNoContinueRowOrCountIsShown() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = account.stableId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = thread,
                        fullDiscussionLoaded = true,
                        contentRevision = 1,
                    ),
                    onLoadMoreRedditComments = { _, _, _, _ -> true },
                )
            }
        }
        composeRule.waitForIdle()

        fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        val deep = androidx.compose.ui.test.hasText("Comment by ida", substring = true)
        val lists = composeRule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
        for (index in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[index].performScrollToNode(deep) }.isSuccess) break
        }
        composeRule.waitForIdle()
        assertEquals(1, shown("Comment by ida"))
        // ISSUE285_DEEP_REPLIES_SAY_WHO_THEY_ANSWER_V1 / REPLYCONTEXT02: only past the
        // indent cap (depth 7 = hal and 8 = ida); the levels the indent shows say nothing.
        assertEquals(1, shown("↳ hal"))
        assertEquals(0, shown("↳ carl"))
        assertEquals(0, shown("↳ anna"))
        assertEquals(0, shown("replying to u/"))
        assertEquals(0, shown("Continue thread"))
        assertEquals(0, shown("5 more"))
    }
}
