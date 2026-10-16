package app.subshell.prototype.auth

import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchResult
import app.subshell.prototype.data.auth.AuthenticatedRedditPeopleSearchTarget
import app.subshell.prototype.model.RedditPeopleSearchItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #229: a composer's mention search asks Reddit's people search like the
 * Search workspace does, but keeps its own answer: neither replaces the
 * other's results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue229MentionSearchCoordinatorTest {
    private val account = "reddit:me"
    private val asked = mutableListOf<String>()

    private val loader = object : AuthenticatedRedditCommunityDiscoveryLoader {
        override suspend fun cached(accountStableId: String, target: AuthenticatedRedditCommunityDiscoveryTarget): AuthenticatedRedditCommunityDiscoveryResult =
            error("a people search must not read communities")

        override suspend fun synchronize(accountStableId: String, target: AuthenticatedRedditCommunityDiscoveryTarget): AuthenticatedRedditCommunityDiscoveryResult =
            error("a people search must not read communities")

        override suspend fun searchPeople(accountStableId: String, target: AuthenticatedRedditPeopleSearchTarget): AuthenticatedRedditPeopleSearchResult {
            asked += target.searchQuery
            return AuthenticatedRedditPeopleSearchResult.Loaded(
                listOf(RedditPeopleSearchItem(username = "${target.searchQuery}_one", title = "", description = "")),
            )
        }
    }

    @Test
    fun aMentionSearchKeepsItsOwnAnswerApartFromTheSearchWorkspace() = runTest(StandardTestDispatcher()) {
        val coordinator = CommunityDiscoveryCoordinator(loader, this)

        coordinator.loadRedditCommunityDiscovery(account, "alice", null, null, false)
        advanceUntilIdle()
        coordinator.loadRedditMentionSearch(account, "bo")
        advanceUntilIdle()

        assertEquals(listOf("alice", "bo"), asked)
        val people = coordinator.redditCommunityDiscoveryUiState.value.peopleSearch
        val mentions = coordinator.redditMentionSearchUiState.value
        assertEquals("alice", people.searchQuery)
        assertEquals(listOf("alice_one"), people.items.map(RedditPeopleSearchItem::username))
        assertEquals(account, mentions.accountStableId)
        assertEquals("bo", mentions.searchQuery)
        assertEquals(listOf("bo_one"), mentions.items.map(RedditPeopleSearchItem::username))

        // The same mention search already answered is not sent again.
        coordinator.loadRedditMentionSearch(account, "bo")
        advanceUntilIdle()
        assertEquals(listOf("alice", "bo"), asked)
    }
}
