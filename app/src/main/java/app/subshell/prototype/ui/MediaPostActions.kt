package app.subshell.prototype.ui

import java.io.IOException
import app.subshell.prototype.media.resolvedRemoteVideoUri
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.media.findActivity
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.components.mediaResource
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal data class PostMediaActionTarget(
    val post: Post,
    val galleryItem: GalleryItem? = null,
)

// LOWUI6_MEDIA_ACTIONS_OUTLIVE_THE_SCREEN_V1 (OCB-160/227): a share or save
// keeps running when its row or the viewer leaves the screen (the sheet closes
// before a share is staged), instead of being cancelled without a word.
internal val mediaActionScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

private data class ShareableMedia(
    val uri: Uri,
    val mimeType: String,
    val extension: String,
    /** The copy in cacheDir/shared_media, when the media was staged there. */
    val stagedFile: File? = null,
)

// LOWUI5_STAGED_MEDIA_IS_CLEANED_UP_V1 (OCB-085): a copy staged for "Save" is
// deleted once it is in Downloads; one staged for a share lives on only for
// the share sheet and is pruned at the next staging after an hour.
private const val SHARED_MEDIA_KEEP_MS = 60L * 60L * 1000L

private fun stagingOperationId(): String = java.util.UUID.randomUUID().toString().take(8)

private fun sharedMediaDirectory(context: Context): File {
    val directory = File(context.cacheDir, "shared_media")
    val cutoff = System.currentTimeMillis() - SHARED_MEDIA_KEEP_MS
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < cutoff) file.delete()
    }
    return directory
}

internal suspend fun sharePostMedia(
    context: Context,
    target: PostMediaActionTarget,
): Boolean {
    val media = stageShareableMedia(context, target) ?: return false
    return sendShareableMedia(context, media)
}

private fun sendShareableMedia(context: Context, media: ShareableMedia): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = media.mimeType
        putExtra(Intent.EXTRA_STREAM, media.uri)
        clipData = ClipData.newUri(context.contentResolver, "subshell media", media.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(send, "Share media"))
        true
    }.getOrDefault(false)
}

// ISSUE35_MEDIA_DOWNLOAD_V1: a whole gallery is saved item by item through
// downloadPostMedia; progress is reported after each item and one failed item
// does not stop the rest.
internal data class GalleryDownloadResult(
    val savedPaths: List<String>,
    val failed: Int,
)

internal suspend fun downloadGalleryItems(
    items: List<GalleryItem>,
    download: suspend (GalleryItem) -> String?,
    onProgress: (done: Int, total: Int) -> Unit,
): GalleryDownloadResult {
    val saved = mutableListOf<String>()
    var failed = 0
    onProgress(0, items.size)
    items.forEachIndexed { index, item ->
        currentCoroutineContext().ensureActive()
        val path = download(item)
        if (path != null) saved += path else failed += 1
        onProgress(index + 1, items.size)
    }
    return GalleryDownloadResult(savedPaths = saved, failed = failed)
}

/** The folder a saved file went to, for "Saved to …". */
internal fun mediaDownloadFolder(path: String): String = path.substringBeforeLast('/', path)

internal suspend fun downloadPostMedia(
    context: Context,
    target: PostMediaActionTarget,
): String? {
    val media = stageShareableMedia(context, target) ?: return null
    return saveStagedMediaToDownloads(context, media, mediaDownloadDisplayName(target, media.extension))
        .also { media.stagedFile?.delete() }
}

/**
 * ISSUE35_COMMENT_MEDIA_ACTIONS_V1: remote media without a post (a comment's
 * image or GIF) is shared and saved through the same staging and Downloads
 * path, named after [stableName].
 */
internal suspend fun shareRemoteMedia(
    context: Context,
    uri: String,
    format: MediaFormat,
    stableName: String,
): Boolean {
    val media = stageRemoteMedia(context, uri, format, PostKind.IMAGE, stableName) ?: return false
    return sendShareableMedia(context, media)
}

