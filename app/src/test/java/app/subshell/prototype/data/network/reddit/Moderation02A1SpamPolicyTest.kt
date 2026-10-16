package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorAction
import app.subshell.prototype.data.account.RedditModeratorActionRequest
import app.subshell.prototype.data.account.RedditModeratorQueueItem
import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.canConfirmModeratorSpam
import app.subshell.prototype.data.account.moderatorSpamRequestOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A1SpamPolicyTest {
    @Test
    fun spamUsesRemoveWithTrueForExactPostAndCommentFullnames() {
        listOf(post(), comment()).forEach { item ->
            val request = requireNotNull(moderatorSpamRequestOrNull("account-a", item))
            assertEquals("account-a", request.accountStableId)
            assertEquals("subshell", request.communityName)
            assertEquals(item.fullname, request.thingFullname)
            assertEquals(RedditModeratorAction.SPAM, request.action)
            assertNull(request.removalReasonId)
            assertNull(request.modNote)
            val form = redditModerationActionRequest(request)
            assertEquals(listOf("api", "remove"), form.pathSegments)
            assertEquals(listOf("id" to item.fullname, "spam" to "true"), form.fields)
        }
    }

    @Test
    fun approveAndOrdinaryRemoveFormsAreUnchangedForBothKinds() {
        listOf(post(), comment()).forEach { item ->
            val request = requireNotNull(moderatorSpamRequestOrNull("account-a", item))
            val approve = redditModerationActionRequest(request.copy(action = RedditModeratorAction.APPROVE))
            assertEquals(listOf("api", "approve"), approve.pathSegments)
            assertEquals(listOf("id" to item.fullname), approve.fields)
            val remove = redditModerationActionRequest(request.copy(action = RedditModeratorAction.REMOVE))
            assertEquals(listOf("api", "remove"), remove.pathSegments)
            assertEquals(listOf("id" to item.fullname, "spam" to "false"), remove.fields)
        }
    }

    @Test
    fun spamCannotCarryRemovalReasonOrModeratorNote() {
        val request = requireNotNull(moderatorSpamRequestOrNull("account-a", post()))
        assertTrue(runCatching { request.copy(removalReasonId = "reason-a") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { request.copy(modNote = "Moderator note") }.exceptionOrNull() is IllegalArgumentException)
        val remove = request.copy(
            action = RedditModeratorAction.REMOVE,
            removalReasonId = "reason-a",
            modNote = "Moderator note",
        )
        assertEquals("reason-a", remove.removalReasonId)
        assertEquals("Moderator note", remove.modNote)
    }

    @Test
    fun invalidAccountCommunityOrKindMismatchCannotCreateSpamRequest() {
        assertNull(moderatorSpamRequestOrNull(" ", post()))
        assertNull(moderatorSpamRequestOrNull("account-a", post().copy(communityName = "bad/community")))
        assertNull(moderatorSpamRequestOrNull("account-a", post().copy(kind = RedditModeratorQueueKind.COMMENT)))
        assertNull(moderatorSpamRequestOrNull("account-a", comment().copy(kind = RedditModeratorQueueKind.POST)))
    }

    @Test
    fun confirmationRequiresSameAccountCommunityTargetKindAndIdleActionState() {
        val item = post()
        val request = requireNotNull(moderatorSpamRequestOrNull("account-a", item))
        fun allowed(
            candidate: RedditModeratorActionRequest = request,
            account: String = "account-a",
            currentItem: RedditModeratorQueueItem = item,
            busy: Boolean = false,
        ) = canConfirmModeratorSpam(candidate, account, currentItem, busy)
        assertTrue(allowed())
        assertFalse(allowed(account = "account-b"))
        assertFalse(allowed(currentItem = item.copy(fullname = "t3_other")))
        assertFalse(allowed(currentItem = item.copy(communityName = "othercommunity")))
        assertFalse(allowed(currentItem = item.copy(kind = RedditModeratorQueueKind.COMMENT)))
        assertFalse(allowed(busy = true))
        assertFalse(allowed(candidate = request.copy(action = RedditModeratorAction.REMOVE)))
        assertFalse(allowed(candidate = request.copy(action = RedditModeratorAction.APPROVE)))
    }

    private fun post() = RedditModeratorQueueItem(
        fullname = "t3_abc123",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = "fixtureauthor",
        summary = "Test post",
    )

    private fun comment() = post().copy(
        fullname = "t1_def456",
        kind = RedditModeratorQueueKind.COMMENT,
        summary = "Test comment",
    )
}
