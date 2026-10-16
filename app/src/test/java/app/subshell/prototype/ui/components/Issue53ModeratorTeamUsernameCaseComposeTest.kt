package app.subshell.prototype.ui.components

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditModeratorRemovalRequest
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE53_CANONICAL_MODERATOR_TARGET_V1 (#53): the moderator team lists
 * usernames in display case; the removal (and permission) request requires
 * canonical names, so "Remove moderator" did nothing for a mixed-case name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue53ModeratorTeamUsernameCaseComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun removingAMixedCaseModeratorSendsCanonicalNames() {
        var removed: RedditModeratorRemovalRequest? = null
        composeRule.setContent {
            SubshellTheme {
                ModeratorRemovalEditor(
                    accountStableId = "reddit:mod",
                    communityName = "AskReddit",
                    target = CommunityModerator(username = "Mixed_Case", permissions = listOf("posts")),
                    avatarUri = null,
                    state = ModeratorRemovalUiState(),
                    onRemove = { request -> removed = request },
                    onAuthorize = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Remove moderator") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("Remove moderator") and hasClickAction()).onLast().performClick()
        composeRule.waitForIdle()

        assertEquals("mixed_case", removed?.username)
        assertEquals("askreddit", removed?.communityName)
    }
}