internal suspend fun downloadRemoteMedia(
    context: Context,
    uri: String,
    format: MediaFormat,
    stableName: String,
): String? {
    val media = stageRemoteMedia(context, uri, format, PostKind.IMAGE, stableName) ?: return null
    return saveStagedMediaToDownloads(
        context,
        media,
        "subshell-${safeFileStem(stableName)}.${media.extension}",
    ).also { media.stagedFile?.delete() }
}

private suspend fun saveStagedMediaToDownloads(
    context: Context,
    media: ShareableMedia,
    displayName: String,
): String? {
    return withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/subshell",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            // LOWUI1_MEDIASTORE_INSERT_FAILURE_IS_NOT_A_CRASH_V1 (OCB-083): MediaProvider
            // can refuse the insert (no unique name left, storage unavailable).
            val destination = nullUnlessCancelled {
                resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                )
            } ?: return@withContext null
            try {
                val written = resolver.openOutputStream(destination, "w")?.use { output ->
                    context.contentResolver.openInputStream(media.uri)?.use { input ->
                        input.copyTo(output)
                        true
                    }
                } == true
                if (!written) {
                    resolver.delete(destination, null, null)
                    return@withContext null
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(destination, values, null, null)
                "Downloads/subshell/$displayName"
            } catch (cancelled: CancellationException) {
                resolver.delete(destination, null, null)
                throw cancelled
            } catch (_: Exception) {
                resolver.delete(destination, null, null)
                null
            }
        } else {
            saveStagedMediaBeforeQ(context, media, displayName)
        }
    }
}

// LOWUI_LEGACY_DOWNLOADS_ASK_FOR_STORAGE_V1 (OCB-084): with the storage grant
// the file goes to the public Downloads/subshell folder and is scanned into
// the gallery; without it, to Subshell's own folder, and the path says so.
// A copy that fails or is cancelled leaves no partial file.
private suspend fun saveStagedMediaBeforeQ(
    context: Context,
    media: ShareableMedia,
    displayName: String,
): String? {
    val requester = context.findActivity() as? LegacyStorageWritePermissionRequester
    val granted = requester != null &&
        withContext(Dispatchers.Main) { requester.requestLegacyStorageWrite() }
    @Suppress("DEPRECATION")
    val publicDirectory = if (granted) {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "subshell")
            .takeIf { it.isDirectory || it.mkdirs() }
    } else {
        null
    }
    val directory = publicDirectory
        ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: return null
    return withContext(Dispatchers.IO) {
        val targetFile = unusedDownloadFile(directory, displayName)
        val written = try {
            context.contentResolver.openInputStream(media.uri)?.use { input ->
                FileOutputStream(targetFile).use(input::copyTo)
                true
            } == true
        } catch (cancelled: CancellationException) {
            targetFile.delete()
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!written) {
            targetFile.delete()
            return@withContext null
        }
        if (publicDirectory == null) return@withContext targetFile.absolutePath
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(media.mimeType), null)
        "Downloads/subshell/${targetFile.name}"
    }
}

/** [displayName], or "name (n).ext" when a file of that name is already there. */
internal fun unusedDownloadFile(directory: File, displayName: String): File {
    val first = File(directory, displayName)
    if (!first.exists()) return first
    val stem = displayName.substringBeforeLast('.', displayName)
    val extension = displayName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    return (1..999).asSequence()
        .map { index -> File(directory, "$stem ($index)$extension") }
        .firstOrNull { !it.exists() }
        ?: first
}

