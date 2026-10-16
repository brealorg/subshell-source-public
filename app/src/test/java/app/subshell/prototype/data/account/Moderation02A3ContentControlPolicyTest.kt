package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A3ContentControlPolicyTest {
    private val target = ModeratorReportTarget("t3_abc", "subshell")

    private fun item(
        author: String = "moderator",
        locked: Boolean? = false,
        distinguished: Boolean? = false,
        allowed: Boolean? = true,
    ) = RedditModeratorQueueItem(
        fullname = "t3_abc",
        kind = RedditModeratorQueueKind.POST,
        communityName = "subshell",
        author = author,
        summary = "Fixture",
        reportDetails = RedditModeratorReportDetails(canModerate = allowed),
        contentState = RedditModeratorContentState(
            locked = locked,
            distinguished = distinguished,
            canModerate = allowed,
        ),
    )

    private fun state(item: RedditModeratorQueueItem = item()) =
        ModeratorContentControlsUiState(
            accountStableId = "owner",
            visible = true,
            target = target,
            item = item,
            revision = 7,
        )

    @Test
    fun lockAndUnlockRequireKnownOppositeState() {
        val unlocked = state(item(locked = false))
        assertEquals(
            RedditModeratorAction.LOCK,
            requireNotNull(contentControlConfirmationOrNull(unlocked, "owner", "moderator", RedditModeratorAction.LOCK)).action,
        )
        assertNull(contentControlConfirmationOrNull(unlocked, "owner", "moderator", RedditModeratorAction.UNLOCK))
        assertNull(contentControlConfirmationOrNull(state(item(locked = null)), "owner", "moderator", RedditModeratorAction.LOCK))

        val locked = state(item(locked = true))
        assertEquals(
            RedditModeratorAction.UNLOCK,
            requireNotNull(contentControlConfirmationOrNull(locked, "owner", "moderator", RedditModeratorAction.UNLOCK)).action,
        )
        assertNull(contentControlConfirmationOrNull(locked, "owner", "moderator", RedditModeratorAction.LOCK))
    }

    @Test
    fun distinguishAndUndoAreStrictlyAuthorBound() {
        val own = state(item(author = "moderator", distinguished = false))
        assertEquals(
            RedditModeratorAction.DISTINGUISH,
            requireNotNull(contentControlConfirmationOrNull(own, "owner", "u/moderator", RedditModeratorAction.DISTINGUISH)).action,
        )
        assertNull(contentControlConfirmationOrNull(own, "owner", "someone_else", RedditModeratorAction.DISTINGUISH))

        val other = state(item(author = "other", distinguished = false))
        assertNull(contentControlConfirmationOrNull(other, "owner", "moderator", RedditModeratorAction.DISTINGUISH))

        val distinguished = state(item(author = "moderator", distinguished = true))
        assertEquals(
            RedditModeratorAction.UNDISTINGUISH,
            requireNotNull(contentControlConfirmationOrNull(distinguished, "owner", "moderator", RedditModeratorAction.UNDISTINGUISH)).action,
        )
    }

    @Test
    fun wrongAccountTargetPermissionBusyOrUnknownStateCannotConfirm() {
        val base = state()
        assertNull(contentControlConfirmationOrNull(base, "other", "moderator", RedditModeratorAction.LOCK))
        assertNull(contentControlConfirmationOrNull(base.copy(target = ModeratorReportTarget("t3_other", "subshell")), "owner", "moderator", RedditModeratorAction.LOCK))
        assertNull(contentControlConfirmationOrNull(state(item(allowed = false)), "owner", "moderator", RedditModeratorAction.LOCK))
        assertNull(contentControlConfirmationOrNull(base.copy(loading = true), "owner", "moderator", RedditModeratorAction.LOCK))
        assertNull(contentControlConfirmationOrNull(state(item(locked = null)), "owner", "moderator", RedditModeratorAction.LOCK))
        assertNull(contentControlConfirmationOrNull(state(item(distinguished = null)), "owner", "moderator", RedditModeratorAction.DISTINGUISH))
    }

    @Test
    fun onlyApproveRemoveAndSpamAreQueueDispositionActions() {
        assertTrue(RedditModeratorAction.APPROVE.removesFromModerationQueue())
        assertTrue(RedditModeratorAction.REMOVE.removesFromModerationQueue())
        assertTrue(RedditModeratorAction.SPAM.removesFromModerationQueue())
        listOf(
            RedditModeratorAction.IGNORE_REPORTS,
            RedditModeratorAction.UNIGNORE_REPORTS,
            RedditModeratorAction.LOCK,
            RedditModeratorAction.UNLOCK,
            RedditModeratorAction.DISTINGUISH,
            RedditModeratorAction.UNDISTINGUISH,
        ).forEach { assertFalse(it.removesFromModerationQueue()) }
    }

    @Test
    fun onlyA3ControlsAreClassifiedAsContentControls() {
        assertEquals(
            setOf(
                RedditModeratorAction.LOCK,
                RedditModeratorAction.UNLOCK,
                RedditModeratorAction.DISTINGUISH,
                RedditModeratorAction.UNDISTINGUISH,
            ),
            RedditModeratorAction.entries.filter { it.isContentControl() }.toSet(),
        )
    }
}
