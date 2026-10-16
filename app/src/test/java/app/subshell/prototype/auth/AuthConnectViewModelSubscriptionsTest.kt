package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.CommunityMembershipFailureKind
import app.subshell.prototype.data.account.CommunityMembershipPhase
import app.subshell.prototype.data.account.NOT_CONFIRMED_MEMBERSHIP_MESSAGE
import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.blocksActionFor
import app.subshell.prototype.data.account.failureMessageFor
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipResult
import app.subshell.prototype.model.Community
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Characterisation of the subscription loads on AuthConnectViewModel and the
 * community-membership reconciliation that drives them, written before they
 * move out of the ViewModel. Until now these paths had only source-text
 * pins. Current behaviour, not a specification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthConnectViewModelSubscriptionsTest {
    private val account = "reddit:test"

    @Test
    fun withoutALoaderALiveLoadReportsClientConfigurationAndACachedLoadDoesNothing() {
        val viewModel = viewModel(loader = null)

        viewModel.loadCachedRedditSubscriptions(account)
        idle()
        assertEquals(null, viewModel.redditSubscriptionsUiState.value.accountStableId)

        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        val state = viewModel.redditSubscriptionsUiState.value
        assertEquals(account, state.accountStableId)
        assertEquals(RedditSubscriptionsUiFailureKind.CLIENT_CONFIGURATION, state.failureKind)
    }

    @Test
    fun aCachedLoadPublishesTheCacheOnceAndMapsNotCachedAndStorageErrors() {
        val loader = FakeLoader(cached = { loaded("android") })
        val viewModel = viewModel(loader)

        viewModel.loadCachedRedditSubscriptions(account)
        idle()
        var state = viewModel.redditSubscriptionsUiState.value
        assertEquals(listOf("android"), state.communities.map { it.name })
        assertTrue(state.fromCache)
        assertTrue(state.loaded)
        assertEquals(1L, state.contentRevision)

        // Already has content for the account: not read again.
        viewModel.loadCachedRedditSubscriptions(account)
        idle()
        assertEquals(1, loader.cachedCalls)

        loader.cached = { AuthenticatedRedditSubscriptionsResult.NotCached }
        viewModel.loadCachedRedditSubscriptions("reddit:other")
        idle()
        state = viewModel.redditSubscriptionsUiState.value
        assertEquals("reddit:other", state.accountStableId)
        assertFalse(state.loaded)
        assertTrue(state.communities.isEmpty())

        loader.cached = { error("disk") }
        viewModel.loadCachedRedditSubscriptions("reddit:third")
        idle()
        state = viewModel.redditSubscriptionsUiState.value
        assertEquals(RedditSubscriptionsUiFailureKind.STORAGE, state.failureKind)
        assertTrue(state.failureRetryable)
    }

    @Test
    fun aLiveLoadShowsTheCacheFirstThenTheSynchronisedListAndRefusesUnforcedReloads() {
        val loader = FakeLoader(cached = { loaded("android") })
        val viewModel = viewModel(loader)

        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        var state = viewModel.redditSubscriptionsUiState.value
        assertTrue(state.loading)
        assertTrue(state.fromCache)
        assertEquals(listOf("android"), state.communities.map { it.name })
        assertEquals(1L, state.contentRevision)

        // A second ordinary load while one is running is ignored; a forced one
        // replaces it (LOW7, OCB-442).
        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        assertEquals(1, loader.synchronizeCalls)

        loader.pendingSync!!.complete(loaded("android", "kotlin"))
        idle()
        state = viewModel.redditSubscriptionsUiState.value
        assertFalse(state.loading)
        assertFalse(state.fromCache)
        assertTrue(state.loaded)
        assertEquals(listOf("android", "kotlin"), state.communities.map { it.name })
        assertEquals(2L, state.contentRevision)

        // Loaded live and not forced: refused.
        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        assertEquals(1, loader.synchronizeCalls)

        // Forced: skips the cache and synchronises again.
        viewModel.loadRedditSubscriptions(account, forceRefresh = true)
        idle()
        assertEquals(1, loader.cachedCalls)
        assertEquals(2, loader.synchronizeCalls)
        assertTrue(viewModel.redditSubscriptionsUiState.value.loading)
        assertEquals(listOf("android", "kotlin"), viewModel.redditSubscriptionsUiState.value.communities.map { it.name })
    }

    @Test
    fun aFailedSynchronisationKeepsTheListAndAllowsAnUnforcedRetry() {
        val loader = FakeLoader(cached = { loaded("android") })
        val viewModel = viewModel(loader)

        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        loader.pendingSync!!.complete(
            AuthenticatedRedditSubscriptionsResult.Failed(
                kind = AuthenticatedRedditSubscriptionsFailureKind.HTTP,
                retryable = true,
            ),
        )
        idle()
        var state = viewModel.redditSubscriptionsUiState.value
        assertFalse(state.loading)
        assertEquals(RedditSubscriptionsUiFailureKind.HTTP, state.failureKind)
        assertEquals(listOf("android"), state.communities.map { it.name })
        assertTrue(state.fromCache)

        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        assertEquals(2, loader.synchronizeCalls)
        loader.pendingSync!!.complete(AuthenticatedRedditSubscriptionsResult.NotCached)
        idle()
        state = viewModel.redditSubscriptionsUiState.value
        assertEquals(RedditSubscriptionsUiFailureKind.STORAGE, state.failureKind)
    }

    @Test
    fun anotherAccountsLoadReplacesTheStateAndTheEarlierResultNeverLands() {
        val loader = FakeLoader(cached = { AuthenticatedRedditSubscriptionsResult.NotCached })
        val viewModel = viewModel(loader)

        viewModel.loadRedditSubscriptions(account, forceRefresh = false)
        idle()
        val first = loader.pendingSync!!
        viewModel.loadRedditSubscriptions("reddit:other", forceRefresh = false)
        idle()
        assertEquals("reddit:other", viewModel.redditSubscriptionsUiState.value.accountStableId)

        first.complete(loaded("android"))
        idle()
        val state = viewModel.redditSubscriptionsUiState.value
        assertEquals("reddit:other", state.accountStableId)
        assertTrue(state.communities.isEmpty())
        assertTrue(state.loading)
    }

    @Test
    fun aConfirmedJoinIsReconciledByOneForcedRefresh() {
        val loader = FakeLoader(cached = { AuthenticatedRedditSubscriptionsResult.NotCached })
        val viewModel = viewModel(loader, activeAccount = account)

        viewModel.updateCommunityMembership(account, "kotlin", CommunityMembershipAction.JOIN)
        idle()
        assertEquals(CommunityMembershipPhase.SUCCEEDED, viewModel.communityMembershipUiState.value.phase)
        // The first reconciliation step runs at once, as a forced load.
        assertEquals(0, loader.cachedCalls)
        assertEquals(1, loader.synchronizeCalls)

        loader.pendingSync!!.complete(loaded("android", "kotlin"))
        idle()
        assertEquals(CommunityMembershipPhase.IDLE, viewModel.communityMembershipUiState.value.phase)

        idleFor(30_000)
        assertEquals(1, loader.synchronizeCalls)
    }

    @Test
    fun anUnconfirmedJoinStaysPendingThroughTheWholeBackoffThenStopsBlocking() {
        val loader = FakeLoader(cached = { AuthenticatedRedditSubscriptionsResult.NotCached })
        val viewModel = viewModel(loader, activeAccount = account)

        viewModel.updateCommunityMembership(account, "kotlin", CommunityMembershipAction.JOIN)
        idle()
        loader.pendingSync!!.complete(loaded("android"))
        idle()
        var membership = viewModel.communityMembershipUiState.value
        assertEquals(CommunityMembershipPhase.FAILED, membership.phase)
        assertEquals(CommunityMembershipFailureKind.OUTCOME_UNKNOWN, membership.failureKind)

        // Issue #43: each later step (1.5 s, 5 s, 15 s) runs while Reddit's
        // list still lacks the community, and the unknown outcome stays.
        for ((expectedCalls, delayMs) in listOf(2 to 1_500L, 3 to 5_000L)) {
            idleFor(delayMs + 100)
            assertEquals(expectedCalls, loader.synchronizeCalls)
            loader.pendingSync!!.complete(loaded("android"))
            idle()
            membership = viewModel.communityMembershipUiState.value
            assertEquals(CommunityMembershipFailureKind.OUTCOME_UNKNOWN, membership.failureKind)
            assertTrue(membership.blocksActionFor(account, "kotlin"))
        }

        idleFor(15_100)
        assertEquals(4, loader.synchronizeCalls)
        loader.pendingSync!!.complete(loaded("android"))
        idle()

        // After the last step: a final, non-blocking state, nothing re-sent.
        membership = viewModel.communityMembershipUiState.value
        assertEquals(CommunityMembershipPhase.FAILED, membership.phase)
        assertEquals(CommunityMembershipFailureKind.NOT_CONFIRMED, membership.failureKind)
        assertFalse(membership.blocksActionFor(account, "kotlin"))
        assertEquals(
            NOT_CONFIRMED_MEMBERSHIP_MESSAGE,
            membership.failureMessageFor(account, "kotlin"),
        )

        idleFor(60_000)
        assertEquals(4, loader.synchronizeCalls)
    }

    @Test
    fun aJoinConfirmedOnALaterStepIsClearedAndTheChecksStop() {
        val loader = FakeLoader(cached = { AuthenticatedRedditSubscriptionsResult.NotCached })
        val viewModel = viewModel(loader, activeAccount = account)

        viewModel.updateCommunityMembership(account, "kotlin", CommunityMembershipAction.JOIN)
        idle()
        loader.pendingSync!!.complete(loaded("android"))
        idle()
        idleFor(1_600)
        loader.pendingSync!!.complete(loaded("android"))
        idle()
        assertEquals(
            CommunityMembershipFailureKind.OUTCOME_UNKNOWN,
            viewModel.communityMembershipUiState.value.failureKind,
        )

        idleFor(5_100)
        assertEquals(3, loader.synchronizeCalls)
        loader.pendingSync!!.complete(loaded("android", "kotlin"))
        idle()
        assertEquals(CommunityMembershipPhase.IDLE, viewModel.communityMembershipUiState.value.phase)

        idleFor(60_000)
        assertEquals(3, loader.synchronizeCalls)
    }

    private fun viewModel(
        loader: AuthenticatedRedditSubscriptionsLoader?,
        activeAccount: String? = null,
    ): AuthConnectViewModel =
        AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                redditSubscriptionsLoader = loader,
                accountSessionDirectory = activeAccount?.let(::FixedDirectory),
                communityMembershipWriter = { request ->
                    AuthenticatedCommunityMembershipResult.Updated(request)
                },
            ),
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun loaded(vararg names: String) = AuthenticatedRedditSubscriptionsResult.Loaded(
        communities = names.map(::community),
        fromCache = false,
    )

    private fun community(name: String) = Community(
        id = "t5_$name",
        name = name,
        description = "",
        members = "1",
        accent = 0L,
        joined = true,
    )

    /** Cache reads answer at once; each synchronisation waits for its gate. */
    private class FakeLoader(
        var cached: () -> AuthenticatedRedditSubscriptionsResult,
    ) : AuthenticatedRedditSubscriptionsLoader {
        var cachedCalls = 0
        var synchronizeCalls = 0
        var pendingSync: CompletableDeferred<AuthenticatedRedditSubscriptionsResult>? = null

        override suspend fun cached(accountStableId: String): AuthenticatedRedditSubscriptionsResult {
            cachedCalls++
            return cached()
        }

        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditSubscriptionsResult {
            synchronizeCalls++
            val gate = CompletableDeferred<AuthenticatedRedditSubscriptionsResult>()
            pendingSync = gate
            return gate.await()
        }
    }

    private class FixedDirectory(private val active: String) : AccountSessionDirectory {
        private val snapshot = AccountSessionDirectorySnapshot(
            accounts = listOf(
                AccountSessionSummary(
                    stableId = active,
                    username = "tester",
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                ),
            ),
            activeAccountId = active,
        )

        override fun snapshot() = snapshot
        override fun selectAccount(stableId: String) = snapshot
    }
}
