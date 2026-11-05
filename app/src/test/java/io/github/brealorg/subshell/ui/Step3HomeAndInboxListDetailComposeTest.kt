package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.app.InboxEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (a11y01a4): on an expanded window in split mode, a post tapped in the
 * Home list pane replaces the post open beside it, and a message tapped in the
 * Inbox list pane replaces the open conversation — neither stacks, so Back
 * leaves the detail and returns to Home or the Inbox, not to the first one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3HomeAndInboxListDetailComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    // A signed-in Home reads Reddit (empty here); the Home test uses the local
    // account, with the signed-in one kept on the device so the app does not
    // show its sign-in onboarding.
    private val local = AccountSessionSummary(
        stableId = "local:reader",
        username = "reader",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )

    private val nowSeconds = System.currentTimeMillis() / 1_000

    private fun message(id: String, sender: String, title: String, preview: String, agoSeconds: Long) = InboxItem(
        id = id,
        kind = ActivityKind.MESSAGE,
        title = title,
        sender = sender,
        age = "1h",
        preview = preview,
        createdEpochSeconds = nowSeconds - agoSeconds,
    )

    private val messageA = message("t4_a", "alice", "First thread", "Hello from alice", agoSeconds = 60)
    private val messageB = message("t4_b", "bob", "Second thread", "Hello from bob", agoSeconds = 120)

    private fun compose(activeLocal: Boolean, inboxItems: List<InboxItem> = emptyList()): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account, local),
            initialActiveAccountId = if (activeLocal) local.stableId else account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(
                        accountStableId = account.stableId,
                        items = inboxItems,
                        loaded = true,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun nodes(matcher: SemanticsMatcher): Int = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** Sample posts whose titles the screen shows right now. */
    private fun shownPosts(): List<Post> = SampleData.posts.filter { count(it.title) >= 1 }

    /** Taps a post in a list as a reader does: its card (or its title when the title is the target). */
    private fun tapPost(post: Post) {
        val direct = hasText(post.title) and hasClickAction()
        val matcher = if (nodes(direct) > 0) direct else hasClickAction() and hasAnyDescendant(hasText(post.title))
        click(matcher)
    }

    private fun postOrigin(state: SubshellState): FeedPostOrigin? =
        (state.currentNavigationRoute as? SubshellRoute.Post)?.postRoute?.origin

    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun aPostTappedInTheHomeListReplacesTheOpenPost() {
        val state = compose(activeLocal = true)
        composeRule.waitUntil(10_000) { shownPosts().size >= 2 }
        val listed = shownPosts()
        val first = listed[0]
        val second = listed[1]

        composeRule.runOnIdle { state.openFromMainFeed(first) }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals(first.id, state.selectedPost?.id)
        assertTrue("the Home list stays beside the post", count(second.title) >= 1)

        tapPost(second)

        assertEquals("replaced, not stacked", depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        assertTrue(postOrigin(state) is FeedPostOrigin.MainFeed)

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertTrue(
            "Back leaves the post for Home, not the first post: ${state.currentNavigationRoute}",
            state.currentNavigationRoute is SubshellRoute.Main,
        )
    }

    @Test
    fun aMessageTappedInTheInboxListReplacesTheOpenConversation() {
        val state = compose(activeLocal = false, inboxItems = listOf(messageA, messageB))
        // As a reader does: open Inbox from the rail, then its Messages tab.
        click(hasContentDescription("Inbox") and hasClickAction())
        click(tab("Messages"))
        assertTrue("both messages are listed", count("Hello from bob") >= 1 && count("Hello from alice") >= 1)

        composeRule.runOnIdle { state.openInboxItem(messageA) }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals(messageA.id, (state.currentNavigationRoute as? SubshellRoute.Conversation)?.inboxItemId)

        click(hasClickAction() and (hasContentDescription("Open ${messageB.title}") or hasAnyDescendant(hasText(messageB.preview))))

        assertEquals("replaced, not stacked", depth, state.navigationRouteDepth)
        assertEquals(messageB.id, (state.currentNavigationRoute as? SubshellRoute.Conversation)?.inboxItemId)

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        assertTrue(
            "Back leaves the conversation for the Inbox: ${state.currentNavigationRoute}",
            state.currentNavigationRoute is SubshellRoute.Main,
        )
    }
}
