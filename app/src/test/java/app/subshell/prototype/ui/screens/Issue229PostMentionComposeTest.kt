package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #229: replying in an open post, typing `@` lists the person answered first,
 * then the post's author, then the others in the thread; the thread's deleted
 * accounts are not offered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue229PostMentionComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val post = Post(
        id = "t3_mentions",
        community = "android",
        author = "alice",
        age = "1h",
        title = "A post with people",
        score = 1,
        comments = 3,
        kind = PostKind.TEXT,
    )
    private val bob = comment("bob", created = 100)
    private val thread = listOf(
        bob,
        comment("carol", created = 300),
        comment("[deleted]", created = 200),
    )

    @Test
    fun typingAtListsThePersonAnsweredThenTheAuthorThenTheThread() {
        ensureWorkManagerInitialized(context)
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = ThreadRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SubshellTheme { PostScreen(post = post, state = state, onBack = {}) }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.openCommentComposer(post, bob) }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Thanks @")
        composeRule.waitForIdle()

        val rows = composeRule.onAllNodesWithContentDescription("Mention u/", substring = true)
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() }
        assertEquals(listOf("Mention u/bob", "Mention u/alice", "Mention u/carol"), rows)
    }

    private fun comment(author: String, created: Long) = Comment(
        id = "t1_$author",
        author = author,
        age = "1h",
        body = "Hello from $author",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
        createdEpochSeconds = created,
    )

    private inner class ThreadRepository(
        private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
    ) : SubshellRepository by delegate {
        override fun commentsForPost(postId: String, request: RepositoryPageRequest): RepositoryResult<List<Comment>> =
            if (postId == post.id) RepositoryResult.Content(thread, nextCursor = null) else delegate.commentsForPost(postId, request)
    }
}