private suspend fun stageShareableMedia(
    context: Context,
    target: PostMediaActionTarget,
): ShareableMedia? {
    val item = target.galleryItem
    item?.localMediaUri?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
        return ShareableMedia(
            uri = Uri.parse(value),
            mimeType = item.localMediaMimeType?.takeIf(String::isNotBlank)
                ?: mimeTypeFor(item.format),
            extension = extensionForMime(
                item.localMediaMimeType?.takeIf(String::isNotBlank)
                    ?: mimeTypeFor(item.format),
            ),
        )
    }

    target.post.localMediaUri
        ?.takeIf { item == null }
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { value ->
            val mime = target.post.localMediaMimeType?.takeIf(String::isNotBlank)
                ?: mimeTypeFor(target.post.kind)
            return ShareableMedia(
                uri = Uri.parse(value),
                mimeType = mime,
                extension = extensionForMime(mime),
            )
        }

    val remoteImageUri = item?.remoteImageUri
        ?: target.post.remoteImageUri?.takeIf {
            item == null && target.post.kind in setOf(PostKind.IMAGE, PostKind.GIF, PostKind.LINK)
        }
    if (!remoteImageUri.isNullOrBlank()) {
        return stageRemoteMedia(
            context = context,
            uri = remoteImageUri,
            format = item?.format,
            postKind = target.post.kind,
            stableName = item?.id ?: target.post.id,
        )
    }

    if (item == null && target.post.kind == PostKind.VIDEO) {
        val exported = exportRemoteVideoToMp4(context, target.post) ?: return null
        return shareableFile(
            context = context,
            file = exported,
            mimeType = MimeTypes.VIDEO_MP4,
            extension = "mp4",
        )
    }

    val resource = item?.asset?.let(::mediaResource)
        ?: target.post.mediaAsset?.let(::mediaResource)
    if (resource != null) {
        val bytes = withContext(Dispatchers.IO) {
            nullUnlessCancelled {
                context.resources.openRawResource(resource).use { it.readBytes() }
            }
        } ?: return null
        val format = item?.format
        val mime = detectImageMime(bytes, format, target.post.kind)
        return stageBytes(
            context = context,
            bytes = bytes,
            mimeType = mime,
            extension = extensionForMime(mime),
            stableName = item?.id ?: target.post.id,
        )
    }

    return null
}

// ISSUE35_MEDIA_STREAMING_V1: remote media is streamed to a cache file (no
// size cap, flat memory), typed from its leading bytes, then shared through
// the FileProvider or copied into Downloads.
private suspend fun stageRemoteMedia(
    context: Context,
    uri: String,
    format: MediaFormat?,
    postKind: PostKind,
    stableName: String,
): ShareableMedia? {
    val directory = sharedMediaDirectory(context)
    if (!directory.exists() && !directory.mkdirs()) return null
    val stem = safeFileStem(stableName)
    // LOWUI6_STAGING_NAMES_ARE_PER_OPERATION_V1 (OCB-294): a share and a save of
    // the same media at once no longer write, rename or delete the same file.
    val operation = stagingOperationId()
    val partial = File(directory, "$stem-$operation.partial")
    val leading = when (val result = RemoteImageLoader.downloadToFile(uri, partial)) {
        is RemoteMediaLoadResult.Ready -> result.value
        is RemoteMediaLoadResult.Failed -> return null
    }
    val mime = detectImageMime(leading, format, postKind)
    val extension = extensionForMime(mime)
    val file = File(directory, "$stem-$operation.$extension")
    file.delete()
    if (!partial.renameTo(file)) {
        partial.delete()
        return null
    }
    return shareableFile(context, file, mime, extension)
}

private suspend fun stageBytes(
    context: Context,
    bytes: ByteArray,
    mimeType: String,
    extension: String,
    stableName: String,
): ShareableMedia? = withContext(Dispatchers.IO) {
    val directory = sharedMediaDirectory(context)
    if (!directory.exists() && !directory.mkdirs()) return@withContext null
    val file = File(directory, "${safeFileStem(stableName)}-${stagingOperationId()}.$extension")
    nullUnlessCancelled {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        ShareableMedia(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            ),
            mimeType = mimeType,
            extension = extension,
            stagedFile = file,
        )
    }
}

private fun shareableFile(
    context: Context,
    file: File,
    mimeType: String,
    extension: String,
): ShareableMedia? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        ShareableMedia(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            ),
            mimeType = mimeType,
            extension = extension,
            stagedFile = file,
        )
    }.getOrNull()
}

