package app.subshell.prototype.ui.screens

import android.content.pm.ActivityInfo
import app.subshell.prototype.media.findActivity
import androidx.compose.ui.semantics.onClick
import app.subshell.prototype.ui.app.rememberPredictiveRouteBackState
import app.subshell.prototype.ui.app.PredictiveRouteBackHandler
import app.subshell.prototype.ui.components.commentMediaStableName
import app.subshell.prototype.ui.components.rememberAccessibleAutoHideMillis
import app.subshell.prototype.ui.downloadRemoteMedia
import app.subshell.prototype.ui.mediaActionScope
import app.subshell.prototype.ui.shareRemoteMedia
import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.components.reportMediaActionFailure
import app.subshell.prototype.ui.components.MEDIA_DOWNLOAD_SAVED_VISIBLE_MS
import app.subshell.prototype.ui.components.MediaDownloadUiState
import app.subshell.prototype.ui.mediaDownloadFolder
import app.subshell.prototype.ui.sharePostMedia
import app.subshell.prototype.ui.downloadPostMedia
import app.subshell.prototype.ui.PostMediaActionTarget
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.GifMedia
import app.subshell.prototype.ui.components.GalleryMetadataBelow
import app.subshell.prototype.ui.components.GalleryMetadataOverlay
import app.subshell.prototype.ui.components.LocalVideoPlayer
import app.subshell.prototype.ui.components.RemoteGifMedia
import app.subshell.prototype.ui.components.RemoteImage
import app.subshell.prototype.ui.components.RemoteMedia
import app.subshell.prototype.ui.components.VideoPlayerPresentation
import app.subshell.prototype.ui.components.VideoMediaActions
import app.subshell.prototype.ui.components.mediaResource
import kotlinx.coroutines.delay
import app.subshell.prototype.ui.components.InlineProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults

@Composable
fun MediaViewerScreen(
    post: Post,
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ISSUE27_PREDICTIVE_MEDIA_BACK_V1: see PredictiveMediaBack.kt. The viewer
    // closes on commit through the same onBack; playback and ownership are
    // untouched (only drawing changes during the gesture).
    val viewerBack = rememberPredictiveRouteBackState()
    PredictiveRouteBackHandler(enabled = true, state = viewerBack, onBack = onBack)
    // LOWUI5_VIEWER_BACK_SEEN_AT_START_AND_END_V1 (OCB-226): the gesture's
    // progress changes every frame; the controls only need started/ended.
    val viewerBackInProgress by remember(viewerBack) { derivedStateOf { viewerBack.gestureInProgress() } }

    if (post.kind == PostKind.VIDEO) {
        var controlsVisible by remember(post.id) { mutableStateOf(true) }
        var fullscreenPlaybackPositionMs by rememberSaveable(post.id) { mutableLongStateOf(0L) }
        FullscreenSystemBars(controlsVisible = controlsVisible)
        val videoContext = LocalContext.current
        val videoActivity = remember(videoContext) { videoContext.findActivity() }
        // VIDEO11_FULLSCREEN_VIDEO_SHARES_AND_DOWNLOADS_V1 (audit): the same share and
        // download as the image viewer (a video is exported to MP4).
        val videoTarget = PostMediaActionTarget(post = post, galleryItem = null)
        // VIDEO11_ROTATE_FOR_WIDE_VIDEOS_V1 (audit B5, setting off by default).
        if (fullscreenRotatesToLandscape(state.videoRotateFullscreen, post.videoWidth, post.videoHeight)) {
            DisposableEffect(videoActivity) {
                val previous = videoActivity?.requestedOrientation
                videoActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                onDispose {
                    if (previous != null) videoActivity.requestedOrientation = previous
                }
            }
        }
        val tabletopVideoHeight = rememberTabletopVideoHeight(videoActivity)
        Box(modifier.fillMaxSize().predictiveMediaBackdrop(viewerBack, state.reduceMotion)) {
            LocalVideoPlayer(
                post = post,
                state = state,
                presentation = VideoPlayerPresentation.FULLSCREEN,
                onExitFullscreen = onBack,
                onControlsVisibilityChanged = { controlsVisible = it },
                initialPlaybackPositionMs = fullscreenPlaybackPositionMs.takeIf { it > 0L },
                onPlaybackPositionChanged = { fullscreenPlaybackPositionMs = it },
                mediaActions = VideoMediaActions(
                    share = { sharePostMedia(videoContext, videoTarget) },
                    download = { downloadPostMedia(videoContext, videoTarget) },
                ),
                tabletopVideoHeight = tabletopVideoHeight,
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveMediaContent(viewerBack, state.reduceMotion),
            )
        }
        return
    }

    var controlsVisible by remember(post.id) { mutableStateOf(true) }
    FullscreenSystemBars(controlsVisible = controlsVisible)
    // ISSUE35_MEDIA_DOWNLOAD_V1: share and download the media on screen (the
    // current gallery item) from the chrome; progress and the saved tick show
    // on the button, failures in the viewport error tray.
    val mediaActionContext = LocalContext.current
    val viewerActions = remember(post.id) { MediaViewerActionState() }
    val controlsAutoHideMillis = rememberAccessibleAutoHideMillis(IMAGE_CONTROLS_AUTO_HIDE_MS)
    LaunchedEffect(post.id, controlsVisible, viewerActions.busy) {
        if (controlsVisible && !viewerActions.busy) {
            delay(controlsAutoHideMillis)
            controlsVisible = false
        }
    }
    val toggleControls = { controlsVisible = !controlsVisible }
    val uriHandler = LocalUriHandler.current
    val viewerTarget = PostMediaActionTarget(
        post = post,
        galleryItem = if (post.kind == PostKind.GALLERY) {
            post.gallery.getOrNull(state.selectedMediaIndex)
        } else {
            null
        },
    )
    val viewerHasMedia = post.kind == PostKind.IMAGE ||
        post.kind == PostKind.GIF ||
        (post.kind == PostKind.GALLERY && viewerTarget.galleryItem != null)

    Box(modifier.fillMaxSize().predictiveMediaBackdrop(viewerBack, state.reduceMotion)) {
        // LOWUI7_AUTO_HIDE_FOLLOWS_THE_ACCESSIBILITY_TIMEOUT_V1 (OCB-318): the
        // hidden chrome can be brought back without a tap on the picture.
        Box(
            Modifier
                .fillMaxSize()
                .predictiveMediaContent(viewerBack, state.reduceMotion)
                .semantics { onClick(label = VIEWER_TOGGLE_CONTROLS_LABEL) { toggleControls(); true } },
        ) {
            when (post.kind) {
                PostKind.IMAGE -> ZoomableImage(post, onTap = toggleControls)
                PostKind.GIF -> Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(post.id) {
                            detectTapGestures(onTap = { toggleControls() })
                        },
                ) {
                    val gifUri = post.remoteImageUri
                    if (gifUri != null) {
                        RemoteGifMedia(
                            uri = gifUri,
                            label = post.mediaLabel,
                            playing = true,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            aspectRatio = null,
                            containerColor = Color.Transparent,
                        )
                    } else {
                        GifMedia(
                            asset = post.mediaAsset,
                            label = post.mediaLabel,
                            playing = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                PostKind.GALLERY -> GalleryViewer(
                    post = post,
                    state = state,
                    controlsVisible = controlsVisible,
                    onTap = toggleControls,
                )
                else -> Text(
                    "This post has no full-screen media.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                )
            }
        }
        FullscreenMediaTopChrome(
            visible = controlsVisible && !viewerBackInProgress,
            title = post.title,
            supportingText = if (post.kind == PostKind.GALLERY) {
                "${state.selectedMediaIndex + 1} / ${post.gallery.size}"
            } else {
                post.mediaLabel
            },
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                if (viewerHasMedia) {
                    MediaViewerActionButtons(
                        state = viewerActions,
                        share = { sharePostMedia(mediaActionContext, viewerTarget) },
                        download = { downloadPostMedia(mediaActionContext, viewerTarget) },
                    )
                }
            },
        )
    }
}

internal fun fullscreenRotatesToLandscape(enabled: Boolean, width: Int?, height: Int?): Boolean =
    enabled && width != null && height != null && width > height

/**
 * VIDEO11_TABLETOP_SPLITS_PICTURE_AND_CONTROLS_V1: in tabletop posture (half
 * open, horizontal fold) the picture ends at the fold; null otherwise.
 */
@Composable
private fun rememberTabletopVideoHeight(activity: android.app.Activity?): androidx.compose.ui.unit.Dp? {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var foldTopPx by remember(activity) { mutableStateOf<Int?>(null) }
    LaunchedEffect(activity) {
        if (activity == null) return@LaunchedEffect
        androidx.window.layout.WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { info -> foldTopPx = tabletopFoldTopPx(info.displayFeatures) }
    }
    return foldTopPx?.let { with(density) { it.toDp() } }
}

internal fun tabletopFoldTopPx(features: List<androidx.window.layout.DisplayFeature>): Int? =
    features.filterIsInstance<androidx.window.layout.FoldingFeature>()
        .firstOrNull { fold ->
            fold.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED &&
                fold.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL
        }
        ?.bounds
        ?.top

@Stable
private class MediaViewerActionState {
    var sharing by mutableStateOf(false)
    var download by mutableStateOf<MediaDownloadUiState?>(null)
    val busy: Boolean get() = sharing || download != null
}

/** Share and Download in the fullscreen chrome; progress and the saved tick show on the button. */
@Composable
private fun MediaViewerActionButtons(
    state: MediaViewerActionState,
    share: suspend () -> Boolean,
    download: suspend () -> String?,
) {
    val errorHost = LocalViewportErrorHostState.current
    FullscreenChromeAction(
        icon = Icons.Rounded.Share,
        contentDescription = "Share media",
        busy = state.sharing,
        enabled = !state.busy,
        onClick = {
            state.sharing = true
            mediaActionScope.launch {
                val shared = share()
                state.sharing = false
                if (!shared) {
                    errorHost?.reportMediaActionFailure(
                        title = "Could not share",
                        message = "This media could not be prepared for sharing",
                    )
                }
            }
        },
    )
    val saved = state.download is MediaDownloadUiState.Saved
    FullscreenChromeAction(
        icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Download,
        contentDescription = if (saved) "Saved" else "Download",
        busy = state.download is MediaDownloadUiState.Saving,
        enabled = !state.busy,
        onClick = {
            state.download = MediaDownloadUiState.Saving(done = 0, total = 1)
            mediaActionScope.launch {
                val destination = download()
                if (destination != null) {
                    state.download = MediaDownloadUiState.Saved(
                        count = 1,
                        total = 1,
                        folder = mediaDownloadFolder(destination),
                    )
                    delay(MEDIA_DOWNLOAD_SAVED_VISIBLE_MS)
                } else {
                    errorHost?.reportMediaActionFailure(
                        title = "Could not download",
                        message = "This media could not be saved",
                    )
                }
                state.download = null
            }
        },
    )
}

/** A 48dp chrome button like Back, showing progress while [busy]. */
@Composable
private fun FullscreenChromeAction(
    icon: ImageVector,
    contentDescription: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // M3225_MEDIA_BUTTONS_ARE_FILLED_TONAL_ICON_BUTTONS_V1 (#225, reading-14,
    // components-11): an M3 filled tonal icon button (state layer, disabled
    // container) instead of a circle Surface around an IconButton.
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(48.dp),
        colors = mediaChromeIconButtonColors(),
    ) {
        if (busy) {
            InlineProgressIndicator(modifier = Modifier.semantics { this.contentDescription = contentDescription })
        } else {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

// ISSUE19_INLINE_COMMENT_IMAGE_FULLSCREEN_V1
@Composable
fun CommentMediaViewerScreen(
    uri: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    // ISSUE27_PREDICTIVE_MEDIA_BACK_V1 (see MediaViewerScreen).
    val viewerBack = rememberPredictiveRouteBackState()
    PredictiveRouteBackHandler(enabled = true, state = viewerBack, onBack = onBack)
    // LOWUI5_VIEWER_BACK_SEEN_AT_START_AND_END_V1 (OCB-226): the gesture's
    // progress changes every frame; the controls only need started/ended.
    val viewerBackInProgress by remember(viewerBack) { derivedStateOf { viewerBack.gestureInProgress() } }

    var controlsVisible by remember(uri) { mutableStateOf(true) }
    FullscreenSystemBars(controlsVisible = controlsVisible)
    // ISSUE35_COMMENT_MEDIA_ACTIONS_V1: share and save the comment image too.
    val mediaActionContext = LocalContext.current
    val viewerActions = remember(uri) { MediaViewerActionState() }
    val commentMediaName = remember(uri) { commentMediaStableName(uri) }
    val controlsAutoHideMillis = rememberAccessibleAutoHideMillis(IMAGE_CONTROLS_AUTO_HIDE_MS)
    LaunchedEffect(uri, controlsVisible, viewerActions.busy) {
        if (controlsVisible && !viewerActions.busy) {
            delay(controlsAutoHideMillis)
            controlsVisible = false
        }
    }
    val toggleControls = { controlsVisible = !controlsVisible }
    Box(
        modifier = modifier
            .fillMaxSize()
            .predictiveMediaBackdrop(viewerBack, reduceMotion),
    ) {
        var sourceAspectRatio by remember(uri) { mutableStateOf<Float?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .predictiveMediaContent(viewerBack, reduceMotion)
                .semantics { onClick(label = VIEWER_TOGGLE_CONTROLS_LABEL) { toggleControls(); true } },
        ) {
            ZoomableContent(
                key = "comment-media-$uri",
                contentAspectRatio = sourceAspectRatio,
                onTap = toggleControls,
            ) {
                RemoteImage(
                    uri = uri,
                    label = "Comment image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    aspectRatio = null,
                    containerColor = Color.Transparent,
                    onSourceAspectRatioChanged = { sourceAspectRatio = it },
                )
            }
        }

        FullscreenMediaTopChrome(
            visible = controlsVisible && !viewerBackInProgress,
            title = "Comment image",
            supportingText = null,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                MediaViewerActionButtons(
                    state = viewerActions,
                    share = { shareRemoteMedia(mediaActionContext, uri, MediaFormat.IMAGE, commentMediaName) },
                    download = { downloadRemoteMedia(mediaActionContext, uri, MediaFormat.IMAGE, commentMediaName) },
                )
            },
        )
    }
}

// ISSUE19_SHARED_M3_FULLSCREEN_CHROME_V1
@Composable
private fun FullscreenMediaTopChrome(
    visible: Boolean,
    title: String,
    supportingText: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(bottom = 28.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = safeDrawingPadding.calculateStartPadding(layoutDirection),
                        top = safeDrawingPadding.calculateTopPadding(),
                        end = safeDrawingPadding.calculateEndPadding(layoutDirection),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                    colors = mediaChromeIconButtonColors(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    supportingText?.takeIf(String::isNotBlank)?.let { value ->
                        Text(
                            value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                actions()
            }
        }
    }
}

@Composable
private fun FullscreenSystemBars(controlsVisible: Boolean) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    val windowSize = LocalWindowInfo.current.containerSize
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    LaunchedEffect(
        activity,
        view,
        controlsVisible,
        orientation,
        windowSize,
    ) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (controlsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun GalleryViewer(
    post: Post,
    state: SubshellState,
    controlsVisible: Boolean,
    onTap: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedMediaIndex.coerceIn(0, (post.gallery.size - 1).coerceAtLeast(0)),
        pageCount = { post.gallery.size },
    )
    val uriHandler = LocalUriHandler.current
    val safeBottomPadding = WindowInsets.safeDrawing
        .asPaddingValues()
        .calculateBottomPadding()
    val metadataVisible =
        state.fullscreenCaptionMode == FullscreenCaptionMode.ALWAYS_VISIBLE ||
            controlsVisible
    var currentPageZoomed by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
        state.updateMediaIndex(post.id, pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !currentPageZoomed,
    ) { page ->
        GalleryViewerPage(
            item = post.gallery[page],
            playing = pagerState.currentPage == page,
            captionMode = state.mediaCaptionMode,
            metadataVisible = metadataVisible,
            safeBottomPadding = safeBottomPadding,
            onTap = onTap,
            onZoomChanged = { zoomed ->
                if (pagerState.currentPage == page) {
                    currentPageZoomed = zoomed
                }
            },
            onOpenLink = { url ->
                runCatching { uriHandler.openUri(url) }
            },
        )
    }
}

@Composable
private fun GalleryViewerPage(
    item: GalleryItem,
    playing: Boolean,
    captionMode: MediaCaptionMode,
    metadataVisible: Boolean,
    safeBottomPadding: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val hasCaption = item.caption.isNotBlank()
    val hasLink = item.outboundUrl.isNotBlank()
    val hasMetadata = hasCaption || hasLink
    val showCaption = captionMode != MediaCaptionMode.HIDDEN
    val showMetadata =
        metadataVisible &&
            hasMetadata &&
            (showCaption || hasLink)

    if (captionMode == MediaCaptionMode.BELOW_MEDIA) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                GalleryViewerMedia(
                    item = item,
                    playing = playing,
                    onTap = onTap,
                    onZoomChanged = onZoomChanged,
                )
            }
            if (showMetadata) {
                GalleryMetadataBelow(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = safeBottomPadding + 16.dp,
                        ),
                    onOpenLink = onOpenLink,
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GalleryViewerMedia(
            item = item,
            playing = playing,
            onTap = onTap,
            onZoomChanged = onZoomChanged,
        )
        if (showMetadata) {
            GalleryMetadataOverlay(
                item = item,
                showCaption = showCaption,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = safeBottomPadding + 16.dp,
                    ),
                onOpenLink = onOpenLink,
            )
        }
    }
}

@Composable
private fun GalleryViewerMedia(
    item: GalleryItem,
    playing: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val imageUri = item.remoteImageUri
    when {
        item.format == MediaFormat.IMAGE && item.localMediaUri != null -> {
            var sourceAspectRatio by remember(item.id) { mutableStateOf<Float?>(null) }
            ZoomableContent(
                key = "gallery-local-${item.id}",
                contentAspectRatio = sourceAspectRatio,
                onTap = onTap,
                onZoomChanged = onZoomChanged,
            ) {
                app.subshell.prototype.ui.components.LocalUriMedia(
                    uri = item.localMediaUri,
                    mimeType = item.localMediaMimeType,
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    onSourceAspectRatioChanged = { sourceAspectRatio = it },
                )
            }
        }

        item.format == MediaFormat.IMAGE && imageUri != null -> {
            var sourceAspectRatio by remember(item.id) { mutableStateOf<Float?>(null) }
            ZoomableContent(
                key = "gallery-remote-${item.id}",
                contentAspectRatio = sourceAspectRatio,
                onTap = onTap,
                onZoomChanged = onZoomChanged,
            ) {
                RemoteMedia(
                    uri = imageUri,
                    label = item.label,
                    format = item.format,
                    playing = playing,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    aspectRatio = null,
                    adaptToExactSourceAspectRatio = true,
                    containerColor = Color.Transparent,
                    onSourceAspectRatioChanged = { sourceAspectRatio = it },
                )
            }
        }

        item.format == MediaFormat.IMAGE -> {
            ZoomableAsset(
                asset = item.asset,
                label = item.label,
                onTap = onTap,
                onZoomChanged = onZoomChanged,
            )
        }

        item.localMediaUri != null -> {
            app.subshell.prototype.ui.components.LocalUriMedia(
                uri = item.localMediaUri,
                mimeType = item.localMediaMimeType,
                contentDescription = item.label,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(onTap = { onTap() })
                    },
            )
        }

        imageUri != null -> {
            RemoteMedia(
                uri = imageUri,
                label = item.label,
                format = item.format,
                playing = playing,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(onTap = { onTap() })
                    },
                contentScale = ContentScale.Fit,
                aspectRatio = null,
                adaptToExactSourceAspectRatio = true,
                containerColor = Color.Transparent,
            )
        }

        item.format == MediaFormat.GIF -> GifMedia(
            asset = item.asset,
            label = item.label,
            playing = playing,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    detectTapGestures(onTap = { onTap() })
                },
        )

        else -> {
            val resource = mediaResource(item.asset) ?: return
            Image(
                painter = painterResource(resource),
                contentDescription = item.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(onTap = { onTap() })
                    },
            )
        }
    }
}

