package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommunityPinnedPostsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE230_PINNED_POSTS_FIRST_V1 (owner 2026-10-16): a signed-in community feed
 * asks for its pinned posts once and shows them first as cards (the default),
 * or as a "Community highlights" row with Compact highlights chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue230PinnedPostsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val requests = mutableListOf<Triple<String, String, Boolean>>()

    @Before
    fun initWorkManager() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
    }

    // Short text posts, so both pinned cards fit above the fold.
    private val textPost = SampleData.posts.first { it.kind == PostKind.TEXT }.copy(body = "")
    private val pinned = listOf(
        textPost.copy(id = "t3_pinrules", community = "pics", title = "Pinned rules thread", pinned = true),
        textPost.copy(id = "t3_pinweekly", community = "pics", title = "Weekly pinned thread", pinned = true),
    )

    private fun show(presentation: PinnedPostsPresentation): SubshellState {
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
            initialPinnedPostsPresentation = presentation,
        )
        val key = communityPostFeedQueryKey(
            account = state.activeAccountSession,
            communityName = "pics",
            sort = state.communitySort,
            timePeriod = state.listingTimePeriod,
        )
        state.ensurePostPaging(key)
        assertTrue("posts should be cached", state.postPagingSnapshot(key).items.isNotEmpty())
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> },
                        onBack = {},
                        redditPinnedPostsState = RedditCommunityPinnedPostsUiState(
                            accountStableId = alice.stableId,
                            communityName = "pics",
                            posts = pinned,
                            loaded = true,
                        ),
                        onLoadRedditPinnedPosts = { account, community, force ->
                            requests += Triple(account, community, force)
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun asCardsThePinnedPostsLeadTheFeed() {
        val state = show(PinnedPostsPresentation.CARDS)

        assertEquals(listOf(Triple(alice.stableId, "pics", false)), requests)
        val firstListed = state.postPagingSnapshot(
            communityPostFeedQueryKey(state.activeAccountSession, "pics", state.communitySort, state.listingTimePeriod),
        ).items.first { !it.pinned }
        val rules = composeRule.onNodeWithText("Pinned rules thread").fetchSemanticsNode().boundsInRoot.top
        val weekly = composeRule.onNodeWithText("Weekly pinned thread").fetchSemanticsNode().boundsInRoot.top
        val listed = composeRule.onAllNodes(hasText(firstListed.title)).fetchSemanticsNodes()
        assertTrue("rules before weekly", rules < weekly)
        assertTrue("the first listed post comes after the pinned ones", listed.isEmpty() || listed.first().boundsInRoot.top > weekly)
        assertEquals(0, composeRule.onAllNodes(hasText("Community highlights")).fetchSemanticsNodes().size)
    }

    @Test
    fun asHighlightsThePinnedPostsAreARowAboveTheFeed() {
        show(PinnedPostsPresentation.HIGHLIGHTS)

        assertEquals(1, composeRule.onAllNodes(hasText("Community highlights")).fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodes(hasContentDescription("Pinned post: Pinned rules thread", substring = true))
                .fetchSemanticsNodes().size,
        )
        // Not repeated as a full card below the row.
        assertEquals(1, composeRule.onAllNodes(hasText("Pinned rules thread"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
