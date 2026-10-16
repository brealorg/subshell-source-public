package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITURL01: link shapes that mature Reddit clients recognise, checked
 * against `reports/2026-09-25-reddit-url-shapes-reference.md`. Every example
 * URL here is made up.
 */
class RedditUrl01ResolverTest {
    private val post = RedditTarget.Post("1abcde")

    @Test
    fun everyRedditSubdomainIsAFrontEndForTheSamePaths() {
        listOf("sh", "amp", "i", "s", "www", "old", "np", "m").forEach { sub ->
            assertEquals(
                sub,
                post,
                resolveRedditTarget("https://$sub.reddit.com/r/kotlin/comments/1abcde/title/"),
            )
        }
        assertNull(resolveRedditTarget("https://notreddit.com/r/kotlin/comments/1abcde/"))
        assertNull(resolveRedditTarget("https://reddit.com.evil.example/r/kotlin/comments/1abcde/"))
    }

    @Test
    fun viewSuffixesAreIgnored() {
        assertEquals(post, resolveRedditTarget("https://www.reddit.com/r/kotlin/comments/1abcde.json"))
        assertEquals(post, resolveRedditTarget("https://www.reddit.com/r/kotlin/comments/1abcde/title.compact"))
        assertEquals(
            RedditTarget.Comment("1abcde", "kz9y8x7"),
            resolveRedditTarget("https://www.reddit.com/r/kotlin/comments/1abcde/title/kz9y8x7.json"),
        )
        assertEquals(RedditTarget.Community("kotlin"), resolveRedditTarget("https://www.reddit.com/r/kotlin.json"))
    }

    @Test
    fun galleryIdsArePostIds() {
        assertEquals(post, resolveRedditTarget("https://www.reddit.com/gallery/1abcde"))
        assertNull(resolveRedditTarget("https://www.reddit.com/gallery/1abcde/extra"))
    }

    @Test
    fun postsInAUserProfileResolveAsPostsAndComments() {
        assertEquals(post, resolveRedditTarget("https://www.reddit.com/user/someone/comments/1abcde/my_post/"))
        assertEquals(post, resolveRedditTarget("https://www.reddit.com/u/someone/comments/1abcde"))
        assertEquals(
            RedditTarget.Comment("1abcde", "kz9y8x7"),
            resolveRedditTarget("https://www.reddit.com/user/someone/comments/1abcde/my_post/kz9y8x7/"),
        )
        // Without a post id it is the profile's comments tab (REDDITURL04).
        assertEquals(
            RedditTarget.User("someone", UserProfileLinkTab.COMMENTS),
            resolveRedditTarget("https://www.reddit.com/user/someone/comments/"),
        )
    }

    @Test
    fun communitySortSegmentsOpenTheCommunityWithThatSort() {
        assertEquals(
            RedditTarget.Community("kotlin", CommunitySort.TOP),
            resolveRedditTarget("https://www.reddit.com/r/kotlin/top/?t=week"),
        )
        assertEquals(
            RedditTarget.Community("kotlin", CommunitySort.NEW),
            resolveRedditTarget("https://old.reddit.com/r/kotlin/new"),
        )
        assertEquals(
            RedditTarget.Community("kotlin", CommunitySort.CONTROVERSIAL),
            resolveRedditTarget("/r/kotlin/controversial/"),
        )
        assertNull(resolveRedditTarget("https://www.reddit.com/r/kotlin/top/extra"))
        assertNull(resolveRedditTarget("https://www.reddit.com/r/kotlin/submit"))
    }

    @Test
    fun wikiLinksOpenTheirPageAndAboutPagesOpenTheCommunity() {
        // REDDITURL06: wiki links name their page; about pages stay the community.
        assertEquals(RedditTarget.Community("kotlin", wikiPage = ""), resolveRedditTarget("https://www.reddit.com/r/kotlin/wiki/"))
        assertEquals(RedditTarget.Community("kotlin", wikiPage = "faq"), resolveRedditTarget("https://www.reddit.com/r/kotlin/wiki/FAQ"))
        assertEquals(
            RedditTarget.Community("kotlin", wikiPage = "guides/setup"),
            resolveRedditTarget("https://www.reddit.com/r/kotlin/wiki/guides/setup"),
        )
        assertEquals(RedditTarget.Community("kotlin", wikiPage = "faq"), resolveRedditTarget("https://www.reddit.com/r/kotlin/w/faq"))
        listOf(
            "https://www.reddit.com/r/kotlin/about/",
            "https://www.reddit.com/r/kotlin/about/rules",
            "https://www.reddit.com/r/kotlin/about/sidebar",
        ).forEach { url ->
            assertEquals(url, RedditTarget.Community("kotlin"), resolveRedditTarget(url))
        }
    }

