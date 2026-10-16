package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REDDITURL04a2: `/message/compose` links. Every example URL here is made up.
 */
class RedditUrl04ComposeLinkTest {
    @Test
    fun composeLinksToAUserCarryRecipientSubjectAndMessage() {
        assertEquals(
            RedditTarget.Compose("someone", subject = "Hi there", message = "Your bot said hello"),
            resolveRedditTarget(
                "https://www.reddit.com/message/compose?to=someone&subject=Hi+there&message=Your%20bot%20said%20hello",
            ),
        )
        assertEquals(
            RedditTarget.Compose("someone"),
            resolveRedditTarget("https://old.reddit.com/message/compose/?to=/u/someone"),
        )
        assertEquals(
            RedditTarget.Compose("someone"),
            resolveRedditTarget("https://www.reddit.com/message/compose?to=%2Fuser%2Fsomeone&subject="),
        )
    }

    @Test
    fun modmailComposeAndMissingRecipientsAreNotSupported() {
        assertNull(resolveRedditTarget("https://www.reddit.com/message/compose?to=/r/kotlin&subject=Hi"))
        assertNull(resolveRedditTarget("https://www.reddit.com/message/compose?to=r%2Fkotlin"))
        assertNull(resolveRedditTarget("https://www.reddit.com/message/compose"))
        assertNull(resolveRedditTarget("https://www.reddit.com/message/compose?to=two+words"))
    }

    @Test
    fun theSubjectBecomesTheFirstParagraphOfTheChatMessage() {
        assertEquals(
            NewMessagePrefill("someone", "Hi there\n\nYour bot said hello"),
            newMessagePrefillFor(RedditTarget.Compose("someone", "Hi there", "Your bot said hello")),
        )
        assertEquals(NewMessagePrefill("someone", ""), newMessagePrefillFor(RedditTarget.Compose("someone")))
        assertEquals(
            NewMessagePrefill("someone", "Only text"),
            newMessagePrefillFor(RedditTarget.Compose("someone", message = "Only text")),
        )
    }

    @Test
    fun openingAComposeLinkOpensNewMessageAndHandsThePrefillOverOnce() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)

        assertTrue(state.openRedditTarget(RedditTarget.Compose("someone", "Hi", "Hello")))

        val route = state.currentNavigationRoute as SubshellRoute.Secondary
        assertEquals(SecondaryDestination.NEW_MESSAGE, route.secondaryDestination)
        assertEquals(NewMessagePrefill("someone", "Hi\n\nHello"), state.consumeNewMessagePrefill())
        assertNull(state.consumeNewMessagePrefill())
    }
}
