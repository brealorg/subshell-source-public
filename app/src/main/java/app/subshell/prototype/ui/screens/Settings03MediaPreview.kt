package app.subshell.prototype.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.VideoTimelineSize

@Composable
internal fun MediaSettingsPreview(
    sensitiveMediaPreviewMode: SensitiveMediaPreviewMode,
    videoAutoplay: VideoAutoplay,
    startVideosMuted: Boolean,
    videoTimelineSize: VideoTimelineSize,
    mediaCaptionMode: MediaCaptionMode,
    fullscreenCaptionMode: FullscreenCaptionMode,
    mediaLabelsVisible: Boolean,
    landscapeVideoGesturesEnabled: Boolean,
    landscapeBrightnessGestureEnabled: Boolean,
    landscapeVolumeGestureEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var viewerControlsVisible by remember { mutableStateOf(true) }

    Surface(
        onClick = { viewerControlsVisible = !viewerControlsVisible },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Live preview",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Media, fullscreen playback and gestures. Tap to toggle controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        if (viewerControlsVisible) "Controls · On" else "Controls · Off",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            UnifiedMediaPreviewSample(
                sensitiveMediaPreviewMode = sensitiveMediaPreviewMode,
                videoAutoplay = videoAutoplay,
                startVideosMuted = startVideosMuted,
                videoTimelineSize = videoTimelineSize,
                mediaCaptionMode = mediaCaptionMode,
                fullscreenCaptionMode = fullscreenCaptionMode,
                mediaLabelsVisible = mediaLabelsVisible,
                controlsVisible = viewerControlsVisible,
                landscapeVideoGesturesEnabled = landscapeVideoGesturesEnabled,
                landscapeBrightnessGestureEnabled = landscapeBrightnessGestureEnabled,
                landscapeVolumeGestureEnabled = landscapeVolumeGestureEnabled,
            )
        }
    }
}


@Composable
private fun UnifiedMediaPreviewSample(
    sensitiveMediaPreviewMode: SensitiveMediaPreviewMode,
    videoAutoplay: VideoAutoplay,
    startVideosMuted: Boolean,
    videoTimelineSize: VideoTimelineSize,
    mediaCaptionMode: MediaCaptionMode,
    fullscreenCaptionMode: FullscreenCaptionMode,
    mediaLabelsVisible: Boolean,
    controlsVisible: Boolean,
    landscapeVideoGesturesEnabled: Boolean,
    landscapeBrightnessGestureEnabled: Boolean,
    landscapeVolumeGestureEnabled: Boolean,
) {
    val brightnessEnabled =
        landscapeVideoGesturesEnabled && landscapeBrightnessGestureEnabled
    val volumeEnabled =
        landscapeVideoGesturesEnabled && landscapeVolumeGestureEnabled
    val brightnessAlpha = if (brightnessEnabled) 1f else 0.30f
    val volumeAlpha = if (volumeEnabled) 1f else 0.30f
    val splitAlpha = if (landscapeVideoGesturesEnabled) 0.48f else 0.18f

    // LOWUI4_MEDIA_PREVIEW_USES_ITS_TESTED_RULES_V1 (OCB-261): the preview
    // renders through the tested label and caption rules (NEVER reads "Manual").
    val autoplayLabel = mediaPreviewAutoplayLabel(videoAutoplay)
    val timelineHeight = when {
        videoTimelineSize.name.contains("COMPACT", ignoreCase = true) -> 2.dp
        videoTimelineSize.name.contains("LARGE", ignoreCase = true) -> 6.dp
        else -> 4.dp
    }

    // LOWUI8_MEDIA_PREVIEW_CAPTIONS_FOLLOW_THE_VIEWER_V1 (OCB-260): the
    // viewer's rule, so Hidden shows no caption and Below waits for the
    // controls like the viewer's metadata.
    val captionOverlayVisible = mediaPreviewCaptionVisible(
        placement = MediaCaptionMode.OVERLAY,
        mode = mediaCaptionMode,
        fullscreenMode = fullscreenCaptionMode,
        controlsVisible = controlsVisible,
    )
    val mediaCaptionBelow = mediaPreviewCaptionVisible(
        placement = MediaCaptionMode.BELOW_MEDIA,
        mode = mediaCaptionMode,
        fullscreenMode = fullscreenCaptionMode,
        controlsVisible = controlsVisible,
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize()) {
                val backdropModifier = when (sensitiveMediaPreviewMode) {
                    SensitiveMediaPreviewMode.BLUR_AND_PRELOAD -> {
                        Modifier.fillMaxSize().blur(5.dp)
                    }
                    else -> Modifier.fillMaxSize()
                }

                FjordPreviewBackdrop(backdropModifier)

                when (sensitiveMediaPreviewMode) {
                    SensitiveMediaPreviewMode.BLUR_AND_PRELOAD -> Unit

                    SensitiveMediaPreviewMode.LOAD_ON_TAP -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.30f)),
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = MaterialTheme.shapes.small,
                            color = Color.Black.copy(alpha = 0.72f),
                            border = BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.18f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 5.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White,
                                )
                                Text(
                                    "Tap to load",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    SensitiveMediaPreviewMode.HIDE_PREVIEW -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.62f)),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White.copy(alpha = 0.82f),
                            )
                            Text(
                                "Preview hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = splitAlpha)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 10.dp,
                            end = 10.dp,
                            top = 12.dp,
                            bottom = if (controlsVisible) 48.dp else 10.dp,
                        ),
                ) {
                    GestureZoneHint(
                        icon = Icons.Rounded.Brightness6,
                        label = "Brightness",
                        alpha = brightnessAlpha,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    GestureZoneHint(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Volume",
                        alpha = volumeAlpha,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }

                if (mediaLabelsVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.72f),
                    ) {
                        Text(
                            "Video",
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 3.dp,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = captionOverlayVisible,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(110)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (controlsVisible) 47.dp else 10.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.Black.copy(alpha = 0.70f),
                    ) {
                        Text(
                            "Evening light over the fjord",
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 4.dp,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(110)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.74f))
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (startVideosMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color.White,
                        )
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                        Text(
                            autoplayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(timelineHeight)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.24f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.58f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }

                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mediaCaptionBelow,
            enter = fadeIn(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(110)),
        ) {
            Text(
                "Evening light over the fjord",
                modifier = Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GestureZoneHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(27.dp),
            tint = Color.White.copy(alpha = alpha),
        )
        Text(
            label,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White.copy(alpha = alpha),
        )
        Text(
            "↑  ↓",
            modifier = Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = alpha),
        )
    }
}

