package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostVoteRequest
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (vote01): a signed-in upvote on a post card sends one request for
 * that post, shows the new score at once and holds the vote buttons (not
 * Save) until Reddit answers; removing a vote counts from Reddit's own vote;
 * a comment's upvote targets the comment; Compact mode puts the score into
 * the comment header; Settings changes the vote controls and colours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3VoteComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:voter",
        username = "voter",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val requests = mutableListOf<PostVoteRequest>()

    private fun signedIn(): SubshellState {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.setAuthenticatedPostVoteRequester { request -> requests += request }
        return state
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun card(state: SubshellState, post: app.subshell.prototype.model.Post) {
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUpvoteSendsOneRequestShowsTheNewScoreAndHoldsOnlyTheVoteButtons() {
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_abc123", score = 41, vote = 0)
        card(state, post)

        composeRule.onAllNodesWithContentDescription("Upvote").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(PostVoteRequest(account.stableId, "t3_abc123", 1)), requests)
        assertEquals(1, shown("42"))
        composeRule.onAllNodesWithContentDescription("Remove upvote").onFirst().assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Save").onFirst().assertIsEnabled()
    }

    @Test
    fun removingAVoteCountsFromRedditsOwnVote() {
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_voted", score = 10, vote = 1)
        card(state, post)

        composeRule.onAllNodesWithContentDescription("Remove upvote").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(PostVoteRequest(account.stableId, "t3_voted", 0)), requests)
        assertEquals(1, shown("9"))
    }

    @Test
    fun aCommentUpvoteTargetsTheCommentAndCompactModeShowsTheScoreInTheHeader() {
        val state = signedIn()
        state.updateVoteControlMode(VoteControlMode.COMPACT)
        val post = SampleData.posts.first().copy(id = "t3_thread", comments = 1)
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = listOf(
                Comment(id = "t1_mine", author = "someone", age = "3h", body = "A comment", score = 7, depth = 0, accent = 0xFF336699),
            ),
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(post = post, state = state, onBack = {}, redditCommentsState = comments)
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("7 · 3h", substring = true).fetchSemanticsNodes().isNotEmpty())

        composeRule.onAllNodes(hasContentDescription("Upvote")).fetchSemanticsNodes().size.let { buttons ->
            assertTrue("post and comment vote buttons: $buttons", buttons >= 2)
        }
        composeRule.onAllNodes(hasContentDescription("Upvote"))[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(PostVoteRequest(account.stableId, "t1_mine", 1)), requests)
    }

    @Test
    fun settingsChangesTheVoteControlsAndColours() {
        val state = SubshellState(initialInteractionSideConfigured = true)
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = repository,
                    onBack = {},
                    initialDestinationName = SettingsDestination.APPEARANCE.name,
                )
            }
        }
        composeRule.waitForIdle()

        fun choose(row: String, option: String) {
            composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(row))
            composeRule.onNodeWithText(row).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(option).onFirst().performClick()
            composeRule.waitForIdle()
        }

        choose("Vote controls", "Compact")
        assertEquals(VoteControlMode.COMPACT, state.voteControlMode)
        choose("Vote colours", "Red & blue")
        assertEquals(VoteColorPalette.RED_BLUE, state.voteColorPalette)
    }
}
