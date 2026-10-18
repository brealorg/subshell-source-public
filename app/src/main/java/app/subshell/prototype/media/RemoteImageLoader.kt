package app.subshell.prototype.media

import app.subshell.prototype.data.network.toNetworkTransportFailureKind
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.error.LocalFailureKind
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellNetworkTransport
import app.subshell.prototype.data.network.isRetryableHttpStatus
import app.subshell.prototype.data.network.isRetryableTransportFailure
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

internal data class RemoteAnimatedImage(
    val encodedBytes: ByteArray,
    val drawable: Drawable,
    val width: Int,
    val height: Int,
)

internal sealed interface RemoteMediaLoadResult<out T> {
    data class Ready<T>(val value: T) : RemoteMediaLoadResult<T>
    data class Failed(val details: FailureDetails) : RemoteMediaLoadResult<Nothing>
}

internal const val REMOTE_IMAGE_MAX_TRUSTED_REDIRECTS = 1

/**
 * ISSUE228_HTTP_IMAGES_ARE_TRIED_OVER_HTTPS_V1 (owner, #228): an image linked
 * over plain http (old posts and comments) is asked for at the same address
 * over https; most hosts serve both. A link with credentials stays refused.
 * Redirects keep the https-only rule.
 */
internal fun remoteImageRequestUrl(uri: String): HttpUrl? {
    val parsed = uri.toHttpUrlOrNull() ?: return null
    return if (parsed.scheme == "http" && parsed.username.isEmpty() && parsed.password.isEmpty()) {
        parsed.newBuilder().scheme("https").build()
    } else {
        parsed
    }
}

internal fun isSupportedRemoteImageRedirectCode(code: Int): Boolean =
    code == 301 ||
        code == 302 ||
        code == 303 ||
        code == 307 ||
        code == 308

private fun isRedditMatrixMediaPath(encodedPath: String): Boolean =
    encodedPath.startsWith("/_matrix/media/", ignoreCase = true) ||
        encodedPath.startsWith("/_matrix/client/v1/media/", ignoreCase = true)

internal fun resolveTrustedRedditMatrixMediaRedirect(
    source: HttpUrl,
    location: String?,
): HttpUrl? {
    if (!source.isHttps ||
        !source.host.equals("matrix.redditspace.com", ignoreCase = true) ||
        !isRedditMatrixMediaPath(source.encodedPath)
    ) {
        return null
    }
    val target = location
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(source::resolve)
        ?: return null
    val targetIsMatrixMedia =
        target.host.equals("matrix.redditspace.com", ignoreCase = true) &&
            isRedditMatrixMediaPath(target.encodedPath)
    val targetIsRedditImageCdn =
        target.host.lowercase() in setOf(
            "i.redd.it",
            "preview.redd.it",
            "external-preview.redd.it",
            "reddit-uploaded-media.s3-accelerate.amazonaws.com",
        ) &&
            target.encodedPath.isNotBlank() &&
            target.encodedPath != "/"
    if (!target.isHttps ||
        target.username.isNotEmpty() ||
        target.password.isNotEmpty() ||
        (!targetIsMatrixMedia && !targetIsRedditImageCdn) ||
        target.queryParameterNames.any { name ->
            name.equals("access_token", ignoreCase = true)
        }
    ) {
        return null
    }
    return target
}

internal fun isAllowedRemoteImageContentType(
    host: String,
    encodedPath: String,
    type: String?,
    subtype: String?,
): Boolean {
    if (type.equals("image", ignoreCase = true)) return true
    return host.equals("matrix.redditspace.com", ignoreCase = true) &&
        isRedditMatrixMediaPath(encodedPath) &&
        type.equals("application", ignoreCase = true) &&
        subtype.equals("octet-stream", ignoreCase = true)
}

/** MEDIAGONE01: a 404/410 source no longer exists (removed post, deleted upload). */
internal fun FailureDetails.isRemoteMediaGone(): Boolean = httpStatusCode == 404 || httpStatusCode == 410

/**
 * MEDIAGONE01_MEDIA_SCOPE_NAMES_THE_SOURCE_V1: the diagnostic scope of an
 * image or GIF failure names the kind of source (no address), so a report
 * tells i.redd.it, previews, imgur and other hosts apart.
 */
