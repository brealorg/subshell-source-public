package app.subshell.prototype.ui.error

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.components.ErrorNotice
import app.subshell.prototype.ui.components.ViewportErrorDetailsSheet
import app.subshell.prototype.ui.components.ViewportErrorTrayHost
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (error01a1, error01a22, error01a221): a screen's error waits in the
 * tray with its title, Retry and "Dismiss error" until the reader acts or the
 * screen leaves — it does not time out; "Details" appears only when there is
 * something to show, and the details sheet shows the details under "Error
 * details" without repeating the tray's words. An error notice's Retry says
 * Retry to its owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ErrorTrayComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val host = ViewportErrorHostState()
    private var retries = 0

    private fun presentation(title: String = "Couldn't refresh Home") = ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = "Saved posts stay on screen.",
        primaryAction = ErrorAction(ErrorActionKind.RETRY, "Retry"),
    )

    private fun present(details: FailureDetails?) {
        host.present(
            scopeKey = "home-feed",
            presentation = presentation(),
            failureDetails = details,
            onRetry = { retries += 1 },
        )
    }

    private fun showTray() {
        composeRule.setContent {
            SubshellTheme {
                ViewportErrorTrayHost(state = host)
                ViewportErrorDetailsSheet(state = host)
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun tap(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun anErrorWithDetailsShowsDetailsRetryAndDismiss() {
        present(FailureDetails(httpStatusCode = 429, retryAfterMs = 30_000))
        showTray()

        assertEquals(1, count("Couldn't refresh Home"))
        assertEquals(1, count("Details"))
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Dismiss error").fetchSemanticsNodes().size)

        tap("Retry")
        assertEquals(1, retries)

        composeRule.onNode(androidx.compose.ui.test.hasContentDescription("Dismiss error") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertNull(host.currentRequest)
    }

    @Test
    fun theErrorStaysUntilTheReaderActs() {
        present(FailureDetails(httpStatusCode = 503))
        showTray()

        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(10 * 60_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals("home-feed", host.currentRequest?.scopeKey)
        assertEquals(1, count("Couldn't refresh Home"))
    }

    @Test
    fun aSimpleTimeoutHasNoDetailsButton() {
        present(FailureDetails(retryable = true, transportKind = NetworkTransportFailureKind.TIMEOUT))
        showTray()

        assertEquals(1, count("Couldn't refresh Home"))
        assertEquals(0, count("Details"))
    }

    @Test
    fun theDetailsSheetDoesNotRepeatTheTraysWords() {
        present(FailureDetails(httpStatusCode = 429, retryAfterMs = 30_000))
        showTray()

        tap("Details")

        assertEquals(1, count("Error details"))
        assertEquals(1, count("Couldn't refresh Home"))
        assertEquals(1, count("Saved posts stay on screen."))
        assertEquals(1, composeRule.onAllNodesWithText("429", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aScreenThatLeavesTakesItsErrorWithIt() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalViewportErrorHostState provides host) {
                    if (shown) {
                        ReportViewportError(
                            scopeKey = "post-comments",
                            presentation = presentation("Couldn't refresh comments"),
                            failureDetails = null,
                            onRetry = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("post-comments", host.currentRequest?.scopeKey)

        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()
        assertNull(host.currentRequest)

        composeRule.runOnIdle { shown = true }
        composeRule.waitForIdle()
        assertEquals("post-comments", host.currentRequest?.scopeKey)
    }

    @Test
    fun anErrorNoticesRetrySaysRetryToItsOwner() {
        val actions = mutableListOf<ErrorActionKind>()
        composeRule.setContent {
            SubshellTheme {
                Column {
                    ErrorNotice(
                        presentation = ErrorPresentation(
                            kind = ErrorPresentationKind.BLOCKING,
                            title = "Couldn't load Home",
                            message = "Check your connection.",
                            primaryAction = ErrorAction(ErrorActionKind.RETRY, "Try again"),
                        ),
                        onAction = { actions += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        tap("Try again")

        assertEquals(listOf(ErrorActionKind.RETRY), actions)
    }
}
