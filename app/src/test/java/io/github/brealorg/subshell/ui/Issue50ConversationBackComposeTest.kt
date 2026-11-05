package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.ui.app.InboxEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.runtime.mutableStateOf
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.ui.screens.resolveConversationRouteItem
import org.junit.Assert.assertNull

/**
 * ISSUE50_CONVERSATION_BACK_WITHOUT_SELECTION_V1: Back from a link opened in
 * a live conversation used to crash (requireNotNull on a selection only the
 * local cache could restore). The live inbox list restores it; with nothing
 * to show, the conversation route is left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue50ConversationBackComposeTest {
    private val composeRule = createComposeRule()

    // Guarded against the Robolectric "lightZ ... Infinity" display flake,
    // which hit a sibling class (Issue46) on 2026-10-04.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    // A live Reddit message: never in the local repository.
    private val live = InboxItem(
        id = "t4_live123",
        kind = ActivityKind.MESSAGE,
        title = "Live thread title",
        sender = "someone_else",
        age = "1h",
        preview = "Hi, see u/someone_else",
    )

    private fun backFromAProfileOpenedInTheConversation(liveItems: List<InboxItem>): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val inbox = mutableStateOf(RedditInboxUiState(accountStableId = account.stableId, items = listOf(live)))
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(redditInboxState = inbox.value),
            )
        }
        composeRule.waitForIdle()
        state.openInboxItem(live)
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)

        state.openUser("someone_else")
        composeRule.waitForIdle()
        // A loaded inbox (ISSUE48_CONVERSATION_WAITS_FOR_THE_LIVE_INBOX_V1: a
        // route waits while the inbox is still loading).
        inbox.value = RedditInboxUiState(accountStableId = account.stableId, items = liveItems, loaded = true)
        state.navigateBack()
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun backFromALinkReturnsToTheLiveConversation() {
        val state = backFromAProfileOpenedInTheConversation(liveItems = listOf(live))

        assertTrue(state.currentNavigationRoute is SubshellRoute.Conversation)
        assertTrue(composeRule.onAllNodesWithText("Live thread title").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun withNothingToShowTheConversationIsLeftInsteadOfCrashing() {
        val state = backFromAProfileOpenedInTheConversation(liveItems = emptyList())

        assertTrue(state.currentNavigationRoute !is SubshellRoute.Conversation)
    }

    @Test
    fun theRuleKeepsTheOldBehaviourWithASelection() {
        assertEquals(live, resolveConversationRouteItem(live, live.id, emptyList()))
        assertEquals(live, resolveConversationRouteItem(null, live.id, listOf(live)))
        assertNull(resolveConversationRouteItem(null, live.id, emptyList()))
    }
}
