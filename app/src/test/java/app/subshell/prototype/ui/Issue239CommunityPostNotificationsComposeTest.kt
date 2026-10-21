package app.subshell.prototype.ui

import android.Manifest
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.communityposts.MAX_WATCHED_COMMUNITIES
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.screens.NewPostNotificationsSettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE239 (#239): a signed-in reader turns new-post notifications on and off
 * from a community's ⋮ (at most 10 communities, said in the tray), a local
 * profile has no such item, and Settings › Notifications › New posts lists
 * the watched communities and removes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue239CommunityPostNotificationsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notices = ViewportErrorHostState()
    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun state(signedIn: Boolean = true) = SubshellState(
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
        initialActiveAccountId = if (signedIn) alice.stableId else localFixtureAccountSummaries(listOf("root")).first().stableId,
    )

    private fun showCommunity(state: SubshellState) {
        ensureWorkManagerInitialized(context)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalViewportErrorHostState provides notices,
                ) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> },
                        onAppendRedditFeed = { _, _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openMenu() {
        composeRule.onAllNodes(hasContentDescription("Actions for r/pics") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
    }

    private fun clickText(text: String) {
        composeRule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theCommunityMenuTurnsNewPostNotificationsOnAndOff() {
        val state = state()
        showCommunity(state)

        openMenu()
        clickText("Notify about new posts")
        assertTrue(state.isWatchingCommunityPosts("pics"))
        assertEquals("Notifications on for r/pics", notices.currentRequest?.presentation?.title)

        openMenu()
        clickText("Stop new-post notifications")
        assertFalse(state.isWatchingCommunityPosts("pics"))
        assertEquals("Notifications off for r/pics", notices.currentRequest?.presentation?.title)
    }

    @Test
    fun theEleventhCommunityIsRefusedWithAReason() {
        val state = state()
        (1..MAX_WATCHED_COMMUNITIES).forEach { state.setWatchingCommunityPosts("c$it", watch = true) }
        showCommunity(state)

        openMenu()
        clickText("Notify about new posts")

        assertFalse(state.isWatchingCommunityPosts("pics"))
        assertEquals(
            "You get notifications from $MAX_WATCHED_COMMUNITIES communities",
            notices.currentRequest?.presentation?.title,
        )
    }

    @Test
    fun aLocalProfileIsNotOfferedNotifications() {
        showCommunity(state(signedIn = false))
        openMenu()
        assertEquals(0, count("Notify about new posts"))
    }

    @Test
    fun theSettingsPageListsTheWatchedCommunitiesAndRemovesThem() {
        val state = state()
        state.setWatchingCommunityPosts("kotlin", watch = true)
        state.setWatchingCommunityPosts("android", watch = true)
        composeRule.setContent { SubshellTheme { NewPostNotificationsSettingsScreen(state = state, onBack = {}) } }
        composeRule.waitForIdle()

        assertEquals(1, count("r/kotlin"))
        composeRule.onAllNodes(hasContentDescription("Stop notifications from r/kotlin") and hasClickAction())
            .onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("android"), state.watchedCommunityNames)
        assertEquals(0, count("r/kotlin"))
    }

    @Test
    fun anEmptySettingsPageSaysWhereToStart() {
        composeRule.setContent { SubshellTheme { NewPostNotificationsSettingsScreen(state = state(), onBack = {}) } }
        composeRule.waitForIdle()
        assertEquals(1, count("No communities yet. Open a community and choose ⋮ › Notify about new posts."))
    }
}
