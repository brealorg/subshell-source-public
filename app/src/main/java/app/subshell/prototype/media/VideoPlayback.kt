package app.subshell.prototype.media

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.ContextWrapper
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.subshell.prototype.applock.LocalAppLocked
import app.subshell.prototype.applock.LocalAppLockedNow
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.PreloadException
import androidx.media3.exoplayer.source.preload.PreloadManagerListener
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.pictureInPictureAspect
import app.subshell.prototype.ui.VideoAudioDefault
import app.subshell.prototype.ui.VideoQuality
import app.subshell.prototype.ui.VideoSubtitleDefault
import androidx.core.os.LocaleListCompat
import androidx.media3.common.TrackSelectionParameters
import kotlin.math.abs

enum class VideoMediaSourceKind {
    PROGRESSIVE,
    DASH,
    HLS,
}

internal fun VideoMediaSourceKind.defaultMimeType(): String? = when (this) {
    VideoMediaSourceKind.PROGRESSIVE -> null
    VideoMediaSourceKind.DASH -> MimeTypes.APPLICATION_MPD
    VideoMediaSourceKind.HLS -> MimeTypes.APPLICATION_M3U8
}

@Immutable
data class VideoMediaTrackSource(
    val uri: String,
    val kind: VideoMediaSourceKind = VideoMediaSourceKind.PROGRESSIVE,
    val mimeType: String? = null,
    val cacheKey: String? = null,
) {
    val resolvedMimeType: String?
        get() = mimeType ?: kind.defaultMimeType()
}

@Immutable
data class VideoMediaTextTrackSource(
    val uri: String,
    val mimeType: String = MimeTypes.TEXT_VTT,
    val language: String? = null,
    val label: String? = null,
)

@Immutable
data class VideoMediaSource(
    val uri: String,
    val kind: VideoMediaSourceKind = VideoMediaSourceKind.PROGRESSIVE,
    val mimeType: String? = null,
    val companionAudio: VideoMediaTrackSource? = null,
    val subtitleTracks: List<VideoMediaTextTrackSource> = emptyList(),
    val cacheKey: String? = null,
) {
    val resolvedMimeType: String?
        get() = mimeType ?: kind.defaultMimeType()

    val isComposite: Boolean
        get() = companionAudio != null
}

/**
 * Provider-neutral media description. Reddit DASH/HLS and progressive fallbacks can be
 * supplied as ordered sources without changing the feed or player UI.
 */
@Immutable
data class VideoMedia(
    val mediaId: String,
    val sources: List<VideoMediaSource>,
) {
    init {
        require(sources.isNotEmpty()) { "VideoMedia requires at least one source" }
    }
}

fun localRawVideoMedia(
    context: Context,
    mediaId: String,
    @RawRes rawResource: Int,
    subtitleTracks: List<VideoMediaTextTrackSource> = emptyList(),
): VideoMedia = VideoMedia(
    mediaId = mediaId,
    sources = listOf(
        VideoMediaSource(
            uri = "android.resource://${context.packageName}/$rawResource",
            kind = VideoMediaSourceKind.PROGRESSIVE,
            mimeType = MimeTypes.VIDEO_MP4,
            subtitleTracks = subtitleTracks,
        ),
    ),
)

internal fun adaptiveAssetVideoMedia(
    mediaId: String,
    assetPath: String,
    kind: VideoMediaSourceKind,
    fallbackSources: List<VideoMediaSource> = emptyList(),
): VideoMedia = VideoMedia(
    mediaId = mediaId,
    sources = listOf(
        VideoMediaSource(
            uri = "asset:///$assetPath",
            kind = kind,
        ),
    ) + fallbackSources,
)

internal fun splitAssetVideoMedia(
    mediaId: String,
    videoAssetPath: String,
    audioAssetPath: String,
    fallbackSources: List<VideoMediaSource> = emptyList(),
): VideoMedia = VideoMedia(
    mediaId = mediaId,
    sources = listOf(
        VideoMediaSource(
            uri = "asset:///$videoAssetPath",
            kind = VideoMediaSourceKind.PROGRESSIVE,
            mimeType = MimeTypes.VIDEO_MP4,
            companionAudio = VideoMediaTrackSource(
                uri = "asset:///$audioAssetPath",
                kind = VideoMediaSourceKind.PROGRESSIVE,
                mimeType = MimeTypes.AUDIO_MP4,
            ),
        ),
    ) + fallbackSources,
)

private const val MERGE_ADJUST_PERIOD_TIME_OFFSETS = true
private const val MERGE_CLIP_DURATIONS = true

internal fun progressiveRemoteCacheKey(
    uri: String,
    kind: VideoMediaSourceKind,
    explicitCacheKey: String?,
    generatedCacheKey: String,
): String? {
    if (!isRemoteMediaUri(uri) || kind != VideoMediaSourceKind.PROGRESSIVE) return null
    return explicitCacheKey?.trim()?.takeIf { it.isNotEmpty() } ?: generatedCacheKey
}

internal fun VideoMediaSource.usesRemoteTransport(): Boolean =
    isRemoteMediaUri(uri) ||
        companionAudio?.let { isRemoteMediaUri(it.uri) } == true ||
        subtitleTracks.any { isRemoteMediaUri(it.uri) }

internal fun VideoMedia.usesRemoteTransport(): Boolean =
    sources.firstOrNull()?.usesRemoteTransport() == true

internal fun fallbackStartPositionMs(currentPositionMs: Long): Long =
    currentPositionMs.coerceAtLeast(0L)

@AndroidxOptIn(UnstableApi::class)
internal class FeedTargetPreloadStatusControl :
    TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
    var focusRank: Int = C.INDEX_UNSET
    var networkSnapshot: VideoNetworkSnapshot = VideoNetworkSnapshot()
    var viewportMotion: FeedViewportMotion = FeedViewportMotion.IDLE
    private val remoteSourceByRank = mutableMapOf<Int, Boolean>()

    fun updateMedia(rank: Int, remoteSource: Boolean) {
        remoteSourceByRank[rank] = remoteSource
    }

    fun removeMedia(rank: Int) {
        remoteSourceByRank.remove(rank)
    }

    override fun getTargetPreloadStatus(index: Int): DefaultPreloadManager.PreloadStatus {
        if (focusRank == C.INDEX_UNSET) {
            return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
        }
        return when (
            videoPreloadTarget(
                remoteSource = remoteSourceByRank[index] == true,
                distanceFromFocus = abs(index - focusRank),
                network = networkSnapshot,
                viewportMotion = viewportMotion,
            )
        ) {
            VideoPreloadTarget.NONE ->
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            VideoPreloadTarget.SOURCE_PREPARED ->
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
            VideoPreloadTarget.TRACKS_SELECTED ->
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_TRACKS_SELECTED
            VideoPreloadTarget.RANGE_3_SECONDS ->
                DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(3_000L)
        }
    }
}

internal const val VIDEO_MAX_REGISTERED_MEDIA = 24

// LOW_MEDIA_UNRANKED_SURFACES_KEEP_THE_FEED_PRELOADS_V1 (OCB-653): a video
// outside the feed (post screen, viewer) has no feed rank; it is played but
// not ranked into the preload set, so it no longer evicts the feed's videos.
internal const val VIDEO_UNRANKED = -1

// LOW_MEDIA_SAME_VIDEO_DESPITE_FRESH_SIGNATURES_V1 (OCB-651): Reddit signs
// its video URLs per response, so the same video read again carries other
// query strings. Two VideoMedia are the same video when their id and every
// source match without the query part.
internal fun sameVideoMedia(a: VideoMedia?, b: VideoMedia): Boolean {
    if (a == null || a.mediaId != b.mediaId || a.sources.size != b.sources.size) return false
    fun unsigned(uri: String) = uri.substringBefore('?').substringBefore('#')
    return a.sources.zip(b.sources).all { (x, y) ->
        unsigned(x.uri) == unsigned(y.uri) &&
            x.kind == y.kind &&
            x.cacheKey == y.cacheKey &&
            (x.companionAudio?.uri?.let(::unsigned)) == (y.companionAudio?.uri?.let(::unsigned))
    }
}

private data class RegisteredVideoMedia(
    val media: VideoMedia,
    val mediaItem: MediaItem,
    val compositeMediaSource: MediaSource?,
    val rank: Int,
)