internal fun remoteMediaDiagnosticScope(kind: String, uri: String?): String {
    val host = remoteImageRequestUrl(uri.orEmpty())?.host?.lowercase().orEmpty()
    val source = when {
        host == "i.redd.it" -> "i_redd_it"
        host == "preview.redd.it" -> "preview_redd_it"
        host == "external-preview.redd.it" -> "external_preview"
        host.endsWith("redditmedia.com") || host.endsWith("redditstatic.com") -> "redditmedia"
        host == "imgur.com" || host.endsWith(".imgur.com") -> "imgur"
        host.endsWith("giphy.com") -> "giphy"
        else -> "other"
    }
    return "${kind}_$source"
}

internal object RemoteImageLoader {
    private val bitmapCache = object : LruCache<String, Bitmap>(REMOTE_IMAGE_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1_024).coerceAtLeast(1)
    }

    private val goneCache = LruCache<String, FailureDetails>(REMOTE_GONE_CACHE_ENTRIES)
    private val encodedCache = object : LruCache<String, ByteArray>(REMOTE_IMAGE_ENCODED_CACHE_KB) {
        override fun sizeOf(key: String, value: ByteArray): Int =
            (value.size / 1_024).coerceAtLeast(1)
    }

    // STEP3_IMAGE_LOADER_CALL_FACTORY_IS_A_PARAMETER_V1: the network is a
    // parameter (default: the shared media read client) so a test can answer
    // the request; every caller uses the default.
    suspend fun load(
        uri: String,
        callFactory: Call.Factory = SubshellNetworkTransport.mediaReadCallFactory,
    ): RemoteMediaLoadResult<Bitmap> {
        bitmapCache.get(uri)?.let { return RemoteMediaLoadResult.Ready(it) }
        val bytes = when (val result = loadEncodedBytes(uri, callFactory)) {
            is RemoteMediaLoadResult.Ready -> result.value
            is RemoteMediaLoadResult.Failed -> return result
        }
        val bitmap = try {
            withContext(Dispatchers.Default) { decodeBoundedBitmap(bytes) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            // MEDIA02: an image too large for the heap fails to show; it must
            // not take the app down.
            null
        } ?: return RemoteMediaLoadResult.Failed(
            FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.DECODING,
            ),
        )
        bitmapCache.put(uri, bitmap)
        return RemoteMediaLoadResult.Ready(bitmap)
    }

    /**
     * ISSUE35_MEDIA_STREAMING_V1: streams remote media into [file] for sharing
     * and saving, with the same HTTPS, trusted-redirect and content-type rules
     * as display loading but no size cap and nothing held in memory. Ready
     * carries the leading bytes of the file, for type detection.
     */
    internal suspend fun downloadToFile(uri: String, file: File): RemoteMediaLoadResult<ByteArray> {
        val parsed = remoteImageRequestUrl(uri)
            ?: return RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.VALIDATION,
                ),
            )
        if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.SECURITY,
                ),
            )
        }
        val result = try {
            withContext(Dispatchers.IO) {
                loadEncodedBytesFromNetwork(parsed, sink = file)
            }
        } catch (cancelled: CancellationException) {
            file.delete()
            throw cancelled
        } catch (error: IOException) {
            RemoteMediaLoadResult.Failed(error.toFailureDetails())
        }
        if (result is RemoteMediaLoadResult.Failed) file.delete()
        return result
    }

    suspend fun loadAnimated(
        uri: String,
        callFactory: Call.Factory = SubshellNetworkTransport.mediaReadCallFactory,
    ): RemoteMediaLoadResult<RemoteAnimatedImage> {
        // MEDIAGONE01_LARGE_GIFS_UP_TO_100_MB_V1 (owner 2026-10-18): a report
        // showed a GIF over the 40 MB image limit; GIFs may be 100 MB. The bytes
        // are held in memory; an out-of-memory decode still fails quietly.
        val bytes = when (val result = loadEncodedBytes(uri, callFactory, REMOTE_GIF_MAX_BYTES)) {
            is RemoteMediaLoadResult.Ready -> result.value
            is RemoteMediaLoadResult.Failed -> return result
        }
        val drawable = try {
            withContext(Dispatchers.Default) {
                decodeRemoteDrawable(bytes)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return RemoteMediaLoadResult.Failed(
            FailureDetails(
                retryable = false,
                localKind = LocalFailureKind.DECODING,
            ),
        )
        return RemoteMediaLoadResult.Ready(
            RemoteAnimatedImage(
                encodedBytes = bytes,
                drawable = drawable,
                width = drawable.intrinsicWidth.coerceAtLeast(1),
                height = drawable.intrinsicHeight.coerceAtLeast(1),
            ),
        )
    }

    private suspend fun loadEncodedBytes(
        uri: String,
        callFactory: Call.Factory = SubshellNetworkTransport.mediaReadCallFactory,
        maxBytes: Long = REMOTE_IMAGE_MAX_BYTES,
    ): RemoteMediaLoadResult<ByteArray> {
        encodedCache.get(uri)?.let { return RemoteMediaLoadResult.Ready(it) }
        goneCache.get(uri)?.let { return RemoteMediaLoadResult.Failed(it) }
        val parsed = remoteImageRequestUrl(uri)
            ?: return RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.VALIDATION,
                ),
            )
        if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.SECURITY,
                ),
            )
        }

        val result = try {
            withContext(Dispatchers.IO) {
                loadEncodedBytesFromNetwork(parsed, callFactory = callFactory, maxBytes = maxBytes)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            RemoteMediaLoadResult.Failed(error.toFailureDetails())
        } catch (_: OutOfMemoryError) {
            RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    localKind = LocalFailureKind.DECODING,
                ),
            )
        }

        if (result is RemoteMediaLoadResult.Ready && shouldCacheEncodedImage(result.value.size)) {
            encodedCache.put(uri, result.value)
        }
        // MEDIAGONE01_A_GONE_SOURCE_IS_NOT_FETCHED_AGAIN_V1: a 404/410 source is
        // remembered for the session, so scrolling back does not fetch (and
        // report) the dead link again.
        if (result is RemoteMediaLoadResult.Failed && result.details.isRemoteMediaGone()) {
            goneCache.put(uri, result.details)
        }
        return result
    }

    private sealed interface RemoteImageHttpOutcome {
        data class Redirect(
            val target: HttpUrl,
            val statusCode: Int,
        ) : RemoteImageHttpOutcome

        data class Terminal(
            val result: RemoteMediaLoadResult<ByteArray>,
        ) : RemoteImageHttpOutcome
    }

    private suspend fun loadEncodedBytesFromNetwork(
        initialUrl: HttpUrl,
        sink: File? = null,
        callFactory: Call.Factory = SubshellNetworkTransport.mediaReadCallFactory,
        maxBytes: Long = REMOTE_IMAGE_MAX_BYTES,
    ): RemoteMediaLoadResult<ByteArray> {
        var currentUrl = initialUrl
        var followedRedirects = 0
        while (true) {
            when (val outcome = executeRemoteImageRequest(currentUrl, sink, callFactory, maxBytes)) {
                is RemoteImageHttpOutcome.Redirect -> {
                    if (followedRedirects >= REMOTE_IMAGE_MAX_TRUSTED_REDIRECTS) {
                        return RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                httpStatusCode = outcome.statusCode,
                                retryable = false,
                            ),
                        )
                    }
                    currentUrl = outcome.target
                    followedRedirects += 1
                }
                is RemoteImageHttpOutcome.Terminal -> return outcome.result
            }
        }
    }

    private suspend fun executeRemoteImageRequest(
        url: HttpUrl,
        sink: File? = null,
        callFactory: Call.Factory = SubshellNetworkTransport.mediaReadCallFactory,
        maxBytes: Long = REMOTE_IMAGE_MAX_BYTES,
    ): RemoteImageHttpOutcome {
        // MEDIA02_CANCELLABLE_MEDIA_BODY_READS_V1: the body copy loops run on
        // a blocking IO thread; they stop as soon as this coroutine is
        // cancelled instead of transferring the whole body first.
        val job = currentCoroutineContext()[Job]
        val stillWanted = { job?.isActive != false }
        val call = callFactory.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "image/*")
                .header("User-Agent", REMOTE_IMAGE_USER_AGENT)
                .build(),
        )
        return call.awaitResponse().use { response ->
            if (isSupportedRemoteImageRedirectCode(response.code)) {
                val target = resolveTrustedRedditMatrixMediaRedirect(
                    source = url,
                    location = response.header("Location"),
                )
                if (target != null) {
                    RemoteImageHttpOutcome.Redirect(
                        target = target,
                        statusCode = response.code,
                    )
                } else {
                    RemoteImageHttpOutcome.Terminal(
                        RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                httpStatusCode = response.code,
                                retryable = false,
                                localKind = LocalFailureKind.SECURITY,
                            ),
                        ),
                    )
                }
            } else {
                RemoteImageHttpOutcome.Terminal(
                    when {
                        !response.isSuccessful -> RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                httpStatusCode = response.code,
                                retryable = isRetryableHttpStatus(response.code),
                            ),
                        )
                        !isAllowedRemoteImageContentType(
                            host = url.host,
                            encodedPath = url.encodedPath,
                            type = response.body.contentType()?.type,
                            subtype = response.body.contentType()?.subtype,
                        ) -> RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                retryable = false,
                                protocolKind = NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE,
                            ),
                        )
                        response.body.contentLength() == 0L -> RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                retryable = false,
                                protocolKind = NetworkProtocolFailureKind.EMPTY_BODY,
                            ),
                        )
                        sink == null &&
                            response.body.contentLength() > maxBytes -> RemoteMediaLoadResult.Failed(
                            FailureDetails(
                                retryable = false,
                                protocolKind = NetworkProtocolFailureKind.BODY_TOO_LARGE,
                            ),
                        )
                        sink != null -> streamImageBodyToFile(response, sink, stillWanted)
                        else -> readBoundedImageBody(response, stillWanted, maxBytes)
                    },
                )
            }
        }
    }

    private fun readBoundedImageBody(
        response: Response,
        stillWanted: () -> Boolean,
        maxBytes: Long,
    ): RemoteMediaLoadResult<ByteArray> {
        val bytes = response.body.byteStream().use { input ->
            readBoundedMediaBytes(
                input = input,
                declaredLength = response.body.contentLength(),
                maxBytes = maxBytes,
                stillWanted = stillWanted,
            )
        } ?: return RemoteMediaLoadResult.Failed(
            FailureDetails(
                retryable = false,
                protocolKind = NetworkProtocolFailureKind.BODY_TOO_LARGE,
            ),
        )
        return if (bytes.isEmpty()) {
            RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    protocolKind = NetworkProtocolFailureKind.EMPTY_BODY,
                ),
            )
        } else {
            RemoteMediaLoadResult.Ready(bytes)
        }
    }

    private fun streamImageBodyToFile(
        response: Response,
        file: File,
        stillWanted: () -> Boolean,
    ): RemoteMediaLoadResult<ByteArray> {
        val streamed = response.body.byteStream().use { input ->
            streamToFileKeepingLeadingBytes(input, file, REMOTE_MEDIA_LEADING_BYTES, stillWanted)
        }
        return if (streamed.totalBytes == 0L) {
            RemoteMediaLoadResult.Failed(
                FailureDetails(
                    retryable = false,
                    protocolKind = NetworkProtocolFailureKind.EMPTY_BODY,
                ),
            )
        } else {
            RemoteMediaLoadResult.Ready(streamed.leadingBytes)
        }
    }

    private fun IOException.toFailureDetails(): FailureDetails {
        // NET03_TLS_ONLY_FOR_HANDSHAKE_FAILURES_V1
        val kind = toNetworkTransportFailureKind()
        return FailureDetails(
            retryable = isRetryableTransportFailure(kind),
            transportKind = kind,
        )
    }

    private fun decodeRemoteDrawable(bytes: ByteArray): Drawable? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeAnimatedDrawable(bytes)
        } else {
            // LOW_MEDIA_BOUNDED_DECODE_BEFORE_P_V1 (OCB-646): before Android 9
            // the drawable is static anyway; decode it bounded like any image.
            decodeBoundedBitmap(bytes)?.let { bitmap ->
                android.graphics.drawable.BitmapDrawable(null as android.content.res.Resources?, bitmap)
            }
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAnimatedDrawable(bytes: ByteArray): Drawable =
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(ByteBuffer.wrap(bytes).asReadOnlyBuffer()),
        ) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val largest = maxOf(width, height)
            if (largest > REMOTE_IMAGE_MAX_DIMENSION) {
                val scale = REMOTE_IMAGE_MAX_DIMENSION.toFloat() / largest.toFloat()
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }

    private fun decodeBoundedBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > REMOTE_IMAGE_MAX_DIMENSION ||
            bounds.outHeight / sampleSize > REMOTE_IMAGE_MAX_DIMENSION
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { _, responseToClose, _ ->
                            responseToClose.close()
                        }
                    }
                },
            )
        }

    /**
     * MEDIA02: an encoded image larger than a quarter of the cache is not
     * kept. LruCache evicts everything to fit an entry, and one larger than
     * the whole cache (the display cap is 40 MiB, the cache 24 MiB) emptied
     * it and was not kept either.
     */
    internal fun shouldCacheEncodedImage(sizeBytes: Int): Boolean =
        sizeBytes / 1_024 <= REMOTE_IMAGE_ENCODED_CACHE_KB / 4

    // Display only: the whole body is held in memory before a bounded decode.
    // Sharing and saving stream to a file instead (downloadToFile).
    private const val REMOTE_IMAGE_MAX_BYTES = 40L * 1_024L * 1_024L
    private const val REMOTE_GIF_MAX_BYTES = 100L * 1_024L * 1_024L
    private const val REMOTE_GONE_CACHE_ENTRIES = 256
    private const val REMOTE_MEDIA_LEADING_BYTES = 16
    private const val REMOTE_IMAGE_MAX_DIMENSION = 2_048
    private const val REMOTE_IMAGE_MEMORY_CACHE_KB = 24 * 1_024
    private const val REMOTE_IMAGE_ENCODED_CACHE_KB = 24 * 1_024
    private const val REMOTE_IMAGE_USER_AGENT = "subshell-media/1.0"
}

