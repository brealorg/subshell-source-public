package io.github.brealorg.subshell.ui.components

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
import io.github.brealorg.subshell.data.account.RedditModerationUiFailureKind
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorActionPhase
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.RedditRemovalReason
import io.github.brealorg.subshell.data.account.RedditRemovalReasonPhase
import io.github.brealorg.subshell.data.account.RedditRemovalReasonUiState
import io.github.brealorg.subshell.data.account.RedditRemovalStepOutcome
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.error.rememberViewportErrorHostState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
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
        details: io.github.brealorg.subshell.data.error.FailureDetails? = null,
    ) = RedditModerationUiState(
        accountStableId = account,
        queueCommunityName = "subshell",
        queueItems = items,
        queueLoaded = true,
        queueLoading = loading,
        queueNextCursor = nextCursor,
        queueFailureKind = failure,
        queueFailureHttpStatusCode = httpStatusCode,
        queueFailureDetails = details,
    )

    // ISSUE295_QUEUE_USES_THE_SHEET_V1: the queue writes through the Moderate
    // sheet's entry points; the harness wires them as SubshellApp does.
    private val quick = ModeratorQuickActionsState()
    private lateinit var notices: ViewportErrorHostState

    private val selectedLists = mutableListOf<io.github.brealorg.subshell.data.account.RedditModeratorQueueList>()

    private fun show(
        uiState: () -> RedditModerationUiState = { queue() },
        actionUiState: () -> RedditModeratorActionUiState = { RedditModeratorActionUiState() },
        removalReasonState: () -> RedditRemovalReasonUiState = { RedditRemovalReasonUiState() },
    ) {
        composeRule.setContent {
            notices = rememberViewportErrorHostState()
            SubshellTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState provides notices,
                ) {
                ModeratorQuickActionsHarness(
                    accountStableId = account,
                    actionState = actionUiState(),
                    onModerate = { moderated += it },
                    notices = notices,
                    removalReasonState = removalReasonState(),
                    onAuthorizeRemovalReasons = { authorizeReasons += 1 },
                    onAuthorize = { authorizeActions += 1 },
                    quickActions = quick,
                ) {
                    ModeratorToolsSheet(
                        communityLabel = "r/subshell",
                        communityName = "subshell",
                        accountStableId = account,
                        uiState = uiState(),
                        onDismiss = {},
                        onRefresh = {},
                        onAppend = { appends += 1 },
                        actionUiState = actionUiState(),
                        onSelectList = { selectedLists += it },
                    )
                }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun trayTitle() = notices.currentRequest?.presentation?.title
    private fun trayMessage() = notices.currentRequest?.presentation?.message

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
        quick.awaiting = action.request
        show(actionUiState = { action })

        // ISSUE295_QUEUE_USES_THE_SHEET_V1: the same question every moderator action asks.
        assertEquals(1, count("Allow moderation"))
        click("Allow")
        assertEquals(1, authorizeActions)

        composeRule.runOnIdle {
            action = action.copy(
                request = RedditModeratorActionRequest(account, "other", "t3_abc123", RedditModeratorAction.APPROVE),
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, count("Allow moderation"))
    }

    @Test
    fun aDeniedActionAndADeniedQueueNameThePostsPermission() {
        quick.awaiting = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.REMOVE)
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
            "Reddit denied this moderation action. Your moderator role may not include the required Posts permission.",
            trayMessage(),
        )
        assertEquals(1, count("Reddit denied this mod queue. Your moderator role may not include the Posts permission."))
    }

    // BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1: Reddit's block
    // page is a 403 too, and is not the moderator role.
    @Test
    fun aQueueRedditBlocksSaysSoInsteadOfBlamingTheModeratorRole() {
        show(
            uiState = {
                queue(
                    failure = RedditModerationUiFailureKind.HTTP,
                    httpStatusCode = 403,
                    details = io.github.brealorg.subshell.data.error.FailureDetails(
                        httpStatusCode = 403,
                        retryable = false,
                        providerCode = io.github.brealorg.subshell.data.error.SafeFailureText.fromUntrusted(
                            io.github.brealorg.subshell.data.error.REDDIT_BLOCK_PAGE_REASON,
                        ),
                    ),
                )
            },
        )

        assertEquals(1, count("Reddit is blocking this request from third-party apps right now."))
        assertEquals(0, countContaining("moderator role"))
    }

    @Test
    fun aRemovalWhoseReasonCouldNotBeConfirmedSaysSo() {
        quick.awaiting = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.REMOVE)
        show(
            actionUiState = {
                RedditModeratorActionUiState(
                    phase = RedditModeratorActionPhase.SUCCEEDED,
                    request = RedditModeratorActionRequest(account, "subshell", "t3_abc123", RedditModeratorAction.REMOVE),
                    removalReasonWarning = true,
                )
            },
        )

        assertEquals("Post removed", trayTitle())
        // Drawn inside the Mod tools sheet's own window, not only behind it.
        assertEquals(1, count("Post removed"))
        assertEquals(removalPartialSuccessMessage(true, RedditRemovalStepOutcome.NOT_REQUESTED), trayMessage())
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

    // ISSUE295_REMOVED_CONTENT_LISTS_V1
    @Test
    fun theCommunitysListsAreChipsAndEachSaysWhatItIs() {
        val lists = io.github.brealorg.subshell.data.account.RedditModeratorQueueList.entries
        var state by mutableStateOf(queue(items = emptyList()))
        show(uiState = { state })

        for (list in listOf("Queue", "Removed", "Edited", "Unmoderated")) {
            assertEquals(list, 1, composeRule.onAllNodes(hasText(list) and hasClickAction()).fetchSemanticsNodes().size)
        }
        assertEquals(1, count("Mod queue is clear"))
        click("Queue")
        assertEquals(emptyList<Any>(), selectedLists)
        click("Removed")
        assertEquals(listOf(lists[1]), selectedLists)

        composeRule.runOnIdle { state = queue(items = emptyList()).copy(queueList = lists[1]) }
        composeRule.waitForIdle()
        assertEquals(1, count("Posts and comments removed by moderators or Reddit's filters. Approve puts one back."))
        assertEquals(1, count("Nothing removed"))
        assertEquals(0, count("Mod queue is clear"))

        // A removed item's card says nothing about reports when it has none.
        composeRule.runOnIdle { state = queue().copy(queueList = lists[1]) }
        composeRule.waitForIdle()
        assertEquals(1, count("A reported post"))
        assertEquals(0, count("No reports returned"))
    }
}
