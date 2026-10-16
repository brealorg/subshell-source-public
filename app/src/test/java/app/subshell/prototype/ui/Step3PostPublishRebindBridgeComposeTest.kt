package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.data.account.TextPostWriteUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
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
 * STEP3 (crosspost01a2): when Reddit accepts a crosspost the app opens the new
 * post at once (the source stays linked, the review is dropped), then asks
 * Reddit for the real post and shows it if the reader is still there; the
 * write state is reset only after that answer, after Back, or after 8 s
 * without an answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3PostPublishRebindBridgeComposeTest {
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
    private val submitted = SubmittedTextPost(
        fullname = "t3_created123",
        id = "created123",
        community = "target",
        title = "My crosspost",
        text = "",
        permalink = "/r/target/comments/created123/",
        crosspostFullname = "t3_source123",
    )

    private lateinit var state: SubshellState
    private lateinit var requestKey: String
    private var write by mutableStateOf(TextPostWriteUiState())
    private val resolves = mutableListOf<Pair<String, String>>()
    private var answer: ((Post?) -> Unit)? = null
    private var resets = 0
    private var invalidations = 0
    private var resetsWhenAsked = -1

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        state = SubshellState(
            initialAccountSessions = listOf(owner),
            initialActiveAccountId = owner.stableId,
            initialInteractionSideConfigured = true,
        )
        assertTrue(state.openCrosspostComposer(source))
        state.updatePostComposerDraft(
            state.postComposerDraft.withCrosspostDestination("target").copy(title = "My crosspost"),
        )
        requestKey = requireNotNull(state.postComposerDraft.crosspostRequestForIdentityOnly()).requestKey
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                postComposerWriteRuntime = PostComposerWriteRuntimeEntryPoints(
                    textPostWriteState = write,
                    onInvalidateCrosspostReview = { invalidations += 1 },
                ),
                onResetTextPostWriteState = {
                    resets += 1
                    write = TextPostWriteUiState()
                },
                onResolveRedditNavigationTarget = { account, id, done ->
                    resolves += account to id
                    resetsWhenAsked = resets
                    answer = done
                },
            )
        }
        composeRule.waitForIdle()
    }

    private fun publish() {
        composeRule.runOnIdle {
            write = TextPostWriteUiState(
                phase = TextPostWritePhase.SUCCEEDED,
                accountStableId = owner.stableId,
                requestKey = requestKey,
                submittedPost = submitted,
                completionRevision = 1L,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theNewCrosspostOpensAtOnceAndTheRealPostIsAskedBeforeTheReset() {
        publish()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Post)
        val opened = requireNotNull(state.selectedPost)
        assertEquals("t3_created123", opened.id)
        assertEquals(PostKind.CROSSPOST, opened.kind)
        assertEquals("", opened.body)
        assertEquals("t3_source123", opened.crosspost?.id)
        assertEquals(listOf("owner" to "created123"), resolves)
        assertEquals(0, resetsWhenAsked)
        assertEquals(0, resets)
        // One from the publish itself, one from the crosspost editor leaving.
        assertEquals(2, invalidations)
    }

    @Test
    fun redditsAnswerReplacesTheOpenPostAndOnlyThenResets() {
        publish()
        val opened = requireNotNull(state.selectedPost)

        composeRule.runOnIdle { requireNotNull(answer)(opened.copy(score = 7, age = "1m")) }
        composeRule.waitForIdle()

        assertEquals(7, state.selectedPost?.score)
        assertEquals("t3_created123", state.selectedPost?.id)
        assertEquals(1, resets)
    }

    @Test
    fun backBeforeTheAnswerWinsAndTheStateIsStillReset() {
        publish()
        val opened = requireNotNull(state.selectedPost)

        composeRule.runOnIdle { state.navigateBack() }
        composeRule.waitForIdle()
        val routeAfterBack = state.currentNavigationRoute
        composeRule.runOnIdle { requireNotNull(answer)(opened.copy(score = 7)) }
        composeRule.waitForIdle()

        assertTrue(routeAfterBack !is SubshellRoute.Post)
        assertEquals(routeAfterBack, state.currentNavigationRoute)
        assertEquals(1, resets)
    }

    @Test
    fun withoutAnAnswerTheStateIsResetAfterEightSecondsAndTheNewPostStays() {
        composeRule.mainClock.autoAdvance = false
        publish()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("owner" to "created123"), resolves)
        assertEquals(0, resets)

        composeRule.mainClock.advanceTimeBy(7_500)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(1, resets)
        assertEquals("t3_created123", state.selectedPost?.id)
        assertEquals(1, state.selectedPost?.score)
    }
}
