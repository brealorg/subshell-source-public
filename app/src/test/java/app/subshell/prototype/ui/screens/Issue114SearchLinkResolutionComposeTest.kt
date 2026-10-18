package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE114_SEARCH_LINK_RESOLUTION_CHECKS_ITS_ORIGIN_V1: a post link pasted in
 * search is read from Reddit; the answer opened the post even after an
 * account switch or after the reader left search, and a post that could not
 * be loaded did nothing at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue114SearchLinkResolutionComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun account(id: String) = AccountSessionSummary(
        stableId = "reddit:$id",
        username = id,
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val linkedPost: Post = SampleData.posts.first().copy(id = "t3_zz114")
    private val noticeHost = ViewportErrorHostState()
    private var answer: ((Post?) -> Unit)? = null
    private val requestedAccounts = mutableListOf<String>()

    private fun searchedForALink(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account("alice"), account("bob")),
            initialActiveAccountId = "reddit:alice",
            initialInteractionSideConfigured = true,
        ).apply {
            openSearch(refocus = false)
            updateSearchQuery("https://www.reddit.com/r/test/comments/zz114/a_title/")
        }
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalViewportErrorHostState provides noticeHost,
            ) {
                SearchScreen(
                    state = state,
                    onBack = {},
                    onResolveRedditNavigationTarget = { accountId, _, onResolved ->
                        requestedAccounts += accountId
                        answer = onResolved
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open Reddit post").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:alice"), requestedAccounts)
        return state
    }

    private fun answerWith(post: Post?) {
        composeRule.runOnIdle { requireNotNull(answer).invoke(post) }
        composeRule.waitForIdle()
    }

    @Test
    fun theAnswerOpensThePostWhileTheReaderWaits() {
        val state = searchedForALink()

        answerWith(linkedPost)

        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertNull(noticeHost.currentRequest)
    }

    @Test
    fun anAnswerAfterAnAccountSwitchIsDropped() {
        val state = searchedForALink()
        composeRule.runOnIdle { state.switchAccount("reddit:bob") }
        composeRule.waitForIdle()

        answerWith(linkedPost)

        assertFalse(state.currentNavigationRoute is SubshellRoute.Post)
    }

    @Test
    fun anAnswerAfterLeavingSearchIsDropped() {
        val state = searchedForALink()
        composeRule.runOnIdle { state.closeSearch() }
        composeRule.waitForIdle()

        answerWith(linkedPost)

        assertFalse(state.currentNavigationRoute is SubshellRoute.Post)
    }

    @Test
    fun aPostThatCannotBeLoadedSaysSo() {
        searchedForALink()

        answerWith(null)

        assertEquals("Couldn't open this post", noticeHost.currentRequest?.presentation?.title)
    }
}
