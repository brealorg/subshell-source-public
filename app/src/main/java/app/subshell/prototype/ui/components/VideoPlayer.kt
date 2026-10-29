package app.subshell.prototype.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.activity.compose.BackHandler
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.SubtitleView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.subshell.prototype.media.remoteMediaDiagnosticScope
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.mediaDownloadFolder
import app.subshell.prototype.ui.mediaActionScope
import app.subshell.prototype.ui.InteractionSide
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.focusable
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.subshell.prototype.ui.playbackOptionsButtonShown
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.subshell.prototype.model.Post
import app.subshell.prototype.media.FeedVideoPosterImageState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VIDEO_UNRANKED
import app.subshell.prototype.media.retainPausedFeedVideoSurface
import app.subshell.prototype.media.MediaPerformanceTrace
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.media.VideoPlaybackDiagnostics
import app.subshell.prototype.media.VideoPlaybackPhase
import app.subshell.prototype.media.VideoSystemPause
import app.subshell.prototype.media.videoSpeedLabel
import app.subshell.prototype.media.videoSpeedButtonLabel
import app.subshell.prototype.media.VideoPlaybackProgress
import app.subshell.prototype.media.VideoPlaybackUiState
import app.subshell.prototype.media.VideoVisibilityPolicy
import app.subshell.prototype.media.videoControlsAutoHideEligible
import app.subshell.prototype.media.videoControlsMustRemainVisible
import app.subshell.prototype.media.videoTransportControlsVisible
import app.subshell.prototype.media.videoBufferedFraction
import app.subshell.prototype.media.formatVideoPlaybackTime
import app.subshell.prototype.media.findActivity
import app.subshell.prototype.media.findLifecycleOwner
import app.subshell.prototype.media.shouldPreserveVideoPlaybackIntentOnDetach
import app.subshell.prototype.ui.LocalMainPaneDestination
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.RemoteMediaKind
import app.subshell.prototype.ui.error.remoteMediaErrorPresentation
import app.subshell.prototype.ui.error.videoPlaybackErrorPresentation
import app.subshell.prototype.ui.mediaActionsAtStart
import app.subshell.prototype.ui.physicalLeftIsRowStart
import app.subshell.prototype.ui.symbols.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue

@Composable
private fun MissingVideoSourcePlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Symbols.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Video unavailable",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Reddit did not provide a playable video source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


enum class VideoPlayerPresentation {
    INLINE,
    FULLSCREEN,
}

private enum class PlaybackModal {
    OPTIONS,
    QUALITY,
    AUDIO,
    SUBTITLES,
    SPEED,
    DETAILS,
}

private val FULLSCREEN_CONTROL_TOUCH_WIDTH = 64.dp
private val FULLSCREEN_CONTROL_TOUCH_HEIGHT = 56.dp
private val FULLSCREEN_CONTROL_VISUAL_SIZE = 48.dp

@Composable
private fun FullscreenControlButton(
    fullscreen: Boolean,
    atStart: Boolean,
    onClick: () -> Unit,
) {
    val contentDescription = if (fullscreen) "Exit full screen" else "Enter full screen"
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(
                width = FULLSCREEN_CONTROL_TOUCH_WIDTH,
                height = FULLSCREEN_CONTROL_TOUCH_HEIGHT,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = if (atStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        // M3225_MEDIA_BUTTONS_ARE_FILLED_TONAL_ICON_BUTTONS_V1: the wide touch
        // area takes the press; the ripple shows on the visible circle.
        Box(
            modifier = Modifier
                .size(FULLSCREEN_CONTROL_VISUAL_SIZE)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = VIDEO_CHROME_BUTTON_ALPHA),
                )
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (fullscreen) Symbols.FullscreenExit else Symbols.Fullscreen,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Share and download for the full-screen menu (VIDEO11/VIDEO12). */
class VideoMediaActions(
    val share: suspend () -> Boolean,
    val download: suspend () -> String?,
)

/** Speed (with the setting on) and mute beside the time in full screen (VIDEO12). */
@Composable
private fun FullscreenQuickControls(
    speedLabel: String?,
    showMute: Boolean,
    muted: Boolean,
    onSpeed: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (speedLabel != null) {
            // VIDEO07_FULLSCREEN_SPEED_BUTTON_V1: the current speed, one tap to change it.
            // Owner: an icon instead of "1×"; the value stays in the description.
            IconButton(onClick = onSpeed) {
                Icon(
                    Symbols.Speed,
                    contentDescription = "Playback speed, $speedLabel",
                    tint = Color.White,
                )
            }
        }
        if (showMute) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (muted) Symbols.VolumeOff else Symbols.VolumeUp,
                    contentDescription = if (muted) "Turn sound on" else "Mute video",
                    tint = Color.White,
                )
            }
        }
    }
}

