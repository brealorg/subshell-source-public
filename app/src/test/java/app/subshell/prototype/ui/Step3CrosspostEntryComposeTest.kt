package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (crosspost01a1e): a signed-in reader finds "Crosspost" in a post's
 * menu; it opens the post composer as a crosspost of that post, owned by the
 * active account. A reader without a Reddit account, or a post whose author
 * is gone, has no such item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CrosspostEntryComposeTest {
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
    private val post = SampleData.posts.first().copy(id = "t3_abc123", author = "someone", permalink = "/r/kotlin/comments/abc123/a_title/")

    private fun show(state: SubshellState, post: Post = this.post) {
        ensureWorkManagerInitialized(context)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = post, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Post actions").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun signedIn() = SubshellState(
        repository = FakeSubshellRepository(),
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun crosspostItems() = composeRule.onAllNodesWithText("Crosspost").fetchSemanticsNodes().size

    @Test
    fun crosspostOpensTheComposerForThisPostAsTheActiveAccount() {
        val state = signedIn()
        show(state)

        composeRule.onAllNodes(hasText("Crosspost") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)
        assertEquals(ComposerPostType.CROSSPOST, state.postComposerDraft.type)
        assertEquals("t3_abc123", state.postComposerDraft.crosspostSource?.fullname)
        assertEquals("reddit:me", state.postComposerDraft.accountStableId)
    }

    @Test
    fun withoutARedditAccountThereIsNoCrosspost() {
        show(SubshellState(repository = FakeSubshellRepository(), initialInteractionSideConfigured = true))
        assertEquals(0, crosspostItems())
    }

    @Test
    fun aPostWhoseAuthorIsGoneHasNoCrosspost() {
        show(signedIn(), post.copy(author = "[deleted]"))
        assertEquals(0, crosspostItems())
    }
}
