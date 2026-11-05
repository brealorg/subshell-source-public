package io.github.brealorg.subshell.data.auth

import io.github.brealorg.subshell.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Reddit01c3aComposerAuthorizationOwnerPolicyTest {
    @Test
    fun composerAuthorizationOwnersHaveStableDistinctWireKeys() {
        val flair = AuthCapabilityAuthorizationOwner.COMPOSER_FLAIR
        val requirements = AuthCapabilityAuthorizationOwner.COMPOSER_REQUIREMENTS

        assertEquals("composer_flair", flair.wireKey)
        assertEquals("composer_requirements", requirements.wireKey)
        assertFalse(flair.wireKey == requirements.wireKey)
        assertEquals(
            flair,
            AuthCapabilityAuthorizationOwner.fromWireKey(flair.wireKey),
        )
        assertEquals(
            requirements,
            AuthCapabilityAuthorizationOwner.fromWireKey(requirements.wireKey),
        )
    }

    @Test
    fun composerFlairScopeRemainsCommentWritePlusFlair() {
        assertEquals(
            RedditOAuth.liveCommentWriteScopes + "flair",
            RedditOAuth.liveComposerFlairScopes,
        )
        assertFalse("flair" in RedditOAuth.liveCommentWriteScopes)
    }
}
