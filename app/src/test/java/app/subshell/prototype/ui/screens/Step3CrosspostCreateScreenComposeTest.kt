package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ComposerPolicySnapshot
import app.subshell.prototype.ui.ComposerPolicyStatus
import app.subshell.prototype.ui.CrosspostReviewPhase
import app.subshell.prototype.ui.CrosspostReviewUiState
import app.subshell.prototype.ui.PostComposerDraft
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.crosspost01A1CPolicy
import app.subshell.prototype.ui.crosspostRequestForIdentityOnly
import app.subshell.prototype.ui.crosspostReviewIdentity
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.withCrosspostDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (crosspost01a1e): a crosspost is written in its own editor with the
 * source locked and no body; Review needs loaded destination rules and only
 * asks; only Publish in the review sends, with the reviewed revision; any edit
 * or leaving the editor drops the review; a crosspost that may already be on
 * Reddit locks the editor and offers only the check; the draft saves itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CrosspostCreateScreenComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val owner = AccountSessionSummary(
        "owner",
        "crossposter",
        AccountSessionSource.AUTHENTICATED,
        AccountSessionState.CONNECTED,
    )
    private val source = Post(
        "t3_source123",
        "source",
        "author",
        "1h",
        "Original title",
        score = 7,
        comments = 2,
        kind = PostKind.TEXT,
    )
    private val loaded = ComposerPolicySnapshot(
        community = "target",
        status = ComposerPolicyStatus.LOADED,
        policy = crosspost01A1CPolicy(),
    )

    private val reviews = mutableListOf<PostComposerDraft>()
    private val publishes = mutableListOf<Pair<PostComposerDraft, Long>>()
    private var invalidations = 0
    private var reconciles = 0
    private val persisted = mutableListOf<List<PostComposerDraft>>()

    private lateinit var state: SubshellState

    @Before
    fun setUp() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        state = SubshellState(
            initialAccountSessions = listOf(owner),
            initialActiveAccountId = owner.stableId,
            persistPostComposerDrafts = { persisted += it },
        )
        assertTrue(state.openCrosspostComposer(source))
        state.updatePostComposerDraft(
            state.postComposerDraft.withCrosspostDestination("target").copy(title = "My crosspost"),
        )
    }

    private fun show(
        policy: () -> ComposerPolicySnapshot = { loaded },
        review: () -> CrosspostReviewUiState = { CrosspostReviewUiState() },
        write: () -> TextPostWriteUiState = { TextPostWriteUiState() },
        visible: () -> Boolean = { true },
    ) {
        composeRule.setContent {
            SubshellTheme {
                if (visible()) {
                    CreatePostScreen(
                        state = state,
                        textPostWriteState = write(),
                        crosspostReviewState = review(),
                        onReviewCrosspost = { reviews += it },
                        onPublishReviewedCrosspost = { draft, revision -> publishes += draft to revision },
                        onInvalidateCrosspostReview = { invalidations += 1 },
                        composerPolicy = policy(),
                        onReconcileTextPostSubmit = { reconciles += 1 },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun readyReview(phase: CrosspostReviewPhase = CrosspostReviewPhase.READY) = CrosspostReviewUiState(
        phase = phase,
        revision = 5L,
        draft = state.postComposerDraft.crosspostReviewIdentity(),
    )

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun enabled(text: String): List<Boolean> =
        composeRule.onAllNodes(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Disabled) == null }

    private fun click(text: String) {
        composeRule.onAllNodes(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val titleField get() = composeRule.onAllNodes(hasSetTextAction()).onFirst()

    @Test
    fun theCrosspostEditorLocksTheSourceAndHasNoBody() {
        show()

        assertEquals(1, count("Original post · source locked"))
        assertEquals(1, count("r/source · u/author"))
        assertEquals(1, count("Original title"))
        assertEquals(1, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun reviewWaitsForTheDestinationRulesAndOnlyAsks() {
        var policy by mutableStateOf(loaded.copy(status = ComposerPolicyStatus.LOADING))
        show(policy = { policy })

        assertEquals(listOf(false), enabled("Review"))

        composeRule.runOnIdle { policy = loaded }
        composeRule.waitForIdle()
        click("Review")

        assertEquals(listOf("My crosspost"), reviews.map { it.title })
        assertEquals(emptyList<Pair<PostComposerDraft, Long>>(), publishes)
    }

    @Test
    fun onlyPublishInTheReviewSendsWithTheReviewedRevision() {
        show(review = { readyReview() })

        assertEquals(1, count("Publish crosspost?"))
        assertEquals(1, count("Posting as u/crossposter to r/target"))
        assertEquals(1, count("NSFW: off · Spoiler: off"))

        click("Publish")

        assertEquals(listOf(5L), publishes.map { it.second })
        assertEquals(listOf("t3_source123"), publishes.map { it.first.crosspostSource?.fullname })
    }

    @Test
    fun backToEditingDropsTheReviewAndAVerifyingReviewCannotPublish() {
        var review by mutableStateOf(readyReview())
        show(review = { review })
        val before = invalidations

        click("Back to editing")
        assertEquals(before + 1, invalidations)
        assertEquals(emptyList<Pair<PostComposerDraft, Long>>(), publishes)

        composeRule.runOnIdle { review = readyReview(CrosspostReviewPhase.VERIFYING) }
        composeRule.waitForIdle()

        assertEquals(1, count("Rechecking source and destination…"))
        assertTrue(enabled("Checking…").isNotEmpty())
        assertTrue(enabled("Checking…").none { it })
    }

    @Test
    fun editingTheTitleDropsTheReview() {
        val review = readyReview()
        show(review = { review })
        assertEquals(1, count("Publish crosspost?"))
        val before = invalidations

        titleField.performTextReplacement("A better title")
        composeRule.waitForIdle()

        assertTrue(invalidations > before)
        assertEquals("A better title", state.postComposerDraft.title)
        assertEquals(0, count("Publish crosspost?"))
    }

    @Test
    fun aCrosspostThatMayBeOnRedditLocksTheEditorAndOffersOnlyTheCheck() {
        val key = requireNotNull(state.postComposerDraft.crosspostRequestForIdentityOnly()).requestKey
        show(
            write = {
                TextPostWriteUiState(
                    phase = TextPostWritePhase.OUTCOME_UNKNOWN,
                    accountStableId = owner.stableId,
                    requestKey = key,
                )
            },
        )

        assertEquals(1, count("Post may have been published"))
        assertEquals(listOf(false), enabled("Review"))
        // The title is still shown but can no longer be edited.
        assertEquals(1, count("My crosspost"))
        assertEquals(0, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)

        click("Check again")

        assertEquals(1, reconciles)
        assertEquals(emptyList<PostComposerDraft>(), reviews)
    }

    @Test
    fun leavingTheEditorDropsTheReview() {
        var visible by mutableStateOf(true)
        show(review = { readyReview() }, visible = { visible })
        val before = invalidations

        composeRule.runOnIdle { visible = false }
        composeRule.waitForIdle()

        assertTrue(invalidations > before)
        assertEquals(emptyList<Pair<PostComposerDraft, Long>>(), publishes)
    }

    @Test
    fun anEditedDraftSavesItself() {
        show()
        persisted.clear()

        titleField.performTextReplacement("Saved by itself")
        composeRule.waitUntil(5_000) {
            persisted.any { drafts -> drafts.any { it.title == "Saved by itself" } }
        }

        assertEquals(
            listOf("t3_source123"),
            persisted.last().filter { it.title == "Saved by itself" }.map { it.crosspostSource?.fullname },
        )
    }
}
