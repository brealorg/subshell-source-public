package app.subshell.prototype.media

import android.content.Context
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.annotation.WorkerThread
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.subshell.prototype.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal const val VIDEO_MEDIA_CACHE_DIRECTORY = "media3-playback"
// VIDEOCACHE01_CONFIGURABLE_LIMIT_V1: the playback cache is bounded by an LRU
// evictor whose limit the user picks (default 1 GB). The choice is
// device-local (its own preferences file, outside the portable settings map)
// and is read when the cache is created, so a change applies on next start.
internal enum class VideoMediaCacheLimit(val bytes: Long, val label: String) {
    MB_256(256L * 1024L * 1024L, "256 MB"),
    MB_512(512L * 1024L * 1024L, "512 MB"),
    GB_1(1024L * 1024L * 1024L, "1 GB"),
    GB_2(2048L * 1024L * 1024L, "2 GB"),
    ;

    companion object {
        val DEFAULT = GB_1

        fun fromStored(name: String?): VideoMediaCacheLimit =
            entries.firstOrNull { limit -> limit.name == name } ?: DEFAULT
    }
}

internal const val VIDEO_MEDIA_CACHE_PREFERENCES = "subshell_video_media_cache"
internal const val VIDEO_MEDIA_CACHE_LIMIT_KEY = "video_media_cache_limit"

internal object VideoMediaCacheLimitStore {
    fun read(context: Context): VideoMediaCacheLimit = VideoMediaCacheLimit.fromStored(
        context.applicationContext
            .getSharedPreferences(VIDEO_MEDIA_CACHE_PREFERENCES, Context.MODE_PRIVATE)
            .getString(VIDEO_MEDIA_CACHE_LIMIT_KEY, null),
    )

    fun write(context: Context, limit: VideoMediaCacheLimit) {
        context.applicationContext
            .getSharedPreferences(VIDEO_MEDIA_CACHE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(VIDEO_MEDIA_CACHE_LIMIT_KEY, limit.name)
            .apply()
    }
}
internal const val VIDEO_MEDIA_HTTP_CONNECT_TIMEOUT_MS = 10_000
internal const val VIDEO_MEDIA_HTTP_READ_TIMEOUT_MS = 20_000

internal data class VideoMediaCacheTelemetrySnapshot(
    val cacheReadEvents: Long = 0L,
    val cachedBytesRead: Long = 0L,
    val upstreamRequests: Long = 0L,
    val upstreamBytesRead: Long = 0L,
    val cacheIgnoredEvents: Long = 0L,
)

internal data class VideoMediaCacheStatus(
    val bytesUsed: Long,
    val maxBytes: Long,
    val resourceCount: Int,
)

internal data class VideoMediaCacheClearResult(
    val before: VideoMediaCacheStatus,
    val after: VideoMediaCacheStatus,
    val resourcesRemoved: Int,
) {
    val bytesRemoved: Long
        get() = (before.bytesUsed - after.bytesUsed).coerceAtLeast(0L)
}

internal fun formatVideoMediaCacheBytes(bytes: Long): String {
    require(bytes >= 0L) { "Cache bytes must be non-negative" }
    val kibibyte = 1024L
    val mebibyte = kibibyte * 1024L
    val gibibyte = mebibyte * 1024L
    return when {
        bytes == 0L -> "0 B"
        bytes < mebibyte -> "${(bytes + kibibyte - 1L) / kibibyte} KB"
        bytes >= gibibyte -> {
            val gibibytes = bytes.toDouble() / gibibyte.toDouble()
            val pattern = if (gibibytes % 1.0 == 0.0) "%.0f GB" else "%.1f GB"
            String.format(Locale.US, pattern, gibibytes)
        }
        else -> {
            val mebibytes = bytes.toDouble() / mebibyte.toDouble()
            val pattern = if (mebibytes >= 10.0 || mebibytes % 1.0 == 0.0) "%.0f MB" else "%.1f MB"
            String.format(Locale.US, pattern, mebibytes)
        }
    }
}

internal fun videoMediaCacheSummary(status: VideoMediaCacheStatus): String =
    "${formatVideoMediaCacheBytes(status.bytesUsed)} used · " +
        "${formatVideoMediaCacheBytes(status.maxBytes)} limit"

@AndroidxOptIn(UnstableApi::class)
private object VideoMediaCacheTelemetry : CacheDataSource.EventListener, TransferListener {
    private val cacheReadEvents = AtomicLong()
    private val cachedBytesRead = AtomicLong()
    private val upstreamRequests = AtomicLong()
    private val upstreamBytesRead = AtomicLong()
    private val cacheIgnoredEvents = AtomicLong()

    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
        cacheReadEvents.incrementAndGet()
        this.cachedBytesRead.addAndGet(cachedBytesRead)
        MediaPerformanceTrace.mark(
            event = "media_cache_read",
            details = "bytes=$cachedBytesRead cache_size_bytes=$cacheSizeBytes",
        )
    }

