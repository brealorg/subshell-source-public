package app.subshell.prototype.ui

import app.subshell.prototype.auth.CommunityPinnedPostsCoordinator
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityPinnedPostsLoader
import app.subshell.prototype.model.Post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE230_PINNED_POSTS_FIRST_V1 (owner 2026-10-16): a community's pinned posts
 * come first in Reddit's order, whatever the sort, never twice; as highlights
 * they move into the row above the feed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Issue230PinnedPostsFirstTest {
    private val base = SampleData.posts.first()
    private fun post(id: String, pinned: Boolean = false): Post = base.copy(id = id, title = id, pinned = pinned)

    private val rules = post("rules", pinned = true)
    private val weekly = post("weekly", pinned = true)
    private val newest = post("newest")
    private val older = post("older")

    @Test
    fun asCardsThePinnedPostsLeadInRedditsOrderAndAreNotRepeated() {
        // Under New, Reddit returns a sticky wherever its date puts it.
        val listing = listOf(newest, weekly, older)
        val result = communityFeedWithPinnedPosts(listing, listOf(rules, weekly), PinnedPostsPresentation.CARDS)

        assertEquals(listOf("rules", "weekly", "newest", "older"), result.feed.map(Post::id))
        assertTrue(result.highlights.isEmpty())
    }

    @Test
    fun withoutAPinnedReadTheListingStaysAsItIs() {
        val listing = listOf(newest, weekly, older)
        val result = communityFeedWithPinnedPosts(listing, emptyList(), PinnedPostsPresentation.CARDS)

        assertEquals(listing, result.feed)
    }

    @Test
    fun asHighlightsThePinnedPostsMoveIntoTheRowAndLeaveTheFeed() {
        val listing = listOf(weekly, newest, older)
        val result = communityFeedWithPinnedPosts(listing, listOf(rules, weekly), PinnedPostsPresentation.HIGHLIGHTS)

        assertEquals(listOf("rules", "weekly"), result.highlights.map(Post::id))
        assertEquals(listOf("newest", "older"), result.feed.map(Post::id))
    }

    @Test
    fun highlightsFallBackToTheListingsOwnPinnedPosts() {
        // Hot already puts stickies first; before the pinned read answers they still go into the row.
        val listing = listOf(rules, weekly, newest)
        val result = communityFeedWithPinnedPosts(listing, emptyList(), PinnedPostsPresentation.HIGHLIGHTS)

        assertEquals(listOf("rules", "weekly"), result.highlights.map(Post::id))
        assertEquals(listOf("newest"), result.feed.map(Post::id))
    }

    @Test
    fun theSettingDefaultsToCardsIsBackedUpAndRestores() {
        assertEquals(PinnedPostsPresentation.CARDS, SubshellSettings().pinnedPostsPresentation)
        assertTrue(SubshellSettings.PINNED_POSTS_PRESENTATION in SubshellSettings.storageKeys)
        val stored = SubshellSettings(pinnedPostsPresentation = PinnedPostsPresentation.HIGHLIGHTS).toStorageMap()
        assertEquals(
            PinnedPostsPresentation.HIGHLIGHTS,
            SubshellSettings.fromStorageMap(stored).pinnedPostsPresentation,
        )
        // An older backup without the key restores the default.
        assertEquals(
            PinnedPostsPresentation.CARDS,
            SubshellSettings.fromStorageMap(stored - SubshellSettings.PINNED_POSTS_PRESENTATION).pinnedPostsPresentation,
        )
    }

    private class FakeLoader : AuthenticatedRedditCommunityPinnedPostsLoader {
        val calls = mutableListOf<Pair<String, String>>()
        var answer: List<Post>? = emptyList()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(accountStableId: String, communityName: String): List<Post>? {
            calls += accountStableId to communityName
            gate?.await()
            return answer
        }
    }

    @Test
    fun theCoordinatorReadsOncePerVisitAndAgainOnRefresh() = runTest {
        val loader = FakeLoader().apply { answer = listOf(rules) }
        val coordinator = CommunityPinnedPostsCoordinator(loader, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        coordinator.load("reddit:a", "Kotlin")
        coordinator.load("reddit:a", "kotlin")
        assertEquals(1, loader.calls.size)
        assertEquals(listOf(rules), coordinator.uiState.value.postsFor("reddit:a", "kotlin"))

        coordinator.load("reddit:a", "kotlin", forceRefresh = true)
        assertEquals(2, loader.calls.size)
    }

    @Test
    fun anAnswerForACommunityNoLongerOpenIsDroppedAndAnotherAccountSeesNothing() = runTest {
        val loader = FakeLoader().apply { answer = listOf(rules); gate = CompletableDeferred() }
        val coordinator = CommunityPinnedPostsCoordinator(loader, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        coordinator.load("reddit:a", "kotlin")
        val slow = loader.gate!!
        loader.gate = null
        loader.answer = listOf(weekly)
        coordinator.load("reddit:a", "android")
        slow.complete(Unit)

        assertEquals(listOf(weekly), coordinator.uiState.value.postsFor("reddit:a", "android"))
        assertTrue(coordinator.uiState.value.postsFor("reddit:a", "kotlin").isEmpty())
        assertTrue(coordinator.uiState.value.postsFor("reddit:b", "android").isEmpty())
    }

    @Test
    fun aFailedRefreshKeepsThePinnedPostsAlreadyShown() = runTest {
        val loader = FakeLoader().apply { answer = listOf(rules) }
        val coordinator = CommunityPinnedPostsCoordinator(loader, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        coordinator.load("reddit:a", "kotlin")

        loader.answer = null
        coordinator.load("reddit:a", "kotlin", forceRefresh = true)

        assertEquals(listOf(rules), coordinator.uiState.value.postsFor("reddit:a", "kotlin"))
        assertEquals(false, coordinator.uiState.value.loading)
    }
}
