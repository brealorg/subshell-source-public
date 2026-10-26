package app.subshell.prototype.media

import android.net.Uri
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.annotation.WorkerThread
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.network.SubshellNetworkTransport
import app.subshell.prototype.model.REDGIFS_VIDEO_SCHEME
import app.subshell.prototype.model.isRedGifsClipId
import app.subshell.prototype.model.redGifsVideoUri
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

/*
 * ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1 (#207, owner: follows "Show NSFW
 * posts", no setting of its own). An NSFW RedGIFs post is a video whose first
 * source is `subshell-redgifs://<id>` (#256: before Reddit's own silent copy,
 * which follows as a fallback). The id is
 * turned into the clip's MP4 only when the player loads it (feed autoplay,
 * preload or opening the video), never while a listing is read, and nothing is
 * asked of RedGIFs while "Show NSFW posts" is off.
 *
 * RedGIFs' temporary token is bound to the User-Agent it was fetched with
 * (checked 2026-10-08: the same token with another User-Agent is a 401), so
 * the token and every lookup use one fixed User-Agent. The token is not
 * account material: it is kept in memory only, never stored or backed up.
 */

private const val REDGIFS_API_BASE = "https://api.redgifs.com"
private val REDGIFS_USER_AGENT = "subshell/${BuildConfig.VERSION_NAME}"

/** RedGIFs issues 24 h tokens; one is renewed before that runs out. */
internal const val REDGIFS_TOKEN_REUSE_MILLIS = 23L * 60L * 60L * 1_000L

private const val REDGIFS_MAX_RESPONSE_BYTES = 1L * 1024L * 1024L

/** RedGIFs' two renditions; HD is often 1080p, SD the smaller mobile file. */
internal enum class RedGifsQuality { HD, SD }

private const val REDGIFS_SD_SUFFIX = "/sd"

/** Seconds a token or clip lookup may take before the next source plays. */
private const val REDGIFS_LOOKUP_TIMEOUT_SECONDS = 8L

private fun redGifsPathOfVideoUri(uri: String): String? =
    uri.trim()
        .takeIf { mediaUriScheme(it) == REDGIFS_VIDEO_SCHEME }
        ?.substringAfter("://", missingDelimiterValue = "")
        ?.trimStart('/')

internal fun redGifsClipIdOfVideoUri(uri: String): String? =
    redGifsPathOfVideoUri(uri)
        ?.removeSuffix(REDGIFS_SD_SUFFIX)
        ?.trim('/')
        ?.takeIf(::isRedGifsClipId)

internal fun redGifsQualityOfVideoUri(uri: String): RedGifsQuality =
    if (redGifsPathOfVideoUri(uri)?.endsWith(REDGIFS_SD_SUFFIX) == true) RedGifsQuality.SD else RedGifsQuality.HD

/**
 * ISSUE256_REDGIFS_CACHE_KEY_IS_THE_CLIP_V1: the video cache keys a source by
 * post and source position, so a post stored before #256 (Reddit's silent copy
 * at position 0) would hand its cached bytes to the RedGIFs source now there.
 * A RedGIFs source is keyed by its clip and quality instead.
 */
internal fun redGifsCacheKeyOf(uri: String): String? =
    redGifsClipIdOfVideoUri(uri)?.let { clipId ->
        "subshell:redgifs:$clipId:${redGifsQualityOfVideoUri(uri).name.lowercase()}"
    }

/**
 * ISSUE256_REDGIFS_HD_THEN_SD_V1 (owner 2026-10-25: HD always first, SD if HD
 * fails, then Reddit's copy): a RedGIFs source becomes its HD and SD sources;
 * any other source stays one.
 */
internal fun redGifsQualitySources(uri: String): List<String> =
    redGifsClipIdOfVideoUri(uri)
        ?.takeIf { redGifsQualityOfVideoUri(uri) == RedGifsQuality.HD }
        ?.let { clipId -> listOf(uri, "${redGifsVideoUri(clipId)}$REDGIFS_SD_SUFFIX") }
        ?: listOf(uri)

/** Set from "Show NSFW posts"; off until the app has read the setting. */
internal object RedGifsPlayback {
    @Volatile
    var allowed: Boolean = false
}

/** RedGIFs says the clip is gone (404/410). */
internal class VideoClipUnavailableException : IOException("Clip not available")

/** RedGIFs is not asked while "Show NSFW posts" is off. */
internal class VideoProviderOffException : IOException("RedGIFs playback is off")

/** A provider lookup answered with this HTTP status. */
internal class VideoProviderHttpException(val statusCode: Int) :
    IOException("Video provider answered HTTP $statusCode")

/** A provider lookup answered 2xx without a playable stream. */
internal class VideoProviderResponseException(message: String) : IOException(message)

