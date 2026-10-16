package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalFailureKind
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.UserFlairSnapshot
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LOWUI6a: account-bound ui state and read-back after an unknown write. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi6aComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // LOWUI6_UNKNOWN_MODERATOR_WRITE_IS_READ_BACK_V1 (OCB-149)
    @Test
    fun anUnknownRemovalReloadsTheModeratorList() {
        val account = "reddit:me"
        val community = "subshell"
        var removal by mutableStateOf(ModeratorRemovalUiState())
        val loads = mutableListOf<Boolean>()
        composeRule.setContent {
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
                onLoad = { force -> loads += force },
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
                onOpenRemovalContext = {},
                onRemoveModerator = {},
                onAuthorizeRemoval = {},
                onDismissRemoval = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()
        val forcedBefore = loads.count { it }

        composeRule.runOnIdle {
            removal = ModeratorRemovalUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
                targetUsername = "other_mod",
                failureKind = ModeratorRemovalFailureKind.OUTCOME_UNKNOWN,
            )
        }
        composeRule.waitForIdle()

        assertEquals(forcedBefore + 1, loads.count { it })
    }

    // LOWUI6_FLAIR_SHEET_SHOWS_ONLY_ITS_OWN_STATE_V1 (OCB-166)
    @Test
    fun theFlairSheetDoesNotShowAnotherCommunitysFlair() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val me = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(me),
            initialActiveAccountId = me.stableId,
        )
        state.synchronizeUserFlairUiState(
            UserFlairUiState(
                accountStableId = me.stableId,
                communityName = "othercommunity",
                snapshot = UserFlairSnapshot(communityName = "othercommunity", currentText = "Other flair"),
                loaded = true,
            ),
        )

        composeRule.setContent {
            UserFlairSheet(state = state, communityName = "subshell", onDismiss = {})
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Other flair", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
