package app.subshell.prototype.ui

import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWritePhase
import app.subshell.prototype.model.PostKind
import org.junit.Assert.*
import org.junit.Test

class Crosspost01A1EReviewPolicyTest {
    private val draft = crosspost01A1CDraft()
    private val evidence = CrosspostSourceEvidence("t3_source123", "source", "author", "Original title", false, false)
    private fun submitted() = SubmittedTextPost(
        fullname = "t3_created123", id = "created123", community = "target", title = draft.title,
        text = "", permalink = "/r/target/comments/created123/", crosspostFullname = "t3_source123",
    )
    private fun completed(d: PostComposerDraft = draft, s: SubmittedTextPost = submitted(), owner: String = "owner") =
        completedCrosspostForDraft(d, owner, true, "crossposter", requireNotNull(draft.crosspostRequestForIdentityOnly()).requestKey, s)

    @Test fun reviewIdentityIgnoresAutosaveTimeButNotEditableFields() {
        assertEquals(draft.crosspostReviewIdentity(), draft.touched(99_000L).crosspostReviewIdentity())
        val reviewed = CrosspostReviewUiState(draft = draft.crosspostReviewIdentity())
        assertTrue(reviewed.matches(draft.touched(99_000L)))
        for (changed in listOf(draft.copy(title = "Changed"), draft.withCrosspostDestination("other"),
            draft.copy(sendReplies = false), draft.copy(flair = ComposerFlair("f", "News")))) {
            assertFalse(reviewed.matches(changed))
        }
    }
    @Test fun unknownAndAuthorizationAndSendingCannotOpenNewReview() {
        for (phase in TextPostWritePhase.entries) {
            assertEquals(phase == TextPostWritePhase.IDLE || phase == TextPostWritePhase.FAILED,
                crosspostWritePhaseAllowsReview(phase))
        }
    }
    @Test fun identityRequestKeepsParentAndNeverBecomesGenericContent() {
        val request = requireNotNull(draft.crosspostRequestForIdentityOnly())
        assertEquals("t3_source123", request.crosspostFullname)
        assertEquals("", request.text)
        assertNull(request.linkUrl); assertNull(request.mediaUri)
        assertNull(draft.copy(body = "replacement").crosspostRequestForIdentityOnly())
        assertNull(draft.copy(type = ComposerPostType.TEXT).crosspostRequestForIdentityOnly())
        assertNull(draft.copy(crosspostSource = null).crosspostRequestForIdentityOnly())
    }
    @Test fun completedWrapperKeepsCanonicalNewIdAndSeparateSourceCard() {
        val post = requireNotNull(completed())
        assertEquals("t3_created123", post.id)
        assertEquals(PostKind.CROSSPOST, post.kind)
        assertEquals("t3_source123", post.crosspost?.id)
        assertEquals("source", post.crosspost?.community)
        assertEquals("", post.body)
        assertEquals("/r/target/comments/created123/", post.permalink)
    }
    @Test fun completionRejectsOtherOwnerRequestSourceOrWrapper() {
        assertNull(completed(owner = "other"))
        assertNull(completed(d = draft.copy(title = "other")))
        assertNull(completed(s = submitted().copy(crosspostFullname = "t3_other")))
        assertNull(completed(s = submitted().copy(fullname = "t3_source123", id = "source123")))
        assertNull(completed(s = submitted().copy(community = "other")))
        assertNull(completed(s = submitted().copy(title = "Other")))
        assertNull(completed(s = submitted().copy(nsfw = true)))
        assertNull(completed(s = submitted().copy(crosspostFullname = null)))
    }
    @Test fun freshSourceIdentityAndOriginalMetadataMustMatch() {
        assertNull(crosspostSourceValidationMessage(draft, evidence))
        for (wrong in listOf(evidence.copy(fullname = "t3_other"), evidence.copy(community = "other"),
            evidence.copy(author = "other"), evidence.copy(title = "Other title"))) {
            assertNotNull(crosspostSourceValidationMessage(draft, wrong))
        }
    }
    @Test fun newlySensitiveSourceRequiresExplicitDestinationFlags() {
        assertNotNull(crosspostSourceValidationMessage(draft, evidence.copy(nsfw = true)))
        assertNotNull(crosspostSourceValidationMessage(draft, evidence.copy(spoiler = true)))
        assertNull(crosspostSourceValidationMessage(draft.copy(nsfw = true, spoiler = true), evidence.copy(nsfw = true, spoiler = true)))
    }
    @Test fun reviewStateIsEphemeralAndEmptyByDefault() {
        val restored = CrosspostReviewUiState()
        assertEquals(CrosspostReviewPhase.IDLE, restored.phase)
        assertNull(restored.draft); assertNull(restored.request)
        assertFalse(restored.matches(draft))
    }
}
