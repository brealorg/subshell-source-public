package app.subshell.prototype.data.auth.reddit

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

internal sealed interface RedditMediaStageResult {
    data class Staged(
        val file: File,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val width: Int? = null,
        val height: Int? = null,
    ) : RedditMediaStageResult

    data object UnsupportedType : RedditMediaStageResult
    data object TooLarge : RedditMediaStageResult
    data object Empty : RedditMediaStageResult
    data object Unavailable : RedditMediaStageResult
}

internal fun interface RedditMediaUploadSource {
    suspend fun stage(uri: String, declaredMimeType: String): RedditMediaStageResult

    suspend fun stageGallery(uri: String, declaredMimeType: String): RedditMediaStageResult =
        stage(uri, declaredMimeType)

    suspend fun stageVideo(uri: String, declaredMimeType: String): RedditMediaStageResult =
        stage(uri, declaredMimeType)

    suspend fun stageVideoPoster(uri: String): RedditMediaStageResult =
        RedditMediaStageResult.UnsupportedType
}

internal class AndroidRedditMediaUploadSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RedditMediaUploadSource {
    private val appContext = context.applicationContext

    override suspend fun stage(
        uri: String,
        declaredMimeType: String,
    ): RedditMediaStageResult = stageInternal(uri, declaredMimeType)

    override suspend fun stageGallery(
        uri: String,
        declaredMimeType: String,
    ): RedditMediaStageResult = stageInternal(uri, declaredMimeType)

    override suspend fun stageVideo(
        uri: String,
        declaredMimeType: String,
    ): RedditMediaStageResult = stageVideoInternal(uri, declaredMimeType)

    override suspend fun stageVideoPoster(
        uri: String,
    ): RedditMediaStageResult = stageVideoPosterInternal(uri)

