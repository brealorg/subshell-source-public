package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityInfoUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (communityinfo01a3, a31, a32): a community reads its information from
 * Reddit only once the reader opens "About r/x", once, and again with force
 * on Retry; a local account reads nothing and sees the community's own
 * description; the open sheet keeps its tab and open rule when the screen's
 * state is restored (the same mechanism brings it back after a moderator's
 * profile).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityAboutEntryComposeTest {
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
    private val loads = mutableListOf<Triple<String, String, Boolean>>()

    private fun state(signedIn: Boolean = true): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        return if (signedIn) {
            SubshellState(
                initialAccountSessions = listOf(account),
                initialActiveAccountId = account.stableId,
                initialInteractionSideConfigured = true,
            )
        } else {
            SubshellState(initialInteractionSideConfigured = true)
        }
    }

    @Composable
    private fun Screen(state: SubshellState, info: RedditCommunityInfoUiState) {
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
            CommunityScreen(
                communityName = "android",
                state = state,
                onBack = {},
                redditCommunityInfoState = info,
                onLoadRedditCommunityInfo = { accountId, community, force -> loads += Triple(accountId, community, force) },
            )
        }
    }

    private fun show(state: SubshellState, info: RedditCommunityInfoUiState = RedditCommunityInfoUiState()) {
        composeRule.setContent { Screen(state, info) }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun openAbout() = click(hasContentDescription("About r/android"))

    private fun loaded(vararg rules: CommunityRule) = RedditCommunityInfoUiState(
        accountStableId = account.stableId,
        communityName = "android",
        snapshot = CommunityInfoSnapshot(communityName = "android", rules = rules.toList()),
        loaded = true,
    )

    @Test
    fun theCommunityIsReadOnlyOnceAboutIsOpened() {
        val state = state()
        show(state, loaded())
        assertEquals(emptyList<Triple<String, String, Boolean>>(), loads)

        openAbout()

        assertTrue(count("About r/android") >= 1)
        assertEquals(listOf(Triple("reddit:me", "android", false)), loads)
        composeRule.runOnIdle { state.updateSearchQuery("anything") }
        composeRule.waitForIdle()
        assertEquals(1, loads.size)
    }

    @Test
    fun retryReadsAgainWithForce() {
        show(
            state(),
            RedditCommunityInfoUiState(
                accountStableId = account.stableId,
                communityName = "android",
                failureKind = RedditCommunityInfoUiFailureKind.TRANSPORT,
                failureRetryable = true,
            ),
        )
        openAbout()

        click(hasText("Retry") and hasClickAction())

        assertEquals(listOf(false, true), loads.map { it.third })
    }

    @Test
    fun aLocalAccountReadsNothingAndSeesTheCommunitysOwnDescription() {
        show(state(signedIn = false))

        openAbout()

        assertEquals(emptyList<Triple<String, String, Boolean>>(), loads)
        assertTrue(count("the wonderfully messy Android ecosystem") >= 1)
        assertEquals(0, count("Moderators"))
    }

    @Test
    fun theOpenSheetKeepsItsTabAndOpenRuleWhenStateIsRestored() {
        val state = state()
        val info = loaded(CommunityRule("No spam", description = "Spam is removed"))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Screen(state, info) }
        composeRule.waitForIdle()
        openAbout()
        click(hasText("Rules") and hasClickAction())
        click(hasText("No spam") and hasClickAction())
        assertEquals(1, count("Spam is removed"))

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertTrue(count("About r/android") >= 1)
        assertEquals(1, count("Spam is removed"))
    }
}
