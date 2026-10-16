package app.subshell.prototype.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.subshell.prototype.share.sharedMediaDirectory
import app.subshell.prototype.ui.ComposerMediaAttachment
import app.subshell.prototype.ui.ComposerMediaKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ViewportErrorHostState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ISSUE90_SENDABLE_IMAGE_V1: Reddit takes JPEG, PNG, WebP and GIF images. A
// picked HEIC/HEIF/AVIF photo (or any other image type) used to be attached
// and then silently refused on Send. It is converted to JPEG in app storage
// (the folder shared and pasted images use, pruned the same way); an image
// that cannot be read is refused with a message instead.

private val REDDIT_SENDABLE_IMAGE_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/gif",
)

/** An image with a definite type Reddit does not take (the wildcard type is left alone). */
internal fun imageNeedsJpegConversion(mimeType: String?): Boolean {
    val type = mimeType?.trim()?.lowercase() ?: return false
    if (!type.startsWith("image/") || type == "image/*") return false
    return type !in REDDIT_SENDABLE_IMAGE_TYPES
}

/** Decodes [uri] and stores it as a JPEG in app storage; null when it cannot be read. */
internal fun convertImageToJpeg(context: Context, uri: Uri): ComposerMediaAttachment? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val directory = sharedMediaDirectory(context).apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.jpg")
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > SENDABLE_IMAGE_MAX_SIDE_PX) {
                val scale = SENDABLE_IMAGE_MAX_SIDE_PX.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        try {
            target.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, SENDABLE_IMAGE_JPEG_QUALITY, output))
            }
        } finally {
            bitmap.recycle()
        }
        ComposerMediaAttachment(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString(),
            mimeType = "image/jpeg",
            kind = ComposerMediaKind.IMAGE,
        )
    } catch (failure: Throwable) {
        // OutOfMemoryError included: a huge image is refused, not a crash.
        target.delete()
        null
    }
}

/**
 * The attachment as it can be sent: unchanged, converted to JPEG (off the
 * main thread), or null when it is an image Subshell cannot convert.
 */
internal suspend fun sendableImageAttachment(
    context: Context,
    attachment: ComposerMediaAttachment,
): ComposerMediaAttachment? {
    if (!imageNeedsJpegConversion(attachment.mimeType)) return attachment
    return withContext(Dispatchers.IO) {
        convertImageToJpeg(context, Uri.parse(attachment.uri))
    }?.copy(sourceLinkUri = attachment.sourceLinkUri)
}

internal const val IMAGE_NOT_SENDABLE_NOTICE_SCOPE = "image-not-sendable"

internal val imageNotSendablePresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Can't use this image",
    message = "Choose a JPEG, PNG, WebP or GIF image",
)

internal fun ViewportErrorHostState.presentImageNotSendable() {
    // clear() first forgets an earlier dismissal of the same notice.
    clear(IMAGE_NOT_SENDABLE_NOTICE_SCOPE)
    present(
        scopeKey = IMAGE_NOT_SENDABLE_NOTICE_SCOPE,
        presentation = imageNotSendablePresentation,
        failureDetails = null,
        onRetry = null,
    )
}

private const val SENDABLE_IMAGE_MAX_SIDE_PX = 4096
private const val SENDABLE_IMAGE_JPEG_QUALITY = 90
