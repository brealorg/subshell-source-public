package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.CommunityMembershipUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (subs04a0): a signed-in community header offers Join or Joined from
 * the account's subscriptions and sends the matching action to Reddit; while
 * the change is on its way it says Joining… or Leaving… and cannot be
 * pressed again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityMembershipHeaderComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val sent = mutableListOf<Triple<String, String, CommunityMembershipAction>>()

    private fun show(joined: Boolean, membership: CommunityMembershipUiState = CommunityMembershipUiState()) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val subscriptions = RedditSubscriptionsUiState(
            accountStableId = account.stableId,
            communities = SampleData.communities.filter { joined || it.name != "android" },
            loaded = true,
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                CommunityScreen(
                    communityName = "android",
                    state = state,
                    onBack = {},
                    redditSubscriptionsState = subscriptions,
                    communityMembershipState = membership,
                    onChangeCommunityMembership = { accountId, name, action -> sent += Triple(accountId, name, action) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun button(label: String) = composeRule.onNode(hasClickAction() and hasText(label))

    private fun sending(action: CommunityMembershipAction) = CommunityMembershipUiState(
        phase = CommunityMembershipPhase.SENDING,
        accountStableId = account.stableId,
        communityName = "android",
        action = action,
    )

    @Test
    fun aCommunityYouHaveNotJoinedOffersJoinAndSendsJoin() {
        show(joined = false)

        button("Join").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(Triple(account.stableId, "android", CommunityMembershipAction.JOIN)), sent)
    }

    @Test
    fun aJoinedCommunitySaysJoinedAndSendsLeave() {
        show(joined = true)

        button("Joined").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(Triple(account.stableId, "android", CommunityMembershipAction.LEAVE)), sent)
    }

    @Test
    fun aJoinOnItsWaySaysJoiningAndCannotBePressedAgain() {
        show(joined = false, membership = sending(CommunityMembershipAction.JOIN))

        button("Joining…").assertIsNotEnabled()
    }

    @Test
    fun aLeaveOnItsWaySaysLeavingAndCannotBePressedAgain() {
        show(joined = true, membership = sending(CommunityMembershipAction.LEAVE))

        button("Leaving…").assertIsNotEnabled()
    }
}