    // DEAD03_IMAGE_STAGING_ALWAYS_ALLOWS_GIF_V1 (OCB-503): both image paths take
    // GIFs (COMPOSER08A), so the static-only branch and its flag are gone.
    private suspend fun stageInternal(
        uri: String,
        declaredMimeType: String,
    ): RedditMediaStageResult = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri.trim()) }.getOrNull()
            ?: return@withContext RedditMediaStageResult.Unavailable
        if (!parsed.scheme.equals("content", ignoreCase = true)) {
            return@withContext RedditMediaStageResult.Unavailable
        }
        val resolver = appContext.contentResolver
        val resolvedMimeType = resolver.getType(parsed) ?: declaredMimeType
        val mimeType = redditGalleryImageMimeTypeOrNull(resolvedMimeType)
            ?: return@withContext RedditMediaStageResult.UnsupportedType
        val extension = when (mimeType) {
            REDDIT_IMAGE_MIME_JPEG -> "jpg"
            REDDIT_IMAGE_MIME_PNG -> "png"
            REDDIT_IMAGE_MIME_WEBP -> "webp"
            REDDIT_IMAGE_MIME_GIF -> "gif"
            else -> return@withContext RedditMediaStageResult.UnsupportedType
        }
        val stagingDirectory = stagingDirectory()
            ?: return@withContext RedditMediaStageResult.Unavailable
        val file = File(stagingDirectory, "subshell-${UUID.randomUUID()}.$extension")
        try {
            val input = resolver.openInputStream(parsed)
                ?: return@withContext RedditMediaStageResult.Unavailable
            var total = 0L
            input.use { source ->
                file.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = source.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > REDDIT_IMAGE_UPLOAD_MAX_BYTES) {
                            throw RedditMediaTooLargeException()
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            }
            if (total == 0L) {
                file.delete()
                return@withContext RedditMediaStageResult.Empty
            }
            RedditMediaStageResult.Staged(
                file = file,
                fileName = file.name,
                mimeType = mimeType,
                sizeBytes = total,
            )
        } catch (cancelled: CancellationException) {
            file.delete()
            throw cancelled
        } catch (_: RedditMediaTooLargeException) {
            file.delete()
            RedditMediaStageResult.TooLarge
        } catch (_: IOException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        } catch (_: SecurityException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        }
    }

    // LOW2_STAGING_CANCELS_AND_SWEEPS_V1 (OCB-504/522): staging copies stop
    // when their coroutine is cancelled and delete their file; staged files
    // left by a killed or cancelled upload (up to 1 GB of video) are removed
    // the next time media is staged.
    private fun stagingDirectory(): File? {
        val directory = File(appContext.cacheDir, REDDIT_MEDIA_STAGING_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return null
        pruneStaleStagedMediaFiles(directory, System.currentTimeMillis())
        return directory
    }

    private suspend fun stageVideoInternal(
        uri: String,
        declaredMimeType: String,
    ): RedditMediaStageResult = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri.trim()) }.getOrNull()
            ?: return@withContext RedditMediaStageResult.Unavailable
        if (!parsed.scheme.equals("content", ignoreCase = true)) {
            return@withContext RedditMediaStageResult.Unavailable
        }
        val resolver = appContext.contentResolver
        val resolvedMimeType = resolver.getType(parsed) ?: declaredMimeType
        val mimeType = redditVideoMimeTypeOrNull(resolvedMimeType)
            ?: return@withContext RedditMediaStageResult.UnsupportedType
        val extension = when (mimeType) {
            REDDIT_VIDEO_MIME_MP4 -> "mp4"
            REDDIT_VIDEO_MIME_QUICKTIME -> "mov"
            REDDIT_VIDEO_MIME_WEBM -> "webm"
            else -> return@withContext RedditMediaStageResult.UnsupportedType
        }
        val stagingDirectory = stagingDirectory()
            ?: return@withContext RedditMediaStageResult.Unavailable
        val file = File(stagingDirectory, "subshell-${UUID.randomUUID()}.$extension")
        try {
            val input = resolver.openInputStream(parsed)
                ?: return@withContext RedditMediaStageResult.Unavailable
            var total = 0L
            input.use { source ->
                file.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = source.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > SUBSHELL_VIDEO_STAGING_MAX_BYTES) {
                            throw RedditMediaTooLargeException()
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            }
            if (total == 0L) {
                file.delete()
                return@withContext RedditMediaStageResult.Empty
            }
            RedditMediaStageResult.Staged(
                file = file,
                fileName = file.name,
                mimeType = mimeType,
                sizeBytes = total,
            )
        } catch (cancelled: CancellationException) {
            file.delete()
            throw cancelled
        } catch (_: RedditMediaTooLargeException) {
            file.delete()
            RedditMediaStageResult.TooLarge
        } catch (_: IOException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        } catch (_: SecurityException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        }
    }

    private suspend fun stageVideoPosterInternal(
        uri: String,
    ): RedditMediaStageResult = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri.trim()) }.getOrNull()
            ?: return@withContext RedditMediaStageResult.Unavailable
        if (!parsed.scheme.equals("content", ignoreCase = true)) {
            return@withContext RedditMediaStageResult.Unavailable
        }
        val stagingDirectory = stagingDirectory()
            ?: return@withContext RedditMediaStageResult.Unavailable
        val file = File(stagingDirectory, "subshell-${UUID.randomUUID()}-poster.jpg")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, parsed)
            val frame = retriever.getFrameAtTime(
                -1L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: return@withContext RedditMediaStageResult.Unavailable
            val dimensions = redditVideoDisplayDimensionsOrNull(
                width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                )?.toIntOrNull(),
                height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                )?.toIntOrNull(),
                rotationDegrees = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
                )?.toIntOrNull(),
            ) ?: redditVideoDisplayDimensionsOrNull(
                width = frame.width,
                height = frame.height,
                rotationDegrees = 0,
            )
            val written = try {
                file.outputStream().buffered().use { sink ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 88, sink)
                }
            } finally {
                frame.recycle()
            }
            if (!written || file.length() <= 0L) {
                file.delete()
                return@withContext RedditMediaStageResult.Unavailable
            }
            RedditMediaStageResult.Staged(
                file = file,
                fileName = file.name,
                mimeType = REDDIT_IMAGE_MIME_JPEG,
                sizeBytes = file.length(),
                width = dimensions?.width,
                height = dimensions?.height,
            )
        } catch (cancelled: CancellationException) {
            file.delete()
            throw cancelled
        } catch (_: IOException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        } catch (_: SecurityException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        } catch (_: RuntimeException) {
            file.delete()
            RedditMediaStageResult.Unavailable
        } finally {
            runCatching { retriever.release() }
        }
    }
}