@AndroidxOptIn(UnstableApi::class)
private suspend fun exportRemoteVideoToMp4(
    context: Context,
    post: Post,
): File? {
    val postVideoUri = post.remoteVideoUri?.trim()?.takeIf(String::isNotEmpty) ?: return null
    // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: a RedGIFs clip is shared as its MP4.
    val sourceUri = withContext(Dispatchers.IO) {
        try {
            resolvedRemoteVideoUri(postVideoUri)
        } catch (_: IOException) {
            null
        }
    } ?: return null
    val outputDirectory = sharedMediaDirectory(context)
    if (!outputDirectory.exists() && !outputDirectory.mkdirs()) return null
    val outputFile = File(outputDirectory, "${safeFileStem(post.id)}-export-${stagingOperationId()}.mp4")
    val inputMimeType = remoteVideoInputMimeType(sourceUri)

    return withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            outputFile.delete()
            lateinit var transformer: Transformer
            val listener = object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(outputFile.takeIf { it.isFile && it.length() > 0L })
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    outputFile.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            transformer = Transformer.Builder(context.applicationContext)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation {
                val cancel = Runnable {
                    runCatching(transformer::cancel)
                    outputFile.delete()
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    cancel.run()
                } else {
                    Handler(Looper.getMainLooper()).post(cancel)
                }
            }
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setMimeType(inputMimeType)
                .build()
            runCatching { transformer.start(mediaItem, outputFile.absolutePath) }
                .onFailure {
                    outputFile.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }
}

internal fun remoteVideoInputMimeType(sourceUri: String): String {
    val normalizedPath = sourceUri.substringBefore('?').lowercase(Locale.US)
    return when {
        normalizedPath.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
        normalizedPath.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        else -> MimeTypes.VIDEO_MP4
    }
}

// LOWUI_MEDIA_TYPE_FROM_ITS_BYTES_V1 (OCB-295): AVIF, HEIC, MP4 and BMP are
// recognised; bytes nothing here recognises are not called a JPEG.
internal fun detectImageMime(
    bytes: ByteArray,
    format: MediaFormat?,
    postKind: PostKind,
): String = when {
    bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF8") -> "image/gif"
    bytes.size >= 8 && bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
    bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
    bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" ->
        isoMediaMime(String(bytes, 8, 4, Charsets.US_ASCII))
    bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "image/bmp"
    format == MediaFormat.GIF || postKind == PostKind.GIF -> "image/gif"
    else -> UNKNOWN_MEDIA_MIME
}

private const val UNKNOWN_MEDIA_MIME = "application/octet-stream"

/** The major brand of an ISO base media file (AVIF, HEIF/HEIC, MP4). */
private fun isoMediaMime(brand: String): String = when (brand) {
    "avif", "avis" -> "image/avif"
    "heic", "heix", "heim", "heis" -> "image/heic"
    "mif1", "msf1" -> "image/heif"
    else -> "video/mp4"
}

private fun mimeTypeFor(format: MediaFormat): String = when (format) {
    MediaFormat.GIF -> "image/gif"
    MediaFormat.IMAGE -> "image/*"
    MediaFormat.VIDEO -> "video/*"
}

private fun mimeTypeFor(kind: PostKind): String = when (kind) {
    PostKind.GIF -> "image/gif"
    PostKind.VIDEO -> "video/mp4"
    else -> "image/*"
}

internal fun extensionForMime(mimeType: String): String = when (mimeType.lowercase(Locale.US)) {
    "image/gif" -> "gif"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/avif" -> "avif"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/bmp" -> "bmp"
    "video/mp4" -> "mp4"
    UNKNOWN_MEDIA_MIME -> "bin"
    else -> "jpg"
}

private fun mediaDownloadDisplayName(
    target: PostMediaActionTarget,
    extension: String,
): String {
    val suffix = target.galleryItem?.id ?: target.post.id
    return "subshell-${safeFileStem(suffix)}.$extension"
}

private fun safeFileStem(value: String): String = value
    .lowercase(Locale.US)
    .map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '-'
    }
    .joinToString("")
    .trim('-')
    .take(72)
    .ifBlank { "media" }

/**
 * Like runCatching(...).getOrNull() for I/O in a coroutine, but a
 * CancellationException is rethrown, never folded into a null result.
 */
internal inline fun <T> nullUnlessCancelled(block: () -> T?): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
