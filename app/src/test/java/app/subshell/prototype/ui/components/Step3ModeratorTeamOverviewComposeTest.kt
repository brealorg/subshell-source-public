package app.subshell.prototype.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.account.ModeratorInviteUiState
import app.subshell.prototype.data.account.ModeratorPermissionUpdateUiState
import app.subshell.prototype.data.account.ModeratorRemovalUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityModerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (moderation02a5a4): the moderators overview reads the list once when
 * it may, and not at all when it may not; it asks for at most three
 * moderator identities at a time; the search box filters the list locally,
 * keeping its order; a moderator whose permissions Reddit did not report
 * says so; the back arrow returns to the mod tools.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ModeratorTeamOverviewComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = "reddit:me"
    private val community = "subshell"
    private val loads = mutableListOf<Boolean>()
    private var backs = 0
    private var inFlight = 0
    private var maxInFlight = 0
    private val identityGate = kotlinx.coroutines.CompletableDeferred<Unit>()

    private fun show(canRead: Boolean, moderators: List<CommunityModerator>) {
        composeRule.setContent {
            ModeratorTeamContent(
                accountStableId = account,
                communityName = community,
                activeUsername = "me_user",
                canRead = canRead,
                uiState = RedditCommunityInfoUiState(
                    accountStableId = account,
                    communityName = community,
                    snapshot = CommunityInfoSnapshot(communityName = community, moderators = moderators),
                    loaded = true,
                ),
                avatarImageUri = { null },
                onEnsureIdentity = {
                    inFlight += 1
                    maxInFlight = maxOf(maxInFlight, inFlight)
                    identityGate.await()
                    inFlight -= 1
                },
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
                removalState = ModeratorRemovalUiState(),
                onOpenRemovalContext = {},
                onRemoveModerator = {},
                onAuthorizeRemoval = {},
                onDismissRemoval = {},
                onBack = { backs += 1 },
            )
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private val team = listOf(
        CommunityModerator("me_user", listOf("all")),
        CommunityModerator("alice", listOf("posts")),
        CommunityModerator("bob", emptyList()),
        CommunityModerator("malina", listOf("wiki")),
        CommunityModerator("carl", listOf("mail")),
        CommunityModerator("dora", listOf("flair")),
    )

    @Test
    fun theListIsReadOnceWhenItMayAndNotWhenItMayNot() {
        show(canRead = true, moderators = team)
        assertEquals(listOf(false), loads)
    }

    @Test
    fun aModeratorWithoutAGrantReadsNothing() {
        show(canRead = false, moderators = emptyList())
        assertEquals(emptyList<Boolean>(), loads)
    }

    // A tall window, so every moderator row is on screen and asks.
    @Test
    @Config(qualifiers = "w411dp-h2400dp")
    fun atMostThreeIdentitiesAreAskedForAtATime() {
        show(canRead = true, moderators = team)
        composeRule.waitForIdle()

        assertEquals(3, maxInFlight)
        identityGate.complete(Unit)
    }

    @Test
    fun theSearchFiltersLocallyAndAMissingPermissionListSaysSo() {
        identityGate.complete(Unit)
        show(canRead = true, moderators = team)

        assertTrue(shown("Permission details not provided") >= 1)
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("ali")
        composeRule.waitForIdle()

        assertEquals(1, shown("u/alice"))
        assertEquals(1, shown("u/malina"))
        assertEquals(0, shown("u/bob"))
        assertTrue(composeRule.onAllNodesWithText("u/alice").fetchSemanticsNodes().single().boundsInRoot.top <
            composeRule.onAllNodesWithText("u/malina").fetchSemanticsNodes().single().boundsInRoot.top)
    }

    @Test
    fun theBackArrowReturnsToModTools() {
        identityGate.complete(Unit)
        show(canRead = true, moderators = team)

        composeRule.onAllNodes(hasContentDescription("Back to Mod tools") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }
}
