package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (deeplink01): tapping a crosspost's source opens the source post in
 * Subshell (not the browser) and Back returns; a signed-in reader who has not
 * made Subshell the handler for Reddit links is asked once, and the two
 * answers go to Android's settings or dismiss the question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RedditLinkSurfacesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    @Test
    fun tappingACrosspostsSourceOpensTheSourcePostAndBackReturns() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val wrapper = Post(
            id = "t3_wrapper",
            community = "kotlin",
            author = "crossposter",
            age = "1h",
            title = "A crosspost",
            score = 1,
            comments = 0,
            kind = PostKind.CROSSPOST,
            crosspost = CrosspostPreview(
                community = "pics",
                author = "photographer",
                title = "The source post",
                score = 10,
                id = "t3_source",
                kind = PostKind.TEXT,
            ),
        )
        val before = state.currentNavigationRoute
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = wrapper, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasClickAction() and hasText("The source post", substring = true)).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("t3_source", (state.currentNavigationRoute as SubshellRoute.Post).postRoute.postId)
        composeRule.runOnIdle { state.navigateBack() }
        assertEquals(before, state.currentNavigationRoute)
    }

    @Test
    fun aSignedInReaderIsAskedOnceWhereTheAnswersGo() {
        ensureWorkManagerInitialized(context)
        val events = mutableListOf<String>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = signedIn(),
                redditLinkHandlingState = RedditLinkHandlingState(
                    supported = true,
                    status = RedditLinkHandlingStatus.DISABLED,
                ),
                redditLinkHandlingPromptSeen = false,
                onOpenRedditLinkSettings = { events += "settings" },
                onDismissRedditLinkPrompt = { events += "notNow" },
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Open Reddit links in subshell?").fetchSemanticsNodes().size)

        composeRule.onNode(hasClickAction() and hasText("Set as default")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasClickAction() and hasText("Not now")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("settings", "notNow"), events)
    }

    @Test
    fun aReaderWhoAlreadyChoseSubshellIsNotAsked() {
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = signedIn(),
                redditLinkHandlingState = RedditLinkHandlingState(
                    supported = true,
                    status = RedditLinkHandlingStatus.ENABLED,
                ),
                redditLinkHandlingPromptSeen = false,
            )
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Open Reddit links in subshell?").fetchSemanticsNodes().isEmpty())
    }
}
