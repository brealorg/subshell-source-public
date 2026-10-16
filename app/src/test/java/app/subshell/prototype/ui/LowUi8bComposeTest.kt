package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalMessage
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.components.RemovalResponseDraft
import app.subshell.prototype.ui.components.RemovalResponseEditor
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.ui.screens.SettingsScreen
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.screens.SearchScreen
import app.subshell.prototype.ui.screens.THREAD_FIND_OPEN_LABEL
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.AppTextScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOWUI8b: LOWUI8_REMOVAL_MESSAGE_FIELD_STOPS_AT_THE_LIMIT_V1 (OCB-162),
 * LOWUI8_KEYBOARD_SEARCH_FOLLOWS_THE_BUTTON_V1 (OCB-253),
 * LOWUI8_THREAD_FIND_SEARCH_USES_THE_TYPED_QUERY_V1 (OCB-280),
 * LOWUI8_PREVIEW_TEXT_SIZE_APPLIED_ONCE_V1 (OCB-254).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUi8bComposeTest {
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
        initialOpenKeyboardOnSearch = false,
    )

    @Test
    fun theRemovalMessageFieldStopsAtTheLimit() {
        val limit = RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS
        val draft = RemovalResponseDraft()
        composeRule.setContent {
            RemovalResponseEditor(
                draft = draft,
                kind = RedditModeratorQueueKind.POST,
                savedResponses = null,
                onDone = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("x".repeat(limit + 5))
        composeRule.waitForIdle()

        assertEquals(limit, draft.body.length)
        val shown = composeRule.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals(limit, shown.length)
    }

    @Test
    fun theKeyboardSearchRefusesWhatTheButtonRefuses() {
        ensureWorkManagerInitialized(context)
        val state = signedIn().apply { openSearch(refocus = false) }
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SearchScreen(state = state, onBack = {})
            }
        }
        composeRule.waitForIdle()
        val before = state.searchSubmissionRevision

        val field = composeRule.onAllNodes(hasSetTextAction())[0]
        field.performTextInput("a")
        field.performImeAction()
        composeRule.waitForIdle()
        assertEquals(before, state.searchSubmissionRevision)

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("b")
        composeRule.onAllNodes(hasSetTextAction())[0].performImeAction()
        composeRule.waitForIdle()
        assertEquals(before + 1, state.searchSubmissionRevision)
    }

    @Test
    fun keyboardSearchInThreadFindStepsToTheTypedQuerysFirstMatch() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val post = SampleData.posts.first().copy(id = "t3_p8b", comments = 2)
        fun comment(id: String, body: String) = Comment(
            id = id,
            author = "someone",
            age = "1h",
            body = body,
            score = 1,
            depth = 0,
            accent = 0xFF336699,
        )
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = listOf(comment("c8a", "alpha words"), comment("c8b", "beta words")),
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(post = post, state = state, onBack = {}, redditCommentsState = comments)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).performClick()
        composeRule.waitForIdle()

        // Search pressed at once, before the typing pause has passed.
        composeRule.mainClock.autoAdvance = false
        val field = composeRule.onNode(hasSetTextAction())
        field.performTextInput("beta")
        field.performImeAction()
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1/1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theAppearancePreviewScalesTextOnce() {
        ensureWorkManagerInitialized(context)
        val state = signedIn().apply { updateAppTextScale(AppTextScale.LARGER) }
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            // As SubshellApp: the whole app runs at the chosen text size.
            SubshellTheme(textScale = state.appTextScale.factor, manageSystemBarContrast = false) {
                Column {
                    Text("r/android", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("reference"))
                    SettingsScreen(
                        state = state,
                        backupRepository = repository,
                        onBack = {},
                        initialDestinationName = SettingsDestination.APPEARANCE.name,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Robolectric does not grow text bounds with the font scale; compare
        // the scale each text is laid out with.
        fun SemanticsNode.layoutFontScale(): Float {
            val results = mutableListOf<TextLayoutResult>()
            config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            return results.single().layoutInput.density.fontScale
        }
        val reference = composeRule.onNodeWithTag("reference", useUnmergedTree = true).fetchSemanticsNode()
            .layoutFontScale()
        val preview = composeRule.onAllNodesWithText("r/android", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { node -> node.config.getOrNull(SemanticsProperties.TestTag) != "reference" }
            .layoutFontScale()
        assertEquals(reference, preview, 0.001f)
    }
}