internal class RedGifsClipResolver(
    private val callFactory: Call.Factory = SubshellNetworkTransport.sharedCallFactory,
    private val apiBase: String = REDGIFS_API_BASE,
    private val allowed: () -> Boolean = { RedGifsPlayback.allowed },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var token: String? = null
    private var tokenFetchedAt: Long = 0L

    /** The clip's MP4 (HD: hd, else sd; SD: sd only). A 401 renews the token once. */
    @WorkerThread
    @Throws(IOException::class)
    fun resolve(clipId: String, quality: RedGifsQuality = RedGifsQuality.HD): String {
        if (!allowed()) throw VideoProviderOffException()
        val first = token(stale = null)
        lookUp(clipId, first, quality)?.let { return it }
        val second = token(stale = first)
        return lookUp(clipId, second, quality) ?: throw VideoProviderHttpException(401)
    }

    private fun token(stale: String?): String = synchronized(lock) {
        val current = token
        val fresh = current != null &&
            current != stale &&
            clock() - tokenFetchedAt < REDGIFS_TOKEN_REUSE_MILLIS
        if (fresh) return current
        if (!allowed()) throw VideoProviderOffException()
        val answer = execute(request("$apiBase/v2/auth/temporary", bearer = null)) { response ->
            if (!response.isSuccessful) throw VideoProviderHttpException(response.code)
            response.jsonBody()["token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?: throw VideoProviderResponseException("RedGIFs sent no token")
        }
        token = answer
        tokenFetchedAt = clock()
        answer
    }

    /** The stream URL, or null when the token was refused (401). */
    private fun lookUp(clipId: String, bearer: String, quality: RedGifsQuality): String? {
        if (!allowed()) throw VideoProviderOffException()
        return execute(request("$apiBase/v2/gifs/$clipId", bearer)) { response ->
            when {
                response.code == 401 -> null
                response.code == 404 || response.code == 410 -> throw VideoClipUnavailableException()
                !response.isSuccessful -> throw VideoProviderHttpException(response.code)
                else -> {
                    val urls = response.jsonBody()["gif"]?.jsonObject?.get("urls")?.jsonObject
                    when (quality) {
                        RedGifsQuality.HD -> sequenceOf("hd", "sd")
                        RedGifsQuality.SD -> sequenceOf("sd")
                    }
                        .mapNotNull { quality -> urls?.get(quality)?.jsonPrimitive?.contentOrNull }
                        .firstOrNull { url -> url.toHttpUrlOrNull()?.isHttps == true }
                        ?: throw VideoProviderResponseException("RedGIFs sent no stream")
                }
            }
        }
    }

    private fun request(url: String, bearer: String?): Request = Request.Builder()
        .url(url)
        .header("User-Agent", REDGIFS_USER_AGENT)
        .header("Accept", "application/json")
        .apply { bearer?.let { header("Authorization", "Bearer $it") } }
        .build()

    // ISSUE256_REDGIFS_LOOKUP_WAITS_AT_MOST_8_SECONDS_V1: a slow RedGIFs makes
    // the player move on to the next source instead of spinning.
    private fun <T> execute(request: Request, read: (Response) -> T): T =
        callFactory.newCall(request)
            .apply { timeout().timeout(REDGIFS_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .execute()
            .use(read)

    private fun Response.jsonBody(): JsonObject {
        val source = body.source()
        if (source.request(REDGIFS_MAX_RESPONSE_BYTES + 1)) {
            throw VideoProviderResponseException("RedGIFs answer too large")
        }
        return runCatching { json.parseToJsonElement(source.buffer.readUtf8()).jsonObject }
            .getOrElse { throw VideoProviderResponseException("RedGIFs answer unreadable") }
    }
}

/** Turns `subshell-redgifs://<id>` into the clip's MP4 as the player opens it. */
@AndroidxOptIn(UnstableApi::class)
internal class RedGifsDataSpecResolver(
    private val resolver: RedGifsClipResolver,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri.toString()
        val clipId = redGifsClipIdOfVideoUri(uri) ?: return dataSpec
        return dataSpec.withUri(Uri.parse(resolver.resolve(clipId, redGifsQualityOfVideoUri(uri))))
    }
}

/** The clip a share/save export reads: a RedGIFs source becomes its MP4. */
@WorkerThread
@Throws(IOException::class)
internal fun resolvedRemoteVideoUri(
    uri: String,
    resolver: RedGifsClipResolver = RedGifsClips.resolver,
): String = redGifsClipIdOfVideoUri(uri)?.let(resolver::resolve) ?: uri

/** One resolver per process, so playback and exports share the token. */
internal object RedGifsClips {
    val resolver: RedGifsClipResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RedGifsClipResolver() }
}
