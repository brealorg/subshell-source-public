package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostManagePhase
import app.subshell.prototype.data.account.PostManageSnapshot
import app.subshell.prototype.data.account.PostManageTarget
import app.subshell.prototype.data.account.PostManageUiState
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE96_CANCEL_WAITING_AUTHORIZATION_V1 (96b): Manage post can be left
 * (cancelling the authorization) while it waits for the browser or loads;
 * only saving on Reddit keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue96bPostManageCancelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private class Calls {
        var cancels = 0
        var closes = 0
    }

    private fun show(phase: PostManagePhase): Calls {
        val calls = Calls()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first().copy(id = "t3_abc123", author = "me")
        val target = PostManageTarget(
            accountStableId = account.stableId,
            postFullname = "t3_abc123",
            postAuthor = "me",
            community = post.community.lowercase(),
        )
        composeRule.setContent {
            PostManageScreen(
                post = post,
                state = state,
                // Authorization starts after a change to loaded settings.
                manageState = PostManageUiState(
                    phase = phase,
                    target = target,
                    snapshot = PostManageSnapshot(
                        postFullname = "t3_abc123",
                        author = "me",
                        community = target.community,
                    ),
                ),
                onLoad = {},
                onMutate = {},
                onAuthorize = {},
                onCancelAuthorization = { calls.cancels += 1 },
                onReset = {},
                onClose = { calls.closes += 1 },
            )
        }
        composeRule.waitForIdle()
        return calls
    }

    @Test
    fun waitingForTheBrowserBackLeavesAndCancelsTheAuthorization() {
        val calls = show(PostManagePhase.AWAITING_CALLBACK)
        assertEquals(1, composeRule.onAllNodesWithText("Cancel authorization").fetchSemanticsNodes().size)

        composeRule.onNode(hasContentDescription("Back")).performClick()
        composeRule.waitForIdle()

        assertEquals(1, calls.closes)
        assertEquals(true, calls.cancels >= 1)
    }

    @Test
    fun savingOnRedditKeepsTheScreen() {
        show(PostManagePhase.SENDING)

        composeRule.onNode(hasContentDescription("Back")).assertIsNotEnabled()
        assertEquals(0, composeRule.onAllNodesWithText("Cancel authorization").fetchSemanticsNodes().size)
    }

    @Test
    fun onlySavingLocksClosing() {
        assertEquals(
            listOf(PostManagePhase.SENDING),
            PostManagePhase.entries.filter(::postManageCloseLocked),
        )
    }
}
