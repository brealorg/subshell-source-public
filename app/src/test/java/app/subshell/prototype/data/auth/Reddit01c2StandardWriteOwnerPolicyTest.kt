package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Reddit01c2StandardWriteOwnerPolicyTest {
    @Test
    fun standardWriteAuthorizationOwnersHaveStableDistinctWireKeys() {
        val owners = listOf(
            AuthCapabilityAuthorizationOwner.COMMENT_WRITE,
            AuthCapabilityAuthorizationOwner.TEXT_POST_WRITE,
            AuthCapabilityAuthorizationOwner.POST_EDIT,
            AuthCapabilityAuthorizationOwner.POST_DELETE,
        )

        assertEquals(4, owners.map { owner -> owner.wireKey }.toSet().size)

        owners.forEach { owner ->
            assertEquals(
                owner,
                AuthCapabilityAuthorizationOwner.fromWireKey(owner.wireKey),
            )
        }
    }

    @Test
    fun standardWriteScopeRelationshipsRemainIntentional() {
        assertEquals(
            setOf("identity", "mysubreddits", "read", "submit"),
            RedditOAuth.liveCommentWriteScopes,
        )
        assertEquals(
            RedditOAuth.liveCommentWriteScopes + "history",
            RedditOAuth.liveTextPostWriteScopes,
        )
        assertEquals(
            RedditOAuth.liveTextPostWriteScopes + "edit",
            RedditOAuth.liveCommentEditScopes,
        )
        assertTrue(
            RedditOAuth.liveCommentWriteScopes
                .all(RedditOAuth.liveTextPostWriteScopes::contains),
        )
        assertTrue(
            RedditOAuth.liveTextPostWriteScopes
                .all(RedditOAuth.liveCommentEditScopes::contains),
        )
    }
}
