package app.subshell.prototype.ui

import app.subshell.prototype.data.account.CustomFeedSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REDDITURL05: the shareable link of a public custom feed. */
class RedditUrl05CustomFeedLinkTest {
    private fun feed(path: String, visibility: String, name: String = "Public") = CustomFeedSummary(
        path = path,
        displayName = name,
        description = "",
        visibility = visibility,
        subredditNames = listOf("kotlin"),
    )

    @Test
    fun theLinkUsesTheFeedsUrlNameNotItsDisplayName() {
        // The owner renamed "public_test" to "Public"; Reddit kept the URL name.
        assertEquals(
            "https://www.reddit.com/user/me/m/public_test/",
            canonicalRedditCustomFeedUrl(feed("/user/me/m/public_test/", "public", name = "Public")),
        )
        assertEquals(
            "https://www.reddit.com/user/gallowboob/m/food/",
            canonicalRedditCustomFeedUrl(feed("/user/gallowboob/m/food", "public")),
        )
        assertNull(canonicalRedditCustomFeedUrl(feed("/r/kotlin", "public")))
    }

    @Test
    fun onlyPublicFeedsOfferTheLink() {
        assertTrue(customFeedLinkShareable(feed("/user/me/m/a/", "public")))
        assertTrue(customFeedLinkShareable(feed("/user/me/m/a/", "Public")))
        assertFalse(customFeedLinkShareable(feed("/user/me/m/a/", "private")))
        assertFalse(customFeedLinkShareable(feed("/user/me/m/a/", "hidden")))
        assertFalse(customFeedLinkShareable(feed("/user/me/m/a/", "")))
    }

    @Test
    fun aSharedLinkOpensTheSameFeedInSubshell() {
        val url = canonicalRedditCustomFeedUrl(feed("/user/me/m/public_test/", "public"))!!

        assertEquals(RedditTarget.CustomFeed("me", "public_test"), resolveRedditTarget(url))
    }
}
