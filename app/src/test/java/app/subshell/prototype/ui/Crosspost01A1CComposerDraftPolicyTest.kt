package app.subshell.prototype.ui

import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun crosspost01A1CSource(nsfw: Boolean = false, spoiler: Boolean = false) =
    ComposerCrosspostSource("t3_source123", "source", "author", "Original title", PostKind.IMAGE, nsfw, spoiler)

internal fun crosspost01A1CDraft() = newCrosspostComposerDraft(crosspost01A1CSource(), "owner", 1_000L)
    .withCrosspostDestination("target", 2_000L)

internal fun crosspost01A1CPolicy() = ComposerCommunityPolicy(
    community = "target",
    loadedFromReddit = true,
    postRequirementsStatus = ComposerPostRequirementsStatus.LOADED,
    flairOptionsLoaded = true,
    crosspostDestination = ComposerCrosspostDestination(
        accountStableId = "owner", community = "target", allowCrossposts = true,
        subredditType = "public", userIsBanned = false,
    ),
)

class Crosspost01A1CComposerDraftPolicyTest {
    @Test
    fun sourceSnapshotRequiresExplicitPostFullnameNotUrlLocalIdOrComment() {
        val post = Post("t3_source123", "r/source", "author", "1h", "Original", score = 1, comments = 0, kind = PostKind.TEXT)
        assertEquals("t3_source123", post.toComposerCrosspostSource()?.fullname)
        for (invalid in listOf("source123", "local-post-1", "t1_source123", "https://reddit.com/comments/source123")) {
            assertNull(post.copy(id = invalid).toComposerCrosspostSource())
        }
        assertNull(post.copy(community = "unknown/path").toComposerCrosspostSource())
    }

    @Test
    fun selectedWrapperIsNotSilentlyFlattenedToNestedParentOrMediaUrl() {
        val post = Post(
            "t3_wrapper", "target", "wrapperauthor", "1h", "Wrapper title", score = 1, comments = 2,
            kind = PostKind.CROSSPOST,
            crosspost = CrosspostPreview("original", "originalauthor", "Parent title", 5, id = "t3_parent", nsfw = true),
            externalUrl = "https://example.com/image.jpg",
        )
        val source = requireNotNull(post.toComposerCrosspostSource())
        assertEquals("t3_wrapper", source.fullname)
        assertEquals("Wrapper title", source.title)
        assertEquals("/r/target/comments/wrapper/", source.permalink)
        assertTrue(source.nsfw)
    }

    @Test
    fun freshCrosspostPinsSourceOwnerAndSensitivityButDoesNotPickDestination() {
        val source = crosspost01A1CSource(nsfw = true, spoiler = true)
        val draft = newCrosspostComposerDraft(source, "owner", 1_000L)
        assertEquals(ComposerPostType.CROSSPOST, draft.type)
        assertEquals("owner", draft.accountStableId)
        assertEquals("", draft.community)
        assertEquals(source.title, draft.title)
        assertEquals(source, draft.crosspostSource)
        assertTrue(draft.nsfw && draft.spoiler && draft.hasContent)
        assertTrue(draft.body.isEmpty() && draft.linkUrl.isEmpty() && draft.media.isEmpty())
        assertNull(draft.flair)
    }

    @Test
    fun destinationChangeClearsOldFlairButKeepsSourceOwnerEditedTitleAndDraftId() {
        val original = crosspost01A1CDraft().copy(title = "Edited title", flair = ComposerFlair("old-flair", "Old"))
        val moved = original.withCrosspostDestination("/r/OTHER", 3_000L)
        assertEquals("other", moved.community)
        assertNull(moved.flair)
        assertEquals(original.crosspostSource, moved.crosspostSource)
        assertEquals(original.accountStableId, moved.accountStableId)
        assertEquals(original.id, moved.id)
        assertEquals("Edited title", moved.title)
        assertTrue(canUpdateSourceBoundComposerDraft(original, moved))
    }

