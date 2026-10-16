package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditInboxUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.ActivityKind
import app.subshell.prototype.model.InboxItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.app.InboxEntryPoints
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (a11y01a2, a11y01a3, a11y01a4; #224 geometry): on a wide window a
 * utility screen (Help) keeps the 640 dp reading width, community discovery
 * may use more but stops at 960 dp, the post beside the Home list reads at
 * 640 dp, and the Inbox on its own (single mode) is a 640 dp column too.
 * Widths are read from the screen's vertical scroll container.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3ContextWidthsComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val local = AccountSessionSummary(
        stableId = "local:reader",
        username = "reader",
        source = AccountSessionSource.LOCAL_FIXTURE,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Local",
    )

    private val nowSeconds = System.currentTimeMillis() / 1_000
    private val messages = (1..12).map { index ->
        InboxItem(
            id = "t4_m$index",
            kind = ActivityKind.MESSAGE,
            title = "Thread $index",
            sender = "sender$index",
            age = "1h",
            preview = "Message number $index",
            createdEpochSeconds = nowSeconds - index * 60L,
        )
    }

    private fun compose(
        activeLocal: Boolean = false,
        mode: TabletLayoutMode = TabletLayoutMode.SPLIT,
    ): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = listOf(account, local),
            initialActiveAccountId = if (activeLocal) local.stableId else account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(mode)
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(
                    redditInboxState = RedditInboxUiState(
                        accountStableId = account.stableId,
                        items = messages,
                        loaded = true,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** Every vertical scroll container on screen. */
    private fun verticalLists(): List<SemanticsNode> = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes()

    private fun widthDp(node: SemanticsNode): Float =
        with(composeRule.density) { node.boundsInRoot.width.toDp().value }

    private fun widestListDp(): Float {
        val lists = verticalLists()
        assertTrue("the screen has a vertical list", lists.isNotEmpty())
        return lists.maxOf(::widthDp)
    }

    @Test
    fun helpKeepsTheReadingWidth() {
        val state = compose()
        composeRule.runOnIdle { state.openSecondary(SecondaryDestination.HELP) }
        composeRule.waitForIdle()

        val width = widestListDp()
        assertTrue("Help is at most 640 dp wide: $width", width <= 640.5f)
        assertTrue("Help still uses the reading width: $width", width >= 500f)
    }

    @Test
    fun communityDiscoveryIsWiderThanReadingButStopsAt960() {
        val state = compose()
        composeRule.runOnIdle { state.openCommunityExplore() }
        composeRule.waitForIdle()

        val width = widestListDp()
        assertTrue("discovery is wider than the reading width: $width", width > 640.5f)
        assertTrue("discovery stops at 960 dp: $width", width <= 960.5f)
    }

    @Test
    fun aPostBesideTheHomeListReadsAtTheReadingWidth() {
        val state = compose(activeLocal = true)
        composeRule.waitUntil(10_000) { SampleData.posts.any { count(it.title) >= 1 } }
        val first: Post = SampleData.posts.first { count(it.title) >= 1 }
        composeRule.runOnIdle { state.openFromMainFeed(first) }
        composeRule.waitForIdle()

        // The detail pane is right of the rail (80 dp) and the list pane
        // (360 dp); its own list is the widest list there (a post can hold
        // narrower lists of its own).
        val paneStartPx = with(composeRule.density) { 440.dp.toPx() }
        val detail = verticalLists()
            .filter { it.boundsInRoot.left >= paneStartPx }
            .maxBy { it.boundsInRoot.width }
        val width = widthDp(detail)
        assertTrue("the open post is at most 640 dp wide: $width", width <= 640.5f)
        assertTrue("the open post uses the reading width: $width", width >= 500f)
    }

    @Test
    fun theInboxOnItsOwnIsAReadingColumn() {
        compose(mode = TabletLayoutMode.SINGLE)
        click(hasContentDescription("Inbox") and hasClickAction())
        click(hasText("Messages") and hasClickAction())
        assertTrue("the messages are listed", count("Message number 1") >= 1 || verticalLists().isNotEmpty())

        val inboxList = verticalLists().firstOrNull { node ->
            composeRule.onAllNodes(hasAnyDescendant(hasText("Message number 1"))).fetchSemanticsNodes()
                .any { it.id == node.id }
        } ?: verticalLists().maxBy(::widthDp)
        val width = widthDp(inboxList)
        assertTrue("the single-pane Inbox is at most 640 dp wide: $width", width <= 640.5f)
        assertTrue("the single-pane Inbox uses the reading width: $width", width >= 500f)
    }
}
