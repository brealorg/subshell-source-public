package app.subshell.prototype.ui

import app.subshell.prototype.data.account.RedditSubscriptionsUiFailureKind
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.model.Community
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth07b2SubscriptionAwareLandingPolicyTest {
    private val accountId = "reddit:test"

    @Test
    fun mismatchedAndLoadingSubscriptionStateWaits() {
        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = RedditSubscriptionsUiState(
                    accountStableId = "reddit:other",
                    loaded = true,
                ),
            ),
        )

        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = RedditSubscriptionsUiState(
                    accountStableId = accountId,
                    loading = true,
                ),
            ),
        )
    }

    @Test
    fun cachedEmptySubscriptionsNeverSelectPopular() {
        assertEquals(
            AuthenticatedMainFeedLandingDecision.WAIT,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = RedditSubscriptionsUiState(
                    accountStableId = accountId,
                    communities = emptyList(),
                    fromCache = true,
                    loaded = true,
                ),
            ),
        )
    }

    @Test
    fun authoritativeEmptySubscriptionsSelectPopular() {
        assertEquals(
            AuthenticatedMainFeedLandingDecision.POPULAR,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = authoritativeSubscriptions(),
            ),
        )
    }

    @Test
    fun authoritativeJoinedSubscriptionsSelectHome() {
        assertEquals(
            AuthenticatedMainFeedLandingDecision.HOME,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = authoritativeSubscriptions(
                    communities = listOf(community()),
                ),
            ),
        )
    }

    @Test
    fun authoritativeFailureAbortsAutomaticLanding() {
        assertEquals(
            AuthenticatedMainFeedLandingDecision.ABORT,
            resolveAuthenticatedLandingFeed(
                accountStableId = accountId,
                subscriptions = RedditSubscriptionsUiState(
                    accountStableId = accountId,
                    failureKind =
                        RedditSubscriptionsUiFailureKind.TRANSPORT,
                ),
            ),
        )
    }

    @Test
    fun zeroSubscriptionHomeRequiresAuthoritativeRemoteEmptyState() {
        assertTrue(
            authoritativeZeroSubscriptionHome(
                accountStableId = accountId,
                source = FeedSource.HOME,
                subscriptions = authoritativeSubscriptions(),
            ),
        )

        assertFalse(
            authoritativeZeroSubscriptionHome(
                accountStableId = accountId,
                source = FeedSource.POPULAR,
                subscriptions = authoritativeSubscriptions(),
            ),
        )

        assertFalse(
            authoritativeZeroSubscriptionHome(
                accountStableId = accountId,
                source = FeedSource.HOME,
                subscriptions = RedditSubscriptionsUiState(
                    accountStableId = accountId,
                    fromCache = true,
                    loaded = true,
                ),
            ),
        )

        assertFalse(
            authoritativeZeroSubscriptionHome(
                accountStableId = accountId,
                source = FeedSource.HOME,
                subscriptions = authoritativeSubscriptions(
                    communities = listOf(community()),
                ),
            ),
        )
    }


    @Test
    fun deferredAuthenticatedLandingCannotReplaceAContextRoute() {
        assertTrue(
            authenticatedMainFeedLandingRouteEligible(
                currentRoute = SubshellRoute.Main(
                    destination = MainDestination.HOME,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                ),
                activeCustomFeedPath = null,
            ),
        )

        assertFalse(
            authenticatedMainFeedLandingRouteEligible(
                currentRoute = SubshellRoute.Post(
                    FeedPostRoute(
                        postId = "t3_post",
                        focusedCommentId = "t1_comment",
                        visitId = 1,
                        origin = FeedPostOrigin.User(
                            destination = MainDestination.HOME,
                            username = "brealorg",
                        ),
                    ),
                ),
                activeCustomFeedPath = null,
            ),
        )

        assertFalse(
            authenticatedMainFeedLandingRouteEligible(
                currentRoute = SubshellRoute.Search(
                    destination = MainDestination.HOME,
                    originCommunity = null,
                    query = "test",
                    scope = SearchScope.POSTS,
                    area = SearchArea.ALL_SUBSHELL,
                ),
                activeCustomFeedPath = null,
            ),
        )

        assertFalse(
            authenticatedMainFeedLandingRouteEligible(
                currentRoute = SubshellRoute.Media(
                    postId = "t3_post",
                    index = 0,
                ),
                activeCustomFeedPath = null,
            ),
        )

        assertFalse(
            authenticatedMainFeedLandingRouteEligible(
                currentRoute = SubshellRoute.Main(
                    destination = MainDestination.HOME,
                    source = FeedSource.HOME,
                    sort = FeedSort.BEST,
                ),
                activeCustomFeedPath = "custom-feed",
            ),
        )
    }

    private fun authoritativeSubscriptions(
        communities: List<Community> = emptyList(),
    ): RedditSubscriptionsUiState =
        RedditSubscriptionsUiState(
            accountStableId = accountId,
            communities = communities,
            loading = false,
            fromCache = false,
            loaded = true,
            failureKind = null,
        )

    private fun community(): Community =
        Community(
            id = "t5_test",
            name = "test",
            description = "",
            members = "1",
            accent = 0xFF6750A4L,
        )
}
