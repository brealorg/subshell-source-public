package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.share.ShareIntoSubshellLaunch
import app.subshell.prototype.ui.app.ShareIntoSubshellEffect
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #33: a share opens the composer for the active account, asking for
 * the community and naming the account when there are several; without a
 * signed-in Reddit account, or with nothing usable, the tray says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue33ShareIntoSubshellComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun account(name: String) = AccountSessionSummary(
        stableId = "reddit:$name",
        username = name,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val link = SharedPostContent(ComposerPostType.LINK, title = "Shared story", linkUrl = "https://example.com/s")

    private fun runEffect(state: SubshellState, launch: ShareIntoSubshellLaunch): ViewportErrorHostState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val host = ViewportErrorHostState()
        var pending by mutableStateOf<ShareIntoSubshellLaunch?>(launch)
        composeRule.setContent {
            ShareIntoSubshellEffect(state = state, launch = pending, onConsumed = { pending = null }, noticeHost = host)
        }
        composeRule.waitForIdle()
        assertEquals(null, pending)
        return host
    }

    @Test
    fun aShareOpensTheComposerForTheActiveAccount() {
        val state = SubshellState(
            initialAccountSessions = listOf(account("me")),
            initialActiveAccountId = "reddit:me",
            initialInteractionSideConfigured = true,
        )

        val host = runEffect(state, ShareIntoSubshellLaunch(1L, link))

        assertEquals(SubshellRoute.PostComposer, state.currentNavigationRoute)
        assertEquals("Shared story", state.postComposerDraft.title)
        assertEquals(null, host.currentRequest)
    }

    @Test
    fun withoutASignedInAccountTheTraySaysSoAndNoDraftIsMade() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        val host = runEffect(state, ShareIntoSubshellLaunch(2L, link))

        assertEquals("Sign in to Reddit to post", host.currentRequest?.presentation?.title)
        assertTrue(state.currentNavigationRoute !is SubshellRoute.PostComposer)
    }

    @Test
    fun anUnusableShareAndLeftOutItemsAreReported() {
        val state = SubshellState(
            initialAccountSessions = listOf(account("me")),
            initialActiveAccountId = "reddit:me",
            initialInteractionSideConfigured = true,
        )
        assertEquals("Nothing to post", runEffect(state, ShareIntoSubshellLaunch(3L, null)).currentRequest?.presentation?.title)
    }

    @Test
    fun theComposerAsksForTheCommunityAndNamesTheAccount() {
        val state = SubshellState(
            initialAccountSessions = listOf(account("me"), account("other")),
            initialActiveAccountId = "reddit:me",
            initialInteractionSideConfigured = true,
        )
        state.openSharedPostComposer(link)
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Posting as u/me to").fetchSemanticsNodes().isNotEmpty())
        // The picker sheet has opened on its own.
        assertTrue(composeRule.onAllNodesWithText("Choose a community").fetchSemanticsNodes().size >= 2)
    }
}
