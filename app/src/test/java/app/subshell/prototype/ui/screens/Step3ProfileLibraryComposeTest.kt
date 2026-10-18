package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditBlockedAccount
import app.subshell.prototype.data.account.RedditBlockedAccountsUiState
import app.subshell.prototype.data.account.RedditPostVisibilityRequest
import app.subshell.prototype.data.account.RedditSavedHistoryUiItem
import app.subshell.prototype.data.account.RedditSavedHistoryUiState
import app.subshell.prototype.data.account.RedditUserBlockRequest
import app.subshell.prototype.data.redditMixedSavedHistoryQueryKey
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (profile01a6, profile01a6a7, profile01a7a4, savedcomments01a3,
 * library02a1, useraction01a2): your own signed-in profile has three
 * primary destinations, and Library's children (Saved, Upvotes, Downvotes,
 * Hidden, Blocked, Drafts) show only under Library, which remembers the last
 * child; another user's profile has none of them. Saved lists posts and
 * comments in Reddit's order, a saved comment opens its post at that
 * comment and Back returns to the row. Upvotes refreshes on first entry,
 * not on re-entry, and again on pull-to-refresh. Blocked lists the accounts
 * with Unblock and asks for the next page.
 *
 * STEP3 (safety01a3, safety01a32): a post in Hidden offers Unhide post and
 * leaves the list as soon as it is tapped; Hide post in Upvotes takes the
 * card out at once. Both ask Reddit once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ProfileLibraryComposeTest {
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
    private val identities = object : IdentityMetadataRepository {
        override fun cachedCommunityIdentity(name: String): Community? = null
        override suspend fun refreshCommunityIdentity(name: String, fallback: Community) = RepositoryResult.Content(fallback)
        override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> =
            RepositoryResult.Content(fallbacks)
        override fun cachedUserIdentity(username: String): UserFixture? = null
        override suspend fun refreshUserIdentity(username: String, fallback: UserFixture) = RepositoryResult.Content(fallback)
    }

    private val historyRefreshes = CopyOnWriteArrayList<RedditAccountHistoryKind>()
    private val savedRefreshes = CopyOnWriteArrayList<Pair<String, String>>()
    private val blockedRefreshes = CopyOnWriteArrayList<String>()
    private val blockedAppends = CopyOnWriteArrayList<String>()
    private val savedResolves = CopyOnWriteArrayList<Triple<String, String, String>>()
    private val blocks = CopyOnWriteArrayList<RedditUserBlockRequest>()
    private val visibility = CopyOnWriteArrayList<RedditPostVisibilityRequest>()

    private fun signedIn(
        tab: Int,
        username: String = "me_user",
        repository: SubshellRepository = FakeSubshellRepository(),
    ): SubshellState {
        ensureWorkManagerInitialized(context)
        return SubshellState(
            repository = repository,
            identityMetadataRepository = identities,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).also { state ->
            state.selectUserProfileTab(username, tab)
            state.setAuthenticatedContentSafetyRequesters(
                postVisibilityRequester = { request -> visibility += request },
                reportRequester = {},
                userBlockRequester = { request -> blocks += request },
            )
        }
    }

    private fun show(
        state: SubshellState,
        username: String = "me_user",
        saved: RedditSavedHistoryUiState = RedditSavedHistoryUiState(),
        blocked: RedditBlockedAccountsUiState = RedditBlockedAccountsUiState(),
        resolvedPost: (String) -> Post? = { null },
        shown: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true),
    ) {
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    if (shown.value) {
                        UserProfileScreen(
                            username = username,
                            state = state,
                            onRefreshRedditAccountHistory = { _, _, kind -> historyRefreshes += kind },
                            redditSavedHistoryState = saved,
                            onRefreshRedditSavedHistory = { accountId, name -> savedRefreshes += accountId to name },
                            onResolveRedditSavedCommentTarget = { accountId, name, postId, onResolved ->
                                savedResolves += Triple(accountId, name, postId)
                                onResolved(resolvedPost(postId))
                            },
                            blockedAccountsState = blocked,
                            onRefreshBlockedAccounts = { accountId -> blockedRefreshes += accountId },
                            onAppendBlockedAccounts = { accountId -> blockedAppends += accountId },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun count(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun named(label: String) = hasText(label) or hasContentDescription(label)

    private fun savedState(items: List<RedditSavedHistoryUiItem>) = RedditSavedHistoryUiState(
        accountStableId = account.stableId,
        username = "me_user",
        queryKey = redditMixedSavedHistoryQueryKey(account.stableId, "me_user"),
        items = items,
        loaded = true,
    )

    private fun savedComment(index: Int, body: String = "Saved comment $index", postId: String = "t3_parent$index") =
        RedditSavedHistoryUiItem.CommentItem(
            fullname = "t1_saved$index",
            comment = ProfileCommentFixture(
                id = "t1_saved$index",
                author = "someone",
                postId = postId,
                age = "1h",
                body = body,
                score = 1,
                placement = ThreadPlacement.TOP,
                community = "kotlin",
                postTitle = "Parent $index",
            ),
        )

    // G a: three primary destinations, Library's children only under Library.
    @Test
    fun ownProfileShowsThreePrimaryDestinationsAndLibraryOpensSaved() {
        val state = signedIn(tab = 0)
        show(state)

        for (label in listOf("Posts", "Comments", "Library")) {
            assertTrue(label, count(hasText(label)) >= 1)
        }
        for (label in listOf("Saved", "Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts")) {
            assertEquals(label, 0, count(named(label)))
        }

        click(hasText("Library") and hasClickAction())

        assertEquals(2, state.userProfileTab("me_user"))
        assertEquals(listOf(account.stableId to "me_user"), savedRefreshes.toList())
        assertTrue(count(hasText("Saved")) >= 1)
        for (label in listOf("Upvotes", "Downvotes", "Hidden", "Blocked", "Drafts")) {
            assertTrue(label, count(hasContentDescription(label)) >= 1)
        }
    }

    // G b: a child selects Library at the primary level; Library comes back to it.
    @Test
    fun libraryReturnsToTheLastChild() {
        val state = signedIn(tab = 2)
        show(state)

        click(hasContentDescription("Blocked"))
        assertEquals(6, state.userProfileTab("me_user"))
        assertEquals(listOf(account.stableId), blockedRefreshes.toList())

        click(hasText("Posts") and hasClickAction())
        assertEquals(0, state.userProfileTab("me_user"))
        click(hasText("Library") and hasClickAction())

        assertEquals(6, state.userProfileTab("me_user"))
    }

    // G c: another user's profile exposes no private destination.
    @Test
    fun anotherUsersProfileHasNoLibrary() {
        val state = signedIn(tab = 0, username = "alice")
        show(state, username = "alice")

        assertTrue(count(hasText("Posts")) >= 1)
        for (label in listOf("Library", "Saved", "Upvotes", "Blocked", "Drafts")) {
            assertEquals(label, 0, count(named(label)))
        }
    }

    // G d: posts and comments in saved order; a comment opens its post at the comment.
    @Test
    @Config(qualifiers = "w411dp-h1800dp")
    fun savedListsPostsAndCommentsInOrderAndACommentOpensAtItself() {
        val state = signedIn(tab = 2)
        val savedPost = SampleData.posts.first { it.kind == PostKind.TEXT }
            .copy(id = "t3_savedpost", title = "Saved post title")
        val parent = SampleData.posts.first { it.kind == PostKind.TEXT }.copy(id = "parent", title = "Parent post")
        show(
            state,
            saved = savedState(
                listOf(
                    RedditSavedHistoryUiItem.PostItem(fullname = "t3_savedpost", post = savedPost),
                    savedComment(0, body = "Saved comment body", postId = "parent").let { item ->
                        item.copy(comment = item.comment.copy(id = "t1_comment"))
                    },
                ),
            ),
            resolvedPost = { postId -> parent.takeIf { postId == "parent" } },
        )

        val postTop = composeRule.onAllNodesWithText("Saved post title", useUnmergedTree = true)
            .onFirst().fetchSemanticsNode().boundsInRoot.top
        val commentTop = composeRule.onAllNodesWithText("Saved comment body", substring = true, useUnmergedTree = true)
            .onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue("post $postTop above comment $commentTop", postTop < commentTop)

        composeRule.onAllNodesWithText("Saved comment body", substring = true).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(Triple(account.stableId, "me_user", "parent")), savedResolves.toList())
        val route = state.currentNavigationRoute as SubshellRoute.Post
        assertEquals("parent", route.postRoute.postId)
        assertEquals("t1_comment", route.postRoute.focusedCommentId)
    }

    // G d: Back from a saved comment's post returns to that row.
    @Test
    fun backReturnsToTheOpenedSavedComment() {
        val state = signedIn(tab = 2)
        val shown = mutableStateOf(true)
        val parent = SampleData.posts.first { it.kind == PostKind.TEXT }
        show(
            state,
            saved = savedState((1..40).map { savedComment(it) }),
            resolvedPost = { postId -> parent.copy(id = postId) },
            shown = shown,
        )
        val target = hasText("Saved comment 30")
        val lists = composeRule.onAllNodes(hasScrollToNodeAction())
        for (index in 0 until lists.fetchSemanticsNodes().size) {
            if (runCatching { lists[index].performScrollToNode(target) }.isSuccess) break
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Saved comment 30").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals("t1_saved30", (state.currentNavigationRoute as SubshellRoute.Post).postRoute.focusedCommentId)

        composeRule.runOnIdle { shown.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.navigateBack()
            shown.value = true
        }
        composeRule.waitForIdle()

        assertEquals(2, state.userProfileTab("me_user"))
        composeRule.onAllNodesWithText("Saved comment 30").onFirst().assertIsDisplayed()
    }

    // G e: Upvotes refreshes on first entry, keeps its cache on re-entry, pull refreshes.
    @Test
    fun upvotesRefreshOnFirstEntryNotOnReentryAndOnPull() {
        val state = signedIn(tab = 3)
        show(state)
        assertEquals(listOf(RedditAccountHistoryKind.UPVOTED), historyRefreshes.toList())

        click(hasText("Posts") and hasClickAction())
        click(hasText("Library") and hasClickAction())
        assertEquals(3, state.userProfileTab("me_user"))
        assertEquals(1, historyRefreshes.count { it == RedditAccountHistoryKind.UPVOTED })

        composeRule.onAllNodes(hasScrollToNodeAction())[0].performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(2, historyRefreshes.count { it == RedditAccountHistoryKind.UPVOTED })
    }

    // G f: Blocked lists accounts with Unblock and asks for the next page once
    // the reader drags to the end (a list asks for more only on a real scroll).
    // The own profile has no route bar; the drag must still scroll the list.
    @Test
    fun blockedListsAccountsUnblocksAndAsksForTheNextPage() {
        val state = signedIn(tab = 6)
        show(
            state,
            blocked = RedditBlockedAccountsUiState(
                accountStableId = account.stableId,
                items = listOf(
                    RedditBlockedAccount(
                        username = "spammer",
                        accountFullname = "t2_spam",
                        blockedAtEpochSeconds = 1_700_846_613,
                    ),
                ) + (1..40).map { index ->
                    RedditBlockedAccount(
                        username = "blocked_$index",
                        accountFullname = "t2_b$index",
                        blockedAtEpochSeconds = 1_700_846_613,
                    )
                },
                loaded = true,
                nextCursor = "c2",
            ),
        )

        assertEquals(1, count(hasText("u/spammer")))
        assertEquals(emptyList<String>(), blockedRefreshes.toList())
        assertEquals(emptyList<String>(), blockedAppends.toList())

        click(hasText("Unblock") and hasClickAction())

        assertEquals(listOf(RedditUserBlockRequest(account.stableId, "spammer", false)), blocks.toList())
        assertEquals(0, count(hasText("u/spammer")))

        repeat(4) {
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = bottom * 0.85f, endY = top + height * 0.15f)
            }
            composeRule.waitForIdle()
        }
        assertEquals(1, count(hasText("u/blocked_40")))
        assertEquals(listOf(account.stableId), blockedAppends.toList())
    }

    // STEP3 (safety01a3 G9, safety01a32 G5): Reddit's Hidden list knows its
    // posts are hidden, so a card there offers Unhide and leaves the list at
    // once; a card elsewhere leaves its list at once on Hide.
    private val hiddenPost = libraryPost("t3_hid1", "A post Reddit keeps hidden")
    private val upvotedPost = libraryPost("t3_upv1", "A post you upvoted")

    private fun libraryPost(id: String, title: String) = Post(
        id = id,
        community = "homelab",
        author = "someone",
        age = "1h",
        title = title,
        body = "Body of $title",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun waitFor(title: String) {
        composeRule.waitUntil(10_000) { count(hasText(title)) > 0 }
    }

    @Test
    fun aHiddenPostOffersUnhideAndLeavesTheListAtOnce() {
        val state = signedIn(tab = 5, repository = AccountHistoryRepository(hidden = listOf(hiddenPost)))
        show(state)
        waitFor("A post Reddit keeps hidden")

        click(hasContentDescription("Post actions"))
        assertEquals(0, count(hasText("Hide post")))
        click(hasText("Unhide post") and hasClickAction())

        assertEquals(listOf(RedditPostVisibilityRequest(account.stableId, "t3_hid1", false)), visibility.toList())
        assertEquals(0, count(hasText("A post Reddit keeps hidden")))
    }

    @Test
    fun hidingAnUpvotedPostTakesItOutOfTheListAtOnce() {
        val state = signedIn(tab = 3, repository = AccountHistoryRepository(upvoted = listOf(upvotedPost)))
        show(state)
        waitFor("A post you upvoted")

        click(hasContentDescription("Post actions"))
        click(hasText("Hide post") and hasClickAction())

        assertEquals(listOf(RedditPostVisibilityRequest(account.stableId, "t3_upv1", true)), visibility.toList())
        assertEquals(0, count(hasText("A post you upvoted")))
    }
}

/**
 * FakeSubshellRepository whose signed-in Hidden and Upvotes histories are
 * the given posts (one page).
 */
private class AccountHistoryRepository(
    private val hidden: List<Post> = emptyList(),
    private val upvoted: List<Post> = emptyList(),
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val key = request.queryKey.value
        val posts = when {
            "profile01a6|history|" in key && key.endsWith("|hidden") -> hidden
            "profile01a6|history|" in key && key.endsWith("|upvoted") -> upvoted
            else -> return delegate.posts(request)
        }
        return if (request.cursor == null) RepositoryResult.Content(posts, nextCursor = null) else RepositoryResult.Empty()
    }
}
