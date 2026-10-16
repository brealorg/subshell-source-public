package app.subshell.prototype.ui

import android.os.Build
import app.subshell.prototype.data.account.CommentDeleteFailureKind
import app.subshell.prototype.data.account.CommentDeletePhase
import app.subshell.prototype.data.account.CommentDeleteUiState
import app.subshell.prototype.data.account.ContentSafetyFailureKind
import app.subshell.prototype.data.account.ContentSafetyOperation
import app.subshell.prototype.data.account.ContentSafetyPhase
import app.subshell.prototype.data.account.ContentSafetyUiState
import app.subshell.prototype.data.account.SubmittedTextPost
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.error.commentDeleteErrorPresentation
import app.subshell.prototype.ui.error.contentSafetyErrorPresentation
import app.subshell.prototype.ui.theme.DarkColors
import app.subshell.prototype.ui.theme.LightColors
import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UILOGIC3 (ui/ logic findings taken over from dev1): safety and delete copy
 * (OCB-170/172), warm surface containers (292), media type from its bytes
 * (295), downloads before Android 10 (084), crosspost flair match (293).
 */
class LowUiLogic3Test {
    private fun safetyMessage(
        operation: ContentSafetyOperation,
        kind: ContentSafetyFailureKind,
        blocked: Boolean? = null,
        hidden: Boolean? = null,
    ) = contentSafetyErrorPresentation(
        ContentSafetyUiState(
            phase = ContentSafetyPhase.FAILED,
            operation = operation,
            accountStableId = "account",
            thingFullname = "t2_user",
            blocked = blocked,
            hidden = hidden,
            failureKind = kind,
        ),
    ).message

    @Test
    fun aBlockOrHideFailureIsNotCalledAReport() {
        val block = safetyMessage(ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyFailureKind.API, blocked = true)
        assertEquals("Reddit rejected the block request.", block)
        val unknownBlock = safetyMessage(ContentSafetyOperation.SET_USER_BLOCKED, ContentSafetyFailureKind.OUTCOME_UNKNOWN, blocked = true)
        assertFalse(unknownBlock, unknownBlock.contains("report"))
        assertTrue(unknownBlock.contains("profile"))
        val unhide = safetyMessage(ContentSafetyOperation.SET_POST_HIDDEN, ContentSafetyFailureKind.OUTCOME_UNKNOWN, hidden = false)
        assertTrue(unhide, unhide.contains("unhide request"))
        assertEquals(
            "Reddit rejected the report.",
            safetyMessage(ContentSafetyOperation.REPORT_CONTENT, ContentSafetyFailureKind.API),
        )
    }

    @Test
    fun anUnconfirmedDeleteDoesNotClaimRedditAcceptedIt() {
        val presentation = commentDeleteErrorPresentation(
            CommentDeleteUiState(
                phase = CommentDeletePhase.FAILED,
                accountStableId = "reddit:u/test",
                postId = "post",
                commentFullname = "t1_abc",
                failureKind = CommentDeleteFailureKind.OUTCOME_NOT_CONFIRMED,
            ),
        )
        assertFalse(presentation.message.contains("accepted"))
        assertTrue(presentation.message.contains("may have received"))
    }

    @Test
    fun staticSchemesHaveWarmSurfaceContainers() {
        fun Color.isWarm() = red > blue
        listOf(LightColors, DarkColors).forEach { scheme ->
            listOf(
                scheme.surfaceDim,
                scheme.surfaceBright,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            ).forEach { tone -> assertTrue(tone.toString(), tone.isWarm()) }
        }
    }

    private fun bytes(text: String, prefix: ByteArray = ByteArray(0)) =
        prefix + text.toByteArray(Charsets.US_ASCII) + ByteArray(8)

    @Test
    fun mediaIsTypedFromItsBytesAndUnknownIsNotAJpeg() {
        val size = byteArrayOf(0, 0, 0, 0x20)
        assertEquals("image/avif", detectImageMime(bytes("ftypavif", size), MediaFormat.IMAGE, PostKind.IMAGE))
        assertEquals("image/heic", detectImageMime(bytes("ftypheic", size), MediaFormat.IMAGE, PostKind.IMAGE))
        assertEquals("video/mp4", detectImageMime(bytes("ftypisom", size), MediaFormat.GIF, PostKind.IMAGE))
        assertEquals("image/bmp", detectImageMime(bytes("BM"), MediaFormat.IMAGE, PostKind.IMAGE))
        val unknown = detectImageMime(bytes("????????????"), MediaFormat.IMAGE, PostKind.IMAGE)
        assertEquals("application/octet-stream", unknown)
        assertEquals("bin", extensionForMime(unknown))
        assertEquals("avif", extensionForMime("image/avif"))
    }

    @Test
    fun anExistingDownloadIsNotOverwritten() {
        val directory = Files.createTempDirectory("uilogic3").toFile()
        try {
            assertEquals("a.jpg", unusedDownloadFile(directory, "a.jpg").name)
            File(directory, "a.jpg").writeText("x")
            File(directory, "a (1).jpg").writeText("x")
            assertEquals("a (2).jpg", unusedDownloadFile(directory, "a.jpg").name)
        } finally {
            directory.deleteRecursively()
        }
        assertTrue(legacyStorageWritePermissionNeeded(Build.VERSION_CODES.P))
        assertFalse(legacyStorageWritePermissionNeeded(Build.VERSION_CODES.Q))
    }

    @Test
    fun aCrosspostIsMatchedWhenItsTemplateFlairHasSpaces() {
        val draft = crosspost01A1CDraft().copy(flair = ComposerFlair(templateId = "tpl", text = "Discussion "))
        val request = requireNotNull(draft.crosspostRequestForIdentityOnly())
        val submitted = SubmittedTextPost(
            fullname = "t3_created123", id = "created123", community = "target", title = draft.title,
            text = "", permalink = "/r/target/comments/created123/", crosspostFullname = "t3_source123",
            flairText = "Discussion",
        )

        assertNotNull(completedCrosspostForDraft(draft, "owner", true, "crossposter", request.requestKey, submitted))
    }
}
