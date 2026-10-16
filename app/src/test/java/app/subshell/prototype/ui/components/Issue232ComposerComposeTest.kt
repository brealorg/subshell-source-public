package app.subshell.prototype.ui.components

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.screens.ConversationScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.ThemeMode
import app.subshell.prototype.visual.VisualFixtures
import app.subshell.prototype.visual.VisualIdentityRepository
import app.subshell.prototype.visual.VisualRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #232: the quick reply is one card that sits on the keyboard, its context
 * line is body text, and Expand opens the same draft as a long reply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue232ComposerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    /** A 300 dp keyboard over a 24 dp gesture bar (density 1 under these qualifiers). */
    private fun openKeyboard() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, IME_PX))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_PX))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        composeRule.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view.rootView, insets)
            view.requestLayout()
        }
        composeRule.waitForIdle()
    }

    private fun composerBottom() =
        composeRule.onNodeWithTag(COMPACT_REPLY_COMPOSER_TEST_TAG).getBoundsInRoot().bottom

    @Test
    fun theCommentComposerSitsOnTheKeyboardWithNothingShowingThrough() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            initialThemeMode = ThemeMode.DARK,
            initialInteractionSideConfigured = true,
            initialAccountSessions = VisualFixtures.accounts,
            initialActiveAccountId = VisualFixtures.active.stableId,
            repository = VisualRepository(),
            identityMetadataRepository = VisualIdentityRepository,
        )
        var comments by mutableStateOf(RedditCommentsUiState())
        composeRule.setContent {
            view = LocalView.current
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = VisualFixtures.subscriptions),
                redditComments = CommentLoadingEntryPoints(
                    redditCommentsState = comments,
                    onLoadRedditComments = { _, postId, sort ->
                        val post = VisualFixtures.original(postId)
                        comments = VisualFixtures.comments(postId, sort, state.commentsForPost(post))
                    },
                ),
            )
        }
        composeRule.waitForIdle()
        val post = VisualFixtures.posts.first { it.kind == PostKind.TEXT }
        composeRule.runOnIdle {
            state.openFromMainFeed(post)
            state.openCommentComposer(post)
        }
        composeRule.waitForIdle()

        openKeyboard()

        // The app leaves out the gesture bar; imePadding counted it again and
        // the composer stopped 24 dp above the keyboard, the post showing in between.
        assertEquals(WINDOW_DP - IME_PX.toFloat(), composerBottom().value, 0.5f)
    }

    @Test
    fun aConversationsComposerSitsOnTheKeyboard() {
        val item = SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }
        composeRule.setContent {
            view = LocalView.current
            SubshellTheme {
                Box(Modifier.fillMaxSize().padding(bottom = NAV_PX.dp)) {
                    ConversationScreen(item = item, onBack = {}, replyParentFullname = "t4_reply")
                }
            }
        }
        composeRule.waitForIdle()

        openKeyboard()

        assertEquals(WINDOW_DP - IME_PX.toFloat(), composerBottom().value, 0.5f)
    }

    private fun showComposer(
        state: TextFieldState,
        onSend: () -> Unit = {},
        contextLabel: String? = "Replying to u/bob · A post",
    ) {
        composeRule.setContent {
            SubshellTheme {
                CompactReplyComposer(
                    state = state,
                    media = null,
                    placeholder = "Reply",
                    sendEnabled = true,
                    onSend = onSend,
                    onMediaReceived = {},
                    onMediaRemoved = {},
                    onPickImage = {},
                    onPickGif = {},
                    mediaEnabled = true,
                    interactionActionsAtStart = false,
                    contextLabel = contextLabel,
                    longReplyTitle = "Reply to u/bob",
                    quote = "The comment being answered",
                    sendLabel = "Post",
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun expand() {
        composeRule.onNode(hasContentDescription("Expand") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun expandOpensTheSameDraftAsALongReplyAndPostSendsItOnce() {
        val state = TextFieldState("A long answer")
        var sends = 0
        showComposer(state, onSend = { sends++ })

        expand()

        // Robolectric does not lay out dialog windows: their nodes exist.
        composeRule.onNodeWithText("Reply to u/bob").assertExistsInTree()
        composeRule.onNodeWithText("The comment being answered").assertExistsInTree()
        assertEquals(2, composeRule.onAllNodesWithText("A long answer").fetchSemanticsNodes().size)

        composeRule.onNode(hasText("Post") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, sends)
        assertEquals(0, composeRule.onAllNodesWithText("Reply to u/bob").fetchSemanticsNodes().size)
    }

    @Test
    fun closingTheLongReplyKeepsWhatWasWrittenThere() {
        val state = TextFieldState("Start")
        showComposer(state)
        expand()

        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("Start and more")
        composeRule.onNode(hasContentDescription("Back to quick reply") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("Start and more", state.text.toString())
        assertEquals(0, composeRule.onAllNodesWithText("Reply to u/bob").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Start and more").fetchSemanticsNodes().size)
    }

    @Test
    fun theContextLineIsBodyTextNotMonospace() {
        showComposer(TextFieldState())

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Replying to u/bob · A post")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertTrue(layouts.isNotEmpty())
        assertNotEquals(FontFamily.Monospace, layouts.first().layoutInput.style.fontFamily)
    }

    @Test
    fun theFieldShowsMarkdownAsItsStyleAndKeepsTheMarkers() {
        val state = TextFieldState("say **this**")
        showComposer(state)

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasSetTextAction())
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val shown = layouts.first().layoutInput.text

        assertEquals("say **this**", shown.text)
        assertTrue(
            shown.spanStyles.any { range ->
                range.item.fontWeight == FontWeight.Bold && shown.text.substring(range.start, range.end) == "**this**"
            },
        )
        assertEquals("say **this**", state.text.toString())
    }

    private fun count(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    @Test
    fun expandStaysWhileTheFormattingActionsAreShown() {
        showComposer(TextFieldState("Text"))
        composeRule.onNode(hasContentDescription("Formatting") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        expand()

        composeRule.onNodeWithText("Reply to u/bob").assertExistsInTree()
    }

    @Test
    fun theLongReplyHasTheSameAaAndTheChoiceCarriesBack() {
        showComposer(TextFieldState("Text"))
        expand()
        assertEquals(2, count(hasContentDescription("Image")))

        // The long reply's Aa is the last one in the tree (the dialog is drawn after the card).
        composeRule.onAllNodes(hasContentDescription("Formatting") and hasClickAction()).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(0, count(hasContentDescription("Image")))
        assertEquals(2, count(hasContentDescription("Undo")))

        composeRule.onNode(hasContentDescription("Back to quick reply") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, count(hasContentDescription("Undo")))
        assertEquals(0, count(hasContentDescription("Image")))
    }

    @Test
    fun previewShowsTheReplyAsTheThreadWillAndEditReturnsToTheField() {
        val state = TextFieldState("say **this**")
        showComposer(state)
        expand()
        assertEquals(2, count(hasSetTextAction()))

        composeRule.onNode(hasContentDescription("Preview") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(1, count(hasSetTextAction()))
        assertEquals(1, count(hasText("say this")))
        // The toolbar goes with the field: only the quick reply keeps its Aa.
        assertEquals(1, count(hasContentDescription("Formatting")))

        composeRule.onNode(hasContentDescription("Edit") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(2, count(hasSetTextAction()))
        assertEquals("say **this**", state.text.toString())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsInTree() {
        // Throws when no node matches; dialog nodes exist but are not laid out.
        fetchSemanticsNode()
    }

    private companion object {
        const val IME_PX = 300
        const val NAV_PX = 24
        const val WINDOW_DP = 891f
    }
}
