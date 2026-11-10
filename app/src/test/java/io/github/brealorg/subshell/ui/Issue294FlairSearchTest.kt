package io.github.brealorg.subshell.ui

import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #294: a tap on a post's flair searches its community for that flair. ISSUE294_FLAIR_OPENS_A_FLAIR_SEARCH_V1. */
class Issue294FlairSearchTest {
    private val newest = RedditSearchOrder(sort = RedditSearchSort.NEW)

    private fun post(community: String = "cotondetulear", flair: String? = "Photo") = Post(
        id = "t3_abc",
        community = community,
        author = "someone",
        age = "1h",
        title = "A post",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
        flair = flair,
    )

    @Test
    fun theFlairBecomesAFlairSearchInThePostsCommunity() {
        assertEquals(
            RedditTarget.Search("flair_name:\"Photo\"", community = "cotondetulear", order = newest),
            flairSearchTarget(post()),
        )
        // Spaces are kept as one, emoji codes are kept as Reddit stores them,
        // quotes are left out (the search syntax cannot escape them).
        assertEquals(
            RedditTarget.Search("flair_name:\"Help :q: needed\"", community = "androiddev", order = newest),
            flairSearchTarget(post(community = "r/androiddev", flair = "  Help  :q:\n needed ")),
        )
        assertEquals(
            RedditTarget.Search("flair_name:\"Say hi\"", community = "x", order = newest),
            flairSearchTarget(post(community = "x", flair = "Say \"hi\"")),
        )
    }

    @Test
    fun noFlairTextOrAProfilePostHasNothingToSearch() {
        assertNull(flairSearchTarget(post(flair = null)))
        assertNull(flairSearchTarget(post(flair = "  ")))
        assertNull(flairSearchTarget(post(flair = "\"\"")))
        assertNull(flairSearchTarget(post(community = "u_someone")))
        assertNull(flairSearchTarget(post(community = "")))
        assertNull(flairSearchTarget(post(flair = "x".repeat(200))))
    }

    @Test
    fun theTapRunsTheSearchInThatCommunity() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertTrue(state.openRedditTarget(flairSearchTarget(post())!!))

        val route = state.currentNavigationRoute as SubshellRoute.Search
        assertEquals("cotondetulear", route.originCommunity)
        assertEquals(SearchArea.CURRENT_COMMUNITY, state.searchArea)
        assertEquals("flair_name:\"Photo\"", state.searchSubmittedQuery)
        assertEquals(RedditSearchSort.NEW, state.searchOrder.sort)

        // A plain search link afterwards is a new search in Relevance order.
        state.openRedditTarget(RedditTarget.Search("flow", community = "kotlin"))
        assertEquals(RedditSearchOrder.DEFAULT, state.searchOrder)
    }

    @Test
    fun aSearchFromALinkOrAFlairAsksTheSearchScreenToLoadItOnce() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        assertFalse(state.searchLoadRequested)

        state.openRedditTarget(RedditTarget.Search("flow", community = "kotlin"))

        assertTrue(state.searchLoadRequested)
        assertTrue(state.takeSearchLoadRequest())
        assertFalse(state.searchLoadRequested)
        assertFalse(state.takeSearchLoadRequest())
    }
}