enum class VideoPlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR,
}

@Immutable
data class VideoPlaybackUiState(
    val mediaId: String? = null,
    val phase: VideoPlaybackPhase = VideoPlaybackPhase.IDLE,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val errorCode: Int? = null,
    val failure: VideoPlaybackFailure? = null,
)

@Immutable
data class VideoPlaybackProgress(
    val mediaId: String? = null,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long? = null,
    val isSeekable: Boolean = false,
)

internal fun normalizedVideoDuration(durationMs: Long): Long? =
    durationMs.takeIf { it != C.TIME_UNSET && it > 0L }

internal fun clampVideoSeekPosition(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))

internal fun videoBufferedFraction(bufferedPositionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return clampVideoSeekPosition(bufferedPositionMs, durationMs).toFloat() / durationMs.toFloat()
}

internal fun formatVideoPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalMinutes / 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun videoPlaybackPhase(
    playbackState: Int,
    hasTerminalError: Boolean,
): VideoPlaybackPhase = when {
    hasTerminalError -> VideoPlaybackPhase.ERROR
    playbackState == Player.STATE_BUFFERING -> VideoPlaybackPhase.BUFFERING
    playbackState == Player.STATE_READY -> VideoPlaybackPhase.READY
    playbackState == Player.STATE_ENDED -> VideoPlaybackPhase.ENDED
    else -> VideoPlaybackPhase.IDLE
}

internal fun videoControlsAutoHideEligible(
    state: VideoPlaybackUiState,
    isScrubbing: Boolean = false,
): Boolean = !isScrubbing && state.phase == VideoPlaybackPhase.READY && state.isPlaying

internal fun videoControlsMustRemainVisible(
    state: VideoPlaybackUiState,
    isScrubbing: Boolean = false,
): Boolean = isScrubbing ||
    state.phase == VideoPlaybackPhase.BUFFERING ||
    state.phase == VideoPlaybackPhase.ENDED ||
    state.phase == VideoPlaybackPhase.ERROR ||
    !state.isPlaying

internal fun videoTransportControlsVisible(
    requestedVisible: Boolean,
    modalVisible: Boolean,
): Boolean = requestedVisible && !modalVisible

/** Retains an already attached feed surface, never grants playback eligibility. */
internal fun retainPausedFeedVideoSurface(
    feedOwnsPlayback: Boolean,
    fullscreen: Boolean,
    mediaId: String,
    selectedMediaId: String?,
    ownsPresentation: Boolean,
): Boolean = feedOwnsPlayback && !fullscreen && ownsPresentation &&
    (selectedMediaId == null || selectedMediaId == mediaId)

@Stable
@AndroidxOptIn(UnstableApi::class)
class VideoPlaybackCoordinator internal constructor(context: Context) {
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    private val appContext = context.applicationContext
    private val dataSourceFactory = VideoMediaDataSource.factory(appContext)
    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    private val preloadStatusControl = FeedTargetPreloadStatusControl()
    private val preloadManagerBuilder = DefaultPreloadManager.Builder(appContext, preloadStatusControl)
        .setDataSourceFactory(dataSourceFactory)
    private val preloadManager = preloadManagerBuilder.build()
    private val networkMonitor = AndroidVideoNetworkMonitor(appContext, ::onNetworkSnapshotChanged)

    val player: ExoPlayer = preloadManagerBuilder.buildExoPlayer(
        ExoPlayer.Builder(appContext).setAudioAttributes(audioAttributes, false),
    ).apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    var uiState by mutableStateOf(VideoPlaybackUiState())
        private set

    var renderedMediaId by mutableStateOf<String?>(null)
        private set

    var playbackDiagnostics by mutableStateOf(VideoPlaybackDiagnostics())
        private set

    var trackOptions by mutableStateOf(PlaybackTrackOptions())
        private set

    var networkSnapshot by mutableStateOf(VideoNetworkSnapshot())
        private set

    var automaticVideoQuality by mutableStateOf(VideoQuality.HIGH)
        private set

    var currentCues by mutableStateOf<List<Cue>>(emptyList())
        private set

    // VIDEO01_SYSTEM_PAUSE_REACHES_THE_SELECTION_V1 (#227): ExoPlayer pauses on its
    // own when headphones disconnect or another app takes audio focus for good.
    // The surface that owns the video turns this into SubshellState's manual
    // pause, so the play button and the selection agree with the player.
    var systemPause by mutableStateOf<VideoSystemPause?>(null)
        private set

    // VIDEO06_SILENT_VIDEO_LOOPS_WITHOUT_A_MUTE_BUTTON_V1 (#227): Reddit serves GIFs
    // as MP4 without sound; known from the tracks, so the Post codec is unchanged.
    var silentMediaId by mutableStateOf<String?>(null)
        private set

    // VIDEO07_PLAYBACK_SPEED_V1 (#227): the reader's speed for this video; a new
    // video starts at 1x. Holding for 2x lends the speed and gives it back.
    var playbackSpeed by mutableFloatStateOf(1f)
        private set
    var holdingDoubleSpeed by mutableStateOf(false)
        private set
    private var loopVideosSetting = false
    private var systemPauseSequence = 0L

    private val registeredMedia = linkedMapOf<String, RegisteredVideoMedia>()
    private var cancelledLoadReloaded = false

    // MEDIA04_RELEASED_COORDINATOR_IS_INERT_V1: Compose onDispose handlers and
    // effects can still call in after the ViewModel released the player and
    // the preload manager; every entry point is then a no-op.
    private var released = false
    private var preloadCandidateMediaId: String? = null
    private var preloadCandidateRank: Int? = null
    private var preloadViewportMotion: FeedViewportMotion = FeedViewportMotion.IDLE

