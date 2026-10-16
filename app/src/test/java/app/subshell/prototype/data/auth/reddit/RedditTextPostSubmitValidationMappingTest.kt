package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.network.reddit.RedditSingleMediaSubmitKind

import app.subshell.prototype.data.network.reddit.RedditTextPostApiError
import app.subshell.prototype.data.network.reddit.RedditTextPostApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditTextPostSubmitValidationMappingTest {
    @Test
    fun flairRequiredMapsToActionableFieldError() {
        val issue = RedditTextPostApiError(
            kind = RedditTextPostApiErrorKind.VALIDATION,
            code = "SUBMIT_VALIDATION_FLAIR_REQUIRED",
            field = "flair",
        ).toAuthenticatedValidationIssue()

        requireNotNull(issue)
        assertEquals(AuthenticatedTextPostValidationField.FLAIR, issue.field)
        assertEquals("Post flair is required", issue.message)
    }


    @Test
    fun badImageWithRedditUrlFieldTargetsMediaInsteadOfLink() {
        val issue = RedditTextPostApiError(
            kind = RedditTextPostApiErrorKind.VALIDATION,
            code = "BAD_IMAGE",
            field = "url",
        ).toAuthenticatedValidationIssue()

        requireNotNull(issue)
        assertEquals(AuthenticatedTextPostValidationField.MEDIA, issue.field)
        assertEquals("Reddit couldn't process this image. Choose another image", issue.message)
    }

    @Test
    fun galleryValidationTargetsMediaCard() {
        val issue = RedditTextPostApiError(
            kind = RedditTextPostApiErrorKind.VALIDATION,
            code = "GALLERY_NOT_ALLOWED",
            field = "items",
        ).toAuthenticatedValidationIssue()

        requireNotNull(issue)
        assertEquals(AuthenticatedTextPostValidationField.MEDIA, issue.field)
        assertEquals(
            "The media doesn't meet this community's posting requirements",
            issue.message,
        )
    }

    @Test
    fun unknownProviderValidationStillTargetsTheReportedField() {
        val issue = RedditTextPostApiError(
            kind = RedditTextPostApiErrorKind.VALIDATION,
            code = "SUBMIT_VALIDATION_FUTURE_TITLE_RULE",
            field = "title",
        ).toAuthenticatedValidationIssue()

        requireNotNull(issue)
        assertEquals(AuthenticatedTextPostValidationField.TITLE, issue.field)
        assertEquals(
            "The title doesn't meet this community's posting requirements",
            issue.message,
        )
        assertNull(
            RedditTextPostApiError(
                kind = RedditTextPostApiErrorKind.PERMISSION,
                code = "FORBIDDEN",
                field = "title",
            ).toAuthenticatedValidationIssue(),
        )
    }
    @Test
    fun videoSubmitApiFailureTargetsMediaWithSanitizedDiagnostics() {
        val issue = RedditTextPostApiError(
            kind = RedditTextPostApiErrorKind.UNKNOWN,
            code = "BAD_VIDEO",
            field = "video_poster_url",
        ).toVideoSubmitValidationIssue()

        assertEquals(AuthenticatedTextPostValidationField.MEDIA, issue.field)
        assertEquals(
            "Reddit rejected this video (code: BAD_VIDEO, field: video_poster_url).",
            issue.message,
        )
    }

    @Test
    fun singleMediaSubmitKindDistinguishesVideoGifFromNormalVideo() {
        assertEquals(
            "video",
            redditSingleMediaSubmitKind(
                mediaMimeType = "video/mp4",
                mediaAsGif = false,
            ).apiValue,
        )
        assertEquals(
            "videogif",
            redditSingleMediaSubmitKind(
                mediaMimeType = "video/mp4",
                mediaAsGif = true,
            ).apiValue,
        )
        assertEquals(
            "image",
            redditSingleMediaSubmitKind(
                mediaMimeType = "image/jpeg",
                mediaAsGif = false,
            ).apiValue,
        )
    }

    @Test
    fun singleImageGifUsesImageSubmitKindNotVideogif() {
        assertEquals(
            RedditSingleMediaSubmitKind.IMAGE,
            redditSingleMediaSubmitKind(
                mediaMimeType = "image/gif",
                mediaAsGif = false,
            ),
        )
    }

}
