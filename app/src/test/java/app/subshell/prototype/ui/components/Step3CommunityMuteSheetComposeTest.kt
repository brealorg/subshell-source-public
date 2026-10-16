package app.subshell.prototype.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.CommunityMute
import app.subshell.prototype.ui.CommunityMutePreset
import app.subshell.prototype.ui.theme.SubshellTheme
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * STEP3 (mute01a1, mute01a2): every preset mutes for its own duration from the
 * tap, Indefinitely has no end, a muted community can change the duration or
 * be unmuted, and Until… refuses a time in the past.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommunityMuteSheetComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val now = 1_800_000_000_000L
    private var clockNow = now
    private val muted = mutableListOf<Long?>()
    private var unmuted = 0

    private fun show(currentMute: CommunityMute? = null) {
        composeRule.setContent {
            SubshellTheme {
                CommunityMuteSheet(
                    communityLabel = "r/pics",
                    nowEpochMillis = now,
                    onDismiss = {},
                    onMute = { muted += it },
                    currentMute = currentMute,
                    onUnmute = { unmuted += 1 },
                    clock = { clockNow },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyPresetMutesForItsOwnDurationAndIndefinitelyHasNoEnd() {
        show()

        CommunityMutePreset.entries.forEach { preset ->
            composeRule.onNodeWithText(preset.label).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }

        assertEquals(CommunityMutePreset.entries.map { preset -> preset.durationMillis?.let { now + it } }, muted)
    }

    @Test
    fun aMutedCommunityCanChangeTheDurationOrBeUnmuted() {
        show(currentMute = CommunityMute(communityName = "pics", expiresAtEpochMillis = now + 60_000L))

        composeRule.onNodeWithText("Change duration").assertExists()
        composeRule.onNodeWithText("Unmute community").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, unmuted)
    }

    // M3225_MUTE_UNTIL_USES_M3_PICKERS_V1: Until… is the M3 date picker, then the
    // M3 time picker; a time that is already past keeps the time picker open
    // with the reason (no Toast), and a future time mutes until it.
    @Test
    fun untilRefusesATimeInThePastAndAcceptsOneInTheFuture() {
        show()
        composeRule.onNodeWithText("Until…").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // M3 pickers, not Android's View dialogs.
        assertTrue(ShadowDialog.getLatestDialog() !is android.app.DatePickerDialog)
        composeRule.onNodeWithText("Next").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Mute until").assertExists()

        // The picker starts tomorrow at this time of day; a clock two days on
        // makes that time the past.
        clockNow = now + 2L * 24L * 60L * 60L * 1_000L
        composeRule.onNodeWithText("OK").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Choose a time in the future").assertExists()
        assertTrue(muted.isEmpty())

        clockNow = now
        composeRule.onNodeWithText("OK").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, muted.size)
        val until = requireNotNull(muted.single())
        assertTrue(until > now)
        val expected = Calendar.getInstance().apply {
            timeInMillis = now + 24L * 60L * 60L * 1_000L
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, until)
    }

    @Test
    fun backFromTheTimePickerReturnsToTheDate() {
        show()
        composeRule.onNodeWithText("Until…").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Next").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Back").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Next").assertExists()
        assertTrue(muted.isEmpty())
    }
}
