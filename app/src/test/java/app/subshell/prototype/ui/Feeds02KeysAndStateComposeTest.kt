package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedPostsUiState
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.CommunitiesScreen
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.screens.CustomFeedBrowser
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ui review round 2 group B (FEEDS02): feed state keyed on what it belongs to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Feeds02KeysAndStateComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
    }

    private fun authenticatedState() = SubshellState(
        initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
        initialActiveAccountId = alice.stableId,
    )

    private fun post(id: String, title: String) = Post(
        id = "t3_$id",
        community = "movies",
        author = "someone",
        age = "1h",
        title = title,
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun feed(name: String) = CustomFeedSummary(
        path = "/user/alice/m/$name/",
        displayName = name,
        description = "",
        visibility = "private",
        subredditNames = listOf("movies"),
    )

    // FEEDS02_CUSTOM_FEED_SCROLL_KEYED_ON_FEED_V1
    @Test
    fun switchingCustomFeedsStartsTheNewFeedAtTheTop() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val feeds = listOf(feed("first"), feed("second"))
        val selected = mutableStateOf(feeds[0])
        fun postsFor(summary: CustomFeedSummary) = CustomFeedPostsUiState(
            accountStableId = "reddit:alice",
            feedPath = summary.path,
            posts = (0 until 30).map { index -> post("${summary.displayName}$index", "${summary.displayName} post $index") },
            contentRevision = 1L,
        )
        composeRule.setContent {
            SubshellTheme {
                CustomFeedBrowser(
                    modifier = Modifier,
                    state = state,
                    accountStableId = "reddit:alice",
                    feed = selected.value,
                    postsState = postsFor(selected.value),
                    contentPadding = PaddingValues(),
                    showContextHeader = false,
                    onBack = {},
                    onRefresh = {},
                    onLoadInitial = {},
                    onAppend = {},
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("first post 0").fetchSemanticsNodes().size)

        selected.value = feeds[1]
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("second post 0").fetchSemanticsNodes().size)
    }

    // FEEDS02_CUSTOM_FEED_EDITOR_KEYED_ON_ACCOUNT_V1
    @Test
    fun anOpenCustomFeedDraftSurvivesRecreation() {
        val state = authenticatedState()
        val createRequested = mutableStateOf(true)
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            SubshellTheme {
                CommunitiesScreen(
                    state = state,
                    customFeedCreateRequested = createRequested.value,
                    onCustomFeedCreateRequestConsumed = { createRequested.value = false },
                )
            }
        }
        composeRule.onNodeWithText("Feeds").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("weekend films")
        composeRule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()

        assertEquals(1, composeRule.onAllNodesWithText("weekend films").fetchSemanticsNodes().size)
    }

    // FEEDS02_TERMINAL_VIDEO_TRACKS_FEED_END_V1
    @Test
    fun theTerminalVideoFollowsTheListingEnd() {
        val exhausted = mutableStateOf(false)
        var terminal: String? = "unset"
        val ids = listOf("t3_a", "t3_b")
        composeRule.setContent {
            terminal = rememberTerminalVideoId(ids, exhausted.value)
        }
        composeRule.waitForIdle()
        assertEquals(null, terminal)

        exhausted.value = true
        composeRule.waitForIdle()
        assertEquals("t3_b", terminal)

        exhausted.value = false
        composeRule.waitForIdle()
        assertEquals("a refresh reopens the listing", null, terminal)
    }

    // FEEDS02_MUTED_COMMUNITY_LOADS_NOTHING_V1
    @Test
    fun aCommunityMutedInAboutOnlyModeFetchesNoPages() {
        val state = authenticatedState()
        state.updateMutedCommunityDirectViewMode(MutedCommunityDirectViewMode.ABOUT_ONLY)
        state.muteCommunity("pics", null)

        assertEquals(0, renderCommunity(state))
    }

    @Test
    fun anUnmutedCommunityStillRefreshesOnEntry() {
        // Control for the muted case: the same screen does ask Reddit.
        assertTrue(renderCommunity(authenticatedState()) > 0)
    }

    private fun renderCommunity(state: SubshellState): Int {
        var requests = 0
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> requests += 1 },
                        onAppendRedditFeed = { _, _, _, _ -> requests += 1 },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return requests
    }
}
