package app.subshell.prototype.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (a11y01a41/a411): the Tablet mode a reader chose is read back at the
 * next start (a missing or unknown value is Split), and the Settings row
 * changes the mode, stores it and shows it in the one Navigation preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3TabletModeSettingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences
        get() = context.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)

    private fun launchedState(): SubshellState {
        ensureWorkManagerInitialized(context)
        var state: SubshellState? = null
        composeRule.setContent { state = rememberSubshellState() }
        composeRule.waitForIdle()
        return requireNotNull(state)
    }

    @Test
    fun aChosenTabletModeIsReadBackAtTheNextStart() {
        preferences.edit().putString(SubshellSettings.TABLET_LAYOUT_MODE, "SINGLE").commit()

        assertEquals(TabletLayoutMode.SINGLE, launchedState().tabletLayoutMode)
    }

    @Test
    fun anUnknownStoredTabletModeStartsSplit() {
        preferences.edit().putString(SubshellSettings.TABLET_LAYOUT_MODE, "THREE_PANE").commit()

        assertEquals(TabletLayoutMode.SPLIT, launchedState().tabletLayoutMode)
    }

    @Test
    fun noStoredTabletModeStartsSplit() {
        assertEquals(false, preferences.contains(SubshellSettings.TABLET_LAYOUT_MODE))

        assertEquals(TabletLayoutMode.SPLIT, launchedState().tabletLayoutMode)
    }

    @Test
    fun theTabletModeRowChangesTheModeAndShowsItInTheOnePreview() {
        ensureWorkManagerInitialized(context)
        var launched: SubshellState? = null
        composeRule.setContent {
            val state = rememberSubshellState()
            launched = state
            SubshellTheme {
                SettingsScreen(
                    state = state,
                    backupRepository = SettingsBackupRepository(context, "test"),
                    onBack = {},
                    initialDestinationName = SettingsDestination.NAVIGATION.name,
                )
            }
        }
        composeRule.waitForIdle()
        val state = requireNotNull(launched)
        assertEquals(TabletLayoutMode.SPLIT, state.tabletLayoutMode)

        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText("Tablet mode"))
        composeRule.waitForIdle()

        chooseSegment("Single")

        assertEquals(TabletLayoutMode.SINGLE, state.tabletLayoutMode)
        assertEquals("SINGLE", preferences.getString(SubshellSettings.TABLET_LAYOUT_MODE, null))
        assertEquals(1, composeRule.onAllNodesWithText("Live preview").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Switch modes to compare the same fixed preview frame.")
                .fetchSemanticsNodes().size,
        )
        assertEquals(1, previewStatus("Single"))
        assertEquals(0, previewStatus("Split"))

        chooseSegment("Split")

        assertEquals(TabletLayoutMode.SPLIT, state.tabletLayoutMode)
        assertEquals("SPLIT", preferences.getString(SubshellSettings.TABLET_LAYOUT_MODE, null))
        assertEquals(1, composeRule.onAllNodesWithText("Live preview").fetchSemanticsNodes().size)
        assertEquals(1, previewStatus("Split"))
        assertEquals(0, previewStatus("Single"))
    }

    private val isSegment = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private fun chooseSegment(label: String) {
        composeRule.onAllNodes(hasText(label) and isSegment).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** The preview's status pill: the same label, not a selectable segment. */
    private fun previewStatus(label: String): Int =
        composeRule.onAllNodes(hasText(label) and !isSegment).fetchSemanticsNodes().size
}
