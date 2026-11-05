package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE47_HELP_SAYS_WHAT_REDDITS_2027_CHANGE_MEANS_V1 (#47): Help & about says
 * what Reddit's planned change for unregistered apps means for subshell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue47HelpRedditApiNoteComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    fun helpSaysWhatReddits2027ChangeMeans() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent { SubshellTheme { HelpScreen(onBack = {}) } }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Reddit's API change").fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            composeRule.onAllNodesWithText("12 January 2027", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