@Composable
private fun FjordPreviewBackdrop(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF5A3A55),
                    Color(0xFFE18B68),
                    Color(0xFFFFC982),
                    Color(0xFF446B7A),
                    Color(0xFF16323E),
                ),
                startY = 0f,
                endY = size.height,
            ),
        )

        drawCircle(
            color = Color(0xFFFFE8A3).copy(alpha = 0.92f),
            radius = size.minDimension * 0.055f,
            center = center.copy(
                x = size.width * 0.57f,
                y = size.height * 0.30f,
            ),
        )

        val farMountains = Path().apply {
            moveTo(0f, size.height * 0.58f)
            lineTo(size.width * 0.14f, size.height * 0.31f)
            lineTo(size.width * 0.25f, size.height * 0.49f)
            lineTo(size.width * 0.38f, size.height * 0.34f)
            lineTo(size.width * 0.52f, size.height * 0.53f)
            lineTo(size.width * 0.66f, size.height * 0.36f)
            lineTo(size.width * 0.77f, size.height * 0.49f)
            lineTo(size.width * 0.89f, size.height * 0.27f)
            lineTo(size.width, size.height * 0.47f)
            lineTo(size.width, size.height * 0.73f)
            lineTo(0f, size.height * 0.73f)
            close()
        }
        drawPath(
            path = farMountains,
            color = Color(0xFF263541).copy(alpha = 0.90f),
        )

        val leftMountain = Path().apply {
            moveTo(0f, size.height * 0.31f)
            lineTo(size.width * 0.13f, size.height * 0.16f)
            lineTo(size.width * 0.26f, size.height * 0.41f)
            lineTo(size.width * 0.38f, size.height * 0.67f)
            lineTo(size.width * 0.26f, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(leftMountain, Color(0xFF101D25))

        val rightMountain = Path().apply {
            moveTo(size.width, size.height * 0.19f)
            lineTo(size.width * 0.88f, size.height * 0.13f)
            lineTo(size.width * 0.77f, size.height * 0.41f)
            lineTo(size.width * 0.66f, size.height * 0.67f)
            lineTo(size.width * 0.76f, size.height)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(rightMountain, Color(0xFF0D1A22))

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF385E6C).copy(alpha = 0.76f),
                    Color(0xFF102A34).copy(alpha = 0.96f),
                ),
                startY = size.height * 0.62f,
                endY = size.height,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                0f,
                size.height * 0.62f,
            ),
        )

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFB15C).copy(alpha = 0.38f),
                    Color.Transparent,
                ),
                startX = size.width * 0.36f,
                endX = size.width * 0.74f,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                0f,
                size.height * 0.63f,
            ),
        )
    }
}


internal fun mediaPreviewAutoplayLabel(
    value: VideoAutoplay,
): String = when (value) {
    VideoAutoplay.ALWAYS -> "Auto"
    VideoAutoplay.WIFI_ONLY -> "Wi-Fi"
    VideoAutoplay.NEVER -> "Manual"
}

internal fun mediaPreviewFullscreenCaptionVisible(
    mode: FullscreenCaptionMode,
    controlsVisible: Boolean,
): Boolean = when (mode) {
    FullscreenCaptionMode.WITH_CONTROLS -> controlsVisible
    FullscreenCaptionMode.ALWAYS_VISIBLE -> true
}

/** Whether the viewer shows its caption at [placement] (MediaViewerScreen's rule). */
internal fun mediaPreviewCaptionVisible(
    placement: MediaCaptionMode,
    mode: MediaCaptionMode,
    fullscreenMode: FullscreenCaptionMode,
    controlsVisible: Boolean,
): Boolean = mode == placement &&
    mode != MediaCaptionMode.HIDDEN &&
    mediaPreviewFullscreenCaptionVisible(fullscreenMode, controlsVisible)
