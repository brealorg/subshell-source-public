package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.model.Community
import app.subshell.prototype.ui.app.AccountScopedEffects
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account-scoped effects in their own file, characterised: an
 * authenticated account refreshes its moderator directory and the inbox
 * tab resets; the one-shot authenticated landing waits for authoritative
 * subscriptions, then lands on Home (subscriptions) or Popular (none),
 * stays pending on a context route or a custom feed, and is dropped for
 * another account or a subscriptions failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AccountScopedEffectsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val kotlin = Community(
        id = "t5_kotlin",
        name = "kotlin",
        description = "",
        members = "1",
        accent = 0L,
        joined = true,
    )

    @Test
    fun anAuthenticatedAccountRefreshesItsModeratorDirectory() {
        val directoryLoads = mutableListOf<Pair<String, Boolean>>()
        host(
            state = authenticatedState(),
            moderatorTools = ModeratorToolsEntryPoints(
                onLoadRedditModeratorCommunities = { account, force -> directoryLoads += account to force },
            ),
        )

        // The inbox tab is reset by its rememberSaveable owner in SubshellApp
        // (LOWUI8_ACCOUNT_RESETS_ONLY_ON_A_CHANGE_V1, LowUi8aRecreationComposeTest).
        assertEquals(listOf(alice.stableId to false), directoryLoads)
    }

    @Test
    fun aLocalAccountDoesNotRefreshTheModeratorDirectory() {
        val directoryLoads = mutableListOf<Pair<String, Boolean>>()
        val local = localFixtureAccountSummaries(listOf("root"))
        host(
            state = SubshellState(initialAccountSessions = local, initialActiveAccountId = local.first().stableId),
            moderatorTools = ModeratorToolsEntryPoints(
                onLoadRedditModeratorCommunities = { account, force -> directoryLoads += account to force },
            ),
        )

        assertEquals(emptyList<Pair<String, Boolean>>(), directoryLoads)
    }

    @Test
    fun theLandingWaitsForAuthoritativeSubscriptionsThenLandsOnHome() {
        val state = authenticatedState()
        state.selectFeedSource(FeedSource.POPULAR)
        val pending = mutableStateOf<String?>(alice.stableId)
        val subscriptions = mutableStateOf(
            RedditSubscriptionsUiState(accountStableId = alice.stableId, loading = true),
        )
        host(state = state, pending = pending, subscriptions = subscriptions)
        assertEquals(alice.stableId, pending.value)

        subscriptions.value = RedditSubscriptionsUiState(
            accountStableId = alice.stableId,
            communities = listOf(kotlin),
            loaded = true,
            contentRevision = 1L,
        )
        composeRule.waitForIdle()

        assertNull(pending.value)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertEquals(FeedSort.BEST, state.feedSort)
    }

    @Test
    fun zeroAuthoritativeSubscriptionsLandOnPopular() {
        val state = authenticatedState()
        val pending = mutableStateOf<String?>(alice.stableId)
        host(
            state = state,
            pending = pending,
            subscriptions = mutableStateOf(
                RedditSubscriptionsUiState(accountStableId = alice.stableId, loaded = true, contentRevision = 1L),
            ),
        )

        assertNull(pending.value)
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(FeedSort.HOT, state.feedSort)
    }

    @Test
    fun theLandingStaysPendingOnAContextRouteOrACustomFeed() {
        val state = authenticatedState()
        state.openUser("bob")
        val pending = mutableStateOf<String?>(alice.stableId)
        val loaded = mutableStateOf(
            RedditSubscriptionsUiState(
                accountStableId = alice.stableId,
                communities = listOf(kotlin),
                loaded = true,
                contentRevision = 1L,
            ),
        )
        val customFeed = mutableStateOf<String?>(null)
        host(state = state, pending = pending, subscriptions = loaded, activeCustomFeedPath = customFeed)
        assertEquals(alice.stableId, pending.value)

        state.navigateBack()
        customFeed.value = "/user/alice/m/cats"
        composeRule.waitForIdle()
        assertEquals(alice.stableId, pending.value)

        customFeed.value = null
        composeRule.waitForIdle()
        assertNull(pending.value)
    }

    @Test
    fun aPendingLandingForAnotherAccountIsDropped() {
        val other = mutableStateOf<String?>("reddit:bob")
        host(state = authenticatedState(), pending = other)
        assertNull(other.value)
    }

    @Test
    fun aSubscriptionsFailureDropsThePendingLandingWithoutLanding() {
        val state = authenticatedState()
        state.selectFeedSource(FeedSource.POPULAR)
        val pending = mutableStateOf<String?>(alice.stableId)
        host(
            state = state,
            pending = pending,
            subscriptions = mutableStateOf(
                RedditSubscriptionsUiState(
                    accountStableId = alice.stableId,
                    failureKind = RedditSubscriptionsUiFailureKind.TRANSPORT,
                    contentRevision = 1L,
                ),
            ),
        )

        assertNull(pending.value)
        assertEquals(FeedSource.POPULAR, state.feedSource)
    }

    private fun authenticatedState() = SubshellState(
        initialAccountSessions = listOf(alice),
        initialActiveAccountId = alice.stableId,
    )

    private fun host(
        state: SubshellState,
        moderatorTools: ModeratorToolsEntryPoints = ModeratorToolsEntryPoints(),
        pending: MutableState<String?> = mutableStateOf(null),
        subscriptions: MutableState<RedditSubscriptionsUiState> =
            mutableStateOf(RedditSubscriptionsUiState()),
        activeCustomFeedPath: MutableState<String?> = mutableStateOf(null),
    ) {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            AccountScopedEffects(
                state = state,
                moderatorTools = moderatorTools,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions.value),
                activeCustomFeedPath = activeCustomFeedPath.value,
                pendingAuthenticatedLandingAccountIdState = pending,
            )
        }
        composeRule.waitForIdle()
    }
}
