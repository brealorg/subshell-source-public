package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditCommunityDiscoveryUiFailureKind
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * STEP3 (search01c2): a search for a name asks Reddit for people only; a
 * newer search replaces an older one whose late answer is dropped; the same
 * search already answered is not sent again; without a loader it says so;
 * and the query text itself is never written to the log, only its length.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3PeopleSearchCoordinatorTest {
    private val account = "reddit:me"
    private val people = mutableListOf<String>()

    private fun person(name: String) = RedditPeopleSearchItem(username = name, title = name, description = "")

    private inner class Loader(
        private val answer: suspend (String) -> AuthenticatedRedditPeopleSearchResult = { query ->
            AuthenticatedRedditPeopleSearchResult.Loaded(listOf(person("${query}_one")))
        },
    ) : AuthenticatedRedditCommunityDiscoveryLoader {
        override suspend fun cached(accountStableId: String, target: AuthenticatedRedditCommunityDiscoveryTarget): AuthenticatedRedditCommunityDiscoveryResult =
            error("a name search must not read communities")

        override suspend fun synchronize(accountStableId: String, target: AuthenticatedRedditCommunityDiscoveryTarget): AuthenticatedRedditCommunityDiscoveryResult =
            error("a name search must not read communities")

        override suspend fun searchPeople(accountStableId: String, target: AuthenticatedRedditPeopleSearchTarget): AuthenticatedRedditPeopleSearchResult {
            people += target.searchQuery
            return answer(target.searchQuery)
        }
    }

    private fun CommunityDiscoveryCoordinator.search(query: String) =
        loadRedditCommunityDiscovery(account, query, searchSort = null, autocompleteIncludeOver18 = null, forceRefresh = false)

    @Test
    fun aNameSearchAsksForPeopleOnlyAndIsNotSentTwice() = runTest(StandardTestDispatcher()) {
        val coordinator = CommunityDiscoveryCoordinator(Loader(), this)

        coordinator.search("alice")
        advanceUntilIdle()
        coordinator.search("alice")
        advanceUntilIdle()

        val state = coordinator.redditCommunityDiscoveryUiState.value.peopleSearch
        assertEquals(listOf("alice"), people)
        assertEquals(listOf("alice_one"), state.items.map(RedditPeopleSearchItem::username))
        assertEquals(1L, state.contentRevision)
    }

    @Test
    fun aNewerSearchDropsTheLateAnswerToTheOlder() = runTest(StandardTestDispatcher()) {
        val older = CompletableDeferred<Unit>()
        val coordinator = CommunityDiscoveryCoordinator(
            Loader { query ->
                if (query == "alice") withContext(NonCancellable) { older.await() }
                AuthenticatedRedditPeopleSearchResult.Loaded(listOf(person("${query}_one")))
            },
            this,
        )

        coordinator.search("alice")
        advanceUntilIdle()
        coordinator.search("bob")
        older.complete(Unit)
        advanceUntilIdle()

        val state = coordinator.redditCommunityDiscoveryUiState.value.peopleSearch
        assertEquals("bob", state.searchQuery)
        assertEquals(listOf("bob_one"), state.items.map(RedditPeopleSearchItem::username))
    }

    @Test
    fun withoutALoaderTheSearchSaysItIsNotConfigured() = runTest(StandardTestDispatcher()) {
        val coordinator = CommunityDiscoveryCoordinator(null, this)

        coordinator.search("alice")

        assertEquals(
            RedditCommunityDiscoveryUiFailureKind.CLIENT_CONFIGURATION,
            coordinator.redditCommunityDiscoveryUiState.value.peopleSearch.failureKind,
        )
    }

    @Test
    fun theQueryIsNeverLoggedOnlyItsLength() = runTest(StandardTestDispatcher()) {
        ShadowLog.clear()
        val coordinator = CommunityDiscoveryCoordinator(Loader(), this)

        coordinator.search("secret words")
        advanceUntilIdle()

        val lines = ShadowLog.getLogs().map { it.msg.orEmpty() }
        assertFalse(lines.toString(), lines.any { "secret" in it })
        assertTrue(lines.toString(), lines.any { "queryLength=12" in it })
    }
}
