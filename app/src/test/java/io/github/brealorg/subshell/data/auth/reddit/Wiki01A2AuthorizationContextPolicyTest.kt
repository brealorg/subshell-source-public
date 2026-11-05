package io.github.brealorg.subshell.data.auth.reddit

import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.data.auth.communityWikiAuthorizationContextKey
import io.github.brealorg.subshell.data.auth.communityWikiCommunityFromAuthorizationContextKey
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
