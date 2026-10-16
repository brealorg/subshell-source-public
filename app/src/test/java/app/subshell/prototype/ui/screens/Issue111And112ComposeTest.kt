package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ConnectAccountPhase
import app.subshell.prototype.data.account.ConnectAccountUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SearchScope
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE111_CONNECTED_SCREEN_SCROLLS_V1: the "You're connected" screen did not
 * scroll, so on a short window its only button was squeezed off screen.
 * ISSUE112_RESUBMIT_USES_THE_NEW_SCOPE_V1: a scope change resubmitted with the
 * previous scope's "includes posts", so Communities → Posts fetched nothing.
 */
@RunWith(RobolectricTestRunner::class)
class Issue111And112ComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    // A phone in landscape: little height under the top bar.
    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h240dp")
    fun theConnectedScreenScrollsToItsButton() {
        var finished = 0
        composeRule.setContent {
            RedditOnboardingScreen(
                authenticatedAccountUsername = "me_user",
                connectAccountState = ConnectAccountUiState(
                    phase = ConnectAccountPhase.CONNECTED,
                    providerLabel = "Reddit",
                    username = "me_user",
                ),
                onConnectAccount = {},
                onCancelConnectAccount = {},
                onResetConnectAccount = {},
                onRedditConfigurationChanged = {},
                onFinish = { finished += 1 },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start using subshell").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, finished)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp")
    fun switchingToPostsFetchesPostsAndSwitchingAwayDoesNot() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val account = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me_user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery("rust async")
            updateSearchScope(SearchScope.COMMUNITIES)
            submitSearch()
        }
        val postSearches = mutableListOf<String>()
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(
                    state = state,
                    onBack = {},
                    onRefreshRedditSearch = { _, query, _ -> postSearches += query },
                )
            }
        }
        composeRule.waitForIdle()
        postSearches.clear()

        fun chip(label: String) = composeRule.onNode(
            hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox),
        )

        chip("Posts").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("rust async"), postSearches)

        chip("Communities").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("rust async"), postSearches)
    }
}
