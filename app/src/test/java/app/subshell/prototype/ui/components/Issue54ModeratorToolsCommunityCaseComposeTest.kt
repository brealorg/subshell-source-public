package app.subshell.prototype.ui.components

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE54_CANONICAL_MODERATED_COMMUNITY_V1 (#54): a community opened with its
 * display case ("AskReddit") made Approve and Remove build a
 * RedditModeratorActionRequest that rejects a non-canonical name, which threw
 * on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue54ModeratorToolsCommunityCaseComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun approveInAMixedCaseCommunitySendsTheCanonicalName() {
        val item = RedditModeratorQueueItem(
            fullname = "t3_abc123",
            kind = RedditModeratorQueueKind.POST,
            communityName = "askreddit",
            author = "someone",
            summary = "A reported post",
        )
        var moderated: RedditModeratorActionRequest? = null
        composeRule.setContent {
            SubshellTheme {
                ModeratorToolsSheet(
                    communityLabel = "r/AskReddit",
                    communityName = "AskReddit",
                    accountStableId = "reddit:mod",
                    uiState = RedditModerationUiState(
                        accountStableId = "reddit:mod",
                        queueCommunityName = "askreddit",
                        queueItems = listOf(item),
                        queueLoaded = true,
                    ),
                    onDismiss = {},
                    onRefresh = {},
                    onAppend = {},
                    onModerate = { request -> moderated = request },
                    savedResponses = SavedResponseActions(emptyList(), onSave = { _, _ -> null }, onDelete = {}),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText("Approve") and hasClickAction()).onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("askreddit", moderated?.communityName)
        assertEquals(RedditModeratorAction.APPROVE, moderated?.action)
    }
}
