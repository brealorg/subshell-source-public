package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.ContentSafetyUiState
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.account.RedditFeedUiState
import io.github.brealorg.subshell.data.account.RedditSavedHistoryUiState
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.app.userProfileContents
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two canonical profile hosts in their own file: each content lambda
 * composes UserProfileScreen wired to the signed-in account's loaders
 * (entry refresh of the user's submitted posts), and only the public host
 * carries the caller's back action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UserProfileContentComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun theYouHostRefreshesTheOwnSubmittedPostsOnEntry() {
        val refreshes = mutableListOf<Pair<String, String>>()
        host(public = false, onRefresh = { accountId, username, _ -> refreshes += accountId to username })

        assertEquals(listOf("reddit:test" to "connected-user"), refreshes)
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun thePublicHostRefreshesThatUserAndCarriesTheBackAction() {
        val refreshes = mutableListOf<Pair<String, String>>()
        var backs = 0
        host(
            public = true,
            username = "alice",
            onRefresh = { accountId, username, _ -> refreshes += accountId to username },
            onBack = { backs += 1 },
        )

        assertEquals(listOf("reddit:test" to "alice"), refreshes)
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertEquals(1, backs)
    }

    // STEP3 (profile01a4b): both hosts hand a tapped comment to the target
    // resolver, so the post opens at it.
    @Test
    fun theYouHostHandsATappedCommentToTheTargetResolver() = tapCommentIn(public = false, username = account.username)

    @Test
    fun thePublicHostHandsATappedCommentToTheTargetResolver() = tapCommentIn(public = true, username = "alice")

    private fun tapCommentIn(public: Boolean, username: String) {
        run {
            val resolved = mutableListOf<String>()
            val history = RedditAccountHistoryUiState(
                accountStableId = account.stableId,
                username = username,
                kind = RedditAccountHistoryKind.COMMENTS,
                queryKey = redditAccountHistoryQueryKey(account.stableId, username, RedditAccountHistoryKind.COMMENTS),
                comments = listOf(
                    ProfileCommentFixture(
                        id = "t1_c",
                        author = username,
                        postId = "t3_p",
                        age = "1h",
                        body = "A comment by $username",
                        score = 1,
                        placement = ThreadPlacement.TOP,
                    ),
                ),
                loaded = true,
            )
            host(
                public = public,
                username = username,
                onRefresh = { _, _, _ -> },
                history = history,
                commentsTab = true,
                onResolve = { accountId, user, postId -> resolved += "$accountId:$user:$postId" },
            )
            composeRule.onAllNodesWithText("A comment by $username", substring = true).onFirst().performClick()
            composeRule.waitForIdle()

            assertEquals(listOf("reddit:test:$username:t3_p"), resolved)
        }
    }

    private fun host(
        public: Boolean,
        username: String = account.username,
        onRefresh: (String, String, io.github.brealorg.subshell.data.RedditProfilePostsOrder) -> Unit,
        onBack: () -> Unit = {},
        history: RedditAccountHistoryUiState = RedditAccountHistoryUiState(),
        commentsTab: Boolean = false,
        onResolve: (String, String, String) -> Unit = { _, _, _ -> },
    ) {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        if (commentsTab) state.selectUserProfileTab(username, 1)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    val (mainYouProfileContent, publicUserProfileContent) = userProfileContents(
                        state = state,
                        redditFeedState = RedditFeedUiState(),
                        onRefreshRedditUserSubmitted = onRefresh,
                        onAppendRedditUserSubmitted = { _, _, _ -> },
                        redditAccountHistoryState = history,
                        onRefreshRedditAccountHistory = { _, _, _ -> },
                        onAppendRedditAccountHistory = { _, _, _ -> },
                        redditSavedHistoryState = RedditSavedHistoryUiState(),
                        onRefreshRedditSavedHistory = { _, _ -> },
                        onAppendRedditSavedHistory = { _, _ -> },
                        onResolveRedditProfileCommentTarget = { accountId, user, postId, onResolved ->
                            onResolve(accountId, user, postId)
                            onResolved(null)
                        },
                        onResolveRedditSavedCommentTarget = { _, _, _, onResolved -> onResolved(null) },
                        onAuthorizeRedditProfileHistory = { _, _ -> },
                        contentSafetyState = ContentSafetyUiState(),
                        onRefreshBlockedAccounts = {},
                        onAppendBlockedAccounts = {},
                    )
                    if (public) {
                        publicUserProfileContent(username, Modifier, PaddingValues(), onBack, true, null, null)
                    } else {
                        mainYouProfileContent(username, Modifier, PaddingValues(), null, null, null)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