    private var activeOwner by mutableStateOf<Any?>(null)
    private var activeMedia: VideoMedia? = null
    private var activeSourceIndex = 0
    private var playRequested = false
    private var currentMuted: Boolean? = null
    private var handlesAudioFocus = false
    private var currentQuality: VideoQuality? = null
    private var qualityProfiles = VideoNetworkQualityProfiles()
    private var playbackDefaults = VideoPlaybackDefaults()
    private var sessionTrackSelection = PlaybackTrackSelectionState()
    private var activeTransitionId = 0L
    private var terminalErrorCode: Int? = null
    // ISSUE11A3: bounded failure metadata belongs to one playback attempt only.
    private var terminalVideoFailure: VideoPlaybackFailure? = null
    private var videoFailureAttemptId = 0L
    private val videoSourceFailures = mutableListOf<VideoSourceFailure>()
    private var scrubbingOwner: Any? = null
    private var scrubbingMediaId: String? = null
    private var resumeAfterScrubbing = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            MediaPerformanceTrace.mark(
                event = "media_item_transition",
                mediaId = mediaItem?.mediaId ?: activeMedia?.mediaId,
                transitionId = activeTransitionId,
                details = "reason=$reason",
            )
        }

        override fun onTracksChanged(tracks: Tracks) {
            playbackDiagnostics = playbackDiagnostics.copy(
                tracks = trackInventoryDiagnostics(tracks),
            )
            trackOptions = playbackTrackOptions(tracks, sessionTrackSelection)
            noteSoundOfActiveMedia(tracks)
        }

        override fun onCues(cueGroup: CueGroup) {
            currentCues = cueGroup.cues.toList()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishUiState()
            MediaPerformanceTrace.mark(
                event = if (playbackState == Player.STATE_READY) "player_ready" else "playback_state",
                mediaId = activeMedia?.mediaId,
                transitionId = activeTransitionId,
                details = "state=${playbackStateName(playbackState)}",
            )
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            handlePlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishUiState()
            MediaPerformanceTrace.mark(
                event = "is_playing_changed",
                mediaId = activeMedia?.mediaId,
                transitionId = activeTransitionId,
                details = "is_playing=$isPlaying volume=${player.volume}",
            )
            if (isPlaying && player.volume > 0f) {
                MediaPerformanceTrace.mark(
                    event = "audible_playback_started",
                    mediaId = activeMedia?.mediaId,
                    transitionId = activeTransitionId,
                )
            }
        }

        override fun onRenderedFirstFrame() {
            // LOW_MEDIA_FIRST_FRAME_OF_THE_CURRENT_ITEM_ONLY_V1 (OCB-650): a
            // first frame posted by the previous item before attach switched
            // media does not mark the new video as rendered.
            if (player.currentMediaItem?.mediaId != activeMedia?.mediaId) return
            renderedMediaId = activeMedia?.mediaId
            MediaPerformanceTrace.mark(
                event = "first_frame_rendered",
                mediaId = activeMedia?.mediaId,
                transitionId = activeTransitionId,
                details = "buffered_duration_ms=${player.totalBufferedDuration}",
            )
            MediaPerformanceTrace.endTransition(
                transitionId = activeTransitionId,
                mediaId = activeMedia?.mediaId,
                outcome = "first_frame",
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val media = activeMedia ?: return
            if (!videoFailureBelongsToCurrentPlayback(
                    activeMediaId = media.mediaId,
                    playerMediaId = player.currentMediaItem?.mediaId,
                    hasOwner = activeOwner != null,
                    playRequested = playRequested,
                    isCurrentPlayerError = player.playerError === error,
                )
            ) return
            val sourceFailure = videoSourceFailure(
                mediaId = media.mediaId,
                attemptId = videoFailureAttemptId,
                sourceIndex = activeSourceIndex,
                media3ErrorCode = error.errorCode,
                cause = error,
                httpStatusCodeOf = { cause ->
                    (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                        ?.responseCode
                        ?: (cause as? VideoProviderHttpException)?.statusCode
                },
            ) ?: run {
                // LOW_MEDIA_CANCELLED_LOAD_IS_RELOADED_V1 (OCB-654): a load that
                // ended in a cancellation is not a broken video (Issue 11), but
                // the player has stopped; it reloads the source once, quietly.
                if (!cancelledLoadReloaded) {
                    cancelledLoadReloaded = true
                    loadSource(
                        media = media,
                        sourceIndex = activeSourceIndex,
                        startPositionMs = fallbackStartPositionMs(player.currentPosition),
                        keepTrackChoices = true,
                    )
                    return
                }
                VideoSourceFailure(
                    mediaId = media.mediaId,
                    attemptId = videoFailureAttemptId,
                    sourceIndex = activeSourceIndex,
                    media3ErrorCode = error.errorCode,
                    httpStatusCode = null,
                    transportKind = VideoFailureTransportKind.OTHER,
                )
            }
            videoSourceFailures.removeAll { it.sourceIndex == activeSourceIndex }
            videoSourceFailures += sourceFailure
            MediaPerformanceTrace.mark(
                event = "player_error",
                mediaId = media.mediaId,
                transitionId = activeTransitionId,
                details = "error_code=${error.errorCode} source_index=$activeSourceIndex " +
                    "http_status=${sourceFailure.httpStatusCode ?: -1} attempt=$videoFailureAttemptId",
            )
            val nextSourceIndex = activeSourceIndex + 1
            if (playRequested && nextSourceIndex < media.sources.size) {
                MediaPerformanceTrace.mark(
                    event = "fallback_source_selected",
                    mediaId = media.mediaId,
                    transitionId = activeTransitionId,
                    details = "source_index=$nextSourceIndex",
                )
                loadSource(
                    media = media,
                    sourceIndex = nextSourceIndex,
                    startPositionMs = fallbackStartPositionMs(player.currentPosition),
                )
            } else {
                terminalVideoFailure = VideoPlaybackFailure(
                    mediaId = media.mediaId,
                    attemptId = videoFailureAttemptId,
                    sources = videoSourceFailures.toList(),
                )
                terminalErrorCode = error.errorCode
                playRequested = false
                player.pause()
                publishUiState()
            }
        }
    }



    private val diagnosticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                videoDecoderName = decoderName,
                videoDecoderInitializationMs = initializationDurationMs,
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                audioDecoderName = decoderName,
                audioDecoderInitializationMs = initializationDurationMs,
            )
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                activeVideoFormat = format.toVideoTrackFormatDiagnostics(),
            )
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                activeAudioFormat = format.toVideoTrackFormatDiagnostics(),
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                droppedVideoFrames = playbackDiagnostics.droppedVideoFrames + droppedFrames,
            )
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                lastCodecEvent = "Video · ${videoCodecError.javaClass.simpleName}",
            )
        }

        override fun onAudioCodecError(
            eventTime: AnalyticsListener.EventTime,
            audioCodecError: Exception,
        ) {
            playbackDiagnostics = playbackDiagnostics.copy(
                lastCodecEvent = "Audio · ${audioCodecError.javaClass.simpleName}",
            )
        }
    }

    private val preloadListener = object : PreloadManagerListener {
        override fun onCompleted(mediaItem: MediaItem) {
            MediaPerformanceTrace.mark(
                event = "preload_completed",
                mediaId = mediaItem.mediaId,
                transitionId = activeTransitionId,
                details = "rank=${registeredMedia[mediaItem.mediaId]?.rank ?: C.INDEX_UNSET}",
            )
        }

        override fun onError(exception: PreloadException) {
            MediaPerformanceTrace.mark(
                event = "preload_error",
                mediaId = exception.mediaItem.mediaId,
                transitionId = activeTransitionId,
                details = "error=${normalizeMediaPerformanceToken(exception.cause?.javaClass?.simpleName ?: exception.javaClass.simpleName)}",
            )
        }
    }

    init {
        MediaPerformanceTrace.installAndroidLogSink()
        player.addListener(listener)
        player.addAnalyticsListener(diagnosticsListener)
        preloadManager.addListener(preloadListener)
        networkMonitor.start()
    }

    fun updateQualityProfiles(
        unmetered: VideoQuality,
        metered: VideoQuality,
    ) {
        if (released) return
        val profiles = VideoNetworkQualityProfiles(unmetered = unmetered, metered = metered)
        if (qualityProfiles == profiles) return
        qualityProfiles = profiles
        activeMedia?.let { media -> applyQualityForMedia(media, reason = "settings") }
    }

    /** Settings' defaults for subtitles and audio; they apply from the next video. */
    fun updatePlaybackDefaults(defaults: VideoPlaybackDefaults) {
        if (released) return
        playbackDefaults = defaults
    }

    fun choosePlaybackSpeed(speed: Float) {
        if (released) return
        playbackSpeed = speed.coerceIn(VIDEO_SPEED_CHOICES.first(), VIDEO_SPEED_CHOICES.last())
        if (!holdingDoubleSpeed) player.setPlaybackSpeed(playbackSpeed)
    }

    /** VIDEO07_HOLD_FOR_DOUBLE_SPEED_V1: 2x while held, then the chosen speed again. */
    fun holdDoubleSpeed(holding: Boolean) {
        if (released || holdingDoubleSpeed == holding) return
        holdingDoubleSpeed = holding
        player.setPlaybackSpeed(if (holding) VIDEO_HOLD_SPEED else playbackSpeed)
    }

    fun updateLoopVideos(enabled: Boolean) {
        if (released) return
        loopVideosSetting = enabled
        applyRepeatMode()
    }

    internal fun noteSoundOfActiveMedia(tracks: Tracks) {
        val mediaId = activeMedia?.mediaId ?: return
        val silent = videoTracksAreSilent(tracks)
        val next = if (silent) mediaId else silentMediaId.takeUnless { it == mediaId }
        if (next == silentMediaId) return
        silentMediaId = next
        applyRepeatMode()
    }

    private fun applyRepeatMode() {
        val silent = silentMediaId != null && silentMediaId == activeMedia?.mediaId
        val repeatMode = videoRepeatMode(loopVideosSetting || silent)
        if (player.repeatMode == repeatMode) return
        player.repeatMode = repeatMode
    }

    /** Display ownership is independent of permission to advance playback. */
    internal fun ownsPresentation(owner: Any, mediaId: String): Boolean =
        activeOwner === owner && activeMedia?.mediaId == mediaId

    /**
     * ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1 (#251): the video a headset,
     * Bluetooth or system media button may play or pause: the one a surface
     * presents right now, only while it plays with sound (a muted or silent
     * video leaves the buttons to other apps).
     */
    internal fun mediaButtonVideoId(): String? {
        if (released || activeOwner == null) return null
        val mediaId = activeMedia?.mediaId ?: return null
        if (currentMuted != false || silentMediaId == mediaId) return null
        return mediaId
    }

    fun attach(
        owner: Any,
        media: VideoMedia,
        muted: Boolean,
        transitionId: Long,
        rank: Int,
        playWhenReady: Boolean,
        initialPositionMs: Long? = null,
    ) {
        if (released) return
        registerMedia(media, rank)
        if (rank != VIDEO_UNRANKED) {
            preloadManager.setCurrentPlayingIndex(rank)
            preloadManager.invalidate()
        }
        activeTransitionId = transitionId
        MediaPerformanceTrace.mark(
            event = "presentation_attached",
            mediaId = media.mediaId,
            transitionId = transitionId,
            details = "same_media=${sameVideoMedia(activeMedia, media)} play_when_ready=$playWhenReady playback_state=${playbackStateName(player.playbackState)} buffered_duration_ms=${player.totalBufferedDuration}",
        )
        activeOwner = owner
        // A new play request (or another video) ends the last system pause, so a
        // surface composed later does not pause the video again.
        if (playWhenReady || systemPause?.mediaId != media.mediaId) systemPause = null
        // Reattaching the same failed video must not silently replay its failed sources.
        if (sameVideoMedia(activeMedia, media) && terminalErrorCode != null) {
            playRequested = false
            player.pause()
            publishUiState()
            return
        }
        playRequested = playWhenReady
        terminalErrorCode = null
        terminalVideoFailure = null
        applyQualityForMedia(media, reason = "attach")
        setMuted(muted)

        if (!sameVideoMedia(activeMedia, media)) {
            clearScrubbingState()
            renderedMediaId = null
            cancelledLoadReloaded = false
            activeMedia = media
            applyRepeatMode()
            holdingDoubleSpeed = false
            playbackSpeed = 1f
            player.setPlaybackSpeed(1f)
            loadSource(
                media = media,
                sourceIndex = 0,
                startPositionMs = initialPositionMs?.coerceAtLeast(0L) ?: 0L,
            )
        } else {
            // Only the signatures differ: keep the loaded item, remember the
            // fresh links for a later retry or fallback.
            activeMedia = media
            if (player.playbackState == Player.STATE_IDLE) {
                MediaPerformanceTrace.mark(
                    event = "prepare_called",
                    mediaId = media.mediaId,
                    transitionId = activeTransitionId,
                    details = "reason=idle_same_media",
                )
                player.prepare()
            }
            if (playWhenReady) {
                MediaPerformanceTrace.mark(
                    event = "play_called",
                    mediaId = media.mediaId,
                    transitionId = activeTransitionId,
                    details = "reason=presentation_attach",
                )
                if (player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
            } else {
                player.pause()
            }
        }

        // Preparing, replacing, or moving a MediaItem between surfaces must never override mute.
        setMuted(muted)
        publishUiState()
    }

    /** The media registered for [mediaId], for tests (LOWUI8_VIDEO_FOLLOWS_ITS_SOURCES_V1). */
    internal fun registeredMediaForTest(mediaId: String): VideoMedia? = registeredMedia[mediaId]?.media

    private var progressOverrideForTest: VideoPlaybackProgress? = null

    /** Robolectric cannot decode video; tests set the position the player would report. */
    internal fun overrideProgressForTest(progress: VideoPlaybackProgress?) {
        progressOverrideForTest = progress
    }

    /** Robolectric cannot decode video; tests set the state the player would publish. */
    internal fun overrideUiStateForTest(state: VideoPlaybackUiState) {
        uiState = state
    }

    fun registerMedia(media: VideoMedia, rank: Int) {
        if (released) return
        val existing = registeredMedia[media.mediaId]
        if (existing != null && sameVideoMedia(existing.media, media) && existing.rank == rank) return
        if (existing != null) unregisterMedia(media.mediaId)
        // LOWUI4C_VIDEO_STREAMS_ONLY_V1 (OCB-108): the download system is
        // retired (owner decision); playback always uses the media's own source.
        // (Replaces LOW_MEDIA_ONE_DOWNLOAD_LOOKUP_PER_REGISTRATION_V1.)
        val source = media.sources[0]
        if (rank == VIDEO_UNRANKED) {
            registeredMedia[media.mediaId] = RegisteredVideoMedia(
                media = media,
                mediaItem = mediaItemFor(media, sourceIndex = 0, source = source),
                compositeMediaSource = null,
                rank = rank,
            )
            return
        }
        // MEDIA04_ONE_REGISTRATION_PER_PRELOAD_RANK_V1: preload status is
        // keyed by rank, so another video still registered at this rank (an
        // older feed generation) would share its remote/local flag. Drop it,
        // unless it is the video playing now (playback uses its preloaded
        // source).
        registeredMedia.values
            .filter { other -> other.rank == rank && other.media.mediaId != media.mediaId }
            .map { other -> other.media.mediaId }
            .filter { otherId -> otherId != activeMedia?.mediaId }
            .forEach(::unregisterMedia)

        val mediaItem = mediaItemFor(media, sourceIndex = 0, source = source)
        val compositeMediaSource = compositeMediaSourceFor(media, sourceIndex = 0, source = source)
        registeredMedia[media.mediaId] = RegisteredVideoMedia(
            media = media,
            mediaItem = mediaItem,
            compositeMediaSource = compositeMediaSource,
            rank = rank,
        )
        preloadStatusControl.updateMedia(
            rank = rank,
            remoteSource = source.usesRemoteTransport(),
        )
        if (compositeMediaSource != null) {
            preloadManager.add(compositeMediaSource, rank)
        } else {
            preloadManager.add(mediaItem, rank)
        }
        trimRegisteredMedia(aroundRank = rank)
        preloadManager.invalidate()
        MediaPerformanceTrace.mark(
            event = "preload_media_registered",
            mediaId = media.mediaId,
            transitionId = activeTransitionId,
            details = "rank=$rank composite=${compositeMediaSource != null} source_count=${preloadManager.getSourceCount()}",
        )
    }

    private fun unregisterMedia(mediaId: String) {
        val registered = registeredMedia.remove(mediaId) ?: return
        if (registered.rank == VIDEO_UNRANKED) return
        registered.compositeMediaSource?.let { preloadManager.remove(it) }
            ?: preloadManager.remove(registered.mediaItem)
        if (registeredMedia.values.none { it.rank == registered.rank }) {
            preloadStatusControl.removeMedia(registered.rank)
        }
    }

    /** Keeps at most [VIDEO_MAX_REGISTERED_MEDIA]; the farthest from [aroundRank] go first. */
    private fun trimRegisteredMedia(aroundRank: Int) {
        val excess = registeredMedia.size - VIDEO_MAX_REGISTERED_MEDIA
        if (excess <= 0) return
        registeredMedia.values
            .filter { it.media.mediaId != activeMedia?.mediaId && it.rank != VIDEO_UNRANKED }
            .sortedByDescending { abs(it.rank - aroundRank) }
            .take(excess)
            .map { it.media.mediaId }
            .forEach(::unregisterMedia)
    }

    internal val registeredMediaCountForTest: Int
        get() = registeredMedia.size

    internal val preloadSourceCountForTest: Int
        get() = preloadManager.getSourceCount()

    fun updatePreloadCandidate(
        mediaId: String?,
        rank: Int?,
        viewportMotion: FeedViewportMotion = FeedViewportMotion.IDLE,
    ) {
        if (released) return
        if (
            preloadCandidateMediaId == mediaId &&
            preloadCandidateRank == rank &&
            preloadViewportMotion == viewportMotion
        ) {
            return
        }
        preloadCandidateMediaId = mediaId
        preloadCandidateRank = rank
        preloadViewportMotion = viewportMotion
        val focusRank = rank ?: C.INDEX_UNSET
        preloadStatusControl.focusRank = focusRank
        preloadStatusControl.viewportMotion = viewportMotion
        val activeRank = activeMedia?.mediaId
            ?.let { registeredMedia[it]?.rank }
            ?.takeIf { it != VIDEO_UNRANKED }
        val rankingAnchor = activeRank ?: focusRank
        if (rankingAnchor != C.INDEX_UNSET) preloadManager.setCurrentPlayingIndex(rankingAnchor)
        preloadManager.invalidate()
        MediaPerformanceTrace.mark(
            event = "preload_focus_changed",
            mediaId = mediaId,
            transitionId = activeTransitionId,
            details =
                "rank=$focusRank motion=${viewportMotion.name.lowercase()} " +
                    "network=${networkSnapshot.networkClass.name.lowercase()}",
        )
    }

    fun playbackProgress(mediaId: String): VideoPlaybackProgress {
        progressOverrideForTest?.takeIf { it.mediaId == mediaId }?.let { return it }
        if (activeMedia?.mediaId != mediaId) {
            return VideoPlaybackProgress(mediaId = mediaId)
        }
        val durationMs = normalizedVideoDuration(player.duration)
        val positionMs = if (durationMs != null) {
            clampVideoSeekPosition(player.currentPosition, durationMs)
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        return VideoPlaybackProgress(
            mediaId = mediaId,
            positionMs = positionMs,
            bufferedPositionMs = durationMs?.let { clampVideoSeekPosition(player.bufferedPosition, it) } ?: 0L,
            durationMs = durationMs,
            isSeekable = durationMs != null &&
                player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        )
    }

    fun beginScrubbing(owner: Any, mediaId: String): Boolean {
        if (released) return false
        val progress = playbackProgress(mediaId)
        if (!progress.isSeekable) return false
        if (scrubbingOwner === owner && scrubbingMediaId == mediaId) return true
        // MEDIA04: another surface showing the same video cannot take the
        // player from its owner by dragging its slider.
        if (activeOwner != null && activeOwner !== owner) return false

        scrubbingOwner = owner
        scrubbingMediaId = mediaId
        activeOwner = owner
        resumeAfterScrubbing = player.playWhenReady
        // VIDEO03_SCRUBBING_SHOWS_LIVE_FRAMES_V1 (#227): ExoPlayer's scrubbing mode
        // makes the repeated seeks of a drag cheap, so the picture follows the thumb.
        player.setScrubbingModeEnabled(true)
        MediaPerformanceTrace.mark(
            event = "scrubbing_started",
            mediaId = mediaId,
            transitionId = activeTransitionId,
            details = "resume_after=$resumeAfterScrubbing position_ms=${progress.positionMs}",
        )
        player.pause()
        publishUiState()
        return true
    }

    fun finishScrubbing(owner: Any, mediaId: String, positionMs: Long) {
        if (released) return
        if (scrubbingOwner !== owner || scrubbingMediaId != mediaId) return
        val progress = playbackProgress(mediaId)
        val targetPositionMs = progress.durationMs?.let { durationMs ->
            clampVideoSeekPosition(positionMs, durationMs)
        }
        val shouldResume = resumeAfterScrubbing && playRequested
        clearScrubbingState()

        if (targetPositionMs != null && progress.isSeekable) {
            player.seekTo(targetPositionMs)
            MediaPerformanceTrace.mark(
                event = "scrubbing_finished",
                mediaId = mediaId,
                transitionId = activeTransitionId,
                details = "position_ms=$targetPositionMs resumed=$shouldResume",
            )
        }
        if (shouldResume) player.play() else player.pause()
        publishUiState()
    }

    /** A move during a drag: show that frame now; the drag's end still decides. */
    fun scrubTo(owner: Any, mediaId: String, positionMs: Long) {
        if (released) return
        if (scrubbingOwner !== owner || scrubbingMediaId != mediaId) return
        val durationMs = playbackProgress(mediaId).durationMs ?: return
        player.seekTo(clampVideoSeekPosition(positionMs, durationMs))
    }

    /** TalkBack's 10-second steps; only the surface that owns the video may seek. */
    fun seekBy(owner: Any, mediaId: String, deltaMs: Long) {
        if (released) return
        if (activeOwner !== owner || activeMedia?.mediaId != mediaId) return
        val progress = playbackProgress(mediaId)
        val durationMs = progress.durationMs ?: return
        if (!progress.isSeekable) return
        player.seekTo(clampVideoSeekPosition(progress.positionMs + deltaMs, durationMs))
        publishUiState()
    }

    fun cancelScrubbing(owner: Any) {
        if (released) return
        if (scrubbingOwner !== owner) return
        clearScrubbingState()
        publishUiState()
    }

    fun detach(owner: Any, preservePlaybackIntent: Boolean = false) {
        if (released) return
        if (activeOwner !== owner) return
        if (scrubbingOwner === owner) clearScrubbingState()
        MediaPerformanceTrace.mark(
            event = "owner_detached",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
            details = "preserve_playback_intent=$preservePlaybackIntent",
        )
        activeOwner = null
        if (!preservePlaybackIntent) {
            playRequested = false
            player.pause()
        }
        publishUiState()
    }

    /** Stop a feed video's sound in the visibility callback, not on a later disposal. */
    fun pauseForFeedVisibility(mediaId: String) {
        if (released) return
        if (activeMedia?.mediaId != mediaId) return
        clearScrubbingState()
        playRequested = false
        player.pause()
        MediaPerformanceTrace.mark(
            event = "feed_visibility_pause",
            mediaId = mediaId,
            transitionId = activeTransitionId,
        )
        // Keep the source, surface ownership and position for the existing attach path.
        publishUiState()
    }

    fun retry(owner: Any) {
        if (activeOwner !== owner) return
        if (released) return
        val media = activeMedia ?: return
        clearScrubbingState()
        playRequested = true
        systemPause = null
        terminalErrorCode = null
        terminalVideoFailure = null
        renderedMediaId = null
        MediaPerformanceTrace.mark(
            event = "manual_retry",
            mediaId = media.mediaId,
            transitionId = activeTransitionId,
        )
        // LOW_MEDIA_RETRY_CONTINUES_WHERE_IT_FAILED_V1 (OCB-568)
        cancelledLoadReloaded = false
        loadSource(
            media,
            sourceIndex = 0,
            startPositionMs = fallbackStartPositionMs(player.currentPosition),
            keepTrackChoices = keepsTrackChoicesOnReload(fromSourceIndex = activeSourceIndex, toSourceIndex = 0),
        )
        setMuted(currentMuted ?: true)
    }

    fun replay(owner: Any) {
        if (released) return
        // MEDIA04: like retry, only the surface that owns the presentation may
        // start the video again; a stale surface cannot take the player.
        if (activeOwner !== owner) return
        val media = activeMedia ?: return
        clearScrubbingState()
        activeOwner = owner
        playRequested = true
        systemPause = null
        terminalErrorCode = null
        terminalVideoFailure = null
        MediaPerformanceTrace.mark(
            event = "manual_replay",
            mediaId = media.mediaId,
            transitionId = activeTransitionId,
        )
        player.seekToDefaultPosition()
        player.play()
        publishUiState()
    }

    fun onHostConfigurationChanged(configuration: Configuration) {
        if (released) return
        MediaPerformanceTrace.mark(
            event = "configuration_changed_in_place",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
            details =
                "orientation=${configuration.orientation} " +
                    "screen_width_dp=${configuration.screenWidthDp} " +
                    "screen_height_dp=${configuration.screenHeightDp} " +
                    "player_identity=${System.identityHashCode(player)}",
        )
    }

    fun pauseForBackground() {
        if (released) return
        clearScrubbingState()
        MediaPerformanceTrace.mark(
            event = "background_pause",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
        )
        playRequested = false
        activeOwner = null
        player.pause()
        publishUiState()
        applyAudioState(
            muted = currentMuted ?: true,
            handleAudioFocus = false,
        )
    }

    fun setMuted(muted: Boolean) {
        if (released) return
        applyAudioState(
            muted = muted,
            handleAudioFocus = playRequested && !muted,
        )
    }

    fun selectAutomaticVideoTrack() {
        if (released) return
        sessionTrackSelection = sessionTrackSelection.withAutomaticVideo()
        val quality = currentQuality ?: VideoQuality.HIGH
        player.trackSelectionParameters = applyAutomaticVideoQuality(
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false),
            quality,
        ).build()
        publishTrackOptions()
        markTrackSelection("video", "auto_${quality.name.lowercase()}")
    }

    fun selectVideoTrack(key: PlaybackTrackKey): Boolean {
        if (released) return false
        val group = supportedTrackGroup(key, C.TRACK_TYPE_VIDEO) ?: return false
        sessionTrackSelection = sessionTrackSelection.withFixedVideo(key)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setMaxVideoFrameRate(Int.MAX_VALUE)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, key.trackIndex))
            .build()
        publishTrackOptions()
        markTrackSelection("video", "fixed_${key.groupIndex}_${key.trackIndex}")
        return true
    }

    fun selectAutomaticAudioTrack() {
        if (released) return
        sessionTrackSelection = sessionTrackSelection.withAutomaticAudio()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        publishTrackOptions()
        markTrackSelection("audio", "auto")
    }

    fun selectAudioTrack(key: PlaybackTrackKey): Boolean {
        if (released) return false
        val group = supportedTrackGroup(key, C.TRACK_TYPE_AUDIO) ?: return false
        sessionTrackSelection = sessionTrackSelection.withFixedAudio(key)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, key.trackIndex))
            .build()
        publishTrackOptions()
        markTrackSelection("audio", "fixed_${key.groupIndex}_${key.trackIndex}")
        return true
    }

    fun selectAutomaticTextTrack() {
        if (released) return
        sessionTrackSelection = sessionTrackSelection.withAutomaticText()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        currentCues = emptyList()
        publishTrackOptions()
        markTrackSelection("text", "auto")
    }

    fun disableTextTracks() {
        if (released) return
        sessionTrackSelection = sessionTrackSelection.withTextDisabled()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        currentCues = emptyList()
        publishTrackOptions()
        markTrackSelection("text", "off")
    }

    fun selectTextTrack(key: PlaybackTrackKey): Boolean {
        if (released) return false
        val group = supportedTrackGroup(key, C.TRACK_TYPE_TEXT) ?: return false
        sessionTrackSelection = sessionTrackSelection.withFixedText(key)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, key.trackIndex))
            .build()
        currentCues = emptyList()
        publishTrackOptions()
        markTrackSelection("text", "fixed_${key.groupIndex}_${key.trackIndex}")
        return true
    }

    fun release() {
        if (released) return
        released = true
        clearScrubbingState()
        MediaPerformanceTrace.mark(
            event = "player_released",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
        )
        MediaPerformanceTrace.endTransition(
            transitionId = activeTransitionId,
            mediaId = activeMedia?.mediaId,
            outcome = "player_release",
        )
        networkMonitor.close()
        preloadManager.removeListener(preloadListener)
        player.removeAnalyticsListener(diagnosticsListener)
        player.removeListener(listener)
        playbackDiagnostics = VideoPlaybackDiagnostics()
        trackOptions = PlaybackTrackOptions()
        currentCues = emptyList()
        uiState = VideoPlaybackUiState()
        player.release()
        preloadManager.release()
    }

    private fun clearScrubbingState() {
        if (scrubbingOwner != null) player.setScrubbingModeEnabled(false)
        scrubbingOwner = null
        scrubbingMediaId = null
        resumeAfterScrubbing = false
    }

    private fun loadSource(
        media: VideoMedia,
        sourceIndex: Int,
        startPositionMs: Long = 0L,
        keepTrackChoices: Boolean = false,
    ) {
        activeSourceIndex = sourceIndex
        if (sourceIndex == 0) {
            videoFailureAttemptId += 1L
            videoSourceFailures.clear()
        }
        terminalErrorCode = null
        terminalVideoFailure = null
        playbackDiagnostics = initialVideoPlaybackDiagnostics(media, sourceIndex)
        // VIDEO04_TRACK_CHOICES_SURVIVE_A_RELOAD_V1 (#227): the same source loaded
        // again has the same tracks, so the reader's quality/audio/subtitle choice
        // stays; another source (fallback) or another video starts from Auto.
        if (!keepTrackChoices) resetSessionTrackSelections()
        uiState = VideoPlaybackUiState(
            mediaId = media.mediaId,
            phase = VideoPlaybackPhase.BUFFERING,
            isPlaying = false,
            playWhenReady = playRequested,
        )
        // LOWUI4C_VIDEO_STREAMS_ONLY_V1: no download can change the source
        // after registration any more (LOW_MEDIA_DOWNLOAD_CHANGE_REREGISTERS_V1
        // retired with the download system).
        val mediaItem = if (sourceIndex == 0) {
            registeredMedia[media.mediaId]?.mediaItem ?: mediaItemFor(media, sourceIndex)
        } else {
            mediaItemFor(media, sourceIndex)
        }
        val preloadedSource = if (sourceIndex == 0) preloadManager.getMediaSource(mediaItem) else null
        val directCompositeSource = if (preloadedSource == null) {
            compositeMediaSourceFor(media, sourceIndex)
        } else {
            null
        }
        MediaPerformanceTrace.mark(
            event = "media_item_set",
            mediaId = media.mediaId,
            transitionId = activeTransitionId,
            details = "source_index=$sourceIndex preloaded=${preloadedSource != null} composite=${directCompositeSource != null || media.sources[sourceIndex].isComposite} start_position_ms=$startPositionMs",
        )
        when {
            preloadedSource != null -> player.setMediaSource(preloadedSource, startPositionMs)
            directCompositeSource != null -> player.setMediaSource(directCompositeSource, startPositionMs)
            else -> player.setMediaItem(mediaItem, startPositionMs)
        }
        MediaPerformanceTrace.mark(
            event = "prepare_called",
            mediaId = media.mediaId,
            transitionId = activeTransitionId,
            details = "source_index=$sourceIndex",
        )
        player.prepare()
        if (playRequested) {
            MediaPerformanceTrace.mark(
                event = "play_called",
                mediaId = media.mediaId,
                transitionId = activeTransitionId,
                details = "source_index=$sourceIndex",
            )
            player.play()
        }
    }

    private fun compositeMediaSourceFor(
        media: VideoMedia,
        sourceIndex: Int,
        source: VideoMediaSource = media.sources[sourceIndex],
    ): MediaSource? {
        val companionAudio = source.companionAudio ?: return null
        val videoMediaSource = mediaSourceFactory.createMediaSource(
            mediaItemFor(media, sourceIndex, source),
        )
        val audioMediaSource = mediaSourceFactory.createMediaSource(
            mediaItemForTrack(
                mediaId = "${media.mediaId}:audio:$sourceIndex",
                source = companionAudio,
            ),
        )
        return MergingMediaSource(
            MERGE_ADJUST_PERIOD_TIME_OFFSETS,
            MERGE_CLIP_DURATIONS,
            videoMediaSource,
            audioMediaSource,
        )
    }

    private fun mediaItemForTrack(mediaId: String, source: VideoMediaTrackSource): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(source.uri)
            .apply {
                source.resolvedMimeType?.let { setMimeType(it) }
                progressiveRemoteCacheKey(
                    uri = source.uri,
                    kind = source.kind,
                    explicitCacheKey = source.cacheKey,
                    generatedCacheKey = "subshell:media-track:$mediaId",
                )?.let { setCustomCacheKey(it) }
            }
            .build()

    private fun mediaItemFor(
        media: VideoMedia,
        sourceIndex: Int,
        source: VideoMediaSource = media.sources[sourceIndex],
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(media.mediaId)
            .setUri(source.uri)
            .apply {
                source.resolvedMimeType?.let { setMimeType(it) }
                progressiveRemoteCacheKey(
                    uri = source.uri,
                    kind = source.kind,
                    explicitCacheKey = source.cacheKey,
                    generatedCacheKey = "subshell:video:${media.mediaId}:source:$sourceIndex",
                )?.let { setCustomCacheKey(it) }
                if (source.subtitleTracks.isNotEmpty()) {
                    setSubtitleConfigurations(
                        source.subtitleTracks.mapIndexed { index, track ->
                            MediaItem.SubtitleConfiguration.Builder(track.uri.toUri())
                                .setId("${media.mediaId}:text:$sourceIndex:$index")
                                .setMimeType(track.mimeType)
                                .setLanguage(track.language)
                                .setLabel(track.label)
                                .build()
                        },
                    )
                }
            }
            .build()
    }

    private fun onNetworkSnapshotChanged(snapshot: VideoNetworkSnapshot) {
        if (networkSnapshot == snapshot) return
        networkSnapshot = snapshot
        preloadStatusControl.networkSnapshot = snapshot
        preloadManager.invalidate()
        activeMedia?.let { media -> applyQualityForMedia(media, reason = "network") }
        MediaPerformanceTrace.mark(
            event = "preload_network_policy_changed",
            mediaId = preloadCandidateMediaId,
            transitionId = activeTransitionId,
            details =
                "class=${snapshot.networkClass.name.lowercase()} " +
                    "validated=${snapshot.validated} metered=${snapshot.metered} " +
                    "data_saver=${snapshot.dataSaverActive}",
        )
    }

    private fun publishUiState() {
        if (released) return
        uiState = VideoPlaybackUiState(
            mediaId = activeMedia?.mediaId,
            phase = videoPlaybackPhase(
                playbackState = player.playbackState,
                hasTerminalError = terminalErrorCode != null,
            ),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            errorCode = terminalErrorCode,
            failure = currentVideoPlaybackFailure(
                failure = terminalVideoFailure,
                mediaId = activeMedia?.mediaId,
                attemptId = videoFailureAttemptId,
            ),
        )
    }

    internal fun handlePlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (released || !videoPausedBySystem(playWhenReady, reason)) return
        val mediaId = activeMedia?.mediaId ?: return
        MediaPerformanceTrace.mark(
            event = "system_pause",
            mediaId = mediaId,
            transitionId = activeTransitionId,
            details = "reason=$reason",
        )
        systemPauseSequence += 1L
        systemPause = VideoSystemPause(mediaId = mediaId, sequence = systemPauseSequence)
    }

    private fun applyAudioState(
        muted: Boolean,
        handleAudioFocus: Boolean,
    ) {
        val targetVolume = playbackVolume(muted)
        if (
            currentMuted == muted &&
            handlesAudioFocus == handleAudioFocus &&
            player.volume == targetVolume
        ) {
            return
        }

        currentMuted = muted
        handlesAudioFocus = handleAudioFocus
        player.setAudioAttributes(audioAttributes, handleAudioFocus)
        // VIDEO01_HEADPHONES_UNPLUGGED_PAUSES_AUDIBLE_VIDEO_V1 (#227): only audible
        // playback pauses on "becoming noisy"; a muted video keeps playing.
        player.setHandleAudioBecomingNoisy(handleAudioFocus)
        player.volume = targetVolume
        MediaPerformanceTrace.mark(
            event = "audio_state_applied",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
            details = "muted=$muted handle_focus=$handleAudioFocus volume=$targetVolume",
        )
    }

    private fun supportedTrackGroup(
        key: PlaybackTrackKey,
        trackType: @C.TrackType Int,
    ): Tracks.Group? {
        val group = player.currentTracks.groups.getOrNull(key.groupIndex) ?: return null
        if (group.type != trackType) return null
        if (key.trackIndex !in 0 until group.length) return null
        return group.takeIf { it.isTrackSupported(key.trackIndex) }
    }

    private fun publishTrackOptions() {
        trackOptions = playbackTrackOptions(player.currentTracks, sessionTrackSelection)
    }

    private fun markTrackSelection(type: String, selection: String) {
        MediaPerformanceTrace.mark(
            event = "track_selection_changed",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
            details = "type=$type selection=$selection",
        )
    }

    private fun resetSessionTrackSelections() {
        sessionTrackSelection = playbackDefaults.initialSelection()
        currentCues = emptyList()
        // The panel shows the default from Settings before the tracks are known.
        trackOptions = PlaybackTrackOptions(selection = sessionTrackSelection)
        val quality = currentQuality ?: VideoQuality.HIGH
        player.trackSelectionParameters = applyAutomaticVideoQuality(
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .applyPlaybackDefaults(playbackDefaults),
            quality,
        ).build()
    }

    private fun applyQualityForMedia(media: VideoMedia, reason: String) {
        val quality = automaticVideoQualityFor(
            remoteSource = media.sources[
                if (sameVideoMedia(activeMedia, media)) activeSourceIndex else 0
            ].usesRemoteTransport(),
            networkClass = networkSnapshot.networkClass,
            profiles = qualityProfiles,
            offlineFallback = currentQuality,
        )
        applyQuality(quality, reason)
    }

    private fun applyQuality(quality: VideoQuality, reason: String) {
        automaticVideoQuality = quality
        if (currentQuality == quality) return
        currentQuality = quality
        if (sessionTrackSelection.videoMode == PlaybackTrackSelectionMode.AUTO) {
            player.trackSelectionParameters = applyAutomaticVideoQuality(
                player.trackSelectionParameters.buildUpon(),
                quality,
            ).build()
        }
        MediaPerformanceTrace.mark(
            event = "automatic_quality_profile_applied",
            mediaId = activeMedia?.mediaId,
            transitionId = activeTransitionId,
            details =
                "reason=$reason network=${networkSnapshot.networkClass.name.lowercase()} " +
                    "quality=${quality.name.lowercase()} fixed_override=" +
                    (sessionTrackSelection.videoMode == PlaybackTrackSelectionMode.FIXED),
        )
    }

    private fun applyAutomaticVideoQuality(
        builder: androidx.media3.common.TrackSelectionParameters.Builder,
        quality: VideoQuality,
    ): androidx.media3.common.TrackSelectionParameters.Builder {
        val policy = quality.toPlaybackPolicy()
        return builder
            .setMaxVideoSize(policy.maxVideoEdgePx, policy.maxVideoEdgePx)
            .setMaxVideoBitrate(policy.maxVideoBitrate)
            .setMaxVideoFrameRate(policy.maxVideoFrameRate)
    }
}

