package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * #223 (M3 audit group 6, chrome and navigation): M3223_ONE_ROUTE_TOP_BAR_V1,
 * M3223_ONE_SCREEN_MARGIN_V1, M3223_SHORT_NAVIGATION_BAR_V1,
 * M3223_DRAWER_ROWS_ARE_MATERIAL_DRAWER_ITEMS_V1, and the lowercase brand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue223ChromeComposeTest {
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

    private fun compose(
        postsPerPage: Int? = 30,
        configure: (SubshellState) -> Unit = {},
    ): SubshellState {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = ManyPostsRepository(postsPerPage),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        configure(state)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = VideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }

    private fun backButtonOnScreen(): Boolean {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return composeRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().any { node ->
            node.boundsInRoot.bottom > root.top + 1f && node.boundsInRoot.top >= root.top - 1f
        }
    }

    private fun scrollDownALot() {
        repeat(3) {
            composeRule.onRoot().performTouchInput {
                swipeUp(startY = bottom * 0.85f, endY = top + height * 0.15f)
            }
            composeRule.waitForIdle()
        }
    }

    private fun scrollUpALittle() {
        composeRule.onRoot().performTouchInput {
            swipeDown(startY = top + height * 0.4f, endY = top + height * 0.55f)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aCommunitysBackComesBackOnAShortScrollUp() {
        compose { it.openSubreddit(SampleData.communities.first().name) }
        assertTrue("Back at the start", backButtonOnScreen())

        scrollDownALot()
        scrollUpALittle()

        assertTrue("Back after a short scroll up", backButtonOnScreen())
    }

    @Test
    fun aListThatFitsTheScreenKeepsItsTopBar() {
        compose(postsPerPage = 0) { it.openSubreddit(SampleData.communities.first().name) }

        scrollDownALot()
        scrollUpALittle()

        assertTrue("a short list cannot hide Back", backButtonOnScreen())
    }

    @Test
    fun theTopBarHidesWhileScrollingDownByDefault() {
        compose { it.openSubreddit(SampleData.communities.first().name) }

        scrollDownALot()

        assertTrue("Back hidden while reading down", !backButtonOnScreen())
    }

    @Test
    fun alwaysShowKeepsTheRouteTopBarWhileScrolling() {
        compose {
            it.updateTopBarBehavior(ChromeBehavior.ALWAYS_SHOW)
            it.openSubreddit(SampleData.communities.first().name)
        }

        scrollDownALot()

        assertTrue("Back stays with Always show", backButtonOnScreen())
    }

    @Test
    fun anOpenPostKeepsItsBackAfterAShortScrollUp() {
        val longPost = SampleData.posts.first { it.kind == PostKind.TEXT }
            .copy(body = (1..120).joinToString("\n\n") { "Paragraph $it of a long post." })
        compose { it.openFromMainFeed(longPost) }

        scrollDownALot()
        scrollUpALittle()

        assertTrue(backButtonOnScreen())
    }

    @Test
    fun aPostCardStartsAtTheSameMarginInSearchResultsAndInACommunity() {
        // Search results had 16 dp and communities 10 dp before #223.
        val state = compose()
        composeRule.runOnIdle {
            state.openSearch(refocus = false)
            state.updateSearchQuery("android")
            state.submitSearch()
        }
        composeRule.waitForIdle()
        val post = SampleData.posts.first { candidate ->
            composeRule.onAllNodesWithText(candidate.title).fetchSemanticsNodes().isNotEmpty()
        }
        val searchLeft = composeRule.onAllNodesWithText(post.title).fetchSemanticsNodes().first().boundsInRoot.left

        composeRule.runOnIdle { state.openSubreddit(SampleData.communities.first().name) }
        composeRule.waitForIdle()
        val communityLeft = composeRule.onAllNodesWithText(post.title).fetchSemanticsNodes().first().boundsInRoot.left

        assertEquals(searchLeft, communityLeft, 1f)
    }

    @Test
    fun partialLabelsStillNameEveryDestination() {
        compose { it.updateNavigationLabelMode(NavigationLabelMode.PARTIAL) }

        // Home is selected and labelled; the others are named by their icon only.
        val width = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        assertEquals(
            0,
            composeRule.onAllNodesWithText("You").fetchSemanticsNodes().count { it.boundsInRoot.left < width },
        )
        for (name in listOf("Communities", "Inbox", "You")) {
            assertTrue(
                "$name has a name",
                composeRule.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun drawerDestinationsAreMaterialDrawerItems() {
        val state = compose()
        composeRule.runOnIdle { state.requestDrawer() }
        composeRule.waitForIdle()

        val row = composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes()
            .first { node -> node.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
        with(composeRule.density) {
            assertTrue("56 dp rows: ${row.boundsInRoot.height}", row.boundsInRoot.height >= 56.dp.toPx() - 1f)
        }
    }

    @Test
    fun theAppNameIsLowercaseInEveryText() {
        val literal = Regex(""""(?:[^"\\\n]|\\.)*"""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SampleData.kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filterNot { line -> "Log." in line || "TAG" in line }
                    .flatMap { line -> literal.findAll(line).map { it.value } }
                    .filter { text -> Regex("""\bSubshell\b""").containsMatchIn(text) }
                    .filterNot { text -> "session directory must expose" in text }
                    .map { text -> "${file.name}: $text" }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}

/** Pages SampleData's posts [postsPerPage] times over (distinct ids), so lists scroll. */
private class ManyPostsRepository(
    private val postsPerPage: Int?,
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        val result = delegate.posts(request)
        if (postsPerPage == null || result !is RepositoryResult.Content) return result
        val base = result.value.ifEmpty { SampleData.posts }
        val posts = (0 until postsPerPage).map { index ->
            val post = base[index % base.size]
            post.copy(id = "${post.id}_$index", community = SampleData.communities.first().name)
        }
        return result.copy(value = posts, nextCursor = null)
    }
}