    @Test
    fun popularAndAllAreTheHomeFeedSources() {
        assertEquals(RedditTarget.Feed(FeedSource.POPULAR), resolveRedditTarget("https://www.reddit.com/r/popular/"))
        assertEquals(RedditTarget.Feed(FeedSource.ALL), resolveRedditTarget("https://www.reddit.com/r/all"))
        assertEquals(RedditTarget.Feed(FeedSource.ALL), resolveRedditTarget("/r/All/top/"))
        assertNull(resolveRedditTarget("https://www.reddit.com/r/all/about"))
    }

    @Test
    fun wrappedRedditLinksAreUnwrappedWithoutNetwork() {
        assertEquals(
            RedditTarget.Community("kotlin"),
            resolveRedditTarget(
                "https://reddit.app.link/?channel=share&\$og_redirect=https%3A%2F%2Fwww.reddit.com%2Fr%2Fkotlin",
            ),
        )
        assertEquals(
            post,
            resolveRedditTarget(
                "https://click.redditmail.com/CL0/https:%2F%2Fwww.reddit.com%2Fr%2Fkotlin%2Fcomments%2F1abcde%2Fx%2F/1/0100abc/xyz=",
            ),
        )
        assertEquals(
            post,
            resolveRedditTarget("https://www.google.com/amp/s/amp.reddit.com/r/kotlin/comments/1abcde/x/"),
        )
        assertNull(resolveRedditTarget("https://www.google.com/amp/s/example.com/story"))
        assertNull(resolveRedditTarget("https://reddit.app.link/AbCdEf"))
    }

    @Test
    fun wrappedAndSubdomainLinksCountAsRedditLinksInTheApp() {
        assertTrue(isRedditWebLink("https://sh.reddit.com/r/kotlin/s/Ab3dE5fG7h"))
        assertTrue(
            isRedditWebLink("https://reddit.app.link/?\$og_redirect=https%3A%2F%2Fwww.reddit.com%2Fr%2Fkotlin%2Fs%2FAb3"),
        )
        assertFalse(isRedditWebLink("https://www.google.com/amp/s/example.com/story"))
        assertFalse(isRedditWebLink("https://reddit.com.evil.example/r/kotlin"))
    }

    @Test
    fun openingASortedCommunityLinkUsesTheSortWithoutStoringItAsThePreference() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val before = state.communitySortPreferences

        assertTrue(state.openRedditTarget(RedditTarget.Community("kotlin", CommunitySort.TOP)))

        val route = state.currentNavigationRoute as SubshellRoute.Community
        assertEquals("kotlin", route.communityName)
        assertEquals(CommunitySort.TOP, route.sort)
        assertEquals(before, state.communitySortPreferences)
    }

    @Test
    fun openingAFeedLinkSelectsThatHomeFeedSource() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertTrue(state.openRedditTarget(RedditTarget.Feed(FeedSource.POPULAR)))

        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(MainDestination.HOME, state.destination)
    }

    @Test
    fun shareLinksAreResolvedThroughRedditsRedirect() {
        assertEquals(
            RedditTarget.ShareLink("https://www.reddit.com/r/kotlin/s/Ab3dE5fG7h"),
            resolveRedditTarget("https://sh.reddit.com/r/kotlin/s/Ab3dE5fG7h?utm_source=share"),
        )
        assertEquals(
            RedditTarget.ShareLink("https://www.reddit.com/u/someone/s/Ab3dE5fG7h"),
            resolveRedditTarget("https://www.reddit.com/u/someone/s/Ab3dE5fG7h"),
        )
        assertNull(RedditTarget.ShareLink("x").postIdOrNull())
        assertNull(resolveRedditTarget("https://www.reddit.com/r/kotlin/s/"))
    }
}
