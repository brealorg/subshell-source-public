package app.subshell.prototype.ui.screens

import app.subshell.prototype.data.account.REDDIT_MODERATION_AGGREGATE_QUEUE
import app.subshell.prototype.data.account.RedditModerationUiState
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.canonicalModerationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mod01A22InboxModTabPolicyTest {
    @Test
    fun modTabOnlyAppearsForAccountsWithModeratedCommunities() {
        assertEquals(
            listOf(InboxPrimaryTab.ACTIVITY, InboxPrimaryTab.MESSAGES),
            inboxPrimaryTabs(showModeration = false),
        )
        assertEquals(
            listOf(
                InboxPrimaryTab.ACTIVITY,
                InboxPrimaryTab.MESSAGES,
                InboxPrimaryTab.MOD,
            ),
            inboxPrimaryTabs(showModeration = true),
        )
    }

    @Test
    fun aggregateQueueOwnsItemsFromEveryModeratedCommunity() {
        val state = RedditModerationUiState(
            accountStableId = "account-1",
            queueCommunityName = REDDIT_MODERATION_AGGREGATE_QUEUE,
            queueLoaded = true,
        )

        assertTrue(state.aggregateQueueMatches("account-1"))
        assertTrue(state.queueContainsContext("account-1", "subshell"))
        assertTrue(state.queueContainsContext("account-1", "android"))
        assertFalse(state.queueContainsContext("account-2", "subshell"))
    }

    @Test
    fun postAndCommentQueueItemsResolveCanonicalExactTargets() {
        val post = queueItem(
            fullname = "t3_post123",
            kind = RedditModeratorQueueKind.POST,
            permalink = "/r/subshell/comments/post123/title/",
        )
        assertEquals("post123", post.canonicalModerationTarget()?.postId)
        assertNull(post.canonicalModerationTarget()?.commentFullname)

        val comment = queueItem(
            fullname = "t1_comment456",
            kind = RedditModeratorQueueKind.COMMENT,
            permalink = "/r/subshell/comments/post123/title/comment456/",
        )
        assertEquals("post123", comment.canonicalModerationTarget()?.postId)
        assertEquals(
            "t1_comment456",
            comment.canonicalModerationTarget()?.commentFullname,
        )
    }


    @Test
    fun moderationCommunityCountCopyIsGrammaticallyStable() {
        assertEquals(
            "Across 1 moderated community",
            moderationCommunityCountLabel(1),
        )
        assertEquals(
            "Across 3 moderated communities",
            moderationCommunityCountLabel(3),
        )
    }

    private fun queueItem(
        fullname: String,
        kind: RedditModeratorQueueKind,
        permalink: String?,
    ) = RedditModeratorQueueItem(
        fullname = fullname,
        kind = kind,
        communityName = "subshell",
        author = "reporter",
        summary = "Reported content",
        reportCount = 1,
        permalink = permalink,
    )
}
