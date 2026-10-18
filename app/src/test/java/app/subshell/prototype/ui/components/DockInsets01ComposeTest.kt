package app.subshell.prototype.ui.components

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ExpandedContextReadingMaxWidth
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.ExpandedContextSurface
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.screens.CommentEditScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.ThemeMode
import app.subshell.prototype.visual.VisualFixtures
import app.subshell.prototype.visual.VisualIdentityRepository
import app.subshell.prototype.visual.VisualRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DOCKINSETS01_ROUTES_SEE_THE_ROOT_PADDING_AS_CONSUMED_V1: the app places a
 * route inside its Scaffold padding, which already leaves out the gesture bar.
 * A bar in the route that pads for the gesture bar or the keyboard must not
 * count the gesture bar again: the formatting dock sits right above the gesture
 * bar, and right on the keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DockInsets01ComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    /** A 24 dp gesture bar, and a 300 dp keyboard when [imePx] is set (density 1). */
    private fun insets(imePx: Int = 0) {
        val builder = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, NAV_PX, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_PX))
        if (imePx > 0) {
            builder.setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
        }
        composeRule.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view.rootView, builder.build())
            view.requestLayout()
        }
        composeRule.waitForIdle()
    }

    private fun app(comments: (String, RedditCommentSort) -> RedditCommentsUiState? = { _, _ -> null }): SubshellState {
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
        var loaded by mutableStateOf(RedditCommentsUiState())
        composeRule.setContent {
            view = LocalView.current
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = VisualFixtures.subscriptions),
                redditComments = CommentLoadingEntryPoints(
                    redditCommentsState = loaded,
                    onLoadRedditComments = { _, postId, sort ->
                        comments(postId, sort)?.let { loaded = it }
                    },
                ),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    /**
     * Opens the screen, then hands the window its insets: Compose re-reads the
     * real (empty) Robolectric insets when a screen replaces the whole tree.
     */
    private fun open(action: () -> Unit) {
        composeRule.runOnIdle(action)
        composeRule.waitForIdle()
        insets()
    }

    /** The dock's Undo button; the dock's row adds 4 dp around the 40 dp button and 3 dp padding. */
    private fun dockBottom() =
        composeRule.onAllNodesWithContentDescription("Undo").onFirst().getBoundsInRoot().bottom.value + DOCK_BELOW_UNDO

    private fun assertDockSitsOnTheGestureBarAndOnTheKeyboard() {
        assertEquals("above the gesture bar", WINDOW_DP - NAV_PX, dockBottom(), 0.5f)
        insets(imePx = IME_PX)
        assertEquals("on the keyboard", WINDOW_DP - IME_PX, dockBottom(), 0.5f)
    }

    private val textPost get() = VisualFixtures.posts.first { it.kind == PostKind.TEXT }

    @Test
    fun postEditsDockIsNotLiftedByTheGestureBarTwice() {
        val state = app()
        open {
            state.openFromMainFeed(textPost)
            state.requestPostEdit(textPost.id)
        }
        assertDockSitsOnTheGestureBarAndOnTheKeyboard()
    }

    /**
     * In the app the editor opens from a comment's menu in PostScreen, which
     * lays it out in the route's ExpandedContextSurface; it is shown here in
     * that surface with the root Scaffold's padding (the gesture bar).
     */
    @Test
    fun commentEditsDockIsNotLiftedByTheGestureBarTwice() {
        val account = VisualFixtures.active
        val state = SubshellState(
            initialAccountSessions = VisualFixtures.accounts,
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val comment = SampleData.comments.first().copy(
            id = "t1_own",
            author = account.username,
            body = "My own comment",
            inlineMedia = null,
            codeSample = null,
        )
        composeRule.setContent {
            view = LocalView.current
            SubshellTheme {
                ExpandedContextSurface(
                    scaffoldPadding = WindowInsets.navigationBars.asPaddingValues(),
                    expanded = false,
                    maxWidth = ExpandedContextReadingMaxWidth,
                ) { contextModifier ->
                    CommentEditScreen(
                        post = textPost,
                        comment = comment,
                        state = state,
                        commentSort = RedditCommentSort.BEST,
                        editState = CommentEditUiState(),
                        onSubmitEdit = {},
                        onAuthorizeEdit = {},
                        onCancelAuthorization = {},
                        onResetEditState = {},
                        onSaved = {},
                        onCheckAgain = {},
                        onClose = {},
                        modifier = contextModifier,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        insets()
        assertDockSitsOnTheGestureBarAndOnTheKeyboard()
    }

    @Test
    fun createPostsDockSitsOnTheGestureBarAndOnTheKeyboard() {
        val state = app()
        open { state.openPostComposer() }
        assertDockSitsOnTheGestureBarAndOnTheKeyboard()
    }

    @Test
    fun aConversationsComposerIsNotLiftedByTheGestureBarTwice() {
        val state = app()
        open { state.openInboxItem(SampleData.inbox.first { it.kind == ActivityKind.MESSAGE }) }
        // The composer's surface also holds the thread's notices below the card.
        fun composerBottom() =
            composeRule.onNodeWithTag(COMPACT_REPLY_COMPOSER_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNode().parent!!.boundsInRoot.bottom
        assertEquals("above the gesture bar", WINDOW_DP - NAV_PX, composerBottom(), 0.5f)
        insets(imePx = IME_PX)
        assertEquals("on the keyboard", WINDOW_DP - IME_PX, composerBottom(), 0.5f)
    }

    private companion object {
        const val IME_PX = 300
        const val NAV_PX = 24
        const val WINDOW_DP = 891f
        const val DOCK_BELOW_UNDO = 7f
    }
}
