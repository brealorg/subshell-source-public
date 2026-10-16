package app.subshell.prototype.ui.components

import app.subshell.prototype.data.account.RedditModeratorQueueKind
import app.subshell.prototype.data.account.RedditRemovalMessage
import app.subshell.prototype.data.account.RedditRemovalMessageType
import app.subshell.prototype.data.account.RedditRemovalReason
import app.subshell.prototype.data.account.RedditRemovalStepOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModResponse01A2RemovalResponseDraftTest {
    private val reason = RedditRemovalReason(id = "r1", title = "Rule 1", message = "Please read rule 1.")

    @Test
    fun offByDefaultAndNothingIsSentUntilTheModeratorTurnsItOn() {
        val draft = RemovalResponseDraft()
        assertFalse(draft.enabled)
        draft.prefillFrom(reason)
        assertEquals("Please read rule 1.", draft.body)
        assertNull("a selected reason alone never sends", draft.toMessageOrNull(reason))
        assertFalse(draft.blocksConfirm)
    }

    @Test
    fun prefillFollowsTheReasonUntilEditedThenKeepsTheEdit() {
        val draft = RemovalResponseDraft()
        draft.prefillFrom(reason)
        val other = RedditRemovalReason(id = "r2", title = "Rule 2", message = "Rule 2 text")
        draft.prefillFrom(other)
        assertEquals("Rule 2 text", draft.body)
        draft.prefillFrom(null)
        assertEquals("", draft.body)

        draft.edit("My own words")
        draft.prefillFrom(reason)
        assertEquals("My own words", draft.body)
        assertTrue(draft.edited)
    }

    @Test
    fun enabledDraftBuildsTheMessageWithTheReasonTitleAndDefaultsToPrivate() {
        val draft = RemovalResponseDraft()
        draft.enabled = true
        assertEquals(RedditRemovalMessageType.PRIVATE, draft.type)
        assertTrue("empty body blocks confirm", draft.blocksConfirm)
        assertNull(draft.toMessageOrNull(reason))

        draft.edit("  Removed for rule 1.  ")
        assertFalse(draft.blocksConfirm)
        assertEquals(
            RedditRemovalMessage(RedditRemovalMessageType.PRIVATE, "Removed for rule 1.", "Rule 1"),
            draft.toMessageOrNull(reason),
        )
        draft.type = RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT
        assertEquals(
            RedditRemovalMessage(RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT, "Removed for rule 1.", "Removed"),
            draft.toMessageOrNull(null),
        )
    }

    @Test
    fun bodyIsBoundedAndTemplatesCountAsEdits() {
        val draft = RemovalResponseDraft()
        draft.edit("x".repeat(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS + 5))
        assertEquals(RedditRemovalMessage.MAX_REMOVAL_MESSAGE_BODY_CHARS, draft.body.length)
        val fresh = RemovalResponseDraft()
        fresh.useTemplate("Template")
        assertTrue(fresh.edited)
        fresh.prefillFrom(reason)
        assertEquals("Template", fresh.body)
    }

    @Test
    fun presentationCopyMatchesRedditSemantics() {
        assertEquals(
            "Reddit posts it as a distinguished, stickied comment.",
            removalResponseTypeDescription(RedditRemovalMessageType.PUBLIC, RedditModeratorQueueKind.POST),
        )
        assertEquals(
            "Reddit posts it as a distinguished reply.",
            removalResponseTypeDescription(RedditRemovalMessageType.PUBLIC_AS_SUBREDDIT, RedditModeratorQueueKind.COMMENT),
        )
        assertEquals(
            "Sent from the subreddit's modmail.",
            removalResponseTypeDescription(RedditRemovalMessageType.PRIVATE_EXPOSED, RedditModeratorQueueKind.POST),
        )
        assertEquals(4, RedditRemovalMessageType.entries.map(::removalResponseTypeLabel).distinct().size)
    }

    @Test
    fun partialSuccessNoticeNamesEveryFailedStep() {
        assertEquals(
            "Reddit removed the item, but the removal message was not sent.",
            removalPartialSuccessMessage(reasonWarning = false, messageOutcome = RedditRemovalStepOutcome.FAILED),
        )
        assertEquals(
            "Reddit removed the item, but the removal reason or mod note could not be confirmed and the removal message was not sent.",
            removalPartialSuccessMessage(reasonWarning = true, messageOutcome = RedditRemovalStepOutcome.FAILED),
        )
        assertEquals(
            "Reddit removed the item, but the removal reason or mod note could not be confirmed.",
            removalPartialSuccessMessage(reasonWarning = true, messageOutcome = RedditRemovalStepOutcome.SENT),
        )
    }
}
