package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostSavePhase
import app.subshell.prototype.data.account.PostSaveRequest
import app.subshell.prototype.data.account.PostSaveUiState
import app.subshell.prototype.data.account.RedditCommunityInfoUiState
import app.subshell.prototype.data.account.RedditContentReportRequest
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.CommunityInfoSnapshot
import app.subshell.prototype.model.CommunityRule
import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.ProfileActivityEvidence
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (commentuser01, commentsave01a1/a2): tapping a comment's author opens
 * their profile; a deleted author says the account is unavailable and opens
 * nothing; the thread button collapses and expands a comment, and a tap on
 * the body collapses it only in the Comment collapse mode; a signed-in reader
 * saves and unsaves a comment from its menu (Reddit's saved state shown, one
 * save at a time, a failed save rolls back); without a Reddit account the
 * menu offers no Save. The thread and options buttons are 48 dp targets
 * (a11y01a11).
 *
 * STEP3 (safety01a3): a signed-in reader reports a comment from its menu with
 * one of the community's comment rules (the comment's fullname, no
 * community); Report comment waits while another safety action is sending;
 * without a Reddit account the menu has no Report comment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CommentRowComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val saves = mutableListOf<PostSaveRequest>()
    private val reports = mutableListOf<RedditContentReportRequest>()

    private fun comment(id: String, author: String, body: String = "Body $id", saved: Boolean = false) = Comment(
        id = id, author = author, age = "1h", body = body, score = 1, depth = 0, accent = 0L, saved = saved,
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    ).also {
        it.setAuthenticatedPostSaveRequester { request -> saves += request }
        it.setAuthenticatedContentSafetyRequesters(
            postVisibilityRequester = {},
            reportRequester = { request -> reports += request },
        )
    }

    private fun show(state: SubshellState, comments: MutableState<List<Comment>>) {
        ensureWorkManagerInitialized(context)
        val post = state.posts.first()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = state.activeAccountId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = comments.value,
                        fullDiscussionLoaded = true,
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        // A local profile's thread has no Reddit comments to scroll to.
        runCatching {
            composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("comment:${comments.value.first().id}")
        }
        composeRule.waitForIdle()
    }

    private fun click(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun shown(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun openCommentMenu() = click(hasContentDescription("Comment options") and hasClickAction())

    @Test
    fun tappingTheAuthorOpensTheirProfile() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_a", "commenter42"))))

        click(hasText("commenter42", substring = true) and hasClickAction())

        assertEquals("commenter42", (state.currentNavigationRoute as SubshellRoute.User).username)
        assertTrue(state.hasPublicProfileActivityEvidence("commenter42", ProfileActivityEvidence.COMMENT))
    }

    @Test
    fun aDeletedAuthorSaysTheAccountIsUnavailableAndOpensNothing() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_d", "[deleted]"))))
        val routeBefore = state.currentNavigationRoute

        click(hasText("[deleted]", substring = true) and hasClickAction())

        assertEquals(routeBefore, state.currentNavigationRoute)
        assertEquals(1, shown("Account unavailable"))
    }

    @Test
    fun theThreadButtonCollapsesAndTheBodyOnlyInCommentMode() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_b", "someone", body = "Body text here"))))

        click(hasContentDescription("Collapse thread") and hasClickAction())
        assertEquals(0, shown("Body text here"))
        click(hasContentDescription("Expand thread") and hasClickAction())
        assertEquals(1, shown("Body text here"))

        // Header mode: the body is not a collapse target.
        assertEquals(0, composeRule.onAllNodes(hasText("Body text here", substring = true) and hasClickAction()).fetchSemanticsNodes().size)

        composeRule.runOnIdle { state.updateCommentCollapseMode(CommentCollapseMode.COMMENT) }
        composeRule.waitForIdle()
        click(hasText("Body text here", substring = true) and hasClickAction())
        assertEquals(0, shown("Body text here"))
    }

    @Test
    fun aSignedInReaderSavesAndUnsavesACommentFromItsMenu() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_s", "someone"))))

        openCommentMenu()
        click(hasText("Save comment") and hasClickAction())

        assertEquals(listOf(PostSaveRequest(account.stableId, "t1_s", true)), saves)
        composeRule.runOnIdle {
            state.synchronizePostSaveState(
                PostSaveUiState(phase = PostSavePhase.FAILED, accountStableId = account.stableId, postFullname = "t1_s", saved = true),
            )
        }
        composeRule.waitForIdle()
        openCommentMenu()
        assertEquals(1, shown("Save comment"))
    }

    @Test
    fun redditsSavedStateIsShownAndUnsaveSendsIt() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_r", "someone", saved = true))))

        openCommentMenu()
        click(hasText("Unsave comment") and hasClickAction())

        assertEquals(listOf(PostSaveRequest(account.stableId, "t1_r", false)), saves)
    }

    @Test
    fun aSaveWaitsWhileAnotherIsSending() {
        val state = signedIn()
        state.toggleSaved(comment("t1_other", "someone_else"))
        assertEquals(1, saves.size)
        saves.clear()
        show(state, mutableStateOf(listOf(comment("t1_w", "someone"))))

        openCommentMenu()
        composeRule.onAllNodes(hasText("Save comment") and hasClickAction()).onFirst().assertIsNotEnabled()
        assertEquals(emptyList<PostSaveRequest>(), saves)
    }

    @Test
    fun withoutARedditAccountTheMenuOffersNoSave() {
        val local = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        show(state, mutableStateOf(listOf(comment("t1_l", "someone"))))

        if (composeRule.onAllNodesWithContentDescription("Comment options").fetchSemanticsNodes().isNotEmpty()) {
            openCommentMenu()
        }
        assertEquals(0, shown("Save comment"))
        assertEquals(0, shown("Unsave comment"))
    }

    // STEP3 (a11y01a11): the thread and options buttons are 48 dp targets,
    // collapsed or not.
    @Test
    fun theThreadAndOptionsButtonsAre48DpTargets() {
        val state = signedIn()
        show(state, mutableStateOf(listOf(comment("t1_t", "someone"))))

        fun assert48(name: String) {
            val button = composeRule.onAllNodes(hasContentDescription(name) and hasClickAction()).onFirst()
            button.assertWidthIsAtLeast(48.dp)
            button.assertHeightIsAtLeast(48.dp)
        }
        assert48("Collapse thread")
        assert48("Comment options")

        click(hasContentDescription("Collapse thread") and hasClickAction())
        assert48("Expand thread")
    }

    // STEP3 (safety01a3 G3): Report comment.
    private fun commentReasonsLoaded(state: SubshellState) {
        val community = state.posts.first().community
        state.synchronizeReportReasonCommunityInfo(
            RedditCommunityInfoUiState(
                accountStableId = account.stableId,
                communityName = community,
                loaded = true,
                snapshot = CommunityInfoSnapshot(
                    communityName = community,
                    freeFormReports = true,
                    rules = listOf(
                        CommunityRule(shortName = "No spam", kind = "link", violationReason = "no-spam"),
                        CommunityRule(shortName = "Be kind", kind = "all", violationReason = "be-kind"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun aSignedInReaderReportsACommentWithACommentRule() {
        val state = signedIn()
        commentReasonsLoaded(state)
        show(state, mutableStateOf(listOf(comment("t1_rep", "someone"))))

        openCommentMenu()
        click(hasText("Report comment") and hasClickAction())
        // Only the rules that apply to comments are offered.
        assertEquals(0, shown("No spam"))
        click(hasText("Be kind") and hasClickAction())
        click(hasText("Report") and hasClickAction())

        assertEquals(listOf(RedditContentReportRequest(account.stableId, "t1_rep", "be-kind")), reports)
    }

    @Test
    fun reportCommentWaitsWhileAnotherSafetyActionIsSending() {
        val state = signedIn()
        state.report(comment("t1_first", "someone_else"), "be-kind")
        assertEquals(1, reports.size)
        reports.clear()
        show(state, mutableStateOf(listOf(comment("t1_wait", "someone"))))

        openCommentMenu()

        composeRule.onAllNodes(hasText("Report comment") and hasClickAction()).onFirst().assertIsNotEnabled()
        assertEquals(emptyList<RedditContentReportRequest>(), reports)
    }

    @Test
    fun withoutARedditAccountTheMenuOffersNoReport() {
        val local = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            initialAccountSessions = local,
            initialActiveAccountId = local.first().stableId,
            initialInteractionSideConfigured = true,
        )
        show(state, mutableStateOf(listOf(comment("t1_lr", "someone"))))

        if (composeRule.onAllNodesWithContentDescription("Comment options").fetchSemanticsNodes().isNotEmpty()) {
            openCommentMenu()
        }
        assertEquals(0, shown("Report comment"))
    }
}
