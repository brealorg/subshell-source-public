package app.subshell.prototype.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.RedditModerationUiFailureKind
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionFailureKind
import app.subshell.prototype.data.account.RedditModeratorActionPhase
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorActionUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalReason
import app.subshell.prototype.data.account.RedditRemovalReasonPhase
import app.subshell.prototype.data.account.RedditRemovalReasonUiState
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mod01a1, mod01a2, mod01a21): a community's mod queue in the mod
 * tools sheet. Remove asks first and names the post or comment; a missing
 * moderator grant asks for it only for this community; failures name the
 * Posts permission; a removal that could not attach its reason says so; the
 * queue pages on its own and a failed page offers Retry; removal reasons keep
 * Reddit's order, travel with the request, and a missing grant for them still
 * lets the moderator remove without one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorQueueSheetComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:mod"
    private val post = RedditModeratorQueueItem(
        fullname = "t3_abc123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "someone",
        summary = "A reported post",
    )
    private val comment = RedditModeratorQueueItem(
        fullname = "t1_x",
        kind = RedditModeratorQueueKind.COMMENT,
        communityName = "subshell",
        author = "someone",
        summary = "A reported comment",
    )
    private val moderated = mutableListOf<RedditModeratorActionRequest>()
    private var authorizeActions = 0
    private var authorizeReasons = 0
    private var appends = 0

    private fun queue(
        items: List<RedditModeratorQueueItem> = listOf(post),
        nextCursor: String? = null,
        loading: Boolean = false,
        failure: RedditModerationUiFailureKind? = null,
        httpStatusCode: Int? = null,
    ) = RedditModerationUiState(
        accountStableId = account,
        queueCommunityName = "subshell",
        queueItems = items,
        queueLoaded = true,
        queueLoading = loading,
        queueNextCursor = nextCursor,
        queueFailureKind = failure,
        queueFailureHttpStatusCode = httpStatusCode,
    )

    private fun show(
        uiState: () -> RedditModerationUiState = { queue() },
        actionUiState: () -> RedditModeratorActionUiState = { RedditModeratorActionUiState() },
        removalReasonState: () -> RedditRemovalReasonUiState = { RedditRemovalReasonUiState() },
    ) {
        composeRule.setContent {
            SubshellTheme {
                ModeratorToolsSheet(
                    communityLabel = "r/subshell",
                    communityName = "subshell",
                    accountStableId = account,
                    uiState = uiState(),
                    onDismiss = {},
                    onRefresh = {},
                    onAppend = { appends += 1 },
                    actionUiState = actionUiState(),
                    onModerate = { moderated += it },
                    onAuthorizeAction = { authorizeActions += 1 },
                    removalReasonState = removalReasonState(),
                    onLoadRemovalReasons = {},
                    onAuthorizeRemovalReasons = { authorizeReasons += 1 },
                    savedResponses = SavedResponseActions(
                        responses = emptyList(),
                        onSave = { _, _ -> null },
                        onDelete = {},
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countContaining(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun scrollToKey(key: String) {
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey(key)
        composeRule.waitForIdle()
    }

    @Test
    fun removeAsksFirstAndSendsOneRemovalForThePost() {
        show()

        click("Remove")
        assertEquals(1, count("Remove this post?"))
        assertEquals(emptyList<RedditModeratorActionRequest>(), moderated)

        // The dialog's own confirm is the last "Remove" with a click action.
        composeRule.onAllNodes(hasText("Remove") and hasClickAction()).fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.size >= 2)
        }
        composeRule.onAllNodes(hasText("Remove") and hasClickAction())
            .let { it[it.fetchSemanticsNodes().size - 1] }
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, moderated.size)
        assertEquals(RedditModeratorAction.REMOVE, moderated.single().action)
        assertEquals("t3_abc123", moderated.single().thingFullname)
        assertEquals("subshell", moderated.single().communityName)
        assertEquals(account, moderated.single().accountStableId)
    }

    @Test
    fun aCommentIsNamedAsAComment() {
        show(uiState = { queue(items = listOf(comment)) })

        click("Remove")

        assertEquals(1, count("Remove this comment?"))
        assertEquals(0, count("Remove this post?"))
    }

    @Test
    fun aMissingModeratorGrantAsksOnlyForThisCommunity() {
        var action by mutableStateOf(
            RedditModeratorActionUiState(
                phase = RedditModeratorActionPhase.AUTHORIZATION_REQUIRED,
                request = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.APPROVE),
            ),
        )
        show(actionUiState = { action })

        assertEquals(1, count("Moderator authorization required"))
        click("Authorize")
        assertEquals(1, authorizeActions)

        composeRule.runOnIdle {
            action = action.copy(
                request = RedditModeratorActionRequest(account, "other", "t3_abc123", RedditModeratorAction.APPROVE),
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, count("Moderator authorization required"))
    }

    @Test
    fun aDeniedActionAndADeniedQueueNameThePostsPermission() {
        show(
            uiState = { queue(failure = RedditModerationUiFailureKind.HTTP, httpStatusCode = 403) },
            actionUiState = {
                RedditModeratorActionUiState(
                    phase = RedditModeratorActionPhase.FAILED,
                    request = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.REMOVE),
                    failureKind = RedditModeratorActionFailureKind.AUTHORIZATION,
                    httpStatusCode = 403,
                )
            },
        )

        assertEquals(1, count("Mod queue"))
        assertEquals(
            1,
            count("Reddit denied this moderation action. Your moderator role may not include the required Posts permission."),
        )
        assertEquals(1, count("Reddit denied this mod queue. Your moderator role may not include the Posts permission."))
    }

    @Test
    fun aRemovalWhoseReasonCouldNotBeConfirmedSaysSo() {
        show(
            actionUiState = {
                RedditModeratorActionUiState(
                    phase = RedditModeratorActionPhase.SUCCEEDED,
                    request = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.REMOVE),
                    removalReasonWarning = true,
                )
            },
        )

        assertEquals(1, count("Item removed"))
        assertEquals(1, count(removalPartialSuccessMessage(true, RedditRemovalStepOutcome.NOT_REQUESTED)))
    }

    @Test
    fun theQueuePagesOnItsOwnAndAFailedPageOffersRetry() {
        var state by mutableStateOf(queue(nextCursor = "c1"))
        show(uiState = { state })
        scrollToKey("modqueue-more-c1")
        assertEquals(1, appends)

        composeRule.runOnIdle { state = queue(nextCursor = "c1", loading = true) }
        composeRule.waitForIdle()
        assertEquals(1, appends)

        composeRule.runOnIdle {
            state = queue(nextCursor = "c1", failure = RedditModerationUiFailureKind.TRANSPORT)
        }
        composeRule.waitForIdle()
        scrollToKey("modqueue-more-c1")
        assertEquals(1, appends)
        assertEquals(1, count("Couldn't load more of the queue"))

        composeRule.onAllNodes(hasText("Retry") and hasClickAction())
            .let { it[it.fetchSemanticsNodes().size - 1] }
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(2, appends)
    }

    @Test
    fun aChosenRemovalReasonAndModNoteTravelWithTheRemoval() {
        show(
            removalReasonState = {
                RedditRemovalReasonUiState(
                    phase = RedditRemovalReasonPhase.LOADED,
                    accountStableId = account,
                    communityName = "subshell",
                    reasons = listOf(
                        RedditRemovalReason("reason-b", "Rule 2", "Second"),
                        RedditRemovalReason("reason-a", "Rule 1", "First"),
                    ),
                )
            },
        )
        click("Remove")

        // Reddit's order is kept: Rule 2 before Rule 1.
        val titles = composeRule.onAllNodesWithText("Rule ", substring = true).fetchSemanticsNodes()
            // A reason row is one control: its title comes first, then the message.
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
        assertEquals(listOf("Rule 2", "Rule 1"), titles.filter { it == "Rule 1" || it == "Rule 2" })

        click("Rule 1")
        composeRule.onAllNodes(hasSetTextAction() and hasText("Mod note (optional)")).onFirst()
            .performTextInput("spam ring")
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("Remove") and hasClickAction())
            .let { it[it.fetchSemanticsNodes().size - 1] }
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("reason-a", moderated.single().removalReasonId)
        assertEquals("spam ring", moderated.single().modNote)
    }

    @Test
    fun noRemovalReasonSendsNone() {
        show(
            removalReasonState = {
                RedditRemovalReasonUiState(
                    phase = RedditRemovalReasonPhase.LOADED,
                    accountStableId = account,
                    communityName = "subshell",
                    reasons = listOf(RedditRemovalReason("reason-a", "Rule 1", "First")),
                )
            },
        )
        click("Remove")
        click("No removal reason")
        composeRule.onAllNodes(hasText("Remove") and hasClickAction())
            .let { it[it.fetchSemanticsNodes().size - 1] }
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertNull(moderated.single().removalReasonId)
        assertNull(moderated.single().modNote)
    }

    @Test
    fun aMissingGrantForReasonsAsksForItAndStillAllowsRemoving() {
        show(
            removalReasonState = {
                RedditRemovalReasonUiState(
                    phase = RedditRemovalReasonPhase.AUTHORIZATION_REQUIRED,
                    accountStableId = account,
                    communityName = "subshell",
                )
            },
        )
        click("Remove")

        assertEquals(1, count("You can still remove without a reason."))
        click("Authorize removal reasons")
        assertEquals(1, authorizeReasons)
        assertTrue(countContaining("modcontributors") >= 1)

        val confirm = composeRule.onAllNodes(hasText("Remove") and hasClickAction())
            .let { it[it.fetchSemanticsNodes().size - 1] }
        assertNull(confirm.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled))
        confirm.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, moderated.size)
        assertNull(moderated.single().removalReasonId)
    }
}
