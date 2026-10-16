package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.components.PostCard
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
 * STEP3 (a11y01a1, a11y01a11): with large system text the app's bottom
 * navigation grows from 64 dp to 88 dp and keeps every destination's full
 * name for TalkBack; the Home header's sort button grows with its text
 * instead of clipping it; a post card's vote and comment actions are at
 * least 48 dp touch targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3A11yLargeTextComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedInState() = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun app(): SubshellState {
        ensureWorkManagerInitialized(context)
        val state = signedInState()
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(matcher: SemanticsMatcher, unmerged: Boolean = false) =
        composeRule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().size

    // A: the bar at the system's own text size.
    @Test
    fun theBottomNavigationIs64DpAtTheNormalTextSize() {
        app()

        composeRule.onNodeWithTag("app-bottom-bar", useUnmergedTree = true).assertHeightIsEqualTo(64.dp)
    }

    // A: large system text grows the bar and keeps the full names.
    @Test
    @Config(fontScale = 2f)
    fun largeTextGrowsTheBottomNavigationAndKeepsTheFullNames() {
        app()

        composeRule.onNodeWithTag("app-bottom-bar", useUnmergedTree = true).assertHeightIsEqualTo(88.dp)
        assertTrue(count(hasContentDescription("Communities")) >= 1)
        assertEquals(0, count(hasContentDescription("Subs")))
        assertEquals(0, count(hasText("Subs"), unmerged = true))
    }

    // B b: the Home header's sort button grows with large text.
    @Test
    @Config(fontScale = 2f)
    fun theHomeSortButtonGrowsWithLargeText() {
        val state = app()

        val sortButton = composeRule.onAllNodes(
            hasClickAction() and hasText(state.feedSort.label, substring = true),
        ).fetchSemanticsNodes().minByOrNull { it.boundsInRoot.top }
            ?: error("no Home sort button")
        val heightDp = with(composeRule.density) { sortButton.boundsInRoot.height.toDp() }

        assertTrue("sort button is $heightDp tall at fontScale 2", heightDp > 48.dp)
    }

    // B a: a post card's vote and comment actions are 48 dp targets.
    @Test
    fun aPostCardsVoteAndCommentActionsAre48DpTargets() {
        ensureWorkManagerInitialized(context)
        val state = signedInState()
        val post = SampleData.posts.first().copy(id = "t3_targets", score = 5, vote = 0)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()

        for (name in listOf("Upvote", "Downvote")) {
            val button = composeRule.onAllNodes(hasContentDescription(name) and hasClickAction()).onFirst()
            button.assertWidthIsAtLeast(48.dp)
            button.assertHeightIsAtLeast(48.dp)
        }
        val comments = composeRule.onAllNodesWithContentDescription("Comments", substring = true)
            .onFirst()
        comments.assertWidthIsAtLeast(48.dp)
        comments.assertHeightIsAtLeast(48.dp)
    }
}
