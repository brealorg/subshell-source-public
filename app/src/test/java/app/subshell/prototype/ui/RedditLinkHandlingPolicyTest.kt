package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditLinkHandlingPolicyTest {
    @Test
    fun canonicalHostSetMatchesTheDeclaredDeeplinkSurface() {
        assertEquals(
            linkedSetOf(
                "reddit.com",
                "www.reddit.com",
                "old.reddit.com",
                "new.reddit.com",
                "np.reddit.com",
                "m.reddit.com",
                "redd.it",
            ),
            REDDIT_LINK_HANDLING_HOSTS,
        )
    }

    @Test
    fun disabledPartialAndEnabledStatesAreDerivedFromAndroidSelection() {
        val none = resolveRedditLinkHandlingState(
            supported = true,
            linkHandlingAllowed = true,
            hostToState = emptyMap(),
        )
        assertEquals(RedditLinkHandlingStatus.DISABLED, none.status)
        assertEquals(0, none.selectedHostCount)

        val partial = resolveRedditLinkHandlingState(
            supported = true,
            linkHandlingAllowed = true,
            hostToState = mapOf(
                "reddit.com" to 1,
                "www.reddit.com" to 2,
            ),
        )
        assertEquals(RedditLinkHandlingStatus.PARTIAL, partial.status)
        assertEquals(2, partial.selectedHostCount)

        val enabled = resolveRedditLinkHandlingState(
            supported = true,
            linkHandlingAllowed = true,
            hostToState = REDDIT_LINK_HANDLING_HOSTS.associateWith { 1 },
        )
        assertEquals(RedditLinkHandlingStatus.ENABLED, enabled.status)
        assertEquals(REDDIT_LINK_HANDLING_HOSTS.size, enabled.selectedHostCount)
    }

    @Test
    fun globalAndroidLinkHandlingToggleOverridesSelectedHosts() {
        val state = resolveRedditLinkHandlingState(
            supported = true,
            linkHandlingAllowed = false,
            hostToState = REDDIT_LINK_HANDLING_HOSTS.associateWith { 1 },
        )

        assertEquals(RedditLinkHandlingStatus.DISABLED, state.status)
        assertFalse(state.linkHandlingAllowed)
        assertEquals(REDDIT_LINK_HANDLING_HOSTS.size, state.selectedHostCount)
    }

    @Test
    fun startupPromptIsOneShotAndOnlyForAuthenticatedAndroid12PlusUsers() {
        val disabled = RedditLinkHandlingState(
            supported = true,
            linkHandlingAllowed = true,
            status = RedditLinkHandlingStatus.DISABLED,
        )
        val enabled = disabled.copy(
            selectedHostCount = REDDIT_LINK_HANDLING_HOSTS.size,
            status = RedditLinkHandlingStatus.ENABLED,
        )

        assertTrue(
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = true,
                promptSeen = false,
                state = disabled,
            ),
        )
        assertFalse(
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = false,
                promptSeen = false,
                state = disabled,
            ),
        )
        assertFalse(
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = true,
                promptSeen = true,
                state = disabled,
            ),
        )
        assertFalse(
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = true,
                promptSeen = false,
                state = enabled,
            ),
        )
        assertFalse(
            shouldShowRedditLinkHandlingPrompt(
                hasAuthenticatedAccounts = true,
                promptSeen = false,
                state = RedditLinkHandlingState(),
            ),
        )
    }
}
