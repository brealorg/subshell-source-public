package app.subshell.prototype.data.auth.reddit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditTextPostOAuthScopePolicyTest {
    @Test
    fun textPostReconciliationAddsHistoryWithoutBroadeningCommentWrite() {
        assertEquals(
            setOf("identity", "mysubreddits", "read", "submit"),
            RedditOAuth.liveCommentWriteScopes,
        )
        assertFalse("history" in RedditOAuth.liveCommentWriteScopes)
        assertEquals(
            RedditOAuth.liveCommentWriteScopes + "history",
            RedditOAuth.liveTextPostWriteScopes,
        )
        assertTrue("history" in RedditOAuth.liveTextPostWriteScopes)
    }
}