    override fun onCacheIgnored(reason: Int) {
        cacheIgnoredEvents.incrementAndGet()
        MediaPerformanceTrace.mark(
            event = "media_cache_ignored",
            details = "reason=$reason",
        )
    }

    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = Unit

    override fun onTransferStart(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) {
        if (isNetwork) upstreamRequests.incrementAndGet()
    }

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
    ) {
        if (isNetwork) upstreamBytesRead.addAndGet(bytesTransferred.toLong())
    }

    override fun onTransferEnd(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) {
        if (!isNetwork) return
        val snapshot = snapshot()
        MediaPerformanceTrace.mark(
            event = "media_cache_upstream_transfer",
            details = "requests=${snapshot.upstreamRequests} bytes=${snapshot.upstreamBytesRead}",
        )
    }

    fun snapshot(): VideoMediaCacheTelemetrySnapshot = VideoMediaCacheTelemetrySnapshot(
        cacheReadEvents = cacheReadEvents.get(),
        cachedBytesRead = cachedBytesRead.get(),
        upstreamRequests = upstreamRequests.get(),
        upstreamBytesRead = upstreamBytesRead.get(),
        cacheIgnoredEvents = cacheIgnoredEvents.get(),
    )
}

@AndroidxOptIn(UnstableApi::class)
internal object VideoMediaDataSource {
    @Volatile
    private var sharedCache: SimpleCache? = null

    @Volatile
    private var activeLimit: VideoMediaCacheLimit? = null

    /** The limit the running cache was created with, or null before first use. */
    fun activeLimit(): VideoMediaCacheLimit? = activeLimit

    fun factory(context: Context): DefaultDataSource.Factory {
        val appContext = context.applicationContext
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("subshell/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(VIDEO_MEDIA_HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(VIDEO_MEDIA_HTTP_READ_TIMEOUT_MS)
            .setTransferListener(VideoMediaCacheTelemetry)
        // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: a RedGIFs source is turned into its
        // MP4 below the cache, so a cached clip plays without asking RedGIFs.
        val resolvingUpstreamFactory = ResolvingDataSource.Factory(
            upstreamFactory,
            RedGifsDataSpecResolver(RedGifsClips.resolver),
        )

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache(appContext))
            .setUpstreamDataSourceFactory(resolvingUpstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(VideoMediaCacheTelemetry)

        // DefaultDataSource keeps asset://, android.resource://, file:// and content:// local.
        // Only HTTP(S) and otherwise unsupported schemes reach the cached base data source.
        return DefaultDataSource.Factory(appContext, cacheDataSourceFactory)
    }

    fun telemetrySnapshot(): VideoMediaCacheTelemetrySnapshot =
        VideoMediaCacheTelemetry.snapshot()

    @WorkerThread
    fun cacheStatus(context: Context): VideoMediaCacheStatus =
        cache(context.applicationContext).statusSnapshot()

    @WorkerThread
    fun clearCache(context: Context): VideoMediaCacheClearResult {
        val cache = cache(context.applicationContext)
        val before = cache.statusSnapshot()
        val keys = cache.keys.toList()
        keys.forEach(cache::removeResource)
        val after = cache.statusSnapshot()
        val result = VideoMediaCacheClearResult(
            before = before,
            after = after,
            resourcesRemoved = keys.size,
        )
        MediaPerformanceTrace.mark(
            event = "media_cache_cleared",
            details =
                "resources=${result.resourcesRemoved} bytes_removed=${result.bytesRemoved} " +
                    "bytes_remaining=${result.after.bytesUsed}",
        )
        return result
    }

    @Synchronized
    private fun cache(context: Context): SimpleCache {
        sharedCache?.let { return it }
        val limit = VideoMediaCacheLimitStore.read(context)
        val cache = SimpleCache(
            File(context.cacheDir, VIDEO_MEDIA_CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(limit.bytes),
            StandaloneDatabaseProvider(context),
        )
        activeLimit = limit
        sharedCache = cache
        return cache
    }
}


@AndroidxOptIn(UnstableApi::class)
private fun SimpleCache.statusSnapshot(): VideoMediaCacheStatus = VideoMediaCacheStatus(
    bytesUsed = cacheSpace.coerceAtLeast(0L),
    maxBytes = (VideoMediaDataSource.activeLimit() ?: VideoMediaCacheLimit.DEFAULT).bytes,
    resourceCount = keys.size,
)
