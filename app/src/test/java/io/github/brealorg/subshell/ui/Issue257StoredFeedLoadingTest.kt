package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SubshellRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.screens.CommunityScreen
import io.github.brealorg.subshell.ui.screens.HomeScreen
import io.github.brealorg.subshell.ui.state.PostPagingExecutor
import io.github.brealorg.subshell.ui.state.PostPagingState
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE257_HOME_SAYS_LOADING_UNTIL_ITS_STORE_IS_READ_V1 (#257): right after the
 * landing, Home read "No cached Reddit posts" with a Refresh button for a
 * quarter of a second while its stored page was still being read (seen in a
 * cold-start recording). A feed whose store read has not finished says it is
 * loading; an empty store still says so once read. The community feed has the
 * same text and follows the same rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue257StoredFeedLoadingTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private class HeldExecutor : PostPagingExecutor {
        val work = ArrayDeque<() -> Unit>()
        val published = ArrayDeque<() -> Unit>()
        override fun execute(work: () -> Unit) { this.work += work }
        override fun publish(update: () -> Unit) { published += update }
        fun runWorker() { while (work.isNotEmpty()) work.removeFirst()() }
        fun runMain() { while (published.isNotEmpty()) published.removeFirst()() }
    }

    // An account whose store holds no posts for any feed.
    private val emptyStore = object : SubshellRepository by FakeSubshellRepository() {
        override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> =
            RepositoryResult.Content(emptyList(), nextCursor = null)
    }

    private val account = AccountSessionSummary(
        stableId = "reddit:reader",
        username = "reader",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aFeedCountsAsReadOnlyOnceItsStoreReadIsPublished() {
        val executor = HeldExecutor()
        val paging = PostPagingState(emptyStore, FeedScrollAnchorStore(), isPostDeleted = { false }, executor = executor)
        val key = RepositoryQueryKey("community|subshell|hot")

        assertFalse("no read yet", paging.postPagingStoreRead(key))
        paging.ensurePostPaging(key)
        executor.runWorker()
        assertFalse("read but not published", paging.postPagingStoreRead(key))
        executor.runMain()
        assertTrue("an empty store, read", paging.postPagingStoreRead(key))

        val refreshing = HeldExecutor()
        val pagingWhileRefreshing =
            PostPagingState(emptyStore, FeedScrollAnchorStore(), isPostDeleted = { false }, executor = refreshing)
        pagingWhileRefreshing.refreshPostPaging(key)
        refreshing.runWorker()
        // Publish only the "refreshing" snapshot, not the result.
        refreshing.published.removeFirst()()
        assertFalse("an empty feed whose store read is in flight", pagingWhileRefreshing.postPagingStoreRead(key))
    }

    private fun signedIn(executor: PostPagingExecutor) = SubshellState(
        repository = emptyStore,
        postPagingExecutor = executor,
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun assertLoadingUntilTheStoreIsRead(executor: HeldExecutor) {
        composeRule.waitForIdle()
        assertEquals("loading while the store is read", 1, shown("Loading from Reddit…"))
        assertEquals(0, shown("No cached Reddit posts"))
        assertEquals("no Refresh button while the store is read", 0, shown("Refresh from Reddit"))

        composeRule.runOnIdle {
            executor.runWorker()
            executor.runMain()
        }
        composeRule.waitForIdle()
        assertEquals("an empty store says so once read", 1, shown("No cached Reddit posts"))
        assertEquals(0, shown("Loading from Reddit…"))
        assertEquals("an empty store offers Refresh", 1, shown("Refresh from Reddit"))
    }

    @Test
    fun homeSaysLoadingUntilItsStoredPageIsRead() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        val executor = HeldExecutor()
        val state = signedIn(executor)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    HomeScreen(state = state)
                }
            }
        }
        assertLoadingUntilTheStoreIsRead(executor)
    }

    @Test
    fun aCommunitySaysLoadingUntilItsStoredPageIsRead() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        val executor = HeldExecutor()
        val state = signedIn(executor)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    CommunityScreen(communityName = "pics", state = state, onBack = {})
                }
            }
        }
        assertLoadingUntilTheStoreIsRead(executor)
    }
}
