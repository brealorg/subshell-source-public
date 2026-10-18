package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (next01a4a, next01a4b): on an expanded window in split mode your own
 * profile is a list-detail workspace. Posts, Comments and Saved say what to
 * pick; a post opens beside the list and the next one replaces it; choosing
 * another tab closes the open post; a saved comment opens its post at that
 * comment. Blocked accounts open beside the list, the next replaces the
 * first, and leaving the Blocked tab closes the open account. Single mode
 * keeps the plain profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3YouWorkspacesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val blockedAccounts = RedditBlockedAccountsUiState(
        accountStableId = "reddit:test",
        items = listOf(
            RedditBlockedAccount(username = "alice", accountFullname = "t2_a"),
            RedditBlockedAccount(username = "bob", accountFullname = "t2_b"),
        ),
        loaded = true,
    )

    private val savedComment = ProfileCommentFixture(
        id = "t1_saved",
        author = "someone",
        postId = SampleData.posts[0].id,
        age = "1h",
        body = "A comment worth keeping",
        score = 3,
        placement = ThreadPlacement.TOP,
        community = SampleData.posts[0].community,
        postTitle = SampleData.posts[0].title,
    )

    private val savedResolves = mutableListOf<Triple<String, String, String>>()

    private fun compose(layout: TabletLayoutMode = TabletLayoutMode.SPLIT): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        app.subshell.prototype.backup.SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            repository = OwnProfileRepository(author = account.username),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(layout)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                contentSafetyState = ContentSafetyUiState(blockedAccounts = blockedAccounts),
                redditSavedHistoryState = RedditSavedHistoryUiState(
                    accountStableId = account.stableId,
                    username = account.username,
                    queryKey = redditMixedSavedHistoryQueryKey(account.stableId, account.username),
                    items = listOf(RedditSavedHistoryUiItem.CommentItem("t1_saved", savedComment)),
                    loaded = true,
                ),
                onResolveRedditSavedCommentTarget = { accountId, username, postId, onResolved ->
                    savedResolves += Triple(accountId, username, postId)
                    onResolved(SampleData.posts.first { it.id == postId })
                },
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun openYou(state: SubshellState, tab: Int = 0) {
        composeRule.runOnIdle {
            state.selectUserProfileTab(account.username, tab)
            state.navigate(MainDestination.YOU)
        }
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tab(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun eachYouTabSaysWhatToPickBesideTheList() {
        val state = compose()
        openYou(state)
        assertEquals(1, shown("Select a post"))

        composeRule.runOnIdle { state.selectUserProfileTab(account.username, 1) }
        composeRule.waitForIdle()
        assertEquals(1, shown("Select a comment"))

        composeRule.runOnIdle { state.selectUserProfileTab(account.username, 2) }
        composeRule.waitForIdle()
        assertEquals(1, shown("Select a saved item"))
    }

    @Test
    fun aPostOpensBesideYourListAndTheNextOneReplacesIt() {
        val state = compose()
        openYou(state)
        // The profile's own posts in the repository's order (cards are tall, so
        // only the first is on screen at once).
        val listed = ownPosts()
        val first = listed[0]
        val second = listed[1]
        composeRule.waitUntil(10_000) { shown(first.title) >= 1 }

        composeRule.runOnIdle { state.openFromUser(first, account.username) }
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        assertEquals(0, shown("Select a post"))
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(second.title))
        composeRule.waitForIdle()
        assertTrue("your list stays beside the post", shown(second.title) >= 1)

        click(hasText(second.title) and hasClickAction())

        assertEquals(depth, state.navigationRouteDepth)
        assertEquals(second.id, state.selectedPost?.id)
        val origin = (state.currentNavigationRoute as SubshellRoute.Post).postRoute.origin
        assertEquals(FeedPostOrigin.User(MainDestination.YOU, account.username), origin)
    }

    @Test
    fun choosingAnotherTabClosesTheOpenPost() {
        val state = compose()
        openYou(state)
        composeRule.runOnIdle { state.openFromUser(SampleData.posts[0], account.username) }
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)

        click(tab("Comments"))

        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(1, shown("Select a comment"))
    }

    @Test
    fun aSavedCommentOpensItsPostAtThatComment() {
        val state = compose()
        openYou(state, tab = 2)
        assertTrue(shown(savedComment.body) >= 1)

        click(hasText(savedComment.body, substring = true) and hasClickAction())

        assertEquals(listOf(Triple(account.stableId, account.username, savedComment.postId)), savedResolves)
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals(savedComment.postId, route.postRoute.postId)
        assertEquals("t1_saved", route.postRoute.focusedCommentId)
    }

    @Test
    fun aBlockedAccountOpensBesideTheListAndLeavingBlockedClosesIt() {
        val state = compose()
        openYou(state, tab = 6)
        assertEquals(1, shown("Select an account"))

        click(hasText("u/alice") and hasClickAction())
        val depth = state.navigationRouteDepth
        assertEquals(SubshellRoute.User(MainDestination.YOU, "alice"), state.currentNavigationRoute)
        assertTrue("the blocked list stays beside the account", shown("u/bob") >= 1)

        click(hasText("u/bob") and hasClickAction())
        assertEquals(SubshellRoute.User(MainDestination.YOU, "bob"), state.currentNavigationRoute)
        assertEquals(depth, state.navigationRouteDepth)

        click(tab("Posts"))
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(1, shown("Select a post"))
    }

    @Test
    fun singleModeKeepsThePlainProfile() {
        val state = compose(layout = TabletLayoutMode.SINGLE)
        openYou(state)

        assertEquals(0, shown("Select a post"))
    }
}

/** SampleData's text posts: short cards, so several fit on screen. */
private fun ownPosts(): List<Post> = SampleData.posts.filter { it.kind == PostKind.TEXT }

/**
 * Pages SampleData like FakeSubshellRepository, but the profile's own posts
 * are [ownPosts] written by [author] (SampleData's first page is all tall
 * video-lab cards).
 */
private class OwnProfileRepository(
    private val author: String,
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val result = delegate.posts(request)
        return if (result is RepositoryResult.Content && "|submitted" in request.queryKey.value) {
            result.copy(value = ownPosts().map { post -> post.copy(author = author) }, nextCursor = null)
        } else {
            result
        }
    }
}
