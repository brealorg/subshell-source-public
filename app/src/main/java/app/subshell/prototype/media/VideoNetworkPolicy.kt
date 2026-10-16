package app.subshell.prototype.media

import app.subshell.prototype.model.REDGIFS_VIDEO_SCHEME
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.Closeable

/** Coarse network class used only for speculative media work. */
enum class VideoNetworkClass {
    OFFLINE,
    METERED,
    UNMETERED,
}

/** Provider-neutral preload target before it is translated to Media3 stages. */
enum class VideoPreloadTarget {
    NONE,
    SOURCE_PREPARED,
    TRACKS_SELECTED,
    RANGE_3_SECONDS,
}

data class VideoNetworkSnapshot(
    val hasInternetCapability: Boolean = false,
    val validated: Boolean = false,
    val metered: Boolean = true,
    val dataSaverStatus: Int = ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
) {
    val networkClass: VideoNetworkClass
        get() = when {
            !hasInternetCapability || !validated -> VideoNetworkClass.OFFLINE
            metered -> VideoNetworkClass.METERED
            else -> VideoNetworkClass.UNMETERED
        }

    val dataSaverActive: Boolean
        get() = dataSaverStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
}

internal fun videoPreloadTarget(
    remoteSource: Boolean,
    distanceFromFocus: Int,
    network: VideoNetworkSnapshot,
    viewportMotion: FeedViewportMotion,
): VideoPreloadTarget {
    if (distanceFromFocus < 0) return VideoPreloadTarget.NONE

    // Never compete with a fling. A direct drag may prepare only the immediate candidate.
    if (viewportMotion == FeedViewportMotion.FLINGING) return VideoPreloadTarget.NONE
    if (viewportMotion == FeedViewportMotion.DRAGGING) {
        return if (distanceFromFocus == 0) {
            VideoPreloadTarget.SOURCE_PREPARED
        } else {
            VideoPreloadTarget.NONE
        }
    }

    // Local assets/resources do not consume network data and keep the established preload depth.
    if (!remoteSource) return fullPreloadTarget(distanceFromFocus)

    return when (network.networkClass) {
        VideoNetworkClass.OFFLINE -> VideoPreloadTarget.NONE
        VideoNetworkClass.METERED -> {
            if (distanceFromFocus == 0) {
                VideoPreloadTarget.SOURCE_PREPARED
            } else {
                VideoPreloadTarget.NONE
            }
        }
        VideoNetworkClass.UNMETERED -> fullPreloadTarget(distanceFromFocus)
    }
}

private fun fullPreloadTarget(distanceFromFocus: Int): VideoPreloadTarget = when (distanceFromFocus) {
    0 -> VideoPreloadTarget.RANGE_3_SECONDS
    1 -> VideoPreloadTarget.TRACKS_SELECTED
    2 -> VideoPreloadTarget.SOURCE_PREPARED
    else -> VideoPreloadTarget.NONE
}

/**
 * MEDIA03_ONE_MEDIA_URI_SCHEME_V1: the one scheme parse behind the cache
 * key, preload remoteness and download eligibility, which used three
 * slightly different copies.
 */
internal fun mediaUriScheme(uri: String): String =
    uri.trim().substringBefore(':', missingDelimiterValue = "").trim().lowercase()

internal fun isRemoteMediaUri(uri: String): Boolean =
    mediaUriScheme(uri).let { scheme ->
        scheme == "https" || scheme == "http" ||
            // ISSUE207_REDGIFS_PLAYS_IN_THE_APP_V1: fetched over HTTPS when it plays.
            scheme == REDGIFS_VIDEO_SCHEME
    }

internal fun isSecureHttpsMediaUri(uri: String): Boolean = mediaUriScheme(uri) == "https"

/**
 * Process-local default-network observer. It publishes only coarse booleans and Data Saver state;
 * SSID, carrier, addresses, URLs and request metadata are never collected.
 */
internal class AndroidVideoNetworkMonitor(
    context: Context,
    private val onSnapshot: (VideoNetworkSnapshot) -> Unit,
) : Closeable {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSnapshot: VideoNetworkSnapshot? = null
    private var started = false

    // LOW_MEDIA_NETWORK_SNAPSHOT_FROM_THE_CALLBACK_V1 (OCB-649): the default
    // network callback hands over the capabilities it is about; reading
    // activeNetwork again from the callback could see the network before or
    // after the change (a lost network still active, a new one not yet).
    @Volatile
    private var defaultNetworkCapabilities: NetworkCapabilities? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            defaultNetworkCapabilities = networkCapabilities
            publishSnapshot()
        }

        override fun onLost(network: Network) {
            defaultNetworkCapabilities = null
            publishSnapshot()
        }
    }

    private val dataSaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = publishSnapshot()
    }

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(
            appContext,
            dataSaverReceiver,
            IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        defaultNetworkCapabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        publishSnapshot()
    }

    override fun close() {
        if (!started) return
        started = false
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { appContext.unregisterReceiver(dataSaverReceiver) }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun publishSnapshot() {
        val snapshot = videoNetworkSnapshotOf(
            capabilities = defaultNetworkCapabilities,
            dataSaverStatus = connectivityManager.restrictBackgroundStatus,
        )
        mainHandler.post {
            if (!started || snapshot == lastSnapshot) return@post
            lastSnapshot = snapshot
            onSnapshot(snapshot)
        }
    }
}

internal fun videoNetworkSnapshotOf(
    capabilities: NetworkCapabilities?,
    dataSaverStatus: Int,
): VideoNetworkSnapshot {
    val temporarilyUnmetered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED) == true
    val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true ||
        temporarilyUnmetered
    return VideoNetworkSnapshot(
        hasInternetCapability =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        metered = !unmetered,
        dataSaverStatus = dataSaverStatus,
    )
}
