package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.data.auth.communityWikiAuthorizationContextKey
import app.subshell.prototype.data.auth.communityWikiCommunityFromAuthorizationContextKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wiki01A2AuthorizationContextPolicyTest {
    @Test
    fun wikiAuthorizationContextRoundTripsNormalizedCommunity() {
        val key = communityWikiAuthorizationContextKey(" /r/Norge ")
        assertTrue(key.startsWith("wiki_"))
        assertEquals(
            "norge",
            communityWikiCommunityFromAuthorizationContextKey(key),
        )
        assertNull(
            communityWikiCommunityFromAuthorizationContextKey("other_616263"),
        )
    }

    @Test
    fun wikiUiStateIdentityIsAccountAndNormalizedCommunityScoped() {
        val state = RedditCommunityWikiUiState(
            accountStableId = "reddit:account",
            communityName = "norge",
        )

        assertTrue(state.matches("reddit:account", "/r/Norge"))
        assertFalse(state.matches("reddit:other", "/r/Norge"))
        assertFalse(state.matches("reddit:account", "/r/android"))
    }
}
