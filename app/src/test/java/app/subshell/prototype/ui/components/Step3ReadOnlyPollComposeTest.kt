package app.subshell.prototype.ui.components

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
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
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.RedditExternalPollHandoffStore
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * STEP3 (polls01a1): a signed-in account cannot vote in a Reddit poll inside
 * Subshell — a vote changes nothing locally, and the poll offers "Open on
 * Reddit to vote" (or "Open on Reddit" once closed); tapping an option opens
 * the poll in the browser's own tab, which may not hand the page to another
 * app, and remembers the hand-off once so the return trip is not reopened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ReadOnlyPollComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val poll: Post = SampleData.posts.single { it.kind == PostKind.POLL }.copy(id = "t3_pollabc")

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun installBrowser() {
        val browser = ComponentName("com.example.browser", "com.example.browser.Main")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
            },
        )
    }

    private fun showCard(post: Post, state: SubshellState) {
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aSignedInVoteChangesNothingLocally() {
        val state = signedIn()

        state.votePoll(poll.id, "media")

        assertNull(state.pollVotes[poll.id])
    }

    @Test
    fun thePollOpensInTheBrowsersOwnTabWhichMayNotHandThePageOn() {
        installBrowser()

        val intent = requireNotNull(redditPollExternalIntent(context, poll))

        assertEquals("https://www.reddit.com/poll/pollabc", intent.dataString)
        assertEquals("com.example.browser", intent.`package`)
        assertFalse(intent.getBooleanExtra("android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER", true))
        assertFalse(intent.getBooleanExtra("androidx.browser.customtabs.extra.INITIAL_NAVIGATION_CAN_LEAVE_BROWSER", true))
        assertTrue(intent.extras!!.containsKey("android.support.customtabs.extra.SESSION"))
        assertNull(redditPollExternalIntent(context, poll.copy(id = "subshell-poll")))
    }

    @Test
    fun theHandOffIsRememberedOnceForTheReturnTrip() {
        val store = RedditExternalPollHandoffStore(context)
        val returnUri = "https://www.reddit.com/r/subshell/comments/pollabc/what_next/"

        assertTrue(store.mark("t3_pollabc"))
        assertTrue(store.consumeIfMatching(returnUri))
        assertFalse(store.consumeIfMatching(returnUri))

        store.mark("t3_pollabc")
        store.clear()
        assertFalse(store.consumeIfMatching(returnUri))
    }

    @Test
    fun aSignedInPollOffersRedditAndAnOptionOpensItThere() {
        installBrowser()
        val state = signedIn()
        showCard(poll, state)
        assertEquals(1, composeRule.onAllNodesWithText("Open on Reddit to vote").fetchSemanticsNodes().size)

        composeRule.onNode(hasClickAction() and hasText("Media viewer")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val started = shadowOf(context).nextStartedActivity
        assertEquals("https://www.reddit.com/poll/pollabc", started.dataString)
        assertNull(state.pollVotes[poll.id])
        assertTrue(
            RedditExternalPollHandoffStore(context)
                .consumeIfMatching("https://www.reddit.com/r/subshell/comments/pollabc/what_next/"),
        )
    }

    @Test
    fun aClosedPollOffersOnlyToOpenItOnReddit() {
        val closed = poll.copy(poll = poll.poll!!.copy(isClosed = true))
        showCard(closed, signedIn())

        assertEquals(1, composeRule.onAllNodesWithText("Open on Reddit").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Open on Reddit to vote").fetchSemanticsNodes().size)
    }
}
