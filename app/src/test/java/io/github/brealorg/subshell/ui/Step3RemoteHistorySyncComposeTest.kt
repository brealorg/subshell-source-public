package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.FakeSubshellRepository
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.RepositoryPageRequest
import io.github.brealorg.subshell.data.RepositoryQueryKey
import io.github.brealorg.subshell.data.RepositoryResult
import io.github.brealorg.subshell.data.SubshellRepository
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.data.account.localFixtureAccountSummaries
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a7a4): when Reddit reports new content for a post history or
 * a feed (a content revision for its query key), the app re-reads the loaded
 * range of that list instead of starting it again from the first page — a
 * reader 24 posts down keeps their 24 posts. A comment history has no post
 * pager and asks for nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3RemoteHistorySyncComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    // The effect reads only the query key and the kind; a key the pager reads
    // page by page (not an account snapshot, which comes back whole whatever
    // the cursor) shows whether the loaded range is kept.
    private val pagedKey = mainPostFeedQueryKey(
        account = localFixtureAccountSummaries(listOf("root")).single(),
        source = FeedSource.HOME,
        sort = FeedSort.BEST,
    )
    private val repository = KeyCountingRepository()

    private var historyState by mutableStateOf(RedditAccountHistoryUiState())
    private var feedState by mutableStateOf(RedditFeedUiState())

    private fun compose(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditFeedState = feedState),
                redditAccountHistoryState = historyState,
            )
        }
        composeRule.waitForIdle()
        return state
    }

    /** Two pages of [pagedKey] loaded, as if the reader had scrolled. */
    private fun scrolledTwoPages(state: SubshellState): List<String> {
        val ids = composeRule.runOnIdle {
            state.appendPostPaging(pagedKey)
            state.appendPostPaging(pagedKey)
            state.postPagingSnapshot(pagedKey).items.map(Post::id)
        }
        assertEquals(24, ids.size)
        return ids
    }

    private fun idsNow(state: SubshellState) =
        composeRule.runOnIdle { state.postPagingSnapshot(pagedKey).items.map(Post::id) }

    @Test
    fun aPostHistoryRevisionKeepsTheLoadedRange() {
        val state = compose()
        val before = scrolledTwoPages(state)

        composeRule.runOnIdle {
            historyState = RedditAccountHistoryUiState(
                accountStableId = account.stableId,
                username = "me_user",
                kind = RedditAccountHistoryKind.UPVOTED,
                queryKey = pagedKey,
                loaded = true,
                contentRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        val after = idsNow(state)
        assertTrue("collapsed to ${after.size}", after.size >= before.size)
        assertEquals(before, after.take(before.size))
        assertTrue("the range was read again", repository.loadsFor(pagedKey) > 2)
    }

    @Test
    fun aFeedRevisionKeepsTheLoadedRange() {
        val state = compose()
        val before = scrolledTwoPages(state)

        composeRule.runOnIdle {
            feedState = RedditFeedUiState(
                accountStableId = account.stableId,
                queryKey = pagedKey,
                contentRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        val after = idsNow(state)
        assertTrue("collapsed to ${after.size}", after.size >= before.size)
        assertEquals(before, after.take(before.size))
        assertTrue("the range was read again", repository.loadsFor(pagedKey) > 2)
    }

    @Test
    fun aCommentHistoryRevisionAsksThePostPagerForNothing() {
        val state = compose()
        val before = scrolledTwoPages(state)
        val loads = repository.loadsFor(pagedKey)

        composeRule.runOnIdle {
            historyState = RedditAccountHistoryUiState(
                accountStableId = account.stableId,
                username = "me_user",
                kind = RedditAccountHistoryKind.COMMENTS,
                queryKey = pagedKey,
                loaded = true,
                contentRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        assertEquals(loads, repository.loadsFor(pagedKey))
        assertEquals(before, idsNow(state))
    }
}

/** FakeSubshellRepository that counts post page loads per query key. */
private class KeyCountingRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    private val loads = java.util.concurrent.ConcurrentHashMap<RepositoryQueryKey, Int>()

    fun loadsFor(key: RepositoryQueryKey): Int = loads[key] ?: 0

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        loads.merge(request.queryKey, 1, Int::plus)
        return delegate.posts(request)
    }
}
