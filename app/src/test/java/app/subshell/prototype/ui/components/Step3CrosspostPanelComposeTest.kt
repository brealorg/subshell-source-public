package app.subshell.prototype.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
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
 * STEP3 (crosspost01): a crosspost shows its source as a card — the source
 * community, its age and author, the formatted body, and "points" and
 * "comments" counted in the singular or plural; a source without a Reddit
 * address cannot be opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3CrosspostPanelComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun show(source: CrosspostPreview) {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val wrapper = Post(
            id = "t3_wrapper",
            community = "kotlin",
            author = "crossposter",
            age = "1h",
            title = "A crosspost",
            score = 1,
            comments = 0,
            kind = PostKind.CROSSPOST,
            crosspost = source,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    PostCard(post = wrapper, state = state, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private val source = CrosspostPreview(
        community = "pics",
        author = "photographer",
        title = "The source post",
        score = 10,
        id = "t3_source",
        age = "3h",
        body = "Some **bold** words",
        comments = 1,
        kind = PostKind.TEXT,
    )

    @Test
    fun theSourceCardShowsWhereItCameFromAndItsFormattedBody() {
        show(source)

        assertTrue(count("r/pics") >= 1)
        assertTrue(count(" · 3h") >= 1)
        assertEquals(1, count("u/photographer"))
        assertEquals(1, count("Some bold words"))
        assertEquals(0, count("**bold**"))
        assertEquals(1, count("10 points · 1 comment"))
    }

    @Test
    fun oneComesInTheSingularAndMoreInThePlural() {
        show(source.copy(score = 1, comments = 2))

        assertEquals(1, count("1 point · 2 comments"))
    }

    @Test
    fun aSourceWithoutARedditAddressCannotBeOpened() {
        show(source.copy(id = "", permalink = null))

        assertEquals(1, count("The source post"))
        assertEquals(0, composeRule.onAllNodes(hasText("The source post") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size)
    }
}
