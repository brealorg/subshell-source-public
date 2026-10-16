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
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import kotlinx.coroutines.runBlocking
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

        val first = viewModel.randomNsfwCommunity(accountA)
        val second = viewModel.randomNsfwCommunity(accountA)

        assertEquals(1, discovery.calls.size)
        assertEquals(accountA to AuthenticatedRedditCommunityDiscoveryTarget.popular(true), discovery.calls.single())
        assertTrue(first in setOf("one", "two"))
        assertTrue(second in setOf("one", "two"))
    }

    @Test
    fun anotherAccountFetchesItsOwnCandidates() = runBlocking {
        val discovery = FakeDiscovery(popular = listOf(item("one"), item("two")))
        val viewModel = viewModel(discovery = discovery)

        viewModel.randomNsfwCommunity(accountA)
        viewModel.randomNsfwCommunity(accountB)
        viewModel.randomNsfwCommunity(accountA)

        assertEquals(listOf(accountA, accountB, accountA), discovery.calls.map { it.first })
    }

    @Test
    fun thePreviousPickIsSkippedWhileThereIsAnotherChoice() = runBlocking {
        val viewModel = viewModel(discovery = FakeDiscovery(popular = listOf(item("one"), item("two"))))

        val picks = List(20) { viewModel.randomNsfwCommunity(accountA) }

        picks.zipWithNext().forEach { (before, after) -> assertNotEquals(before, after) }
    }

    @Test
    fun aSingleCandidateIsPickedAgain() = runBlocking {
        val viewModel = viewModel(discovery = FakeDiscovery(popular = listOf(item("only"))))

        assertEquals("only", viewModel.randomNsfwCommunity(accountA))
        assertEquals("only", viewModel.randomNsfwCommunity(accountA))
    }

    @Test
    fun noNsfwCommunityInPopularFallsBackToAnNsfwSearch() = runBlocking {
        val discovery = FakeDiscovery(
            popular = listOf(item("safe", nsfw = false)),
            search = listOf(item("found"), item("plain", nsfw = false)),
        )
        val viewModel = viewModel(discovery = discovery)

        assertEquals("found", viewModel.randomNsfwCommunity(accountA))
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

        assertNull(viewModel.randomNsfwCommunity(accountA))
        assertNull(viewModel.randomNsfwCommunity(accountA))
        assertEquals(4, discovery.calls.size)
    }

    @Test
    fun withoutADiscoveryLoaderNothingIsPicked() = runBlocking {
        assertNull(viewModel(discovery = null).randomNsfwCommunity(accountA))
    }

    @Test
    fun thePreferenceReadAndTheOptInAreBoundToTheActiveAccount() = runBlocking {
        val service = FakeAccessService()
        val viewModel = viewModel(discovery = null, service = service)

        assertEquals(service.known, viewModel.readNsfwAccountPreferences(accountA))
        assertEquals(QuarantineOptInResult.Sent, viewModel.optInToQuarantinedCommunity(accountA, "kept"))
        viewModel.readNsfwAccountPreferences(accountB)
        viewModel.optInToQuarantinedCommunity(accountB, "kept")

        assertEquals(listOf(accountA to true, accountB to false), service.reads)
        assertEquals(listOf(Triple(accountA, "kept", true), Triple(accountB, "kept", false)), service.optIns)
    }

    @Test
    fun withoutTheServiceTheReadFailsAndTheOptInIsRefused() = runBlocking {
        val viewModel = viewModel(discovery = null, service = null)

        assertEquals(NsfwPreferencesResult.Failed, viewModel.readNsfwAccountPreferences(accountA))
        assertEquals(QuarantineOptInResult.Refused(null), viewModel.optInToQuarantinedCommunity(accountA, "kept"))
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

    private fun item(name: String, nsfw: Boolean = true) = CommunityDiscoveryItem(
        community = Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L),
        nsfw = nsfw,
    )

    private class FakeDiscovery(
        private val popular: List<CommunityDiscoveryItem>,
        private val search: List<CommunityDiscoveryItem> = emptyList(),
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
            val items = if (target.cacheToken.startsWith("popular")) popular else search
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