internal fun playbackVolume(muted: Boolean): Float = if (muted) 0f else 1f

// VIDEO05_PLAYBACK_DEFAULTS_IN_SETTINGS_V1 (#227): what a new video starts
// with; the playback options panel changes it for that video only.
data class VideoPlaybackDefaults(
    val subtitles: VideoSubtitleDefault = VideoSubtitleDefault.WHEN_MARKED,
    val audio: VideoAudioDefault = VideoAudioDefault.VIDEO_DEFAULT,
    val deviceLanguages: List<String> = emptyList(),
)

internal fun VideoPlaybackDefaults.initialSelection(): PlaybackTrackSelectionState =
    if (subtitles == VideoSubtitleDefault.OFF) {
        PlaybackTrackSelectionState(textMode = PlaybackTrackSelectionMode.OFF)
    } else {
        PlaybackTrackSelectionState()
    }

internal fun TrackSelectionParameters.Builder.applyPlaybackDefaults(
    defaults: VideoPlaybackDefaults,
): TrackSelectionParameters.Builder {
    when (defaults.subtitles) {
        VideoSubtitleDefault.OFF -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredTextLanguages()
            .setPreferredTextRoleFlags(0)
        VideoSubtitleDefault.WHEN_MARKED -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguages()
            .setPreferredTextRoleFlags(0)
        VideoSubtitleDefault.SYSTEM_CAPTIONS -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings()
    }
    when (defaults.audio) {
        VideoAudioDefault.VIDEO_DEFAULT -> setPreferredAudioLanguages()
        VideoAudioDefault.DEVICE_LANGUAGE -> setPreferredAudioLanguages(*defaults.deviceLanguages.toTypedArray())
    }
    return this
}

