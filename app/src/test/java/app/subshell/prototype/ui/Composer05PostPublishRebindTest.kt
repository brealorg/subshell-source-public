package app.subshell.prototype.ui

import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COMPOSER05 (ui review round 3 P4): a posted text/link/media post is rebound
 * to Reddit's own copy, like crossposts, unless Reddit's copy does not carry
 * the media yet or the reader has moved on.
 */
class Composer05PostPublishRebindTest {
    private val writer = AccountSessionSummary(
        stableId = "reddit:redreader_compatibility:t2_writer",
        username = "writer",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private val submitted = SubmittedTextPost(
        "t3_proof1", "proof1", "subshell", "Posted", "Body",
        "/r/subshell/comments/proof1/posted/",
    )

    private fun reddit(kind: PostKind = PostKind.TEXT) = Post(
        id = "t3_proof1",
        community = "Subshell",
        author = "writer",
        age = "1m",
        title = "Posted",
        body = "Body",
        score = 7,
        comments = 2,
        kind = kind,
    )

    private fun local(kind: PostKind) = reddit(kind).copy(id = "proof1", score = 1, comments = 0)

    private fun openedAfterPost(): SubshellState {
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialAccountSessions = listOf(writer),
            initialActiveAccountId = writer.stableId,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(
            state.postComposerDraft.copy(community = "subshell", title = "Posted", body = "Body"),
        )
        val key = TextPostWriteRequest(
            accountStableId = writer.stableId,
            community = "subshell",
            title = "Posted",
            text = "Body",
        ).requestKey
        state.completeRemoteTextPostComposer(writer.stableId, key, submitted)
        return state
    }

    @Test
    fun theOpenedPostIsReplacedByRedditsCopy() {
        val state = openedAfterPost()
        assertEquals(1, state.selectedPost?.score)

        assertTrue(state.rebindCompletedTextPostToCanonical(submitted, reddit()))
        assertEquals(7, state.selectedPost?.score)
        assertEquals(2, state.selectedPost?.comments)
    }

    @Test
    fun afterBackTheReadBackChangesNothing() {
        val state = openedAfterPost()
        state.navigateBack()
        val route = state.currentNavigationRoute

        assertFalse(state.rebindCompletedTextPostToCanonical(submitted, reddit()))
        assertEquals(route, state.currentNavigationRoute)
    }

    @Test
    fun anotherPostOrAChangedTitleIsNotAccepted() {
        val fallback = local(PostKind.TEXT)
        assertSame(
            fallback,
            canonicalTextPostCompletionOrFallback(submitted, fallback, reddit().copy(id = "t3_other")),
        )
        assertSame(
            fallback,
            canonicalTextPostCompletionOrFallback(submitted, fallback, reddit().copy(title = "Other")),
        )
        assertSame(
            fallback,
            canonicalTextPostCompletionOrFallback(submitted, fallback, reddit().copy(community = "other")),
        )
        assertSame(fallback, canonicalTextPostCompletionOrFallback(submitted, fallback, null))
    }

    @Test
    fun mediaStillProcessingOnRedditKeepsTheLocalCopy() {
        val image = local(PostKind.IMAGE).copy(localMediaUri = "content://media/1")
        assertSame(image, canonicalTextPostCompletionOrFallback(submitted, image, reddit(PostKind.IMAGE)))
        val readyImage = reddit(PostKind.IMAGE).copy(remoteImageUri = "https://i.redd.it/proof1.jpg")
        assertSame(readyImage, canonicalTextPostCompletionOrFallback(submitted, image, readyImage))

        val video = local(PostKind.VIDEO).copy(localMediaUri = "content://media/2")
        assertSame(video, canonicalTextPostCompletionOrFallback(submitted, video, reddit(PostKind.VIDEO)))
        val readyVideo = reddit(PostKind.VIDEO).copy(
            remoteVideoHlsUri = "https://v.redd.it/proof1/HLSPlaylist.m3u8",
        )
        assertSame(readyVideo, canonicalTextPostCompletionOrFallback(submitted, video, readyVideo))

        val localGallery = local(PostKind.GALLERY).copy(
            gallery = listOf(galleryItem("a", null), galleryItem("b", null)),
        )
        val halfReady = reddit(PostKind.GALLERY).copy(
            gallery = listOf(galleryItem("a", "https://i.redd.it/a.jpg"), galleryItem("b", null)),
        )
        assertSame(localGallery, canonicalTextPostCompletionOrFallback(submitted, localGallery, halfReady))
        val ready = halfReady.copy(
            gallery = listOf(
                galleryItem("a", "https://i.redd.it/a.jpg"),
                galleryItem("b", "https://i.redd.it/b.jpg"),
            ),
        )
        assertSame(ready, canonicalTextPostCompletionOrFallback(submitted, localGallery, ready))
    }

    @Test
    fun aLinkPostAcceptsRedditsPreviewKindButNotAPlainTextPost() {
        val link = local(PostKind.LINK)
        val imagePreview = reddit(PostKind.IMAGE)
        assertSame(imagePreview, canonicalTextPostCompletionOrFallback(submitted, link, imagePreview))
        assertSame(link, canonicalTextPostCompletionOrFallback(submitted, link, reddit(PostKind.TEXT)))
    }

    private fun galleryItem(id: String, remote: String?) = GalleryItem(
        id = id,
        label = id,
        format = app.subshell.prototype.model.MediaFormat.IMAGE,
        remoteImageUri = remote,
    )
}
