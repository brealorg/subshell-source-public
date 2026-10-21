package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.DirectChatLiveUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditModmailMessage
import app.subshell.prototype.harness.NoOpMagnifier
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.MessageBubble
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.InboxModmailMessageRow
import app.subshell.prototype.ui.components.MarkdownText
import app.subshell.prototype.ui.screens.ConversationScreen
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #235: a long press selects the text of an open post, a comment and a
 * private message or modmail message, and Android's own menu (here a
 * recording text context menu) is offered with Copy; a single tap still collapses a
 * comment in the Comment collapse mode; previews and a hidden spoiler are not
 * selectable; a direct chat message is selected the same way (owner: one
 * behaviour in the whole app) and keeps its menu on the ⋮.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", shadows = [NoOpMagnifier::class])
// Selection handles are drawn into bitmaps; the magnifier is a no-op (NoOpMagnifier).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Issue235TextSelectionComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val toolbar = RecordingTextContextMenu()

    /** Android's selection menu as Compose 1.12 asks for it; counts menus that offer Copy. */
    private class RecordingTextContextMenu : TextContextMenuProvider {
        var copyOffered = 0

        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            if (dataProvider.data().components.any { it.key == TextContextMenuKeys.CopyKey }) copyOffered += 1
            awaitCancellation()
        }
    }

    private fun withToolbar(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides toolbar) { content() }
        }
        composeRule.waitForIdle()
    }

    // On the text itself: a clickable card merges its texts into one node.
    private fun longPress(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher, useUnmergedTree = true).onFirst().performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(
            AccountSessionSummary(
                stableId = "reddit:test",
                username = "me",
                source = AccountSessionSource.AUTHENTICATED,
                state = AccountSessionState.CONNECTED,
                providerLabel = "Reddit",
            ),
        ),
        initialActiveAccountId = "reddit:test",
        initialInteractionSideConfigured = true,
    )

    private fun showThread(state: SubshellState, body: String, postBody: String = "") {
        ensureWorkManagerInitialized(context)
        val post = state.posts.first().copy(kind = PostKind.TEXT, body = postBody)
        val coordinator = testVideoPlaybackCoordinator(context)
        val comment = Comment(id = "t1_s", author = "someone", age = "1h", body = body, score = 1, depth = 0, accent = 0L)
        withToolbar {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = state.activeAccountId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = listOf(comment),
                        fullDiscussionLoaded = true,
                    ),
                )
            }
        }
        runCatching {
            composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("comment:${comment.id}")
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aLongPressSelectsACommentsText() {
        showThread(signedIn(), body = "Selectable comment words")

        longPress(hasText("Selectable comment words", substring = true))

        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)
    }

    @Test
    fun aLongPressSelectsTheOpenPostsBody() {
        showThread(signedIn(), body = "A comment", postBody = "Open post words to select")
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("post-card")
        composeRule.waitForIdle()

        longPress(hasText("Open post words to select", substring = true))

        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)
    }

    @Test
    fun aTapStillCollapsesACommentInTheCommentMode() {
        val state = signedIn()
        state.updateCommentCollapseMode(CommentCollapseMode.COMMENT)
        showThread(state, body = "Tap collapses this")

        composeRule.onAllNodes(hasText("Tap collapses this", substring = true)).onFirst().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(0, shown("Tap collapses this"))
        assertEquals(0, toolbar.copyOffered)
    }

    @Test
    fun previewsAndUnselectableTextOfferNothing() {
        withToolbar {
            SubshellTheme {
                androidx.compose.foundation.layout.Column {
                    MarkdownText(text = "Plain body words")
                    MarkdownText(text = "Preview words", maxLines = 2, selectable = true)
                }
            }
        }

        longPress(hasText("Plain body words", substring = true))
        longPress(hasText("Preview words", substring = true))

        assertEquals(0, toolbar.copyOffered)
    }

    @Test
    fun aHiddenSpoilerIsNotSelectedButItsTextIsAfterRevealing() {
        withToolbar {
            SubshellTheme { MarkdownText(text = ">!the secret!<", selectable = true) }
        }

        // A long press on the hidden label selects nothing; its release is
        // the block's tap and reveals it (as before #235).
        longPress(hasText("Spoiler · tap to reveal", substring = true))
        assertEquals(0, toolbar.copyOffered)
        assertEquals(1, shown("the secret"))

        longPress(hasText("the secret", substring = true))
        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)
    }

    private fun message(id: String, sender: String, body: String) =
        MessageBubble(id = id, sender = sender, age = "1h", body = body)

    @Test
    fun aLongPressSelectsAPrivateMessage() {
        val thread = InboxItem(
            id = "t4_pm",
            kind = ActivityKind.MESSAGE,
            title = "Plans",
            sender = "bob",
            age = "1h",
            preview = "See you",
            conversation = listOf(message("t4_1", "bob", "See you at noon")),
        )
        withToolbar { ConversationScreen(item = thread, onBack = {}) }

        longPress(hasText("See you at noon", substring = true))

        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)
    }

    @Test
    fun aLongPressSelectsAModmailMessage() {
        withToolbar {
            SubshellTheme {
                InboxModmailMessageRow(
                    message = RedditModmailMessage(
                        id = "m1",
                        authorName = "someone",
                        body = "Modmail words to select",
                        createdEpochSeconds = 1L,
                        isInternal = false,
                        participatingAs = "participant_user",
                    ),
                    communityName = "subshell",
                )
            }
        }

        longPress(hasText("Modmail words to select", substring = true))

        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)
    }

    @Test
    fun aDirectChatMessageIsSelectedLikeEveryOtherTextAndKeepsItsMenu() {
        // A live message has the menu (React, Delete).
        val live = MessageBubble(id = "\$own1", sender = "me", age = "now", body = "hello there", outgoing = true)
        val chat = InboxItem(
            id = "chat_1",
            kind = ActivityKind.MESSAGE,
            title = "[direct chat room]",
            sender = "peer",
            age = "1h",
            preview = "hello there",
            conversation = listOf(live),
        )
        withToolbar {
            ConversationScreen(
                item = chat,
                onBack = {},
                writeEnabled = true,
                accountStableId = "reddit:test",
                ownUsername = "me",
                directChatLiveState = DirectChatLiveUiState(
                    accountStableId = "reddit:test",
                    inboxItemId = "chat_1",
                    participantUsername = "peer",
                    live = true,
                    messages = listOf(live),
                ),
                directChatParticipantUsername = "peer",
                onReactDirectChatMessage = { _, _ -> },
            )
        }

        // The long press selects, as everywhere (owner); the menu is the ⋮.
        longPress(hasText("hello there", substring = true))
        assertTrue("copy offered ${toolbar.copyOffered}", toolbar.copyOffered > 0)

        click(hasContentDescription("Message actions") and hasClickAction())
        assertEquals(1, shown("React"))
    }
}