internal class StreamedFile(val totalBytes: Long, val leadingBytes: ByteArray)

/** Copies [input] into [file] without holding it in memory, keeping its first [leadingCount] bytes. */
internal fun streamToFileKeepingLeadingBytes(
    input: InputStream,
    file: File,
    leadingCount: Int,
    stillWanted: () -> Boolean = { true },
): StreamedFile {
    val leading = ByteArray(leadingCount)
    var leadingSize = 0
    var totalBytes = 0L
    FileOutputStream(file).use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            if (!stillWanted()) throw CancellationException("Media download cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            if (leadingSize < leading.size) {
                val take = minOf(read, leading.size - leadingSize)
                buffer.copyInto(leading, leadingSize, 0, take)
                leadingSize += take
            }
            output.write(buffer, 0, read)
            totalBytes += read
        }
        output.fd.sync()
    }
    return StreamedFile(totalBytes = totalBytes, leadingBytes = leading.copyOf(leadingSize))
}

/**
 * MEDIA02_BOUNDED_MEDIA_BYTES_V1
 * Reads at most [maxBytes] (null when the body is larger). A known
 * [declaredLength] is read straight into an array of that size, so the
 * common case holds the image once instead of twice (a growing buffer plus
 * its toByteArray copy). Stops when [stillWanted] turns false.
 */