internal fun deviceLanguageTags(): List<String> =
    LocaleListCompat.getAdjustedDefault().toLanguageTags()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

internal val VIDEO_SPEED_CHOICES = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
internal const val VIDEO_HOLD_SPEED = 2f

internal fun videoSpeedButtonLabel(speed: Float): String = "${speed.toString().removeSuffix(".0")}×"

internal fun videoSpeedLabel(speed: Float): String =
    if (speed == 1f) "Normal" else "${speed.toString().removeSuffix(".0")}×"

/** Known tracks with video and no audio: a GIF served as MP4. */
internal fun videoTracksAreSilent(tracks: Tracks): Boolean =
    tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.containsType(C.TRACK_TYPE_AUDIO)

/**
 * VIDEO07_PICTURE_IN_PICTURE_V1: auto-enter (Android 12+) or enter on leaving
 * (older) only while SubshellState says a full-screen video plays with the
 * setting on; the window's mode is reported back so the player hides controls.
 */
@Composable
private fun PictureInPictureEffect(state: SubshellState, activity: android.app.Activity?) {
    val host = activity as? androidx.activity.ComponentActivity ?: return
    val video = state.pictureInPictureVideo()
    val eligible = video != null
    val aspect = video?.let { pictureInPictureAspect(it.videoWidth, it.videoHeight) }
    val settingOn = state.videoPictureInPicture
    // VIDEO11_PICTURE_IN_PICTURE_PLAY_PAUSE_V1: the small window's play/pause goes
    // through SubshellState, so a pause there is the reader's pause.
    val viewerPlaying = state.selectedMediaPost?.let { it.kind == PostKind.VIDEO && state.activeAnimatedMediaId == it.id } == true
    var paramsSent by remember { mutableStateOf(false) }
    DisposableEffect(host, eligible, aspect, settingOn, viewerPlaying) {
        val params = androidx.core.app.PictureInPictureParamsCompat.Builder()
            .setEnabled(eligible)
            .apply { aspect?.let { (w, h) -> setAspectRatio(android.util.Rational(w, h)) } }
            .apply { if (settingOn) setActions(listOf(pictureInPicturePlayPauseAction(host, viewerPlaying))) }
            .build()
        // Never touch the window's PiP parameters for a reader who has not turned it on.
        if (settingOn || paramsSent) {
            host.setPictureInPictureParams(params)
            paramsSent = settingOn
        }
        val onLeave = Runnable {
            if (eligible && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                host.enterPictureInPictureMode(params)
            }
        }
        host.addOnUserLeaveHintListener(onLeave)
        onDispose { host.removeOnUserLeaveHintListener(onLeave) }
    }
    DisposableEffect(host, state, settingOn) {
        if (!settingOn) return@DisposableEffect onDispose { }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val post = state.selectedMediaPost?.takeIf { it.kind == PostKind.VIDEO } ?: return
                if (state.activeAnimatedMediaId == post.id) state.stopVideo(post.id) else state.playVideo(post.id)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            host,
            receiver,
            android.content.IntentFilter(PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { host.unregisterReceiver(receiver) }
    }
    DisposableEffect(host, state) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            state.updateInPictureInPicture(info.isInPictureInPictureMode)
        }
        host.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host.removeOnPictureInPictureModeChangedListener(listener) }
    }
}

