package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CommentDeletePhase
import io.github.brealorg.subshell.data.account.CommentDeleteUiState
import io.github.brealorg.subshell.data.account.PostEditRequest
import io.github.brealorg.subshell.data.account.PostEditUiState
import io.github.brealorg.subshell.data.RedditAccountHistoryKind
import io.github.brealorg.subshell.data.account.RedditAccountHistoryUiState
import io.github.brealorg.subshell.data.account.RedditBlockedAccount
import io.github.brealorg.subshell.data.account.RedditBlockedAccountsUiState
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.data.redditAccountHistoryQueryKey
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.model.ActivityKind
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.model.ProfileCommentFixture
import io.github.brealorg.subshell.model.ThreadPlacement
import io.github.brealorg.subshell.ui.components.LocalVideoPlayer
import io.github.brealorg.subshell.ui.screens.ConversationScreen
import io.github.brealorg.subshell.ui.screens.PostEditScreen
import io.github.brealorg.subshell.ui.screens.PostScreen
import io.github.brealorg.subshell.ui.screens.UserProfileScreen
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI8c: LOWUI8_CONVERSATION_OPENS_AT_THE_NEWEST_MESSAGE_V1 (OCB-195),
 * LOWUI8_PROFILE_KEYS_MATCH_THE_ROWS_V1 (OCB-283/325),
 * LOWUI8_VIDEO_FOLLOWS_ITS_SOURCES_V1 (OCB-302),
 * LOWUI8_POST_EDIT_KEEPS_LEADING_INDENTATION_V1 (OCB-319),
 * LOWUI8_WRITE_REFRESH_KEEPS_THE_FOCUSED_COMMENT_V1 (OCB-238).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi8cComposeTest {
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

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun userAnchorKey(state: SubshellState): String? =
        state.feedScrollAnchorSnapshot().entries
            .single { (key, _) -> key is FeedScrollKey.User }
            .value.itemKey

    @Test
    @Config(qualifiers = "w411dp-h400dp")
    fun aConversationOpensAtItsNewestMessage() {
        val messages = (1..40).map { n ->
            MessageBubble(id = "m$n", sender = "someone_else", age = "1h", body = "Line $n", createdEpochSeconds = 1_700_000_000L + n)
        }
        composeRule.setContent {
            ConversationScreen(
                item = InboxItem(
                    id = "chat_195",
                    kind = ActivityKind.MESSAGE,
                    title = "[direct chat room]",
                    sender = "someone_else",
                    age = "1h",
                    preview = "Line 40",
                    conversation = messages,
                ),
                onBack = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Line 40").assertIsDisplayed()
    }

    @Test
    fun theBlockedTabAnchorNamesTheRowOnScreen() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.selectUserProfileTab("me_user", 6)
        val blocked = RedditBlockedAccountsUiState(
            accountStableId = account.stableId,
            items = (1..30).map { n -> RedditBlockedAccount(username = "blocked_$n", accountFullname = "t2_b$n") },
            loaded = true,
        )
        composeRule.setContent {
            SubshellTheme { UserProfileScreen(username = "me_user", state = state, blockedAccountsState = blocked) }
        }
        composeRule.waitForIdle()
        // Unblocked here, Reddit not yet asked again: the row leaves the list.
        composeRule.runOnIdle { state.setUserBlocked("blocked_1", blocked = false) }
        composeRule.waitForIdle()

        // Rows: profile-header, tabs, heading, blocked_2, blocked_3, …
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
        composeRule.waitForIdle()

        assertEquals("blocked-account-blocked_12", userAnchorKey(state))
    }

    @Test
    fun theCommentsTabAnchorNamesTheRowOnScreen() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        state.selectUserProfileTab("me_user", 1)
        val history = RedditAccountHistoryUiState(
            accountStableId = account.stableId,
            username = "me_user",
            kind = RedditAccountHistoryKind.COMMENTS,
            queryKey = redditAccountHistoryQueryKey(account.stableId, "me_user", RedditAccountHistoryKind.COMMENTS),
            comments = (1..30).map { n ->
                ProfileCommentFixture(
                    id = "pc$n",
                    author = "me_user",
                    postId = "t3_p$n",
                    age = "1h",
                    body = "Comment $n",
                    score = 1,
                    placement = ThreadPlacement.TOP,
                )
            },
            fromCache = true,
            loaded = true,
            authorizationRequired = true,
        )
        composeRule.setContent {
            SubshellTheme { UserProfileScreen(username = "me_user", state = state, redditAccountHistoryState = history) }
        }
        composeRule.waitForIdle()

        // Rows: profile-header, tabs, pc1, pc2, … and the status row last.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        composeRule.waitForIdle()

        assertEquals("profile-comment-pc10", userAnchorKey(state))
    }

    @Test
    fun anInlineVideoFollowsNewSourcesForTheSamePost() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val coordinator = testVideoPlaybackCoordinator(context)
        val local = SampleData.posts.first().copy(
            id = "t3_v302",
            kind = PostKind.VIDEO,
            localMediaUri = "content://media/picked/302",
            localMediaMimeType = "video/mp4",
        )
        var post by mutableStateOf(local)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                LocalVideoPlayer(post = post, state = state)
            }
        }
        composeRule.waitForIdle()
        assertEquals("content://media/picked/302", coordinator.registeredMediaForTest("t3_v302")?.sources?.first()?.uri)

        // Reddit's own sources replace the local upload under the same id.
        composeRule.runOnIdle {
            post = local.copy(
                localMediaUri = null,
                localMediaMimeType = null,
                remoteVideoUri = "https://v.redd.it/abc302/DASH_720.mp4",
            )
        }
        composeRule.waitForIdle()

        assertEquals(
            "https://v.redd.it/abc302/DASH_720.mp4",
            coordinator.registeredMediaForTest("t3_v302")?.sources?.first()?.uri,
        )
    }

    @Test
    fun anEditedPostKeepsItsLeadingIndentation() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val sent = mutableListOf<PostEditRequest>()
        val post = SampleData.posts.first().copy(id = "t3_e319", author = "me_user", kind = PostKind.TEXT, body = "old")
        composeRule.setContent {
            SubshellTheme {
                PostEditScreen(
                    post = post,
                    state = state,
                    editState = PostEditUiState(),
                    onSubmitEdit = { request -> sent += request },
                    onAuthorizeEdit = {},
                    onCancelAuthorization = {},
                    onResetEditState = {},
                    onSaved = {},
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("    val x = 1\n\nText  ")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        assertEquals("    val x = 1\n\nText", sent.single().text)
    }

    @Test
    fun aDeleteOnACommentLinkRefreshesKeepingTheLinkedComment() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_p238", comments = 2)
        fun comment(id: String, author: String) = Comment(
            id = id,
            author = author,
            age = "1h",
            body = "Body $id",
            score = 1,
            depth = 0,
            accent = 0xFF336699,
        )
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = listOf(comment("focus238", "someone"), comment("mine238", "me_user")),
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        var deleteState by mutableStateOf(CommentDeleteUiState())
        val refreshes = mutableListOf<String>()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    routeFocusedCommentId = "focus238",
                    redditCommentsState = comments,
                    commentDeleteState = deleteState,
                    onRetryRedditComments = { _, _, _ -> refreshes += "plain" },
                    onRefreshRedditCommentsPreservingFocusedTarget = { _, _, _, target -> refreshes += "keep:$target" },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        // The own comment's options are the last set on screen.
        val options = composeRule.onAllNodesWithContentDescription("Comment options")
        options[options.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()
        // ISSUE295_YOUR_COMMENT_ACTIONS_IN_ONE_SHEET_V1
        composeRule.onNodeWithText("Your comment…").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete comment").performClick()
        composeRule.waitForIdle()
        refreshes.clear()

        composeRule.runOnIdle {
            deleteState = CommentDeleteUiState(
                phase = CommentDeletePhase.SUCCEEDED,
                accountStableId = account.stableId,
                postId = post.id,
                commentFullname = "t1_mine238",
                completionRevision = 1L,
            )
        }
        composeRule.waitForIdle()

        assertEquals(listOf("keep:focus238"), refreshes)
    }
}
