package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.MutedCommunityDirectViewSetting
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.screens.CommunitiesScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mute01a1–a3): the ways in to muting. The post menu mutes and unmutes,
 * a joined community's mute button asks for the editor, the drawer badge opens
 * a muted community's settings, and the Settings choice changes the direct
 * view mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3MuteEntryPointsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val post = Post(
        id = "t3_m1",
        community = "pics",
        author = "someone",
        age = "1h",
        title = "A picture",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    @Test
    fun thePostMenuOpensTheMuteSheetAndUnmutesAMutedCommunity() {
        val state = SubshellState()
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = state, onOpen = {}) } }

        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.onNodeWithText("Mute r/pics…").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Indefinitely").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(state.isCommunityMuted("pics"))

        composeRule.onNodeWithContentDescription("Post actions").performClick()
        composeRule.onNodeWithText("Unmute r/pics").performClick()
        composeRule.waitForIdle()
        assertFalse(state.isCommunityMuted("pics"))
    }

    @Test
    fun aJoinedCommunitysMuteButtonAsksForTheEditor() {
        val state = SubshellState()
        val joined = state.communities.first { it.joined }.name
        composeRule.setContent { SubshellTheme { CommunitiesScreen(state = state) } }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Mute ${state.subredditLabel(joined)}").onFirst().performClick()

        assertEquals(joined, state.requestedCommunityMuteEditorName)
    }

    @Test
    fun theDrawerBadgeOpensAMutedCommunitysSettings() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        // A signed-in account on the device keeps the sign-in onboarding away;
        // the local account stays active, so its joined communities are listed.
        val local = localFixtureAccountSummaries(listOf("root"))
        val signedIn = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = local + signedIn,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        // The full list shows every joined community with its badge.
        state.updateDrawerCommunitiesMode(DrawerCommunitiesMode.FULL)
        // The drawer list is sorted and lazy; the first name is on screen.
        val joined = state.communities.filter { it.id in state.joinedCommunityIds }.minBy { it.name.lowercase() }.name
        state.muteCommunity(joined, null)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Mute settings for r/$joined").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Change duration").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theSettingsChoiceChangesTheDirectViewMode() {
        val mode = mutableStateOf(MutedCommunityDirectViewMode.SHOW_POSTS)
        composeRule.setContent {
            SubshellTheme {
                MutedCommunityDirectViewSetting(mode = mode.value, onModeChange = { mode.value = it })
            }
        }

        composeRule.onNodeWithText("About only").performClick()
        composeRule.waitForIdle()

        assertEquals(MutedCommunityDirectViewMode.ABOUT_ONLY, mode.value)
    }
}