internal fun redditStaticImageMimeTypeOrNull(value: String?): String? = when (
    value?.trim()?.lowercase()
) {
    REDDIT_IMAGE_MIME_JPEG,
    "image/jpg",
    -> REDDIT_IMAGE_MIME_JPEG

    REDDIT_IMAGE_MIME_PNG -> REDDIT_IMAGE_MIME_PNG
    REDDIT_IMAGE_MIME_WEBP -> REDDIT_IMAGE_MIME_WEBP
    else -> null
}

internal fun redditGalleryImageMimeTypeOrNull(value: String?): String? =
    redditStaticImageMimeTypeOrNull(value) ?: when (value?.trim()?.lowercase()) {
        REDDIT_IMAGE_MIME_GIF -> REDDIT_IMAGE_MIME_GIF
        else -> null
    }

internal fun redditVideoMimeTypeOrNull(value: String?): String? = when (
    value?.trim()?.lowercase()
) {
    REDDIT_VIDEO_MIME_MP4 -> REDDIT_VIDEO_MIME_MP4
    REDDIT_VIDEO_MIME_QUICKTIME -> REDDIT_VIDEO_MIME_QUICKTIME
    REDDIT_VIDEO_MIME_WEBM -> REDDIT_VIDEO_MIME_WEBM
    else -> null
}


internal data class RedditVideoDimensions(
    val width: Int,
    val height: Int,
)

internal fun redditVideoDisplayDimensionsOrNull(
    width: Int?,
    height: Int?,
    rotationDegrees: Int?,
): RedditVideoDimensions? {
    val validWidth = width?.takeIf { it > 0 } ?: return null
    val validHeight = height?.takeIf { it > 0 } ?: return null
    val rotation = (((rotationDegrees ?: 0) % 360) + 360) % 360

    return if (rotation == 90 || rotation == 270) {
        RedditVideoDimensions(
            width = validHeight,
            height = validWidth,
        )
    } else {
        RedditVideoDimensions(
            width = validWidth,
            height = validHeight,
        )
    }
}

private class RedditMediaTooLargeException : IOException()

internal const val REDDIT_IMAGE_UPLOAD_MAX_BYTES = 20L * 1024L * 1024L
internal const val SUBSHELL_VIDEO_STAGING_MAX_BYTES = 1_000_000_000L
internal const val REDDIT_IMAGE_MIME_JPEG = "image/jpeg"
internal const val REDDIT_IMAGE_MIME_PNG = "image/png"
internal const val REDDIT_IMAGE_MIME_WEBP = "image/webp"
internal const val REDDIT_IMAGE_MIME_GIF = "image/gif"
internal const val REDDIT_VIDEO_MIME_MP4 = "video/mp4"
internal const val REDDIT_VIDEO_MIME_QUICKTIME = "video/quicktime"
internal const val REDDIT_VIDEO_MIME_WEBM = "video/webm"
private const val REDDIT_MEDIA_STAGING_DIRECTORY = "reddit-media-upload"
private const val STALE_STAGED_FILE_MILLIS = 6L * 60L * 60L * 1000L

/** Deletes staged files older than six hours (left by a killed or cancelled upload). */
internal fun pruneStaleStagedMediaFiles(directory: File, nowMs: Long) {
    val staleBefore = nowMs - STALE_STAGED_FILE_MILLIS
    directory.listFiles()?.forEach { staged ->
        if (staged.isFile && staged.lastModified() < staleBefore) staged.delete()
    }
}
