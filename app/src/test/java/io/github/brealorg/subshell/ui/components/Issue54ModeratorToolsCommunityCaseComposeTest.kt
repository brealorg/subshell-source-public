package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.brealorg.subshell.data.account.RedditModerationUiState
import io.github.brealorg.subshell.data.account.RedditModeratorAction
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.ui.theme.SubshellTheme
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
                // ISSUE295_QUEUE_USES_THE_SHEET_V1: Approve writes through the
                // Moderate sheet's entry points, which ask with the canonical name.
                ModeratorQuickActionsHarness(
                    accountStableId = "reddit:mod",
                    actionState = io.github.brealorg.subshell.data.account.RedditModeratorActionUiState(),
                    onModerate = { request -> moderated = request },
                    notices = null,
                ) {
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
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText("Approve") and hasClickAction()).onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("askreddit", moderated?.communityName)
        assertEquals(RedditModeratorAction.APPROVE, moderated?.action)
    }
}
