package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.RedditAccessCheckResult
import io.github.brealorg.subshell.data.account.RedditAccessProbe
import io.github.brealorg.subshell.data.account.RedditAccessProbeOutcome
import io.github.brealorg.subshell.data.diagnostics.RedditAccessCheckStore
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: Check Reddit access shows what each read
 * got under the button and keeps it for the report; signed out, it is not offered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RedditCheck01AccessCheckComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun clearStore() {
        RedditAccessCheckStore(context).clear()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theCheckShowsEachReadAndKeepsItForTheReport() {
        ensureWorkManagerInitialized(context)
        var checks = 0
        composeRule.setContent {
            DiagnosticsScreen(
                onBack = {},
                checkRedditAccess = {
                    checks++
                    RedditAccessCheckResult.Checked(
                        listOf(
                            RedditAccessProbe("me", RedditAccessProbeOutcome.ANSWERED, 200),
                            RedditAccessProbe("api_info", RedditAccessProbeOutcome.HTTP_FAILURE, 403, "network_security_block"),
                            RedditAccessProbe("inbox", RedditAccessProbeOutcome.HTTP_FAILURE, 429),
                            RedditAccessProbe("user_about", RedditAccessProbeOutcome.SKIPPED),
                        ),
                    )
                },
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Check Reddit access"))
        composeRule.onNode(hasClickAction() and hasText("Check Reddit access"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { count("Check again") == 1 }

        assertEquals(1, checks)
        assertEquals(1, count("Blocked by Reddit (403)"))
        assertEquals(1, count("Too many requests (429)"))
        assertEquals(1, count("Not checked"))
        assertEquals(1, composeRule.onAllNodesWithText(": 1 of 3 work", substring = true).fetchSemanticsNodes().size)
        composeRule.waitUntil(5_000) { RedditAccessCheckStore(context).read()?.probes?.size == 4 }
    }

    @Test
    fun signedOutTheCheckIsNotOffered() {
        ensureWorkManagerInitialized(context)
        composeRule.setContent { DiagnosticsScreen(onBack = {}) }
        composeRule.waitForIdle()

        assertEquals(0, count("Reddit access"))
        assertEquals(0, count("Check Reddit access"))
    }
}
