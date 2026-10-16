package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.ui.app.linkedCustomFeedPlaceholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITURL04a3: multireddit (custom feed) links, any owner. Every example
 * URL here is made up.
 */
class RedditUrl04MultiredditLinkTest {
    @Test
    fun multiredditLinksNameTheirOwnerAndFeed() {
        assertEquals(
            RedditTarget.CustomFeed("someone", "programming"),
            resolveRedditTarget("https://www.reddit.com/user/someone/m/programming/"),
        )
        assertEquals(
            RedditTarget.CustomFeed("someone", "rust-and-go"),
            resolveRedditTarget("https://old.reddit.com/u/someone/m/rust-and-go/top/?t=week"),
        )
        assertEquals(
            RedditTarget.CustomFeed(null, "programming"),
            resolveRedditTarget("https://www.reddit.com/me/m/programming"),
        )
        assertNull(resolveRedditTarget("https://www.reddit.com/user/someone/m/"))
        assertNull(resolveRedditTarget("https://www.reddit.com/user/someone/m/programming/submit"))
        assertNull(resolveRedditTarget("https://www.reddit.com/me/m/"))
    }

    @Test
    fun openingAFeedLinkRequestsTheFeedPathForSubshellApp() {
        val state = signedInAs("me")

        assertTrue(state.openRedditTarget(RedditTarget.CustomFeed("someone", "programming")))
        assertEquals("/user/someone/m/programming/", state.consumeCustomFeedLinkPath())
        assertNull(state.consumeCustomFeedLinkPath())

        assertTrue(state.openRedditTarget(RedditTarget.CustomFeed(null, "mine")))
        assertEquals("/user/me/m/mine/", state.consumeCustomFeedLinkPath())
    }

    @Test
    fun anOwnFeedLinkNeedsASignedInAccount() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertFalse(state.openRedditTarget(RedditTarget.CustomFeed(null, "mine")))
        assertNull(state.consumeCustomFeedLinkPath())
    }

    @Test
    fun aLinkedFeedShowsItsNameFromThePathUntilRedditSaysMore() {
        val placeholder = linkedCustomFeedPlaceholder("/user/someone/m/programming/")

        assertEquals("/user/someone/m/programming/", placeholder.path)
        assertEquals("programming", placeholder.displayName)
        assertEquals(emptyList<String>(), placeholder.subredditNames)
    }

    private fun signedInAs(username: String): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:$username",
            username = username,
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }
}
