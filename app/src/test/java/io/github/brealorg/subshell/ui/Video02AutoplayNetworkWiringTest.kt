package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.releaseWithoutHanging
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.ProvideVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoNetworkClass
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** VIDEO02 (#227): the coordinator's network class reaches the selection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Video02AutoplayNetworkWiringTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theSelectionKnowsWhetherTheNetworkIsUnmetered() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState()
        val coordinator = testVideoPlaybackCoordinator(context)
        try {
            composeRule.setContent {
                ProvideVideoPlaybackCoordinator(state = state, coordinator = coordinator) {}
            }
            composeRule.waitForIdle()

            assertEquals(
                coordinator.networkSnapshot.networkClass == VideoNetworkClass.UNMETERED,
                state.videoNetworkUnmetered,
            )
        } finally {
            coordinator.releaseWithoutHanging()
        }
    }
}