/** The round 48 dp button the video chrome uses for its actions. */
@Composable
private fun VideoChromeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // M3225_MEDIA_BUTTONS_ARE_FILLED_TONAL_ICON_BUTTONS_V1 (components-11)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = VIDEO_CHROME_BUTTON_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun VideoTopActionCluster(
    fullscreen: Boolean,
    showPlaybackOptions: Boolean,
    showMute: Boolean,
    muted: Boolean,
    atStart: Boolean,
    onPlaybackOptions: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val playbackOptionsAction: @Composable () -> Unit = {
        if (showPlaybackOptions) {
            VideoChromeIconButton(
                icon = Symbols.Settings,
                contentDescription = "Playback options",
                onClick = onPlaybackOptions,
            )
        }
    }
    val muteAction: @Composable () -> Unit = {
        if (showMute) {
            VideoChromeIconButton(
                icon = if (muted) Symbols.VolumeOff else Symbols.VolumeUp,
                contentDescription = if (muted) "Turn sound on" else "Mute video",
                onClick = onToggleMute,
            )
        }
    }
    val fullscreenAction: @Composable () -> Unit = {
        // VIDEO06_ONE_WAY_OUT_OF_FULL_SCREEN_V1: full screen keeps only its back
        // arrow; a second "Exit full screen" button beside it was a duplicate.
        if (!fullscreen) {
            FullscreenControlButton(
                fullscreen = fullscreen,
                atStart = atStart,
                onClick = onToggleFullscreen,
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (atStart) {
            fullscreenAction()
            muteAction()
            playbackOptionsAction()
        } else {
            playbackOptionsAction()
            muteAction()
            fullscreenAction()
        }
    }
}

@Composable
private fun VideoPosterFrame(
    post: Post,
    modifier: Modifier = Modifier,
    feedImageState: FeedVideoPosterImageState<Bitmap>? = null,
) {
    val localUri = post.localMediaUri?.takeIf(String::isNotBlank)
    val remoteUri = post.remoteImageUri?.takeIf(String::isNotBlank)

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            localUri != null -> LocalUriMedia(
                uri = localUri,
                mimeType = post.localMediaMimeType,
                contentDescription = post.mediaLabel,
                modifier = Modifier.fillMaxSize(),
            )

            remoteUri != null && feedImageState?.matches(post.id, remoteUri) == true -> {
                // Read the SAME decoded image in the static card and Media3 shutter.
                // Mounting a player must not start another Loading -> Ready image cycle.
                when (val result = feedImageState.result) {
                    null -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    is RemoteMediaLoadResult.Ready -> Image(
                        bitmap = result.value.asImageBitmap(),
                        contentDescription = post.mediaLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is RemoteMediaLoadResult.Failed -> MediaFailurePlaceholder(
                        presentation = remoteMediaErrorPresentation(
                            kind = RemoteMediaKind.IMAGE,
                            details = result.details,
                        ),
                        diagnosticScopeKey = remoteMediaDiagnosticScope("media_image", remoteUri),
                        onRetry = feedImageState::retry,
                    )
                }
            }

            remoteUri != null -> RemoteImage(
                uri = remoteUri,
                label = post.mediaLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                aspectRatio = null,
                containerColor = Color.Black,
            )

            else -> presentationMediaResource(post.mediaAsset)?.let { posterResource ->
                Image(
                    painter = painterResource(posterResource),
                    contentDescription = post.mediaLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The image load belongs to the feed card, not to a temporary shutter composition. */
@Composable
private fun rememberFeedVideoPosterImage(
    post: Post,
    enabled: Boolean,
): FeedVideoPosterImageState<Bitmap>? {
    if (!enabled || !post.localMediaUri.isNullOrBlank()) return null
    val uri = post.remoteImageUri?.takeIf(String::isNotBlank) ?: return null
    // VIDEOTABSWITCH01: a card composed again (a tab shown again) starts from the
    // decoded image in memory instead of a black loading frame.
    val image = remember(post.id, uri) {
        FeedVideoPosterImageState<Bitmap>(post.id, uri, initial = RemoteImageLoader.cachedBitmap(uri))
    }
    LaunchedEffect(image, image.retryNonce) {
        image.load(RemoteImageLoader::load)
    }
    LaunchedEffect(image, image.result) {
        MediaPerformanceTrace.mark(
            event = "feed_poster_image_state",
            mediaId = post.id,
            details = "image_owner=${System.identityHashCode(image)} image_state=${image.phase}",
        )
    }
    return image
}

@AndroidxOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalVideoPlayer(
    post: Post,
    state: SubshellState,
    modifier: Modifier = Modifier,
    presentation: VideoPlayerPresentation = VideoPlayerPresentation.INLINE,
    posterOnly: Boolean = false,
    onExitFullscreen: (() -> Unit)? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    initialPlaybackPositionMs: Long? = null,
    onPlaybackPositionChanged: (Long) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    inPostDetail: Boolean = false,
    // VIDEO11_FULLSCREEN_VIDEO_SHARES_AND_DOWNLOADS_V1: the viewer's share/download,
    // offered as rows of the full-screen options menu (VIDEO12).
    mediaActions: VideoMediaActions? = null,
    holdControls: Boolean = false,
    // VIDEO11_TABLETOP_SPLITS_PICTURE_AND_CONTROLS_V1: the fold's top in tabletop posture.
    tabletopVideoHeight: Dp? = null,
) {
    val context = LocalContext.current
    val coordinator = LocalVideoPlaybackCoordinator.current
    val windowView = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    val fullscreen = presentation == VideoPlayerPresentation.FULLSCREEN
    SideEffect { VideoPlayerCompositionProbe.compositions++ }
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val landscapeGestureControlsActive = fullscreenVideoGesturesActive(
        fullscreen = fullscreen,
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        masterEnabled = state.landscapeVideoGesturesEnabled,
        brightnessEnabled = state.landscapeBrightnessGestureEnabled,
        volumeEnabled = state.landscapeVolumeGestureEnabled,
    )
    var fullscreenGestureOverlay by remember(post.id, presentation) {
        mutableStateOf<FullscreenVideoGestureOverlayState?>(null)
    }
    var fullscreenGestureDismissToken by remember(post.id, presentation) { mutableIntStateOf(0) }
    var seekHint by remember(post.id, presentation) { mutableStateOf<String?>(null) }
    var seekHintToken by remember(post.id, presentation) { mutableIntStateOf(0) }
    LaunchedEffect(seekHintToken) {
        if (seekHint != null) {
            delay(DOUBLE_TAP_SEEK_HINT_MS)
            seekHint = null
        }
    }
    val fullscreenGestureScope = rememberCoroutineScope()
    val errorHost = LocalViewportErrorHostState.current
    // The level overlay stays a moment after a drag ends or is cut short.
    val lingerThenHideLevel: () -> Unit = {
        fullscreenGestureDismissToken += 1
        val token = fullscreenGestureDismissToken
        fullscreenGestureScope.launch {
            delay(FULLSCREEN_GESTURE_OVERLAY_LINGER_MS)
            if (fullscreenGestureDismissToken == token) fullscreenGestureOverlay = null
        }
    }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()

    if (fullscreen) {
        DisposableEffect(activity) {
            val window = activity?.window
            val previousBrightness = window?.attributes?.screenBrightness
            onDispose {
                if (window != null && previousBrightness != null) {
                    val attributes = window.attributes
                    attributes.screenBrightness = previousBrightness
                    window.attributes = attributes
                }
            }
        }
    }
    val surfaceOwner = remember(post.id, presentation) { Any() }
    // VIDEO06_FEED_VIDEO_STARTS_WITHOUT_CONTROLS_V1 (#227): an autoplayed feed video
    // showed its full controls while it buffered; in the feed they wait for a tap.
    val quietFeedControls = !fullscreen && !inPostDetail
    var controlsVisible by remember(post.id) { mutableStateOf(!quietFeedControls) }
    // Controls the reader asked for (a tap or a drag), not the ones an
    // autoplayed video shows by itself for a moment.
    var controlsShownByReader by remember(post.id) { mutableStateOf(false) }
    var controlsInteraction by remember(post.id) { mutableIntStateOf(0) }
    var mediaSharing by remember(post.id) { mutableStateOf(false) }
    var mediaDownload by remember(post.id) { mutableStateOf<String?>(null) }
    var playbackModal by remember(post.id) { mutableStateOf<PlaybackModal?>(null) }
    var isScrubbing by remember(post.id) { mutableStateOf(false) }
    var scrubPositionMs by remember(post.id) { mutableLongStateOf(0L) }
    var playbackProgress by remember(post.id) {
        mutableStateOf(VideoPlaybackProgress(mediaId = post.id))
    }
    var pendingInitialPositionMs by remember(post.id, presentation) {
        mutableLongStateOf(initialPlaybackPositionMs?.takeIf { it > 0L } ?: -1L)
    }
    val feedGeneration = state.videoFeedGeneration
    val rank = remember(post.id, feedGeneration) { state.videoFeedRank(post.id) ?: VIDEO_UNRANKED }
    // VIDEO06_TALL_FEED_VIDEO_IS_BOUNDED_V1: like feed images, a tall video is no
    // taller than 4:5 inline (it could not reach the autoplay threshold in landscape).
    val aspectRatio = if (fullscreen) mediaAspectRatio(post) else boundedInlineVideoAspectRatio(mediaAspectRatio(post))
    // LOWUI8_VIDEO_FOLLOWS_ITS_SOURCES_V1 (OCB-302): keyed on the post, so new
    // sources for the same id (a refresh, a local upload replaced by Reddit's)
    // are used; an equal result keeps its instance.
    val freshMedia = remember(post, context.packageName) {
        videoMediaForPost(context, post)
    }
    val media = remember(post.id, freshMedia) { freshMedia }
    if (media == null) {
        val unavailableModifier = if (fullscreen) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        }
        MissingVideoSourcePlaceholder(
            modifier = unavailableModifier,
        )
        return
    }
    // The coordinator applies the automatic ceiling for the source it actually
    // plays; the panel shows that value instead of computing its own (audit H6).
    val automaticVideoQuality = coordinator.automaticVideoQuality
    LaunchedEffect(coordinator, media, rank) {
        coordinator.registerMedia(media, rank)
    }

    val visibilityModifier = if (fullscreen) {
        modifier
    } else {
        modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            state.updateVideoVisibility(
                mediaId = post.id,
                surfaceOwner = surfaceOwner,
                metrics = VideoVisibilityPolicy.measureSurface(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    viewportWidth = windowView.width,
                    viewportHeight = windowView.height,
                    isTerminalItem = state.isTerminalFeedVideo(post.id),
                ),
            )
        }
    }
    DisposableEffect(surfaceOwner, presentation) {
        onDispose {
            if (!fullscreen) state.removeVideoSurface(surfaceOwner)
        }
    }

    val feedPausePresentation = !fullscreen && state.googleFeedOwnsPlayback
    // Stay above BOTH early returns: selection and shutter changes must not reload the poster.
    // VIDEOTABSWITCH01_FEED_POSTER_NEVER_RELOADS_V1: enabled for every feed card, not only
    // while the feed holds playback; flipping it dropped the remembered image (a black
    // card with a spinner on a tab switch).
    val feedPosterImage = rememberFeedVideoPosterImage(post, enabled = !fullscreen && !inPostDetail)

    if (posterOnly) {
        val posterModifier = if (fullscreen) {
            visibilityModifier.fillMaxSize()
        } else {
            visibilityModifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        }
        Box(posterModifier.background(Color.Black)) {
            VideoPosterFrame(
                post = post,
                modifier = Modifier.fillMaxSize(),
                feedImageState = feedPosterImage,
            )
        }
        return
    }

    val playing = state.activeAnimatedMediaId == post.id
    val manuallyPaused = state.isVideoManuallyPaused(post.id)
    val retainedFeedSurface = retainPausedFeedVideoSurface(
        feedOwnsPlayback = feedPausePresentation,
        fullscreen = fullscreen,
        mediaId = post.id,
        selectedMediaId = state.activeAnimatedMediaId,
        ownsPresentation = coordinator.ownsPresentation(surfaceOwner, post.id),
    )
    // Visibility still controls playing. Keep the SAME drawing surface while paused.
    val surfaceSelected = playing || manuallyPaused || retainedFeedSurface
    // VIDEOTABSWITCH01_A_LEAVING_TAB_SHOWS_THE_POSTER_V1: in a tab that is fading
    // out after a tap on another tab no live surface stays (Media3 keeps
    // SurfaceView, Google's choice from Android 7); the card shows its poster.
    val paneLeaving = !fullscreen &&
        LocalMainPaneDestination.current?.let { pane -> pane != state.destination } == true
    if (!surfaceSelected || paneLeaving) {
        val staticFrameModifier = if (fullscreen) {
            visibilityModifier.fillMaxSize()
        } else {
            visibilityModifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        }
        // ISSUE276_NO_PLAY_BUTTON_WHILE_AUTOPLAY_IS_ABOUT_TO_START_V1: a feed video
        // that starts by itself waits ~500 ms for the visibility dwell; a play
        // button then read as "tap to play". It shows only when the video will not
        // start by itself (autoplay off or Wi-Fi only on mobile data, NSFW still
        // covered); otherwise the poster alone, and a tap anywhere still plays it.
        val startsByItself = !fullscreen && !paneLeaving &&
            state.videoAutoplayAllowed && state.sensitiveMediaAutoplayAllowed(post)
        val frameModifier = if (startsByItself) {
            staticFrameModifier
                .clickable(onClickLabel = "Play video") { state.playVideo(post.id) }
                .semantics { contentDescription = "Play video" }
        } else {
            staticFrameModifier
        }
        Box(frameModifier.background(Color.Black)) {
            VideoPosterFrame(
                post = post,
                modifier = Modifier.fillMaxSize(),
                feedImageState = feedPosterImage,
            )
            if (!startsByItself) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(VIDEO_CENTER_BUTTON_SIZE),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = VIDEO_CENTER_BUTTON_SCRIM_ALPHA),
                    onClick = { state.playVideo(post.id) },
                ) {
                    Icon(
                        Symbols.PlayArrowFilled,
                        contentDescription = "Play video",
                        modifier = Modifier.padding(12.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        return
    }

    val playbackOwner = surfaceOwner
    val muted = state.feedVideoMuted
    val transitionId = state.activeVideoTransitionId
    val posterVisible = coordinator.renderedMediaId != post.id
    val playbackUiState = coordinator.uiState.takeIf { it.mediaId == post.id }
        ?: VideoPlaybackUiState(
            mediaId = post.id,
            phase = if (posterVisible) VideoPlaybackPhase.BUFFERING else VideoPlaybackPhase.READY,
            isPlaying = playing,
            playWhenReady = playing,
        )
    val controlsMustRemainVisible = manuallyPaused || holdControls || mediaSharing || mediaDownload != null ||
        videoControlsForcedVisible(playbackUiState, isScrubbing, quietFeedControls)
    val modalVisible = playbackModal != null
    // VIDEO07_PICTURE_IN_PICTURE_V1: the small window shows the picture only.
    val inPictureInPicture = state.inPictureInPicture
    // ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1: full screen only, never in PiP; a new
    // video, a rotation or leaving full screen starts again at 1×.
    val pinchToZoom = fullscreen && state.videoPinchToZoom && !inPictureInPicture
    // One state per video: the pinch detector keeps reading and writing this
    // holder, so a rotation resets its value instead of replacing it (a new
    // holder left the detector on the old one and the pinch did nothing).
    var videoZoom by remember(post.id, presentation) { mutableStateOf(FullscreenVideoZoom()) }
    LaunchedEffect(configuration.orientation) { videoZoom = FullscreenVideoZoom() }
    var videoAreaSize by remember(post.id, presentation) { mutableStateOf(Size.Zero) }
    // The picture's edges come from the decoded video, as ContentFrame fits it;
    // Reddit's width and height can differ (a narrower video showed black edges
    // when panned on the owner's phone), so they are only the fallback.
    val zoomAspectRatio = if (fullscreen) {
        fullscreenVideoZoomAspectRatio(
            decodedVideoSize = rememberPresentationState(coordinator.player).videoSizeDp ?: Size.Unspecified,
            fallback = aspectRatio,
        )
    } else {
        aspectRatio
    }
    // The pinch detector lives as long as the video; it reads the shape as it is now.
    val currentZoomAspectRatio by rememberUpdatedState(zoomAspectRatio)
    LaunchedEffect(pinchToZoom) {
        if (!pinchToZoom) videoZoom = FullscreenVideoZoom()
    }
    val videoZoomed = pinchToZoom && videoZoom.zoomed
    val showControls = videoTransportControlsVisible(
        requestedVisible = (controlsVisible || controlsMustRemainVisible) && !inPictureInPicture,
        modalVisible = modalVisible,
    )
    val systemChromeVisible = showControls || modalVisible
    val dismissPlaybackModal: () -> Unit = {
        playbackModal = null
        controlsVisible = true
    }

    // VIDEO09_PROGRESS_REDRAWS_ONLY_THE_TIMELINE_V1 (#227 audit H2): the poll writes
    // the position four times a second; only the timeline reads it, the player
    // reads the duration through derivedStateOf.
    LaunchedEffect(surfaceSelected, post.id, playbackUiState.phase, isScrubbing) {
        while (surfaceSelected) {
            if (!isScrubbing) {
                playbackProgress = coordinator.playbackProgress(post.id)
            }
            delay(VIDEO_PROGRESS_REFRESH_MS)
        }
    }
    val progressDurationMs by remember(post.id) { derivedStateOf { playbackProgress.durationMs } }
    // VIDEO09_FULLSCREEN_POSITION_SAVED_ON_STOP_V1 (audit H3): the viewer keeps the
    // position for a re-created surface; it is written when the screen stops or
    // the surface leaves, not on every poll (that recomposed the viewer 4x/s).
    if (fullscreen) {
        val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
        val currentOnPositionChanged by rememberUpdatedState(onPlaybackPositionChanged)
        DisposableEffect(lifecycleOwner, post.id) {
            fun savePosition() {
                val progress = coordinator.playbackProgress(post.id)
                if (progress.durationMs != null) currentOnPositionChanged(progress.positionMs)
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) savePosition()
            }
            lifecycleOwner?.lifecycle?.addObserver(observer)
            onDispose {
                savePosition()
                lifecycleOwner?.lifecycle?.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(feedPausePresentation, playing, retainedFeedSurface, post.id) {
        if (feedPausePresentation) {
            MediaPerformanceTrace.mark(
                event = "feed_surface_retention",
                mediaId = post.id,
                transitionId = transitionId,
                details = "retained=$retainedFeedSurface playing=$playing " +
                    "same_owner=${coordinator.ownsPresentation(playbackOwner, post.id)}",
            )
        }
    }

    LaunchedEffect(media.mediaId, transitionId, posterVisible) {
        MediaPerformanceTrace.mark(
            event = "poster_visibility_changed",
            mediaId = media.mediaId,
            transitionId = transitionId,
            details = "visible=$posterVisible",
        )
    }

    val requestFeedRoute = state.currentNavigationRoute
    val requestVisibilityEpoch = state.googleFeedVisibilityEpoch
    LaunchedEffect(
        playing, manuallyPaused, media, playbackOwner, transitionId, rank,
        feedPausePresentation, requestFeedRoute, requestVisibilityEpoch,
    ) {
        if (feedPausePresentation) {
            // Capture scope, not permission. A visibility callback may have revoked
            // this request before the effect got its turn on the application thread.
            state.applyCurrentGoogleFeedVideoRequest(
                mediaId = post.id,
                requestRoute = requestFeedRoute,
                requestVisibilityEpoch = requestVisibilityEpoch,
                onApply = { playNow, currentTransitionId ->
                    if (!playNow && coordinator.ownsPresentation(playbackOwner, post.id)) {
                        coordinator.pauseForFeedVisibility(post.id)
                    } else {
                        coordinator.attach(
                            owner = playbackOwner,
                            media = media,
                            muted = state.feedVideoMuted,
                            transitionId = currentTransitionId,
                            rank = state.videoFeedRank(post.id) ?: rank,
                            playWhenReady = playNow,
                            initialPositionMs = pendingInitialPositionMs.takeIf { it >= 0L },
                        )
                        pendingInitialPositionMs = -1L
                        state.completeVideoPresentationHandoff(post.id)
                    }
                },
                onRevoked = {
                    // Retention keeps pixels, never playback. Also cancels a matching
                    // player's pending playWhenReady while it is still buffering.
                    // Never pause another surface that has since acquired the player.
                    if (coordinator.ownsPresentation(playbackOwner, post.id)) {
                        coordinator.pauseForFeedVisibility(post.id)
                    }
                },
            )
            return@LaunchedEffect
        }
        // Standalone post/fullscreen behavior is unchanged.
        if (!playing && !manuallyPaused) return@LaunchedEffect
        val initialPositionMs = pendingInitialPositionMs.takeIf { it >= 0L }
        coordinator.attach(
            owner = playbackOwner,
            media = media,
            muted = muted,
            transitionId = transitionId,
            rank = rank,
            playWhenReady = playing,
            initialPositionMs = initialPositionMs,
        )
        pendingInitialPositionMs = -1L
        state.completeVideoPresentationHandoff(post.id)
    }
    // Feed visibility transitions are not surface lifetime transitions.
    // Keep the existing lifetime behavior outside the Google-managed feed.
    val surfaceLifetimeKey = if (feedPausePresentation) 0L else transitionId
    DisposableEffect(playbackOwner, media.mediaId, surfaceLifetimeKey) {
        MediaPerformanceTrace.mark(
            event = "surface_mounted",
            mediaId = media.mediaId,
            transitionId = transitionId,
            details = "aspect_ratio=$aspectRatio",
        )
        onDispose {
            coordinator.cancelScrubbing(playbackOwner)
            state.cancelVideoScrubbing(post.id)
            MediaPerformanceTrace.mark(
                event = "surface_unmounted",
                mediaId = media.mediaId,
                transitionId = transitionId,
            )
            coordinator.detach(
                owner = playbackOwner,
                preservePlaybackIntent = shouldPreserveVideoPlaybackIntentOnDetach(
                    presentationHandoff = state.isVideoPresentationHandoff(post.id),
                    isChangingConfigurations = context.findActivity()?.isChangingConfigurations == true,
                ),
            )
        }
    }

    LaunchedEffect(systemChromeVisible, presentation) {
        if (fullscreen) onControlsVisibilityChanged(systemChromeVisible)
    }

    val videoControlsAutoHideMillis = rememberAccessibleAutoHideMillis(VIDEO_CONTROLS_AUTO_HIDE_MS)
    // VIDEO10_TALKBACK_KEEPS_THE_CONTROLS_V1 (audit A1): with touch exploration on,
    // focus moved to a control would land on nothing once it hid.
    val touchExploration = rememberTouchExplorationEnabled()
    LaunchedEffect(
        controlsVisible,
        playbackModal,
        playbackUiState.phase,
        playbackUiState.isPlaying,
        isScrubbing,
        // VIDEO10_USING_A_CONTROL_RESTARTS_AUTO_HIDE_V1 (audit C7)
        controlsInteraction,
        touchExploration,
    ) {
        if (
            controlsVisible &&
            !modalVisible &&
            !touchExploration &&
            videoControlsAutoHideEligible(playbackUiState, isScrubbing)
        ) {
            delay(videoControlsAutoHideMillis)
            controlsVisible = false
        }
    }
    LaunchedEffect(manuallyPaused, playbackUiState.phase) {
        if (manuallyPaused || videoPhaseShowsControls(playbackUiState.phase, quietFeedControls)) {
            controlsVisible = true
        }
    }
    // VIDEO01_SYSTEM_PAUSE_REACHES_THE_SELECTION_V1: only the surface that owns
    // the video records the player's own pause as the reader's pause.
    val systemPause = coordinator.systemPause
    LaunchedEffect(systemPause) {
        if (
            videoSystemPauseStops(
                pause = systemPause,
                mediaId = post.id,
                ownsPresentation = coordinator.ownsPresentation(playbackOwner, post.id),
            )
        ) {
            state.stopVideo(post.id)
        }
    }
    // VIDEO03_FAB_STEPS_ASIDE_FOR_INLINE_CONTROLS_V1 (#227): the Create button sat
    // over the end of a feed video's seek bar; it steps aside while the reader
    // uses the inline controls.
    LaunchedEffect(showControls) {
        if (!showControls) controlsShownByReader = false
    }
    val readerUsesInlineControls = !fullscreen && showControls && controlsShownByReader
    DisposableEffect(readerUsesInlineControls, post.id) {
        if (readerUsesInlineControls) state.updateInlineVideoControls(post.id, visible = true)
        onDispose { state.updateInlineVideoControls(post.id, visible = false) }
    }
    // VIDEO01_PLAYING_VIDEO_KEEPS_THE_SCREEN_ON_V1 (#227)
    val keepsScreenOn = videoKeepsScreenOn(
        phase = playbackUiState.phase,
        playWhenReady = playbackUiState.playWhenReady,
        fullscreen = fullscreen,
        muted = muted,
    )

    val tabletop = fullscreen && tabletopVideoHeight != null
    val videoAreaModifier = if (tabletop) {
        Modifier.fillMaxWidth().height(tabletopVideoHeight)
    } else {
        Modifier.fillMaxSize()
    }
    // VIDEO13_FEED_SURFACE_STARTS_IN_THE_VIDEOS_SHAPE_V1 (owner, 0.80): in a 4:5 or
    // 16:9 bounded feed frame the surface took the whole frame until the player knew
    // the video size, then shrank to it; the SurfaceView kept its first width, so the
    // picture was stretched and cut at the right edge. The surface now starts in the
    // video's own shape, centred, and never has to shrink.
    val inlineSurfaceModifier = if (fullscreen || tabletop) {
        videoAreaModifier
    } else {
        Modifier.fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .aspectRatio(mediaAspectRatio(post))
            .testTag(INLINE_VIDEO_SURFACE_TEST_TAG)
    }
    var frameHeightPx by remember(post.id) { mutableIntStateOf(0) }
    val frameDensity = LocalDensity.current
    val centerAlignment: Alignment = if (tabletop && frameHeightPx > 0) {
        tabletopControlsAlignment(
            foldTopPx = with(frameDensity) { tabletopVideoHeight.toPx() },
            heightPx = frameHeightPx.toFloat(),
        )
    } else {
        Alignment.Center
    }
    // Full screen without its own long press: a long press brings back the
    // controls with share and download (it did nothing there).
    val longPress: (() -> Unit)? = onLongPress ?: if (fullscreen) {
        {
            controlsVisible = true
            controlsShownByReader = true
            controlsInteraction += 1
        }
    } else {
        null
    }
    val toggleControls: () -> Unit = {
        if (playbackModal == null) {
            controlsVisible = !controlsVisible
            controlsShownByReader = controlsVisible
        }
    }
    val playerFrameModifier = if (fullscreen) {
        visibilityModifier.fillMaxSize()
    } else {
        visibilityModifier.fillMaxWidth().aspectRatio(aspectRatio)
    }

    Box(
        playerFrameModifier
            .onSizeChanged { frameHeightPx = it.height }
            .then(if (keepsScreenOn) Modifier.keepScreenOn() else Modifier)
            .background(Color.Black)
            // VIDEO10_FRAME_SEMANTICS_DO_NOT_SWALLOW_THE_CONTROLS_V1 (audit A3): a
            // clickable frame merged the title and times into its own node; the
            // frame now offers its actions without merging what it contains.
            .semantics {
                onClick(label = if (showControls) "Hide video controls" else "Show video controls") {
                    toggleControls()
                    true
                }
                if (longPress != null) {
                    onLongClick(label = if (onLongPress != null) "Media actions" else "Show controls") {
                        longPress()
                        true
                    }
                }
                // ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1: zoom is touch-only; TalkBack can reset it.
                if (videoZoomed) {
                    customActions = listOf(
                        CustomAccessibilityAction(FULLSCREEN_VIDEO_RESET_ZOOM_LABEL) {
                            videoZoom = FullscreenVideoZoom()
                            true
                        },
                    )
                }
            }
            .pointerInput(longPress != null) {
                detectTapGestures(
                    onTap = { toggleControls() },
                    onLongPress = longPress?.let { action -> { _ -> action() } },
                )
            }
            // VIDEO10_KEYBOARD_CONTROLS_THE_VIDEO_V1 (audit A4): Space/K, J/L or the
            // arrows, M and F, for tablets and keyboards.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (videoKeyAction(event.key)) {
                    VideoKeyAction.PLAY_PAUSE -> {
                        if (playing) state.stopVideo(post.id) else state.playVideo(post.id)
                    }
                    VideoKeyAction.BACK -> coordinator.seekBy(playbackOwner, post.id, -VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS)
                    VideoKeyAction.FORWARD -> coordinator.seekBy(playbackOwner, post.id, VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS)
                    VideoKeyAction.MUTE -> state.toggleVideoMuted()
                    VideoKeyAction.FULLSCREEN -> if (fullscreen) {
                        (onExitFullscreen ?: state::closeMedia).invoke()
                    } else {
                        state.openMedia(post)
                    }
                    null -> return@onKeyEvent false
                }
                controlsVisible = true
                controlsInteraction += 1
                true
            }
            .focusable(),
    ) {
        if (fullscreen) {
            // SUBSHELL_ISSUE20_ENDED_SURFACE_POSTER_FALLBACK_V1
            // Surface readiness is local to this presentation. A media ID that rendered on the
            // previous inline surface does not mean this fresh fullscreen TextureView has a frame.
            ContentFrame(
                player = coordinator.player,
                modifier = videoAreaModifier
                    .onSizeChanged { videoAreaSize = it.toSize() }
                    .clipToBounds()
                    .fullscreenVideoZoomLayer { videoZoom }
                    .testTag(FULLSCREEN_VIDEO_FRAME_TEST_TAG),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
                keepContentOnReset = false,
                shutter = {
                    VideoPosterFrame(post = post, modifier = Modifier.fillMaxSize())
                },
            )
        } else if (feedPausePresentation) {
            // Media3 owns first-frame/shutter readiness for this surface instance.
            // Keep the surface composed during pause; do not preserve another clip on reset.
            ContentFrame(
                player = coordinator.player,
                modifier = inlineSurfaceModifier,
                contentScale = ContentScale.Fit,
                keepContentOnReset = false,
                shutter = {
                    DisposableEffect(playbackOwner, post.id) {
                        MediaPerformanceTrace.mark(
                            event = "feed_poster_shutter",
                            mediaId = post.id,
                            transitionId = transitionId,
                            details = "visible=true image_state=${feedPosterImage?.phase ?: "non_remote"}",
                        )
                        onDispose {
                            MediaPerformanceTrace.mark(
                                event = "feed_poster_shutter",
                                mediaId = post.id,
                                transitionId = transitionId,
                                details = "visible=false",
                            )
                        }
                    }
                    VideoPosterFrame(post = post, modifier = Modifier.fillMaxSize(), feedImageState = feedPosterImage)
                },
            )
        } else {
            // Standalone/post surfaces need the same per-surface readiness rule as fullscreen.
            // In particular, ENDED -> new surface may never emit another first frame until replay.
            ContentFrame(
                player = coordinator.player,
                modifier = inlineSurfaceModifier,
                contentScale = ContentScale.Fit,
                keepContentOnReset = false,
                shutter = {
                    VideoPosterFrame(post = post, modifier = Modifier.fillMaxSize())
                },
            )
        }
        // Feed readiness belongs to ContentFrame, not the coordinator's media-ID-only marker.
        if (!feedPausePresentation && posterVisible) {
            VideoPosterFrame(
                post = post,
                modifier = videoAreaModifier,
            )
        }
        AndroidView(
            factory = { subtitleContext ->
                SubtitleView(subtitleContext).apply {
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            },
            update = { subtitleView ->
                subtitleView.setCues(coordinator.currentCues)
                subtitleView.setBottomPaddingFraction(if (showControls) SUBTITLE_PADDING_WITH_CONTROLS else SUBTITLE_PADDING_WITHOUT_CONTROLS)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // VIDEO07_DOUBLE_TAP_SEEK_V1 / VIDEO07_HOLD_FOR_DOUBLE_SPEED_V1: full screen,
        // each behind its own setting (default off), on the same layer as the
        // landscape swipes so one layer owns the gestures over the picture.
        // ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1: while zoomed, a double-tap resets the zoom
        // and hold-for-2× and the swipes pause; at 1× they are back.
        val doubleTapSeek = fullscreen && state.videoDoubleTapSeek
        val holdForDoubleSpeed = fullscreen && state.videoHoldForDoubleSpeed && !videoZoomed
        val swipeGesturesActive = landscapeGestureControlsActive && !videoZoomed
        val tapGesturesActive = doubleTapSeek || holdForDoubleSpeed || videoZoomed
        LaunchedEffect(videoZoomed) {
            if (videoZoomed) {
                fullscreenGestureOverlay = null
                coordinator.holdDoubleSpeed(false)
            }
        }
        if (
            (swipeGesturesActive || tapGesturesActive || pinchToZoom) &&
            !modalVisible && !isScrubbing && !inPictureInPicture
        ) {
            // LOWUX2_GESTURE_OVERLAY_LEAVES_WITH_ITS_LAYER_V1 (OCB-303): a drag
            // cut short by the layer leaving (a rotation to portrait) had no
            // end event, so its level stayed on the video.
            DisposableEffect(Unit) {
                onDispose {
                    fullscreenGestureOverlay = null
                    coordinator.holdDoubleSpeed(false)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (pinchToZoom) {
                            Modifier.fullscreenVideoPinchToZoom(
                                key = post.id,
                                zoom = { videoZoom },
                            ) { centroid, zoomChange, pan ->
                                videoZoom = videoZoom.transformedBy(
                                    centroid = centroid,
                                    zoomChange = zoomChange,
                                    pan = pan,
                                    viewportSize = videoAreaSize,
                                    contentAspectRatio = currentZoomAspectRatio,
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (tapGesturesActive) {
                            Modifier.pointerInput(post.id, doubleTapSeek, holdForDoubleSpeed, videoZoomed) {
                                var heldForSpeed = false
                                detectTapGestures(
                                    onPress = {
                                        if (holdForDoubleSpeed) {
                                            val hold = fullscreenGestureScope.launch {
                                                delay(viewConfiguration.longPressTimeoutMillis)
                                                heldForSpeed = true
                                                coordinator.holdDoubleSpeed(true)
                                            }
                                            tryAwaitRelease()
                                            hold.cancel()
                                            coordinator.holdDoubleSpeed(false)
                                        }
                                    },
                                    onDoubleTap = if (videoZoomed) {
                                        { videoZoom = FullscreenVideoZoom() }
                                    } else if (doubleTapSeek) {
                                        { offset ->
                                            val deltaMs = doubleTapSeekDeltaMs(offset.x, size.width.toFloat())
                                            controlsVisible = true
                                            coordinator.seekBy(playbackOwner, post.id, deltaMs)
                                            seekHint = if (deltaMs < 0) "−10 s" else "+10 s"
                                            seekHintToken += 1
                                        }
                                    } else {
                                        null
                                    },
                                    onTap = {
                                        if (heldForSpeed) {
                                            heldForSpeed = false
                                        } else if (!modalVisible) {
                                            controlsVisible = !controlsVisible
                                            controlsShownByReader = controlsVisible
                                        }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(if (swipeGesturesActive) Modifier.pointerInput(
                        post.id,
                        state.landscapeBrightnessGestureEnabled,
                        state.landscapeVolumeGestureEnabled,
                    ) {
                        var gesture: FullscreenVideoGesture? = null
                        var level = 0f
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                fullscreenGestureDismissToken += 1
                                gesture = fullscreenVideoGestureForPosition(
                                    x = offset.x,
                                    width = size.width.toFloat(),
                                    brightnessEnabled = state.landscapeBrightnessGestureEnabled,
                                    volumeEnabled = state.landscapeVolumeGestureEnabled,
                                )
                                // VIDEO10_VOLUME_GESTURE_UNMUTES_V1 (audit B7a): the overlay
                                // showed 60 % while the video stayed silent.
                                if (volumeGestureUnmutes(gesture, state.feedVideoMuted)) state.toggleVideoMuted()
                                level = when (gesture) {
                                    FullscreenVideoGesture.BRIGHTNESS -> currentFullscreenBrightness(context, activity)
                                    FullscreenVideoGesture.VOLUME -> currentMediaVolumeFraction(audioManager)
                                    null -> 0f
                                }
                                gesture?.let { activeGesture ->
                                    fullscreenGestureOverlay = FullscreenVideoGestureOverlayState(activeGesture, level)
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                val activeGesture = gesture
                                if (activeGesture != null) {
                                    change.consume()
                                    level = fullscreenGestureLevelAfterDrag(level, dragAmount, size.height.toFloat())
                                    when (activeGesture) {
                                        FullscreenVideoGesture.BRIGHTNESS -> setFullscreenWindowBrightness(activity, level)
                                        FullscreenVideoGesture.VOLUME -> setMediaVolumeFraction(audioManager, level)
                                    }
                                    fullscreenGestureOverlay = FullscreenVideoGestureOverlayState(activeGesture, level)
                                }
                            },
                            onDragEnd = { gesture = null; lingerThenHideLevel() },
                            onDragCancel = { gesture = null; lingerThenHideLevel() },
                        )
                    } else Modifier),
            )
        }

        seekHint?.let { hint ->
            Surface(
                modifier = Modifier
                    .align(if (hint.startsWith("−")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (fullscreen && coordinator.holdingDoubleSpeed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = safeDrawingPadding.calculateTopPadding() + 72.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    text = "2× speed",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        fullscreenGestureOverlay?.let { overlay ->
            FullscreenVideoGestureLevelOverlay(
                overlay = overlay,
                modifier = Modifier.align(centerAlignment),
            )
        }

        if (showControls) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                // VIDEO06_FULLSCREEN_TITLE_IS_READABLE_V1: the title sat on 0.20.
                                Color.Black.copy(alpha = if (fullscreen) 0.60f else 0.20f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f),
                            ),
                        ),
                    ),
            )
            val mediaActionsAtStart = state.interactionSide.mediaActionsAtStart()
            val actionsAtRowStart = physicalLeftIsRowStart(mediaActionsAtStart, layoutDirection)
            val videoTopActions: @Composable () -> Unit = {
                VideoTopActionCluster(
                    fullscreen = fullscreen,
                    // VIDEO04_PLAYBACK_OPTIONS_PLACEMENT_V1
                    showPlaybackOptions = playbackOptionsButtonShown(
                        placement = state.playbackOptionsPlacement,
                        fullscreen = fullscreen,
                        inPostDetail = inPostDetail,
                    ),
                    // VIDEO06_SILENT_VIDEO_LOOPS_WITHOUT_A_MUTE_BUTTON_V1
                    showMute = coordinator.silentMediaId != post.id,
                    muted = muted,
                    atStart = actionsAtRowStart,
                    onPlaybackOptions = {
                        playbackModal = PlaybackModal.OPTIONS
                        controlsVisible = false
                    },
                    onToggleMute = {
                        controlsVisible = true
                        controlsInteraction += 1
                        state.toggleVideoMuted()
                    },
                    onToggleFullscreen = {
                        controlsVisible = true
                        controlsInteraction += 1
                        if (fullscreen) {
                            (onExitFullscreen ?: state::closeMedia).invoke()
                        } else {
                            state.openMedia(post)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        start = if (fullscreen) {
                            safeDrawingPadding.calculateStartPadding(layoutDirection)
                        } else {
                            0.dp
                        },
                        top = if (fullscreen) safeDrawingPadding.calculateTopPadding() else 0.dp,
                        end = if (fullscreen) {
                            safeDrawingPadding.calculateEndPadding(layoutDirection)
                        } else {
                            0.dp
                        },
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // VIDEO12_FULLSCREEN_KEEPS_THE_TITLE_V1 (owner, from a screenshot: five
                // buttons left "UA …" of the title): full screen has Back, the title
                // on two lines and one menu; share, download and the playback choices
                // live in that menu, mute and speed sit by the time below.
                if (fullscreen) {
                    IconButton(onClick = onExitFullscreen ?: state::closeMedia) {
                        Icon(
                            Symbols.ArrowBack,
                            contentDescription = "Exit full screen",
                            tint = Color.White,
                        )
                    }
                }

                if (!fullscreen && actionsAtRowStart) videoTopActions()

                if (fullscreen) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = post.title,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = post.mediaLabel,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    IconButton(
                        onClick = {
                            playbackModal = PlaybackModal.OPTIONS
                            controlsVisible = false
                        },
                    ) {
                        // Owner: the gear, not three dots.
                        Icon(
                            Symbols.Settings,
                            contentDescription = "Playback options",
                            tint = Color.White,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (!fullscreen && !actionsAtRowStart) videoTopActions()
            }
        }

        val durationMs = progressDurationMs
        val bottomBarModifier = Modifier.align(Alignment.BottomCenter)
        // In full screen the bottom row (mute, speed, time) shows even before the
        // duration is known; the timeline waits for it.
        if (showControls && (durationMs != null || fullscreen)) ProgressReadingScope {
            val displayedPositionMs = if (isScrubbing) scrubPositionMs else playbackProgress.positionMs
            Column(
                modifier = bottomBarModifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(
                        start = if (fullscreen) {
                            safeDrawingPadding.calculateStartPadding(layoutDirection)
                        } else {
                            0.dp
                        },
                        end = if (fullscreen) {
                            safeDrawingPadding.calculateEndPadding(layoutDirection)
                        } else {
                            0.dp
                        },
                        bottom = if (fullscreen) safeDrawingPadding.calculateBottomPadding() else 0.dp,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (durationMs != null) VideoTimeline(
                    positionMs = displayedPositionMs,
                    durationMs = durationMs,
                    bufferedPositionMs = playbackProgress.bufferedPositionMs,
                    seekable = playbackProgress.isSeekable,
                    scrubbing = isScrubbing,
                    size = state.videoTimelineSize,
                    onScrub = { positionMs ->
                        controlsVisible = true
                        controlsShownByReader = true
                        val scrubbingStarted = isScrubbing ||
                            coordinator.beginScrubbing(playbackOwner, post.id)
                        if (scrubbingStarted) {
                            if (!isScrubbing) state.beginVideoScrubbing(post.id)
                            isScrubbing = true
                            scrubPositionMs = positionMs
                            coordinator.scrubTo(playbackOwner, post.id, positionMs)
                        }
                    },
                    onScrubFinished = {
                        controlsInteraction += 1
                        if (isScrubbing) {
                            coordinator.finishScrubbing(
                                owner = playbackOwner,
                                mediaId = post.id,
                                positionMs = scrubPositionMs,
                            )
                            state.endVideoScrubbing(post.id)
                            playbackProgress = coordinator.playbackProgress(post.id)
                            isScrubbing = false
                            controlsVisible = true
                        }
                    },
                    onSeekBy = { deltaMs ->
                        controlsVisible = true
                        controlsInteraction += 1
                        coordinator.seekBy(playbackOwner, post.id, deltaMs)
                        playbackProgress = coordinator.playbackProgress(post.id)
                    },
                )
                if (fullscreen) {
                    // VIDEO12_FULLSCREEN_KEEPS_THE_TITLE_V1: the quick controls follow the
                    // reader's side; the time takes the other end.
                    val timeText: @Composable () -> Unit = {
                        Text(
                            text = if (durationMs != null) {
                                "${formatVideoPlaybackTime(displayedPositionMs)} / ${formatVideoPlaybackTime(durationMs)}"
                            } else {
                                ""
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    val quickControls: @Composable () -> Unit = {
                        FullscreenQuickControls(
                            speedLabel = videoSpeedButtonLabel(coordinator.playbackSpeed).takeIf { state.videoSpeedControls },
                            showMute = coordinator.silentMediaId != post.id,
                            muted = muted,
                            onSpeed = {
                                playbackModal = PlaybackModal.SPEED
                                controlsVisible = false
                            },
                            onToggleMute = {
                                controlsVisible = true
                                controlsInteraction += 1
                                state.toggleVideoMuted()
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val quickAtRowStart = physicalLeftIsRowStart(
                            left = state.interactionSide == InteractionSide.LEFT,
                            layoutDirection = layoutDirection,
                        )
                        if (quickAtRowStart) quickControls() else timeText()
                        Spacer(Modifier.weight(1f))
                        if (quickAtRowStart) timeText() else quickControls()
                    }
                } else if (durationMs != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatVideoPlaybackTime(displayedPositionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = formatVideoPlaybackTime(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // VIDEO03_SCRUBBING_SHOWS_LIVE_FRAMES_V1: the target time stays readable
        // in the middle of the picture while the thumb is under the finger.
        if (isScrubbing && durationMs != null) {
            Surface(
                modifier = Modifier.align(centerAlignment),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = VIDEO_CENTER_BUTTON_SCRIM_ALPHA),
            ) {
                Text(
                    text = "${formatVideoPlaybackTime(scrubPositionMs)} / ${formatVideoPlaybackTime(durationMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (!modalVisible && !inPictureInPicture) {
            when (playbackUiState.phase) {
                VideoPlaybackPhase.BUFFERING -> if (!isScrubbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(centerAlignment).size(44.dp),
                        color = Color.White,
                    )
                }

                // ISSUE11A3R2_CANONICAL_ERROR_NOTICE_V1
                VideoPlaybackPhase.ERROR -> ErrorNotice(
                    presentation = videoPlaybackErrorPresentation(playbackUiState),
                    diagnosticScopeKey = "media_video_playback",
                    modifier = Modifier
                        .align(centerAlignment)
                        .widthIn(max = 320.dp),
                    onAction = { action ->
                        if (action == ErrorActionKind.RETRY) {
                            controlsVisible = true
                            coordinator.retry(playbackOwner)
                        }
                    },
                )

                VideoPlaybackPhase.ENDED -> Surface(
                    modifier = Modifier.align(centerAlignment).size(VIDEO_CENTER_BUTTON_SIZE),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = VIDEO_CENTER_BUTTON_SCRIM_ALPHA),
                    onClick = {
                        controlsVisible = true
                        // MEDIA05: SubshellState selects this video first, so its
                        // visibility and route rules own the replayed playback.
                        state.replayVideo(post.id)
                        coordinator.replay(playbackOwner)
                    },
                ) {
                    Icon(
                        Symbols.Refresh,
                        contentDescription = "Replay video",
                        modifier = Modifier.padding(12.dp),
                        tint = Color.White,
                    )
                }

                else -> if (showControls && !isScrubbing) {
                    Surface(
                        modifier = Modifier.align(centerAlignment).size(VIDEO_CENTER_BUTTON_SIZE),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = VIDEO_CENTER_BUTTON_SCRIM_ALPHA),
                        onClick = {
                            controlsVisible = true
                            if (playing) state.stopVideo(post.id) else state.playVideo(post.id)
                        },
                    ) {
                        Icon(
                            if (playing) Symbols.PauseFilled else Symbols.PlayArrowFilled,
                            contentDescription = if (playing) "Pause video" else "Resume video",
                            modifier = Modifier.padding(12.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // VIDEO04_PLAYBACK_OPTIONS_IN_A_BOTTOM_SHEET_V1 (#227): an M3 bottom sheet on
        // any surface that shows the button; it scrolls, so it fits a landscape
        // phone, and Back closes it (the arrow steps out of a sub panel).
        if (modalVisible) {
            ModalBottomSheet(
                onDismissRequest = dismissPlaybackModal,
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            ) {
                val panelModifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                when (playbackModal) {
                    PlaybackModal.OPTIONS -> PlaybackOptionsPanel(
                        options = coordinator.trackOptions,
                        automaticVideoQuality = automaticVideoQuality.label,
                        onQuality = { playbackModal = PlaybackModal.QUALITY },
                        onAudio = { playbackModal = PlaybackModal.AUDIO },
                        onSubtitles = { playbackModal = PlaybackModal.SUBTITLES },
                        // VIDEO07_PLAYBACK_SPEED_V1: offered only with the setting on.
                        speedSummary = videoSpeedLabel(coordinator.playbackSpeed)
                            .takeIf { state.videoSpeedControls },
                        onSpeed = { playbackModal = PlaybackModal.SPEED },
                        mediaActionRows = if (mediaActions != null) {
                            {
                                PlaybackActionRow(
                                    title = "Share",
                                    summary = if (mediaSharing) "Preparing…" else "Send the video to another app",
                                    busy = mediaSharing,
                                    enabled = !mediaSharing && mediaDownload == null,
                                    onClick = {
                                        mediaSharing = true
                                        mediaActionScope.launch {
                                            val shared = mediaActions.share()
                                            mediaSharing = false
                                            if (!shared) {
                                                errorHost?.reportMediaActionFailure(
                                                    title = "Could not share",
                                                    message = "This media could not be prepared for sharing",
                                                )
                                            }
                                        }
                                    },
                                )
                                PlaybackActionRow(
                                    title = "Download",
                                    summary = mediaDownload ?: "Save to Downloads",
                                    busy = mediaDownload == VIDEO_DOWNLOAD_SAVING,
                                    enabled = !mediaSharing && mediaDownload == null,
                                    onClick = {
                                        mediaDownload = VIDEO_DOWNLOAD_SAVING
                                        mediaActionScope.launch {
                                            val destination = mediaActions.download()
                                            if (destination != null) {
                                                mediaDownload = "Saved to ${mediaDownloadFolder(destination)}"
                                                delay(MEDIA_DOWNLOAD_SAVED_VISIBLE_MS)
                                            } else {
                                                errorHost?.reportMediaActionFailure(
                                                    title = "Could not download",
                                                    message = "This media could not be saved",
                                                )
                                            }
                                            mediaDownload = null
                                        }
                                    },
                                )
                            }
                        } else {
                            null
                        },
                        showTechnicalDetails = state.videoTechnicalDetails,
                        onTechnicalDetails = { playbackModal = PlaybackModal.DETAILS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    PlaybackModal.SPEED -> PlaybackSpeedPanel(
                        speed = coordinator.playbackSpeed,
                        onSpeed = coordinator::choosePlaybackSpeed,
                        onBack = { playbackModal = PlaybackModal.OPTIONS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    PlaybackModal.QUALITY -> PlaybackQualityPanel(
                        options = coordinator.trackOptions,
                        automaticVideoQuality = automaticVideoQuality.label,
                        onAutomaticVideo = coordinator::selectAutomaticVideoTrack,
                        onVideoTrack = { key -> coordinator.selectVideoTrack(key) },
                        onBack = { playbackModal = PlaybackModal.OPTIONS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    PlaybackModal.AUDIO -> PlaybackAudioPanel(
                        options = coordinator.trackOptions,
                        onAutomaticAudio = coordinator::selectAutomaticAudioTrack,
                        onAudioTrack = { key -> coordinator.selectAudioTrack(key) },
                        onBack = { playbackModal = PlaybackModal.OPTIONS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    PlaybackModal.SUBTITLES -> PlaybackSubtitlesPanel(
                        options = coordinator.trackOptions,
                        onAutomaticText = coordinator::selectAutomaticTextTrack,
                        onTextOff = coordinator::disableTextTracks,
                        onTextTrack = { key -> coordinator.selectTextTrack(key) },
                        onBack = { playbackModal = PlaybackModal.OPTIONS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    PlaybackModal.DETAILS -> PlaybackDiagnosticsPanel(
                        // Read here, not in the player: diagnostics change on every
                        // dropped frame and format event (audit H2).
                        diagnostics = coordinator.playbackDiagnostics.takeIf { it.mediaId == post.id }
                            ?: VideoPlaybackDiagnostics(mediaId = post.id),
                        onBack = { playbackModal = PlaybackModal.OPTIONS },
                        onDismiss = dismissPlaybackModal,
                        modifier = panelModifier,
                    )

                    null -> Unit
                }
            }
        }
    }
}

private const val DOUBLE_TAP_SEEK_HINT_MS = 700L
private const val VIDEO_DOWNLOAD_SAVING = "Saving…"
private val VIDEO_CENTER_BUTTON_SIZE = 58.dp
private const val VIDEO_CENTER_BUTTON_SCRIM_ALPHA = 0.72f
private const val VIDEO_CHROME_BUTTON_ALPHA = 0.92f
private const val SUBTITLE_PADDING_WITH_CONTROLS = 0.16f
private const val SUBTITLE_PADDING_WITHOUT_CONTROLS = 0.08f

/** Physical left half goes back 10 s, right half forward. */
internal fun doubleTapSeekDeltaMs(x: Float, width: Float): Long =
    if (x < width / 2f) -VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS else VIDEO_TIMELINE_ACCESSIBILITY_STEP_MS

/**
 * A composable lambda is its own recompose scope: state read inside [content]
 * (the playback position) redraws only it, not the whole player.
 */
@Composable
private fun ProgressReadingScope(content: @Composable () -> Unit) {
    content()
}

/** Counts LocalVideoPlayer recompositions so a test can prove the poll stays out (H2). */
@androidx.annotation.VisibleForTesting
internal object VideoPlayerCompositionProbe {
    var compositions = 0
}

internal enum class VideoKeyAction { PLAY_PAUSE, BACK, FORWARD, MUTE, FULLSCREEN }

internal fun videoKeyAction(key: Key): VideoKeyAction? = when (key) {
    Key.Spacebar, Key.K, Key.MediaPlayPause -> VideoKeyAction.PLAY_PAUSE
    Key.DirectionLeft, Key.J -> VideoKeyAction.BACK
    Key.DirectionRight, Key.L -> VideoKeyAction.FORWARD
    Key.M -> VideoKeyAction.MUTE
    Key.F -> VideoKeyAction.FULLSCREEN
    else -> null
}

internal fun volumeGestureUnmutes(gesture: FullscreenVideoGesture?, muted: Boolean): Boolean =
    gesture == FullscreenVideoGesture.VOLUME && muted

/** Tabletop: the controls centre in the half below the fold. */
internal fun tabletopControlsAlignment(foldTopPx: Float, heightPx: Float): BiasAlignment {
    val centreOfLowerHalf = foldTopPx + (heightPx - foldTopPx) / 2f
    return BiasAlignment(0f, (centreOfLowerHalf / heightPx) * 2f - 1f)
}

internal const val INLINE_VIDEO_SURFACE_TEST_TAG = "inline-video-surface"
internal const val FULLSCREEN_VIDEO_FRAME_TEST_TAG = "fullscreen-video-frame"
internal const val FULLSCREEN_VIDEO_RESET_ZOOM_LABEL = "Reset zoom"

internal fun boundedInlineVideoAspectRatio(aspectRatio: Float): Float =
    aspectRatio.coerceIn(4f / 5f, 16f / 9f).takeIf { aspectRatio > 0f } ?: 16f / 9f

/** Phases that bring the controls back; a quiet feed video only shows them at its end or on a failure. */
internal fun videoPhaseShowsControls(phase: VideoPlaybackPhase, quietFeed: Boolean): Boolean =
    if (quietFeed) {
        phase == VideoPlaybackPhase.ENDED || phase == VideoPlaybackPhase.ERROR
    } else {
        phase != VideoPlaybackPhase.READY
    }

internal fun videoControlsForcedVisible(
    state: VideoPlaybackUiState,
    isScrubbing: Boolean,
    quietFeed: Boolean,
): Boolean = if (quietFeed) {
    isScrubbing || state.phase == VideoPlaybackPhase.ENDED || state.phase == VideoPlaybackPhase.ERROR
} else {
    videoControlsMustRemainVisible(state, isScrubbing)
}

internal fun videoSystemPauseStops(
    pause: VideoSystemPause?,
    mediaId: String,
    ownsPresentation: Boolean,
): Boolean = pause != null && pause.mediaId == mediaId && ownsPresentation

/**
 * A video that plays with sound, or fullscreen, keeps the screen awake; a muted
 * feed video does not, so a looping clip cannot keep a phone on forever.
 */
internal fun videoKeepsScreenOn(
    phase: VideoPlaybackPhase,
    playWhenReady: Boolean,
    fullscreen: Boolean,
    muted: Boolean,
): Boolean =
    playWhenReady &&
        (phase == VideoPlaybackPhase.READY || phase == VideoPlaybackPhase.BUFFERING) &&
        (fullscreen || !muted)

private const val VIDEO_CONTROLS_AUTO_HIDE_MS = 2_500L
private const val VIDEO_PROGRESS_REFRESH_MS = 250L
