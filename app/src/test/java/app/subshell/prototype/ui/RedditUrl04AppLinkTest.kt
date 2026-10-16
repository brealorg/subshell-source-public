package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITURL04a: profile tab and search links. Every example URL here is made up.
 */
class RedditUrl04AppLinkTest {
    @Test
    fun profileTabLinksOpenTheProfileOnThatTab() {
        assertEquals(
            RedditTarget.User("someone", UserProfileLinkTab.POSTS),
            resolveRedditTarget("https://www.reddit.com/user/someone/submitted/?sort=top&t=all"),
        )
        assertEquals(
            RedditTarget.User("someone", UserProfileLinkTab.COMMENTS),
            resolveRedditTarget("https://www.reddit.com/u/someone/comments/"),
        )
        assertEquals(
            RedditTarget.User("someone", UserProfileLinkTab.SAVED),
            resolveRedditTarget("https://old.reddit.com/user/someone/saved"),
        )
        assertEquals(RedditTarget.User("someone"), resolveRedditTarget("https://www.reddit.com/user/someone/"))
        assertNull(resolveRedditTarget("https://www.reddit.com/user/someone/gilded"))
    }

    @Test
    fun searchLinksCarryTheQueryAndOnlyARestrictedSearchKeepsItsCommunity() {
        assertEquals(
            RedditTarget.Search("jetpack compose"),
            resolveRedditTarget("https://www.reddit.com/search/?q=jetpack+compose&sort=new&t=month"),
        )
        assertEquals(
            RedditTarget.Search("flow", community = "kotlin"),
            resolveRedditTarget("https://www.reddit.com/r/kotlin/search?q=flow&restrict_sr=on"),
        )
        assertEquals(
            RedditTarget.Search("flow & coroutines"),
            resolveRedditTarget("https://www.reddit.com/r/kotlin/search?q=flow%20%26%20coroutines"),
        )
        assertNull(resolveRedditTarget("https://www.reddit.com/search/"))
        assertEquals(RedditTarget.Community("search"), resolveRedditTarget("https://www.reddit.com/r/search"))
    }

    @Test
    fun aSearchLinkRunsTheSearchAcrossReddit() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        state.openSubreddit("android")

        assertTrue(state.openRedditTarget(RedditTarget.Search("jetpack compose")))

        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
        assertEquals("jetpack compose", state.searchSubmittedQuery)
        assertEquals(SearchArea.ALL_SUBSHELL, state.searchArea)
    }

    @Test
    fun aRestrictedSearchLinkRunsTheSearchInThatCommunity() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertTrue(state.openRedditTarget(RedditTarget.Search("flow", community = "kotlin")))

        val route = state.currentNavigationRoute as SubshellRoute.Search
        assertEquals("kotlin", route.originCommunity)
        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals("flow", state.searchSubmittedQuery)
    }

    @Test
    fun aTabLinkSelectsThatTabAndOwnOnlyTabsNeedTheOwnProfile() {
        val me = AccountSessionSummary(
            stableId = "reddit:me",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(me),
            initialActiveAccountId = me.stableId,
            initialInteractionSideConfigured = true,
        )

        state.openRedditTarget(RedditTarget.User("someone", UserProfileLinkTab.COMMENTS))
        assertEquals(1, state.userProfileTab("someone"))

        state.openRedditTarget(RedditTarget.User("someone", UserProfileLinkTab.SAVED))
        assertEquals(1, state.userProfileTab("someone"))

        state.openRedditTarget(RedditTarget.User("me", UserProfileLinkTab.SAVED))
        assertEquals(2, state.userProfileTab("me"))
    }
}
