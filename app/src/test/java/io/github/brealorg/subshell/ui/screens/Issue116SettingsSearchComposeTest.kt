package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE116_EVERY_VISIBLE_SETTING_IS_SEARCHABLE_V1: Settings search found
 * nothing for "tablet", "brightness", "landscape", "collapse" or "mute"
 * (muted communities), although those settings exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue116SettingsSearchComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun search(query: String) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState().apply { settingsQuery = query }
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            SubshellTheme {
                SettingsScreen(state = state, backupRepository = repository, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun shows(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertExists()
    }

    @Test
    fun collapseFindsTheCollapseSetting() {
        search("collapse")
        shows("Collapse comments")
    }

    @Test
    fun tabletFindsTabletMode() {
        search("tablet")
        shows("Tablet mode")
    }

    @Test
    fun brightnessFindsTheGesture() {
        search("brightness")
        shows("Brightness gesture")
    }

    @Test
    fun muteFindsTheMutedCommunitySetting() {
        search("muted")
        shows("When opening a muted community")
    }
}
