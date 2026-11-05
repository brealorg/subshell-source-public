package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CustomFeedSummary
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.data.account.RedditSubscriptionsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.model.Community
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE95_FEED_EDITOR_LISTS_EVERY_MEMBER_V1: the feed editor listed only the
 * reader's subscriptions, so a member of the feed they do not subscribe to was
 * counted ("2 selected") but could not be unchecked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue95FeedEditorMembersComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    @Test
    fun aMemberTheReaderDoesNotSubscribeToCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val subscribed = state.communityIdentityFor("android")
        composeRule.setContent {
            CommunitiesScreen(
                state = state,
                redditSubscriptionsState = RedditSubscriptionsUiState(
                    accountStableId = account.stableId,
                    communities = listOf<Community>(subscribed),
                    loaded = true,
                ),
                customFeedsState = CustomFeedsUiState(
                    accountStableId = account.stableId,
                    feeds = listOf(
                        CustomFeedSummary(
                            path = "/user/connected-user/m/tech",
                            displayName = "Tech",
                            description = "",
                            visibility = "private",
                            // The reader has left r/Kotlin since adding it.
                            subredditNames = listOf("android", "Kotlin"),
                        ),
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Feeds")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Manage Tech").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit feed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 selected").assertExists()

        composeRule.onNodeWithText(state.subredditLabel("Kotlin")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 selected").assertExists()
    }
}