internal fun readBoundedMediaBytes(
    input: InputStream,
    declaredLength: Long,
    maxBytes: Long,
    stillWanted: () -> Boolean = { true },
): ByteArray? {
    if (declaredLength > maxBytes) return null
    if (declaredLength > 0L) {
        val exact = ByteArray(declaredLength.toInt())
        var filled = 0
        while (filled < exact.size) {
            if (!stillWanted()) throw CancellationException("Media load cancelled")
            val read = input.read(exact, filled, exact.size - filled)
            // LOW_MEDIA_TRUNCATED_IMAGE_IS_NOT_COMPLETE_V1 (OCB-647): a body
            // that ends before its declared length is a failed read (retried),
            // not a complete image to decode and cache.
            if (read < 0) {
                throw java.io.EOFException("Image body ended after $filled of $declaredLength bytes")
            }
            filled += read
        }
        val next = input.read()
        if (next < 0) return exact
        // The server sent more than it declared: continue bounded.
        val output = ByteArrayOutputStream(exact.size + DEFAULT_BUFFER_SIZE)
        output.write(exact)
        output.write(next)
        return readRemainingBounded(input, output, maxBytes, stillWanted)
    }
    return readRemainingBounded(input, ByteArrayOutputStream(), maxBytes, stillWanted)
}

private fun readRemainingBounded(
    input: InputStream,
    output: ByteArrayOutputStream,
    maxBytes: Long,
    stillWanted: () -> Boolean,
): ByteArray? {
    if (output.size() > maxBytes) return null
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        if (!stillWanted()) throw CancellationException("Media load cancelled")
        val read = input.read(buffer)
        if (read < 0) break
        if (output.size().toLong() + read > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
