package app.subshell.prototype.data.network.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditSubscriptionNetworkDataSourceTest {
    @Test
    fun requestUsesMineSubscriberCursorAndMaximumPageSize() {
        val request = redditSubscriptionsJsonRequest(
            cursor = "t5_after",
            limit = REDDIT_SUBSCRIPTIONS_MAX_PAGE_SIZE,
        )

        assertEquals(
            listOf("subreddits", "mine", "subscriber"),
            request.pathSegments,
        )
        assertEquals(
            listOf(
                "raw_json" to "1",
                "limit" to "100",
                "after" to "t5_after",
            ),
            request.queryParameters,
        )
    }

    @Test
    fun listingMapsOnlyValidCommunitiesAndKeepsRemoteCursor() {
        val page = RedditSubscriptionListingDto(
            data = RedditSubscriptionListingDataDto(
                after = "t5_next",
                children = listOf(
                    RedditSubscriptionThingDto(
                        kind = "t5",
                        data = RedditCommunityAboutDataDto(
                            id = "volvo",
                            name = "t5_volvo",
                            displayName = "Volvo",
                            publicDescription = "Volvo community",
                            subscribers = 12_500,
                            userIsSubscriber = true,
                            communityIcon =
                                "https://styles.redditmedia.com/icon.png?x=1&amp;y=2",
                            primaryColor = "#123456",
                        ),
                    ),
                    RedditSubscriptionThingDto(
                        kind = "t3",
                        data = RedditCommunityAboutDataDto(displayName = "not-a-community"),
                    ),
                    RedditSubscriptionThingDto(
                        kind = "t5",
                        data = RedditCommunityAboutDataDto(displayName = ""),
                    ),
                ),
            ),
        ).toNetworkPage()

        val community = page.items.single().community
        assertEquals("t5_volvo", community.id)
        assertEquals("Volvo", community.name)
        assertEquals("Volvo community", community.description)
        assertEquals("12.5k", community.members)
        assertEquals(0xFF123456L, community.accent)
        assertEquals(true, community.joined)
        assertEquals(
            "https://styles.redditmedia.com/icon.png?x=1&y=2",
            community.iconImageUri,
        )
        assertEquals("t5_next", page.nextCursor)
    }

    @Test
    fun blankCursorIsNotSent() {
        val request = redditSubscriptionsJsonRequest(cursor = "  ", limit = 25)

        assertNull(request.queryParameters.firstOrNull { it.first == "after" })
    }
}