internal const val PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION = "app.subshell.prototype.action.PICTURE_IN_PICTURE_PLAY_PAUSE"

internal fun pictureInPicturePlayPauseLabel(playing: Boolean): String = if (playing) "Pause" else "Play"

private fun pictureInPicturePlayPauseAction(context: android.content.Context, playing: Boolean): android.app.RemoteAction {
    val label = pictureInPicturePlayPauseLabel(playing)
    val intent = android.content.Intent(PICTURE_IN_PICTURE_PLAY_PAUSE_ACTION).setPackage(context.packageName)
    val pending = android.app.PendingIntent.getBroadcast(
        context,
        0,
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val icon = android.graphics.drawable.Icon.createWithResource(
        context,
        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
    )
    return android.app.RemoteAction(icon, label, label, pending)
}

internal fun keepsTrackChoicesOnReload(fromSourceIndex: Int, toSourceIndex: Int): Boolean =
    fromSourceIndex == toSourceIndex

/** One pause the player made by itself; [sequence] makes a repeat a new event. */
data class VideoSystemPause(
    val mediaId: String,
    val sequence: Long,
)

internal fun videoPausedBySystem(playWhenReady: Boolean, reason: Int): Boolean =
    !playWhenReady && (
        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
        )

internal fun videoRepeatMode(loopVideos: Boolean): Int =
    if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

internal fun playbackStateName(playbackState: Int): String = when (playbackState) {
    Player.STATE_IDLE -> "idle"
    Player.STATE_BUFFERING -> "buffering"
    Player.STATE_READY -> "ready"
    Player.STATE_ENDED -> "ended"
    else -> "unknown_$playbackState"
}

val LocalVideoPlaybackCoordinator = staticCompositionLocalOf<VideoPlaybackCoordinator> {
    error("VideoPlaybackCoordinator was not provided")
}

@Composable
fun ProvideVideoPlaybackCoordinator(
    state: SubshellState,
    coordinator: VideoPlaybackCoordinator,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val activity = remember(context) { context.findActivity() }
    // ISSUE38_APP_LOCK_V1: a return to a locked app resumes video only after unlock.
    val appLocked = LocalAppLocked.current
    val appLockedNow by rememberUpdatedState(LocalAppLockedNow.current)
    var resumeAfterUnlock by remember { mutableStateOf(false) }
    PictureInPictureEffect(state = state, activity = activity)
    // ISSUE251_MEDIA_BUTTONS_CONTROL_THE_VIDEO_V1
    VideoMediaSessionEffect(state = state, coordinator = coordinator, appLocked = appLocked)

    // VIDEO02_AUTOPLAY_FOLLOWS_WIFI_ONLY_V1: the selection learns whether the
    // network is unmetered, so "Wi-Fi only" can hold autoplay on mobile data.
    val unmetered = coordinator.networkSnapshot.networkClass == VideoNetworkClass.UNMETERED
    LaunchedEffect(state, unmetered) {
        state.updateVideoNetworkUnmetered(unmetered)
    }
    LaunchedEffect(appLocked) {
        if (!appLocked && resumeAfterUnlock) {
            resumeAfterUnlock = false
            state.resumeVideoPlaybackAfterForeground()
        }
    }

    DisposableEffect(coordinator, lifecycleOwner, activity, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val changingConfigurations = activity?.isChangingConfigurations == true
                    if (shouldPauseVideoPlaybackForActivityStop(changingConfigurations)) {
                        coordinator.pauseForBackground()
                        state.suspendVideoPlaybackForBackground()
                    } else {
                        MediaPerformanceTrace.mark(
                            event = "configuration_stop_player_preserved",
                            mediaId = coordinator.uiState.mediaId,
                            details = "player_identity=${System.identityHashCode(coordinator.player)}",
                        )
                    }
                }

                // MEDIA07_APP_LOCK_READ_LIVE_AT_START_V1 (#150): the lock flips in
                // this same dispatch, before any recomposition; read it live.
                Lifecycle.Event.ON_START -> if (appLockedNow()) {
                    resumeAfterUnlock = true
                } else {
                    state.resumeVideoPlaybackAfterForeground()
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(coordinator, state.feedVideoMuted) {
        coordinator.setMuted(state.feedVideoMuted)
    }

    LaunchedEffect(coordinator, state.loopVideos) {
        coordinator.updateLoopVideos(state.loopVideos)
    }

    LaunchedEffect(coordinator, state.videoSubtitleDefault, state.videoAudioDefault) {
        coordinator.updatePlaybackDefaults(
            VideoPlaybackDefaults(
                subtitles = state.videoSubtitleDefault,
                audio = state.videoAudioDefault,
                deviceLanguages = deviceLanguageTags(),
            ),
        )
    }

    LaunchedEffect(coordinator, state.wifiVideoQuality, state.mobileVideoQuality) {
        coordinator.updateQualityProfiles(
            unmetered = state.wifiVideoQuality,
            metered = state.mobileVideoQuality,
        )
    }

    val preloadMediaId = state.preloadVideoMediaId
    val preloadViewportMotion = state.feedViewportState.motion
    val videoFeedGeneration = state.videoFeedGeneration
    val preloadRank = remember(preloadMediaId, videoFeedGeneration) {
        preloadMediaId?.let(state::videoFeedRank)
    }
    LaunchedEffect(
        coordinator,
        preloadMediaId,
        preloadRank,
        preloadViewportMotion,
        videoFeedGeneration,
    ) {
        coordinator.updatePreloadCandidate(
            mediaId = preloadMediaId,
            rank = preloadRank,
            viewportMotion = preloadViewportMotion,
        )
    }

    CompositionLocalProvider(
        LocalVideoPlaybackCoordinator provides coordinator,
        content = content,
    )
}

internal fun shouldPauseVideoPlaybackForActivityStop(
    isChangingConfigurations: Boolean,
): Boolean = !isChangingConfigurations

internal fun shouldPreserveVideoPlaybackIntentOnDetach(
    presentationHandoff: Boolean,
    isChangingConfigurations: Boolean,
): Boolean = presentationHandoff || isChangingConfigurations

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}
