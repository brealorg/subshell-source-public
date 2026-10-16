package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.RedditAccountHistoryUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.data.redditAccountHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import app.subshell.prototype.ui.screens.UserProfileScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a4b): tapping a comment in a signed-in profile asks Reddit
 * for its post and opens the post at that comment; a local profile says it
 * has no comments, and its Saved and Upvoted tabs list only the saved and
 * upvoted posts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ProfileCommentsComposeTest {
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

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun tappingASignedInProfileCommentOpensItsPostAtThatComment() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.selectUserProfileTab("me_user", 1)
        val history = RedditAccountHistoryUiState(
            accountStableId = account.stableId,
            username = "me_user",
            kind = RedditAccountHistoryKind.COMMENTS,
            queryKey = redditAccountHistoryQueryKey(account.stableId, "me_user", RedditAccountHistoryKind.COMMENTS),
            comments = listOf(
                ProfileCommentFixture(
                    id = "t1_mine",
                    author = "me_user",
                    postId = "t3_target",
                    age = "1h",
                    body = "My comment on the target",
                    score = 3,
                    placement = ThreadPlacement.TOP,
                    community = "kotlin",
                    postTitle = "The target post",
                ),
            ),
            loaded = true,
        )
        val target = SampleData.posts.first().copy(id = "t3_target", title = "The target post")
        val resolved = mutableListOf<Triple<String, String, String>>()
        composeRule.setContent {
            SubshellTheme {
                UserProfileScreen(
                    username = "me_user",
                    state = state,
                    redditAccountHistoryState = history,
                    onResolveRedditProfileCommentTarget = { accountId, username, postId, onResolved ->
                        resolved += Triple(accountId, username, postId)
                        onResolved(target)
                    },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("My comment on the target", substring = true).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Triple(account.stableId, "me_user", "t3_target")), resolved)
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals("t3_target", route.postRoute.postId)
        assertEquals("t1_mine", route.postRoute.focusedCommentId)
    }

    private fun localState(): SubshellState {
        ensureWorkManagerInitialized(context)
        val local = localFixtureAccountSummaries(listOf("root"))
        return SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = local + account,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
    }

    @Test
    fun aLocalProfileWithoutCommentsSaysSo() {
        val state = localState()
        state.selectUserProfileTab("nobody_here", 1)
        composeRule.setContent { SubshellTheme { UserProfileScreen(username = "nobody_here", state = state) } }
        composeRule.waitForIdle()

        assertEquals(1, shown("No local comments"))
    }

    @Test
    fun aLocalProfilesSavedAndUpvotedTabsListOnlyThosePosts() {
        val state = localState()
        val saved: Post = SampleData.posts[0]
        val upvoted: Post = SampleData.posts[1]
        state.toggleSaved(saved)
        state.vote(upvoted, 1)
        val user = state.activeAccountUsername
        state.selectUserProfileTab(user, 2)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    UserProfileScreen(username = user, state = state)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, shown(saved.title))
        assertEquals(0, shown(upvoted.title))
        assertEquals(0, shown(SampleData.posts[2].title))

        composeRule.runOnIdle { state.selectUserProfileTab(user, 3) }
        composeRule.waitForIdle()
        assertEquals(1, shown(upvoted.title))
        assertEquals(0, shown(saved.title))
    }

    // A post the local account hid is listed on its Hidden tab (the card used
    // to drop itself there because it was told the post is not hidden).
    @Test
    fun aLocalProfilesHiddenTabListsTheHiddenPosts() {
        val state = localState()
        val hidden: Post = SampleData.posts[0]
        state.hidePost(hidden.id)
        val user = state.activeAccountUsername
        state.selectUserProfileTab(user, 5)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    UserProfileScreen(username = user, state = state)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, shown(hidden.title))
        assertEquals(0, shown(SampleData.posts[1].title))
    }
}
