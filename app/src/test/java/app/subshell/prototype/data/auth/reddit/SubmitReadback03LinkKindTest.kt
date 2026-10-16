package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SUBMITREADBACK03 (#146): Reddit marks a link post to YouTube or to a direct
 * image with post_hint rich:video/image, so the read-back saw VIDEO/IMAGE and
 * never matched the LINK that was sent; the attempt stayed unknown and blocked
 * the account. An uploaded image reads as a LINK to i.redd.it until Reddit
 * fills post_hint.
 */
class SubmitReadback03LinkKindTest {
    private val youtube = "https://www.youtube.com/watch?v=abc123"

    @Test
    fun aLinkPostRedditHintedAsVideoOrImageIsRecognised() {
        for (kind in listOf(RedditSubmitReconciliationKind.VIDEO, RedditSubmitReconciliationKind.IMAGE)) {
            assertEquals(
                "$kind",
                "abc123",
                select(linkRequest(), candidate(kind = kind, destinationUrl = youtube))?.id,
            )
        }
        // The link itself must still match.
        assertNull(
            select(
                linkRequest(),
                candidate(kind = RedditSubmitReconciliationKind.VIDEO, destinationUrl = "https://www.youtube.com/watch?v=other"),
            ),
        )
    }

    @Test
    fun anUploadedImageShownAsALinkToIReddItIsRecognisedButOtherLinksAreNot() {
        val upload = linkRequest().copy(linkUrl = null, mediaUri = "content://media/1", mediaMimeType = "image/jpeg")
        assertEquals(
            "abc123",
            select(upload, candidate(RedditSubmitReconciliationKind.LINK, "https://i.redd.it/abc.jpg"))?.id,
        )
        assertNull(select(upload, candidate(RedditSubmitReconciliationKind.LINK, "https://example.com/abc.jpg")))
        assertNull(select(upload, candidate(RedditSubmitReconciliationKind.VIDEO, "https://i.redd.it/abc.jpg")))
    }

    private fun select(request: AuthenticatedTextPostSubmitRequest, candidate: RedditSubmitReconciliationCandidate) =
        selectRedditSubmitReconciliationCandidate(
            request = request,
            username = "brealorg",
            dispatchedAtEpochMs = 1_000_000L,
            candidates = listOf(candidate),
        )

    private fun linkRequest() = AuthenticatedTextPostSubmitRequest(
        accountStableId = "reddit:test:t2_user",
        community = "subshell",
        title = "Composer09 proof",
        text = "",
        linkUrl = youtube,
        requestKey = "request-key",
    )

    private fun candidate(kind: RedditSubmitReconciliationKind, destinationUrl: String) =
        RedditSubmitReconciliationCandidate(
            fullname = "t3_abc123",
            id = "abc123",
            community = "subshell",
            author = "brealorg",
            title = "Composer09 proof",
            text = "",
            createdUtcEpochSeconds = 1_005L,
            permalink = "/r/subshell/comments/abc123/",
            destinationUrl = destinationUrl,
            kind = kind,
            flairText = null,
            nsfw = false,
            spoiler = false,
        )
}
