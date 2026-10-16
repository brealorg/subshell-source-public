package app.subshell.prototype.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a5a6, a5a7): a full moderator may edit the permissions of
 * another moderator whose permissions Reddit reported, never their own; a
 * change Reddit acknowledged reloads the list from Reddit instead of editing
 * it locally (the removed moderator stays until the reload); Back closes the
 * permission editor, but not while the change is being sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorTeamComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:me"
    private val community = "subshell"
    private val loads = mutableListOf<Boolean>()
    private var dismissedPermissionEditors = 0
    private var permission by mutableStateOf(ModeratorPermissionUpdateUiState())
    private var removal by mutableStateOf(ModeratorRemovalUiState())
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun show() {
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
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
                            CommunityModerator("unknown_mod", emptyList()),
                        ),
                    ),
                    loaded = true,
                ),
                avatarImageUri = { null },
                onEnsureIdentity = {},
                onLoad = { force -> loads += force },
                inviteState = ModeratorInviteUiState(),
                onOpenInviteContext = {},
                onInvite = {},
                onAuthorizeInvite = {},
                onDismissInvite = {},
                permissionUpdateState = permission,
                onOpenPermissionUpdateContext = {},
                onUpdatePermissions = {},
                onAuthorizePermissionUpdate = {},
                onDismissPermissionUpdate = { dismissedPermissionEditors += 1 },
                removalState = removal,
                onOpenRemovalContext = {},
                onRemoveModerator = {},
                onAuthorizeRemoval = {},
                onDismissRemoval = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun openPermissionEditor() {
        composeRule.onAllNodes(hasClickAction() and hasText("Edit permissions")).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun onlyAnotherModeratorWithKnownPermissionsCanBeEdited() {
        show()

        assertEquals(1, composeRule.onAllNodes(hasClickAction() and hasText("Edit permissions")).fetchSemanticsNodes().size)
    }

    @Test
    fun anAcknowledgedPermissionChangeReloadsFromReddit() {
        show()
        openPermissionEditor()
        val forcedBefore = loads.count { it }

        composeRule.runOnIdle {
            permission = ModeratorPermissionUpdateUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = "other_mod",
                completionRevision = 1L,
                lastUpdatedUsername = "other_mod",
            )
        }
        composeRule.waitForIdle()

        assertEquals(forcedBefore + 1, loads.count { it })
        assertEquals(1, shown("Reddit acknowledged the permission change for u/other_mod."))
    }

    @Test
    fun anAcknowledgedRemovalReloadsAndKeepsTheModeratorUntilThen() {
        show()
        val forcedBefore = loads.count { it }

        composeRule.runOnIdle {
            removal = ModeratorRemovalUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = "other_mod",
                completionRevision = 1L,
                lastRemovedUsername = "other_mod",
            )
        }
        composeRule.waitForIdle()

        assertEquals(forcedBefore + 1, loads.count { it })
        assertEquals(1, shown("Moderator removed"))
        assertTrue(shown("other_mod") >= 2)
    }

    @Test
    fun backClosesThePermissionEditorButNotWhileTheChangeIsSent() {
        show()
        openPermissionEditor()
        composeRule.runOnIdle {
            permission = ModeratorPermissionUpdateUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = "other_mod",
                sending = true,
            )
        }
        composeRule.waitForIdle()

        back()
        assertEquals(0, dismissedPermissionEditors)

        composeRule.runOnIdle { permission = permission.copy(sending = false) }
        composeRule.waitForIdle()
        back()
        assertEquals(1, dismissedPermissionEditors)
    }

    @Test
    fun removalDoesNotDependOnTheOrderRedditListsModerators() {
        val owner = CommunityModerator("owner", listOf("all"))
        val target = CommunityModerator("first_listed", listOf("posts"))

        assertTrue(moderatorTeamCanRemove(listOf(target, owner), "owner", target))
        assertTrue(moderatorTeamCanRemove(listOf(owner, target), "owner", target))
    }
}
