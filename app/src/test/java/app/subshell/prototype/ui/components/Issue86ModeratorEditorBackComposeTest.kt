package app.subshell.prototype.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE86_EDITOR_STAYS_WHILE_ITS_WRITE_RUNS_V1: system Back closed a
 * moderator editor while its write was in flight, so the outcome (even an
 * unknown one) was never shown and the write could be sent again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue86ModeratorEditorBackComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = "reddit:me"
    private val community = "subshell"
    private var removal by mutableStateOf(ModeratorRemovalUiState())
    private var dismissals = 0
    private lateinit var dispatcher: OnBackPressedDispatcher
    private val workspaceBusy = ModeratorWorkspaceBusy()

    private fun team() {
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CompositionLocalProvider(LocalModeratorWorkspaceBusy provides workspaceBusy) {
                ModeratorTeamContent(
                    accountStableId = account,
                    communityName = community,
                    activeUsername = "me_user",
                    canRead = true,
                    uiState = RedditCommunityInfoUiState(
                        accountStableId = account,
                        communityName = community,
                        snapshot = CommunityInfoSnapshot(
                            communityName = community,
                            moderators = listOf(
                                CommunityModerator("me_user", listOf("all")),
                                CommunityModerator("other_mod", listOf("posts")),
                            ),
                        ),
                        loaded = true,
                    ),
                    avatarImageUri = { null },
                    onEnsureIdentity = {},
                    onLoad = {},
                    inviteState = ModeratorInviteUiState(),
                    onOpenInviteContext = {},
                    onInvite = {},
                    onAuthorizeInvite = {},
                    onDismissInvite = {},
                    permissionUpdateState = ModeratorPermissionUpdateUiState(),
                    onOpenPermissionUpdateContext = {},
                    onUpdatePermissions = {},
                    onAuthorizePermissionUpdate = {},
                    onDismissPermissionUpdate = {},
                    removalState = removal,
                    onOpenRemovalContext = { username ->
                        removal = ModeratorRemovalUiState(
                            visible = true,
                            accountStableId = account,
                            communityName = community,
                            targetUsername = username,
                        )
                    },
                    onRemoveModerator = {},
                    onAuthorizeRemoval = {},
                    onDismissRemoval = { dismissals += 1 },
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backDoesNothingWhileTheRemovalRunsAndClosesAfterwards() {
        team()
        composeRule.onNodeWithText("Remove moderator").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { removal = removal.copy(removing = true) }
        composeRule.waitForIdle()
        assertTrue(workspaceBusy.busy)

        pressBack()
        assertEquals(0, dismissals)
        assertTrue(composeRule.onAllNodesWithText("Remove moderator").fetchSemanticsNodes().isNotEmpty())

        composeRule.runOnIdle { removal = removal.copy(removing = false) }
        composeRule.waitForIdle()
        assertFalse(workspaceBusy.busy)
        pressBack()
        assertEquals(1, dismissals)
    }
}