    @Test
    fun destinationCaseNormalizationDoesNotClearSameCommunityFlair() {
        val original = crosspost01A1CDraft().copy(flair = ComposerFlair("f", "News"))
        assertEquals(original.flair, original.withCrosspostDestination("r/TARGET").flair)
    }

    @Test
    fun editingCannotReplaceOwnerSourceDraftIdOrType() {
        val draft = crosspost01A1CDraft()
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(accountStableId = "other")))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(id = "other")))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(crosspostSource = crosspost01A1CSource().copy(fullname = "t3_other"))))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(crosspostSource = null)))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(type = ComposerPostType.TEXT)))
        assertTrue(canUpdateSourceBoundComposerDraft(draft, draft.copy(title = "Reviewed title", sendReplies = false)))
    }

    @Test
    fun editingCannotInjectReplacementBodyLinkMediaOrCarryOldDestinationFlair() {
        val draft = crosspost01A1CDraft().copy(flair = ComposerFlair("old", "Old"))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(body = "replacement")))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(linkUrl = "https://example.com")))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(media = listOf(ComposerMediaAttachment("content://x", "image/png", ComposerMediaKind.IMAGE)))))
        assertFalse(canUpdateSourceBoundComposerDraft(draft, draft.copy(community = "other")))
        assertTrue(canUpdateSourceBoundComposerDraft(draft, draft.copy(community = "other", flair = null)))
    }

    @Test
    fun sourceSensitivityCannotBeRemovedDuringDraftEditingOrRequestAssembly() {
        val draft = newCrosspostComposerDraft(crosspost01A1CSource(true, true), "owner", 1_000L)
            .withCrosspostDestination("target")
        for (weakened in listOf(draft.copy(nsfw = false), draft.copy(spoiler = false))) {
            assertFalse(canUpdateSourceBoundComposerDraft(draft, weakened))
            assertFalse(weakened.isValidSourceBoundCrosspostDraft())
            assertNull(weakened.toCrosspostWriteRequest("owner", true, crosspost01A1CPolicy()))
        }
    }

    @Test
    fun requestUsesNativeSourceIdentityNotPreviewBodyOrLink() {
        val draft = crosspost01A1CDraft().copy(flair = ComposerFlair("target-flair", "News"))
        val policy = crosspost01A1CPolicy().copy(flairRequired = true, flairOptions = listOf(draft.flair!!))
        val request = requireNotNull(draft.toCrosspostWriteRequest("owner", true, policy))
        assertEquals("owner", request.accountStableId)
        assertEquals("target", request.community)
        assertEquals("t3_source123", request.crosspostFullname)
        assertEquals("", request.text)
        assertNull(request.linkUrl)
        assertNull(request.mediaUri)
        assertTrue(request.galleryItems.isEmpty())
        assertEquals("target-flair", request.flairId)
        val other = draft.copy(crosspostSource = draft.crosspostSource!!.copy(fullname = "t3_other"))
        assertFalse(request.requestKey == other.toCrosspostWriteRequest("owner", true, policy)?.requestKey)
    }

    @Test
    fun genericLocalOrRemoteComposerValidationNeverApprovesSourceBoundDrafts() {
        val draft = crosspost01A1CDraft()
        assertNotNull(validateRemoteTextPostComposerDraftIssue(draft))
        assertNotNull(validatePostComposerDraftIssue(draft, listOf("target")))
        assertTrue(runCatching { buildLocalPost(draft, "me", "local-post-1") }.isFailure)
        assertNotNull(validateRemoteTextPostComposerDraftIssue(draft.copy(type = ComposerPostType.TEXT)))
    }

    @Test
    fun crosspostIsNotAddedToFreeStandingTypeChoices() {
        assertEquals(listOf(ComposerPostType.TEXT, ComposerPostType.LINK, ComposerPostType.MEDIA), ComposerPostType.standaloneTypes)
        assertTrue(ComposerPostType.CROSSPOST in ComposerPostType.entries)
        assertFalse(ComposerCommunityPolicy.local("target").allows(ComposerPostType.CROSSPOST))
    }
}
