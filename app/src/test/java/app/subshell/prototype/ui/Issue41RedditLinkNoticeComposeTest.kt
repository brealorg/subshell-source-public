package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #41: a Reddit link that Subshell cannot open shows a notice
 * instead of doing nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue41RedditLinkNoticeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val openedInApp = mutableListOf<String>()

    private fun launch(
        rawUri: String,
        resolve: (String, String, (Post?) -> Unit) -> Unit = { _, _, _ -> },
        resolveShareLink: (String, (String?) -> Unit) -> Unit = { _, done -> done(null) },
    ): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        var intentLaunch by mutableStateOf<RedditIntentLaunch?>(RedditIntentLaunch(1L, rawUri))
        val uriHandler = object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                openedInApp += uri
            }
        }
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalUriHandler provides uriHandler,
            ) {
                SubshellApp(
                    videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                    state = state,
                    onResolveRedditNavigationTarget = resolve,
                    redditIntentLaunch = intentLaunch,
                    onRedditIntentConsumed = { intentLaunch = null },
                    onResolveRedditShareLink = resolveShareLink,
                )
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun anUnsupportedRedditLinkShowsANotice() {
        launch("https://www.reddit.com/settings/account")

        composeRule.onNodeWithText("Can't open this link here").assertIsDisplayed()
        composeRule.onNodeWithText("subshell doesn't support this kind of Reddit link").assertIsDisplayed()
    }

    @Test
    fun aPostThatCannotBeLoadedShowsANotice() {
        launch("https://www.reddit.com/r/gone/comments/1abcxyz/title/", resolve = { _, _, done -> done(null) })

        composeRule.onNodeWithText("Couldn't open this post").assertIsDisplayed()
    }

    @Test
    fun anInlineVideoLinkOpensTheViewerAndShowsNoNotice() {
        val state = launch("https://reddit.com/link/pbpmii5/video/8n56vczekerh1/player")

        assertEquals("inline-video-8n56vczekerh1", state.selectedMediaPost?.id)
        assertEquals(0, composeRule.onAllNodesWithText("Can't open this link here").fetchSemanticsNodes().size)
    }

    // REDDITURL03: a share link opens where Reddit redirects it.
    @Test
    fun aShareLinkOpensTheLinkItResolvesTo() {
        launch("https://www.reddit.com/r/kotlin/s/Ab3dE5fG7h") { url, done ->
            assertEquals("https://www.reddit.com/r/kotlin/s/Ab3dE5fG7h", url)
            done("https://www.reddit.com/r/kotlin/comments/1abcde/null_safety/")
        }

        assertEquals(listOf("https://www.reddit.com/r/kotlin/comments/1abcde/null_safety/"), openedInApp)
    }

    @Test
    fun aShareLinkRedditDoesNotResolveShowsANotice() {
        launch("https://www.reddit.com/r/kotlin/s/Ab3dE5fG7h") { _, done -> done(null) }

        composeRule.onNodeWithText("Couldn't open this shared link").assertIsDisplayed()
        assertEquals(emptyList<String>(), openedInApp)
    }
}