@Composable
private fun ZoomableImage(post: Post, onTap: () -> Unit) {
    val imageUri = post.remoteImageUri
    if (imageUri != null) {
        var sourceAspectRatio by remember(post.id) { mutableStateOf<Float?>(null) }
        ZoomableContent(
            key = "post-${post.id}",
            contentAspectRatio = sourceAspectRatio,
            onTap = onTap,
        ) {
            RemoteImage(
                uri = imageUri,
                label = post.mediaLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                aspectRatio = null,
                containerColor = Color.Transparent,
                onSourceAspectRatioChanged = { sourceAspectRatio = it },
            )
        }
    } else {
        ZoomableAsset(post.mediaAsset, post.mediaLabel, onTap)
    }
}

@Composable
private fun ZoomableAsset(
    asset: app.subshell.prototype.model.MediaAsset?,
    label: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
) {
    val resource = mediaResource(asset) ?: return
    val painter = painterResource(resource)
    val intrinsicSize = painter.intrinsicSize
    val sourceAspectRatio = if (
        intrinsicSize.width > 0f && intrinsicSize.height > 0f
    ) {
        intrinsicSize.width / intrinsicSize.height
    } else {
        null
    }
    ZoomableContent(
        key = asset,
        contentAspectRatio = sourceAspectRatio,
        onTap = onTap,
        onZoomChanged = onZoomChanged,
    ) {
        Image(
            painter = painter,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ZoomableContent(
    key: Any?,
    contentAspectRatio: Float? = null,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var translation by remember(key) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(key) { mutableStateOf(Size.Zero) }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
        val center = viewportSize.center
        val focus = (centroid.takeIf { it.isSpecified } ?: center) - center
        val scaleRatio = if (oldScale > 0f) newScale / oldScale else 1f
        val nextTranslation = if (newScale <= 1.001f) {
            Offset.Zero
        } else {
            translation + focus * (1f - scaleRatio) + panChange
        }
        scale = newScale
        translation = clampZoomTranslation(
            nextTranslation,
            newScale,
            viewportSize,
            contentAspectRatio,
        )
    }

    LaunchedEffect(scale, contentAspectRatio) {
        translation = clampZoomTranslation(
            translation,
            scale,
            viewportSize,
            contentAspectRatio,
        )
        onZoomChanged(scale > 1.01f)
    }
    DisposableEffect(key) {
        onDispose { onZoomChanged(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize = size.toSize()
                translation = clampZoomTranslation(
                    translation,
                    scale,
                    viewportSize,
                    contentAspectRatio,
                )
            }
            .transformable(
                state = transformState,
                canPan = { fullscreenZoomCanPan(scale) },
            )
            .pointerInput(key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapCentroid ->
                        if (scale > 1.01f) {
                            scale = 1f
                            translation = Offset.Zero
                        } else {
                            val newScale = 2.5f
                            val center = viewportSize.center
                            val focus = (tapCentroid.takeIf { it.isSpecified } ?: center) - center
                            scale = newScale
                            translation = clampZoomTranslation(
                                focus * (1f - newScale),
                                newScale,
                                viewportSize,
                                contentAspectRatio,
                            )
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = translation.x
                    translationY = translation.y
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                },
            content = content,
        )
    }
}

internal fun fullscreenZoomCanPan(scale: Float): Boolean =
    scale > FULLSCREEN_ZOOM_PAN_THRESHOLD

internal fun fittedZoomContentSize(
    viewportSize: Size,
    contentAspectRatio: Float?,
): Size {
    if (
        viewportSize.width <= 0f ||
        viewportSize.height <= 0f ||
        contentAspectRatio == null ||
        contentAspectRatio <= 0f
    ) {
        return viewportSize
    }
    val viewportAspectRatio = viewportSize.width / viewportSize.height
    return if (contentAspectRatio >= viewportAspectRatio) {
        Size(
            width = viewportSize.width,
            height = viewportSize.width / contentAspectRatio,
        )
    } else {
        Size(
            width = viewportSize.height * contentAspectRatio,
            height = viewportSize.height,
        )
    }
}

internal fun clampZoomTranslation(
    translation: Offset,
    scale: Float,
    viewportSize: Size,
    contentAspectRatio: Float? = null,
): Offset {
    if (scale <= 1.001f || viewportSize.width <= 0f || viewportSize.height <= 0f) {
        return Offset.Zero
    }
    val fittedSize = fittedZoomContentSize(viewportSize, contentAspectRatio)
    val maxX = ((fittedSize.width * scale - viewportSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((fittedSize.height * scale - viewportSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = translation.x.coerceIn(-maxX, maxX),
        y = translation.y.coerceIn(-maxY, maxY),
    )
}

private const val FULLSCREEN_ZOOM_PAN_THRESHOLD = 1.01f
private const val IMAGE_CONTROLS_AUTO_HIDE_MS = 2_500L
internal const val VIEWER_TOGGLE_CONTROLS_LABEL = "Show or hide controls"

/** The media viewer's chrome buttons: the old 94 % surfaceContainerHigh circle, now with M3 states. */
@Composable
internal fun mediaChromeIconButtonColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    contentColor = MaterialTheme.colorScheme.onSurface,
)
