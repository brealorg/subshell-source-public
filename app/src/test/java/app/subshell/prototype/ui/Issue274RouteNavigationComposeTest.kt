package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.IdentityMetadataRepository
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.UserFixture
import app.subshell.prototype.ui.app.routeKeepsNavigationBar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #274 (owner): deep in a community, a profile or search results the phone
 * keeps its bottom navigation (ISSUE274_LIST_ROUTES_KEEP_THE_NAVIGATION_BAR_V1);
 * a post, a conversation and settings do not. Search starts with the bars
 * shown. The community's top bar shows its icon when it has one
 * (ISSUE274_COMMUNITY_BAR_SHOWS_ITS_ICON_V1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue274RouteNavigationComposeTest {
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

    @Test
    fun listRoutesKeepTheBarOtherRoutesDoNot() {
        val home = SubshellRoute.Main(MainDestination.HOME, FeedSource.HOME, FeedSort.BEST)
        assertFalse(routeKeepsNavigationBar(home))
        assertTrue(routeKeepsNavigationBar(SubshellRoute.Community(MainDestination.HOME, "motogp", CommunitySort.HOT)))
        assertTrue(routeKeepsNavigationBar(SubshellRoute.User(MainDestination.HOME, "someone")))
        assertFalse(routeKeepsNavigationBar(SubshellRoute.Conversation(MainDestination.INBOX, "t1_x")))
        assertFalse(
            routeKeepsNavigationBar(
                SubshellRoute.Secondary(MainDestination.YOU, SecondaryDestination.SETTINGS),
            ),
        )
    }

    @Test
    fun aCommunityShowsTheBottomNavigationAndHomeLeavesIt() {
        val state = app()
        composeRule.onAllNodesWithTag("app-bottom-bar", useUnmergedTree = true).assertCountEquals(1)

        // From Communities, so Home is an unlabelled item named by its description.
        composeRule.runOnIdle {
            state.navigate(MainDestination.SUBS)
            state.openSubreddit("motogp")
        }
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        composeRule.onAllNodesWithTag("app-bottom-bar", useUnmergedTree = true).assertCountEquals(1)

        composeRule.onNodeWithContentDescription("Home", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.HOME, (state.currentNavigationRoute as SubshellRoute.Main).destination)
    }

    @Test
    fun settingsKeepsTheWholeScreen() {
        val state = app()
        composeRule.runOnIdle { state.openSecondary(SecondaryDestination.SETTINGS) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("app-bottom-bar", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun searchFromAScrolledCommunityStartsWithTheBarsShown() {
        val state = app()
        composeRule.runOnIdle { state.openSubreddit("motogp") }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        }
        composeRule.waitForIdle()
        assertFalse(state.chromeVisible)

        composeRule.runOnIdle { state.openSearch() }
        composeRule.waitForIdle()

        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
        assertTrue(state.chromeVisible)
        composeRule.onAllNodesWithTag("app-bottom-bar", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theCommunityBarShowsTheIconOnlyWhenThereIsOne() {
        val state = app(iconFor = setOf("motogp"))
        composeRule.runOnIdle { state.openSubreddit("motogp") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("community-bar-icon", useUnmergedTree = true).assertExists()

        composeRule.runOnIdle { state.openSubreddit("formula1") }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("community-bar-icon", useUnmergedTree = true).assertCountEquals(0)
    }

    // ISSUE274_COMMUNITIES_TABS_FOLLOW_THE_TOP_BAR_V1
    @Test
    fun communitiesTabsFollowTheTopBarUp() {
        val state = app()
        composeRule.runOnIdle { state.navigate(MainDestination.SUBS) }
        composeRule.waitForIdle()
        fun tabTop() = composeRule.onNode(hasText("Favorites") and hasClickAction(), useUnmergedTree = false)
            .getUnclippedBoundsInRoot().top
        val shown = tabTop()

        composeRule.runOnIdle {
            state.onMainContentScroll(deltaPx = 500f, atTop = false, hideThresholdPx = 1f, showThresholdPx = 1f)
        }
        composeRule.waitForIdle()
        val hidden = tabTop()
        assertTrue("tabs moved up from $shown to $hidden", shown - hidden >= 56.dp)

        composeRule.runOnIdle { state.showChrome() }
        composeRule.waitForIdle()
        assertEquals(shown, tabTop())
    }

    private fun app(iconFor: Set<String> = emptySet()): SubshellState {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            identityMetadataRepository = IconIdentities(iconFor),
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialReduceMotion = true,
        )
        runBlocking { iconFor.forEach { name -> state.ensureCommunityIdentity(name) } }
        SettingsBackupRepository(context, appVersion = "test").dismissFolderOnboarding()
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
        return state
    }
}

private class IconIdentities(private val withIcon: Set<String>) : IdentityMetadataRepository {
    override fun cachedCommunityIdentity(name: String): Community? = null

    override suspend fun refreshCommunityIdentity(name: String, fallback: Community): RepositoryResult<Community> =
        RepositoryResult.Content(
            if (name.lowercase() in withIcon) {
                fallback.copy(iconImageUri = "https://styles.redditmedia.com/$name-icon.png")
            } else {
                fallback
            },
        )

    override suspend fun refreshCommunityIdentities(fallbacks: List<Community>): RepositoryResult<List<Community>> =
        RepositoryResult.Content(fallbacks)

    override fun cachedUserIdentity(username: String): UserFixture? = null

    override suspend fun refreshUserIdentity(username: String, fallback: UserFixture): RepositoryResult<UserFixture> =
        RepositoryResult.Content(fallback)
}
