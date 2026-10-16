package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticOperation
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (diag01a3): "Report a problem" marks the incident before it builds
 * the report, so the report carries the user's incident; neither the
 * screen nor the report sheet takes free text, and the report wraps instead
 * of scrolling sideways.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReportAProblemComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun operations() = DiagnosticEventStore(context).snapshot().events.map { it.operation }

    @Test
    fun reportAProblemMarksTheIncidentFirstAndTakesNoFreeText() {
        ensureWorkManagerInitialized(context)
        composeRule.setContent { DiagnosticsScreen(onBack = {}) }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Something went wrong?").fetchSemanticsNodes().size)

        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Report a problem"))
        composeRule.onNode(hasClickAction() and hasText("Report a problem")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Diagnostic report").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            1,
            composeRule.onAllNodesWithText("operation=user_incident_marked", substring = true).fetchSemanticsNodes().size,
        )

        val events = operations()
        assertTrue(
            events.toString(),
            events.lastIndexOf(DiagnosticOperation.USER_INCIDENT_MARKED) in 0 until events.lastIndexOf(DiagnosticOperation.REPORT_CREATED),
        )
        assertEquals(0, composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
                    hasAnyDescendant(hasText("operation=user_incident_marked", substring = true)),
            ).fetchSemanticsNodes().size,
        )
    }
}
