package app.subshell.prototype.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.NsfwAccountPreferences
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.account.QuarantineOptInResult
import app.subshell.prototype.data.account.RedditCommunityAccessService
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.model.RandomNsfwCommunityPick
import android.os.Looper
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticOperation
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #216: characterises the #211 NSFW/quarantine block through the ViewModel
 * before it moves to a coordinator (random NSFW community candidates kept
 * per account, previous pick skipped, search fallback, account-bound
 * preference read and quarantine opt-in).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue216CommunityAccessCharacterisationTest {
    private val accountA = "reddit:a"
    private val accountB = "reddit:b"

    @Test
    fun aSecondPickForTheSameAccountAsksRedditNothing() = runBlocking {
        val discovery = FakeDiscovery(popular = listOf(item("one"), item("two"), item("safe", nsfw = false)))
        val viewModel = viewModel(discovery = discovery)

        val first = viewModel.communities().randomNsfwCommunity(accountA)
        val second = viewModel.communities().randomNsfwCommunity(accountA)

        assertEquals(1, discovery.calls.size)
        assertEquals(accountA to AuthenticatedRedditCommunityDiscoveryTarget.popular(true), discovery.calls.single())
        assertTrue(first in setOf(found("one"), found("two")))
        assertTrue(second in setOf(found("one"), found("two")))
    }

    @Test
    fun anotherAccountFetchesItsOwnCandidates() = runBlocking {
        val discovery = FakeDiscovery(popular = listOf(item("one"), item("two")))
        val viewModel = viewModel(discovery = discovery)

        viewModel.communities().randomNsfwCommunity(accountA)
        viewModel.communities().randomNsfwCommunity(accountB)
        viewModel.communities().randomNsfwCommunity(accountA)

        assertEquals(listOf(accountA, accountB, accountA), discovery.calls.map { it.first })
    }

    @Test
    fun thePreviousPickIsSkippedWhileThereIsAnotherChoice() = runBlocking {
        val viewModel = viewModel(discovery = FakeDiscovery(popular = listOf(item("one"), item("two"))))

        val picks = List(20) { viewModel.communities().randomNsfwCommunity(accountA) }

        picks.zipWithNext().forEach { (before, after) -> assertNotEquals(before, after) }
    }

    @Test
    fun aSingleCandidateIsPickedAgain() = runBlocking {
        val viewModel = viewModel(discovery = FakeDiscovery(popular = listOf(item("only"))))

        assertEquals(found("only"), viewModel.communities().randomNsfwCommunity(accountA))
        assertEquals(found("only"), viewModel.communities().randomNsfwCommunity(accountA))
    }

    @Test
    fun noNsfwCommunityInPopularFallsBackToAnNsfwSearch() = runBlocking {
        val discovery = FakeDiscovery(
            popular = listOf(item("safe", nsfw = false)),
            search = listOf(item("found"), item("plain", nsfw = false)),
        )
        val viewModel = viewModel(discovery = discovery)

        assertEquals(found("found"), viewModel.communities().randomNsfwCommunity(accountA))
        assertEquals(
            listOf(
                AuthenticatedRedditCommunityDiscoveryTarget.popular(true),
                AuthenticatedRedditCommunityDiscoveryTarget.search(
                    rawQuery = "nsfw",
                    sort = CommunityDiscoverySort.RELEVANCE,
                    includeOver18 = true,
                ),
            ),
            discovery.calls.map { it.second },
        )
    }

    @Test
    fun noCandidatesPicksNothingAndAsksAgainNextTime() = runBlocking {
        val discovery = FakeDiscovery(popular = emptyList(), search = emptyList())
        val viewModel = viewModel(discovery = discovery)

        assertEquals(RandomNsfwCommunityPick.NoneOffered(0, 0), viewModel.communities().randomNsfwCommunity(accountA))
        assertEquals(RandomNsfwCommunityPick.NoneOffered(0, 0), viewModel.communities().randomNsfwCommunity(accountA))
        assertEquals(4, discovery.calls.size)
    }

    // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_NOT_A_FAILURE_V1: "none" is
    // Reddit's answer only when both reads were answered.
    @Test
    fun aFailedReadIsAFailureNotNoneOffered() = runBlocking {
        val popularFails = FakeDiscovery(popular = emptyList(), search = emptyList(), failing = setOf("popular"))
        assertEquals(RandomNsfwCommunityPick.Failed, viewModel(discovery = popularFails).communities().randomNsfwCommunity(accountA))
        assertEquals(2, popularFails.calls.size)

        val searchFails = FakeDiscovery(popular = listOf(item("safe", nsfw = false)), failing = setOf("search"))
        assertEquals(RandomNsfwCommunityPick.Failed, viewModel(discovery = searchFails).communities().randomNsfwCommunity(accountA))

        val popularFailsSearchFinds = FakeDiscovery(popular = emptyList(), search = listOf(item("found")), failing = setOf("popular"))
        assertEquals(found("found"), viewModel(discovery = popularFailsSearchFinds).communities().randomNsfwCommunity(accountA))
    }

    // ISSUE263_NO_NSFW_COMMUNITY_OFFERED_IS_RECORDED_V1: the counts are what
    // each read returned, and the diagnostics log keeps them with the
    // account's own NSFW preferences.
    @Test
    fun noneOfferedCountsWhatEachReadReturnedAndIsRecorded() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        DiagnosticEventStore(application).clear()
        val discovery = FakeDiscovery(
            popular = listOf(item("a", nsfw = false), item("b", nsfw = false), item("c", nsfw = false)),
            search = listOf(item("d", nsfw = false), item("e", nsfw = false)),
        )
        val viewModel = viewModel(discovery = discovery, service = FakeAccessService())

        assertEquals(RandomNsfwCommunityPick.NoneOffered(3, 2), viewModel.communities().randomNsfwCommunity(accountA))

        val deadline = System.currentTimeMillis() + 5_000L
        var recorded = emptyList<app.subshell.prototype.data.diagnostics.DiagnosticEvent>()
        while (recorded.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            recorded = DiagnosticEventStore(application).snapshot().events
                .filter { it.operation == DiagnosticOperation.NSFW_COMMUNITIES_NONE_OFFERED }
            if (recorded.isEmpty()) Thread.sleep(20)
        }
        val event = recorded.single()
        assertEquals(3, event.popularCommunities)
        assertEquals(2, event.searchedCommunities)
        assertEquals(true, event.accountOver18)
        assertNull(event.accountSearchOver18)
    }

    @Test
    fun withoutADiscoveryLoaderNothingIsPicked() = runBlocking {
        assertEquals(RandomNsfwCommunityPick.Failed, viewModel(discovery = null).communities().randomNsfwCommunity(accountA))
    }

    @Test
    fun thePreferenceReadAndTheOptInAreBoundToTheActiveAccount() = runBlocking {
        val service = FakeAccessService()
        val viewModel = viewModel(discovery = null, service = service)

        assertEquals(service.known, viewModel.communities().readNsfwAccountPreferences(accountA))
        assertEquals(QuarantineOptInResult.Sent, viewModel.communities().optInToQuarantinedCommunity(accountA, "kept"))
        viewModel.communities().readNsfwAccountPreferences(accountB)
        viewModel.communities().optInToQuarantinedCommunity(accountB, "kept")

        assertEquals(listOf(accountA to true, accountB to false), service.reads)
        assertEquals(listOf(Triple(accountA, "kept", true), Triple(accountB, "kept", false)), service.optIns)
    }

    @Test
    fun withoutTheServiceTheReadFailsAndTheOptInIsRefused() = runBlocking {
        val viewModel = viewModel(discovery = null, service = null)

        assertEquals(NsfwPreferencesResult.Failed, viewModel.communities().readNsfwAccountPreferences(accountA))
        assertEquals(QuarantineOptInResult.Refused(null), viewModel.communities().optInToQuarantinedCommunity(accountA, "kept"))
    }

    @Test
    fun theCandidatesAreKeptForTenMinutes() = runBlocking {
        val discovery = FakeDiscovery(popular = listOf(item("one"), item("two")))
        var clock = 1_000_000L
        val coordinator = CommunityAccessCoordinator(
            accessService = null,
            discoveryLoader = discovery,
            activeAccountId = { accountA },
            now = { clock },
        )

        coordinator.randomNsfwCommunity(accountA)
        clock += RANDOM_NSFW_CANDIDATES_TTL_MS - 1
        coordinator.randomNsfwCommunity(accountA)
        assertEquals(1, discovery.calls.size)

        clock += 1
        coordinator.randomNsfwCommunity(accountA)
        assertEquals(2, discovery.calls.size)
    }

    private fun viewModel(
        discovery: AuthenticatedRedditCommunityDiscoveryLoader?,
        service: RedditCommunityAccessService? = null,
    ) = AuthConnectViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        unavailableAuthApplicationRuntime().copy(
            redditCommunityDiscoveryLoader = discovery,
            communityAccessService = service,
            accountSessionDirectory = ActiveDirectory(accountA),
        ),
    )

    private fun found(name: String) = RandomNsfwCommunityPick.Found(name)

    private fun item(name: String, nsfw: Boolean = true) = CommunityDiscoveryItem(
        community = Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L),
        nsfw = nsfw,
    )

    private class FakeDiscovery(
        private val popular: List<CommunityDiscoveryItem>,
        private val search: List<CommunityDiscoveryItem> = emptyList(),
        private val failing: Set<String> = emptySet(),
    ) : AuthenticatedRedditCommunityDiscoveryLoader {
        val calls = mutableListOf<Pair<String, AuthenticatedRedditCommunityDiscoveryTarget>>()

        override suspend fun cached(
            accountStableId: String,
            target: AuthenticatedRedditCommunityDiscoveryTarget,
        ): AuthenticatedRedditCommunityDiscoveryResult = error("not used")

        override suspend fun synchronize(
            accountStableId: String,
            target: AuthenticatedRedditCommunityDiscoveryTarget,
        ): AuthenticatedRedditCommunityDiscoveryResult {
            calls += accountStableId to target
            val kind = if (target.cacheToken.startsWith("popular")) "popular" else "search"
            if (kind in failing) {
                return AuthenticatedRedditCommunityDiscoveryResult.Failed(
                    kind = AuthenticatedRedditCommunityDiscoveryFailureKind.TRANSPORT,
                    retryable = true,
                )
            }
            val items = if (kind == "popular") popular else search
            return AuthenticatedRedditCommunityDiscoveryResult.Loaded(items, fromCache = false)
        }

        override suspend fun searchPeople(
            accountStableId: String,
            target: AuthenticatedRedditPeopleSearchTarget,
        ): AuthenticatedRedditPeopleSearchResult = error("not used")
    }

    private class FakeAccessService : RedditCommunityAccessService {
        val known = NsfwPreferencesResult.Known(NsfwAccountPreferences(over18 = true, searchIncludeOver18 = null))
        val reads = mutableListOf<Pair<String, Boolean>>()
        val optIns = mutableListOf<Triple<String, String, Boolean>>()

        override suspend fun read(account: String, isCurrent: () -> Boolean): NsfwPreferencesResult {
            reads += account to isCurrent()
            return known
        }

        override suspend fun optIn(account: String, communityName: String, isCurrent: () -> Boolean): QuarantineOptInResult {
            optIns += Triple(account, communityName, isCurrent())
            return QuarantineOptInResult.Sent
        }
    }

    private class ActiveDirectory(private val active: String) : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(
            accounts = listOf("reddit:a", "reddit:b").map { id ->
                AccountSessionSummary(
                    stableId = id,
                    username = id.removePrefix("reddit:"),
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                )
            },
            activeAccountId = active,
        )

        override fun selectAccount(stableId: String) = snapshot()
    }
}
