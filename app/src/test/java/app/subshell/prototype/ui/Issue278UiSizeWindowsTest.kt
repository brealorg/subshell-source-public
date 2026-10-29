package app.subshell.prototype.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.ui.theme.AppUiScale
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.UiScaleContext
import app.subshell.prototype.ui.theme.setScaledContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * ISSUE278_EVERY_WINDOW_FOLLOWS_THE_UI_SIZE_V1 (owner 2026-10-28: "den må sitte
 * 110 % korrekt"): a dialog, sheet or menu is its own window with its own
 * composition root. Set up as MainActivity does (root view on a UiScaleContext,
 * SubshellTheme inside), its content must see the UI size (a 100 dp box is
 * 100 dp × scale on screen) and Text size, and its minimum component size must
 * stay 48 dp physical; so must Compose's touch-area widening, except inside a
 * window below 100 % (owner's choice A, see below).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class Issue278UiSizeWindowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class Seen {
        var physicalDensity = 0f
        var physicalFontScale = 0f
        var fontScale = 0f
        var boxPx = 0
        var minimumComponentPx = 0f
        var touchWideningPx = 0f
    }

    private var current by mutableStateOf<Pair<String, AppUiScale>?>(null)
    private var seen = Seen()
    private lateinit var scaleContext: UiScaleContext

    @Composable
    private fun Probe() {
        val density = LocalDensity.current
        seen.fontScale = density.fontScale
        seen.minimumComponentPx = LocalMinimumInteractiveComponentSize.current.value * density.density
        seen.touchWideningPx = LocalViewConfiguration.current.minimumTouchTargetSize.width.value * density.density
        Box(Modifier.size(100.dp).onSizeChanged { seen.boxPx = it.width })
    }

    // As MainActivity: one root view; the scale changes through its context.
    @OptIn(ExperimentalMaterial3Api::class)
    private fun start() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            seen.physicalDensity = activity.resources.displayMetrics.density
            seen.physicalFontScale = activity.resources.configuration.fontScale
            scaleContext = UiScaleContext(activity)
            activity.setScaledContent(scaleContext) {
                val (window, scale) = current ?: return@setScaledContent
                SubshellTheme(uiScale = scale.factor, textScale = AppTextScale.LARGE.factor) {
                    when (window) {
                        "screen" -> Box { Probe() }
                        "Dialog" -> Dialog(onDismissRequest = {}) { Probe() }
                        "AlertDialog" -> AlertDialog(
                            onDismissRequest = {},
                            confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                            text = { Probe() },
                        )
                        "ModalBottomSheet" -> ModalBottomSheet(
                            onDismissRequest = {},
                            sheetState = rememberBottomSheetState(
                                initialValue = SheetValue.Expanded,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                            ),
                        ) { Probe() }
                        "DropdownMenu" -> Box { DropdownMenu(expanded = true, onDismissRequest = {}) { Probe() } }
                        else -> error(window)
                    }
                }
            }
        }
    }

    private fun measure(window: String, scale: AppUiScale): Seen {
        current = null
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            scaleContext.update(scale.factor, AppTextScale.LARGE.factor)
        }
        val physical = seen
        seen = Seen().apply {
            physicalDensity = physical.physicalDensity
            physicalFontScale = physical.physicalFontScale
        }
        current = window to scale
        composeRule.waitForIdle()
        return seen
    }

    @Test
    fun everyWindowFollowsTheUiSizeAndTextSize() {
        val windows = listOf("screen", "Dialog", "AlertDialog", "ModalBottomSheet", "DropdownMenu")
        val problems = mutableListOf<String>()
        start()
        for (scale in AppUiScale.entries) {
            for (window in windows) {
                val seen = measure(window, scale)
                val minimumPx = 48f * seen.physicalDensity - 0.5f
                val expectedBox = 100f * seen.physicalDensity * scale.factor
                if (kotlin.math.abs(seen.boxPx - expectedBox) > 1.5f) {
                    problems += "$window @ ${scale.label}: 100 dp box is ${seen.boxPx} px, expected ${expectedBox.toInt()}"
                }
                if (seen.fontScale <= seen.physicalFontScale + 0.01f) {
                    problems += "$window @ ${scale.label}: Text size 115 % lost (fontScale ${seen.fontScale})"
                }
                if (seen.minimumComponentPx < minimumPx) {
                    problems += "$window @ ${scale.label}: minimum component ${seen.minimumComponentPx / seen.physicalDensity} dp physical"
                }
                // Owner chose A (2026-10-28): a window resets Compose's touch widening to
                // 48 dp in its own (scaled) density; below 100 % that is accepted in
                // dialogs, sheets and menus, as Android's Display size does. The screen
                // and every size from 100 % keep 48 dp physical.
                val widenedAsScaled = window != "screen" && scale.factor < 1f &&
                    kotlin.math.abs(seen.touchWideningPx - 48f * seen.physicalDensity * scale.factor) < 0.5f
                if (seen.touchWideningPx < minimumPx && !widenedAsScaled) {
                    problems += "$window @ ${scale.label}: touch area widened to ${seen.touchWideningPx / seen.physicalDensity} dp physical"
                }
            }
        }
        assertTrue(problems.joinToString("\n", prefix = "\n"), problems.isEmpty())
    }

    // A new UI size reaches the open screen as a configuration change: what the
    // screen remembers (scroll, drafts, open sheets) stays.
    @Test
    fun aNewUiSizeKeepsWhatTheScreenRemembers() {
        val remembered = mutableListOf<Any>()
        composeRule.runOnUiThread {
            scaleContext = UiScaleContext(composeRule.activity)
            composeRule.activity.setScaledContent(scaleContext) {
                val token = remember { Any() }
                SideEffect { remembered += token }
                Box { Box(Modifier.size(100.dp).onSizeChanged { seen.boxPx = it.width }) }
            }
        }
        composeRule.waitForIdle()
        val before = seen.boxPx
        composeRule.runOnUiThread {
            scaleContext.update(AppUiScale.SMALLEST.factor, 1f)
        }
        composeRule.waitForIdle()

        assertEquals("the box follows the new size", (before * 0.85f).roundToInt(), seen.boxPx)
        assertEquals("the same remembered object before and after", 1, remembered.distinct().size)
    }

    // MainActivity learns the stored size from the app's first composition,
    // which runs before setScaledContent returns: a size set that early applies.
    @Test
    fun aSizeKnownBeforeTheRootExistsApplies() {
        composeRule.runOnUiThread {
            scaleContext = UiScaleContext(composeRule.activity)
            scaleContext.update(AppUiScale.SMALLEST.factor, 1f)
            composeRule.activity.setScaledContent(scaleContext) {
                Box { Box(Modifier.size(100.dp).onSizeChanged { seen.boxPx = it.width }) }
            }
        }
        composeRule.waitForIdle()

        assertEquals(255, seen.boxPx)
    }
}
