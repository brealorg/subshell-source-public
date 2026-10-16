package app.subshell.prototype.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostManageMutationRequest
import app.subshell.prototype.data.account.PostManageOperation
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
 * STEP3 (write02f): Manage post offers post flair, NSFW, spoiler and reply
 * notifications, and each switch sends the opposite of the post's current
 * value for its own operation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PostManageScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun eachSwitchSendsTheOppositeOfThePostsCurrentValue() {
        val sent = mutableListOf<PostManageMutationRequest>()
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first().copy(id = "t3_abc123", author = "me")
        val target = PostManageTarget(account.stableId, "t3_abc123", "me", post.community.lowercase())
        composeRule.setContent {
            PostManageScreen(
                post = post,
                state = state,
                manageState = PostManageUiState(
                    phase = PostManagePhase.IDLE,
                    target = target,
                    snapshot = PostManageSnapshot(
                        postFullname = "t3_abc123",
                        author = "me",
                        community = target.community,
                        nsfw = false,
                        spoiler = true,
                        sendReplies = true,
                    ),
                ),
                onLoad = {},
                onMutate = { request -> sent += request },
                onAuthorize = {},
                onCancelAuthorization = {},
                onReset = {},
                onClose = {},
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Post flair").fetchSemanticsNodes().size)

        listOf("NSFW", "Spoiler", "Reply notifications").forEach { label ->
            composeRule.onNode(hasClickAction() and hasText(label)).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }

        assertEquals(
            listOf(
                PostManageOperation.SET_NSFW to true,
                PostManageOperation.SET_SPOILER to false,
                PostManageOperation.SET_SEND_REPLIES to false,
            ),
            sent.map { it.operation to it.enabled },
        )
    }
}
