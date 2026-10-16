package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3_NO_BAR_NO_BAR_SCROLL_V1: a screen that leaves its route bar out (a
 * community beside a post on a wide screen) still scrolls when the reader
 * drags; a bar that was never laid out must not take the drag. The own
 * profile's case is Step3ProfileLibraryComposeTest's Blocked test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RouteBarScrollComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun aCommunityWithoutItsHeaderScrollsWhenDragged() {
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")),
            initialInteractionSideConfigured = true,
        )
        val key = communityPostFeedQueryKey(
            account = state.activeAccountSession,
            communityName = SampleData.VIDEO_LAB_COMMUNITY_NAME,
            sort = state.communitySort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(
                        communityName = SampleData.VIDEO_LAB_COMMUNITY_NAME,
                        state = state,
                        onBack = {},
                        showContextHeader = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue("starts at the top", listOffset() == 0f)

        composeRule.onRoot().performTouchInput {
            swipeUp(startY = bottom * 0.85f, endY = top + height * 0.15f)
        }
        composeRule.waitForIdle()

        assertTrue("the drag scrolled the posts", listOffset() > 0f)
    }

    private fun listOffset(): Float = composeRule.onAllNodes(hasScrollToNodeAction())
        .fetchSemanticsNodes()
        .first()
        .config[SemanticsProperties.VerticalScrollAxisRange]
        .value()
}
