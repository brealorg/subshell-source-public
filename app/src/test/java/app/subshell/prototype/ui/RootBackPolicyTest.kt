package app.subshell.prototype.ui

import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.model.Community
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootBackPolicyTest {
    @Test
    fun onlyHomeWithAnotherSourceReturnsToTheLandingSource() {
        // Other tabs sit above Main(HOME) on the route stack; nothing to do here.
        for (destination in MainDestination.entries - MainDestination.HOME) {
            for (source in FeedSource.entries) {
                assertNull(rootBackFeedSource(destination, source, landingSource = FeedSource.HOME))
            }
        }
        assertEquals(FeedSource.HOME, rootBackFeedSource(MainDestination.HOME, FeedSource.POPULAR, FeedSource.HOME))
        assertEquals(FeedSource.HOME, rootBackFeedSource(MainDestination.HOME, FeedSource.ALL, FeedSource.HOME))
        assertEquals(FeedSource.HOME, rootBackFeedSource(MainDestination.HOME, FeedSource.SAVED, FeedSource.HOME))
        assertEquals(FeedSource.POPULAR, rootBackFeedSource(MainDestination.HOME, FeedSource.ALL, FeedSource.POPULAR))
        assertEquals(FeedSource.POPULAR, rootBackFeedSource(MainDestination.HOME, FeedSource.HOME, FeedSource.POPULAR))
    }

    @Test
    fun homeWithTheLandingSourceLetsBackThrough() {
        assertNull(rootBackFeedSource(MainDestination.HOME, FeedSource.HOME, FeedSource.HOME))
        assertNull(rootBackFeedSource(MainDestination.HOME, FeedSource.POPULAR, FeedSource.POPULAR))
    }

    @Test
    fun landingSourceIsPopularOnlyForAuthoritativelyZeroSubscriptions() {
        val account = "reddit:test"
        val empty = RedditSubscriptionsUiState(accountStableId = account, loaded = true)
        val joined = empty.copy(
            communities = listOf(
                Community(id = "t5_1", name = "android", description = "", members = "", accent = 0L),
            ),
        )
        assertEquals(FeedSource.POPULAR, rootBackLandingSource(account, authenticated = true, subscriptions = empty))
        assertEquals(FeedSource.HOME, rootBackLandingSource(account, authenticated = true, subscriptions = joined))
        // Unknown or untrusted subscriptions keep Home.
        assertEquals(FeedSource.HOME, rootBackLandingSource(account, true, RedditSubscriptionsUiState()))
        assertEquals(FeedSource.HOME, rootBackLandingSource(account, true, empty.copy(fromCache = true)))
        assertEquals(FeedSource.HOME, rootBackLandingSource(account, true, empty.copy(loading = true)))
        assertEquals(
            FeedSource.HOME,
            rootBackLandingSource(
                account,
                true,
                empty.copy(failureKind = RedditSubscriptionsUiFailureKind.entries.first()),
            ),
        )
        assertEquals(FeedSource.HOME, rootBackLandingSource("reddit:other", true, empty))
        // Anonymous accounts land on Home.
        assertEquals(FeedSource.HOME, rootBackLandingSource(account, authenticated = false, subscriptions = empty))
    }
}
