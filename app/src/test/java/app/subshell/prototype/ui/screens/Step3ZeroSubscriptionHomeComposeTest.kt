package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryResult
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.FeedSource
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (auth07b2): a signed-in account that Reddit says has joined no
 * community sees an empty Home with two ways out (Browse Popular, Find
 * communities) instead of a feed request; while the first landing is still
 * deciding, Home says it is preparing the feed. A joined-communities list
 * read from the cache is not proof of "none joined", so it never shows the
 * empty Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3ZeroSubscriptionHomeComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val repository = NoPostsRepository()

    private fun signedIn(): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        return SubshellState(
            repository = repository,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun showHome(
        state: SubshellState,
        landingPending: Boolean = false,
        withoutSubscriptions: Boolean = false,
        onBrowsePopular: () -> Unit = {},
        onFindCommunities: () -> Unit = {},
    ) {
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                    HomeScreen(
                        state = state,
                        authenticatedLandingPending = landingPending,
                        authenticatedHomeWithoutSubscriptions = withoutSubscriptions,
                        onBrowsePopular = onBrowsePopular,
                        onFindCommunities = onFindCommunities,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun count(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun click(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun anAccountWithoutCommunitiesSeesAnEmptyHomeWithTwoWaysOut() {
        var popular = 0
        var find = 0
        showHome(
            signedIn(),
            withoutSubscriptions = true,
            onBrowsePopular = { popular += 1 },
            onFindCommunities = { find += 1 },
        )

        assertEquals(1, count("Your Home feed is empty"))
        // No Home feed is asked for while there is nothing to build it from.
        assertEquals(0, repository.postReads)

        click(hasText("Browse Popular") and hasClickAction())
        assertEquals(1, popular)
        click(hasText("Find communities") and hasClickAction())
        assertEquals(1, find)
    }

    @Test
    fun aPendingLandingSaysTheFeedIsBeingPrepared() {
        showHome(signedIn(), landingPending = true)

        assertEquals(1, count("Preparing your feed"))
        assertEquals(0, count("Your Home feed is empty"))
        assertEquals(0, count("Browse Popular"))
        assertEquals(0, repository.postReads)
    }

    @Test
    fun theAppShowsTheEmptyHomeOnlyForAnAuthoritativeEmptyList() {
        val state = signedIn()
        var subscriptions by mutableStateOf(
            RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true, fromCache = true),
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions),
            )
        }
        composeRule.waitForIdle()
        // A cached empty list may be stale: Home stays a normal feed.
        assertEquals(0, count("Your Home feed is empty"))

        composeRule.runOnIdle {
            subscriptions = RedditSubscriptionsUiState(
                accountStableId = account.stableId,
                loaded = true,
                contentRevision = 1L,
            )
        }
        composeRule.waitForIdle()
        // The first landing for an account without communities is Popular.
        assertEquals(FeedSource.POPULAR, state.feedSource)

        // Choosing Home afterwards shows the empty Home with its ways out.
        composeRule.runOnIdle { state.selectFeedSource(FeedSource.HOME) }
        composeRule.waitForIdle()
        assertEquals(1, count("Your Home feed is empty"))

        click(hasText("Browse Popular") and hasClickAction())
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(0, count("Your Home feed is empty"))
    }

    @Test
    fun findCommunitiesInTheAppOpensDiscover() {
        val state = signedIn()
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(
                    redditSubscriptionsState = RedditSubscriptionsUiState(
                        accountStableId = account.stableId,
                        loaded = true,
                        contentRevision = 1L,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.selectFeedSource(FeedSource.HOME) }
        composeRule.waitForIdle()

        click(hasText("Find communities") and hasClickAction())

        val route = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.COMMUNITY_EXPLORE, route.secondaryDestination)
    }
}

/** A repository whose feeds have no posts; counts the post pages asked for. */
private class NoPostsRepository(
    private val delegate: FakeSubshellRepository = FakeSubshellRepository(),
) : SubshellRepository by delegate {
    @Volatile var postReads = 0

    override fun posts(request: RepositoryPageRequest): RepositoryResult<List<Post>> {
        postReads += 1
        return RepositoryResult.Empty()
    }
}
