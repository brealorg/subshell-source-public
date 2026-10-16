package app.subshell.prototype.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.components.convertImageToJpeg
import app.subshell.prototype.ui.components.imageNeedsJpegConversion
import app.subshell.prototype.ui.screens.COMMENT_REQUEST_UNBUILDABLE_MESSAGE
import app.subshell.prototype.ui.screens.CommentComposerScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ISSUE90_SENDABLE_IMAGE_V1: images Reddit does not take (HEIC/HEIF/AVIF ...)
 * are converted to JPEG; a comment that still cannot be built says so on Send.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Issue90SendableImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyDefiniteImageTypesRedditDoesNotTakeAreConverted() {
        listOf("image/heic", "image/heif", "image/avif", "IMAGE/HEIC ", "image/bmp").forEach {
            assertTrue(it, imageNeedsJpegConversion(it))
        }
        listOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/*", "video/mp4", null)
            .forEach { assertFalse("$it", imageNeedsJpegConversion(it)) }
    }

    @Test
    fun aReadableImageBecomesAJpegInAppStorageNoLargerThan4096() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val source = File(context.cacheDir, "wide.png")
        val bitmap = Bitmap.createBitmap(5000, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val converted = requireNotNull(convertImageToJpeg(context, Uri.fromFile(source)))

        assertEquals("image/jpeg", converted.mimeType)
        assertEquals(ComposerMediaKind.IMAGE, converted.kind)
        val stored = File(context.filesDir, "shared_into_subshell/${Uri.parse(converted.uri).lastPathSegment}")
        val bytes = stored.readBytes()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals(4096, bounds.outWidth)
    }

    @Test
    fun anUnreadableImageIsRefused() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val source = File(context.cacheDir, "broken.heic").apply { writeText("not an image") }

        assertEquals(null, convertImageToJpeg(context, Uri.fromFile(source)))
    }

    @Test
    fun sendWithAReplyThatCannotBeBuiltSaysSoInsteadOfDoingNothing() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        // A Reddit post id, so the comment has a real parent.
        val post = SampleData.posts.first().copy(id = "t3_abc123")
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.openCommentComposer(post)
        // An attachment kept from before this fix (a pasted HEIC kept as is).
        state.updateCommentComposerDraft(
            requireNotNull(state.commentComposerDraft).copy(
                media = ComposerMediaAttachment("content://media/image/1", "image/heic", ComposerMediaKind.IMAGE),
            ),
        )
        var submitted = 0
        composeRule.setContent {
            SubshellTheme {
                CommentComposerScreen(
                    post = post,
                    state = state,
                    onClose = {},
                    onSubmitComment = { submitted += 1 },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("Post comment")).performClick()
        composeRule.waitForIdle()

        assertEquals(0, submitted)
        assertEquals(
            1,
            composeRule.onAllNodesWithText(COMMENT_REQUEST_UNBUILDABLE_MESSAGE).fetchSemanticsNodes().size,
        )
    }
}
