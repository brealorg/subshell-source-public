package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationCandidate
import app.subshell.prototype.data.network.reddit.RedditSubmitReconciliationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Crosspost01A1AAuthenticatedRequestPolicyTest {
    private val request = AuthenticatedTextPostSubmitRequest(
        accountStableId = "reddit:redreader_compatibility:t2_crossposter",
        community = "subshell", title = "Shared original", text = "",
        crosspostFullname = " T3_Source123 ",
    )

    @Test
    fun authenticatedBoundaryKeepsAnExplicitCanonicalParent() {
        assertEquals("t3_source123", request.validatedCrosspostFullname())
        assertNull(request.copy(crosspostFullname = null).validatedCrosspostFullname())
    }

    @Test
    fun directAuthenticatedRequestsCannotBypassCrosspostExclusivity() {
        val invalid = listOf(
            request.copy(crosspostFullname = ""),
            request.copy(text = "Body"),
            request.copy(linkUrl = "https://example.org/"),
            request.copy(mediaUri = ""),
            request.copy(mediaMimeType = "image/png"),
            request.copy(mediaAsGif = true),
            request.copy(galleryItems = listOf(AuthenticatedTextPostGalleryItem("content://a", "image/png"))),
            request.copy(flairText = "No template"),
            request.copy(flairId = "flair1", flairText = "x".repeat(65)),
        )
        invalid.forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { candidate.validatedCrosspostFullname() }
        }
    }

    @Test
    fun parentBlindCrosspostReconciliationNeverUsesTheGenericMatcher() {
        // CROSSPOST01A1B successor: scanning is permitted, success without parent evidence is not.
        val candidate = RedditSubmitReconciliationCandidate(
            fullname = "t3_created123", id = "created123", community = "subshell",
            author = "crossposter", title = "Shared original", text = "",
            createdUtcEpochSeconds = 1L, permalink = "/r/subshell/comments/created123/",
            destinationUrl = null, kind = RedditSubmitReconciliationKind.TEXT,
            flairText = null, nsfw = false, spoiler = false,
        )
        for (kind in RedditSubmitReconciliationKind.entries) {
            assertNull(selectRedditSubmitReconciliationCandidate(
                request = request, username = "crossposter", dispatchedAtEpochMs = 1_000L,
                candidates = listOf(candidate.copy(kind = kind)),
            ))
        }
    }
}
