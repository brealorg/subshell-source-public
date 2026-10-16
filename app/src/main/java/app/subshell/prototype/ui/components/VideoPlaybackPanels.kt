package app.subshell.prototype.ui.components

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.media.VideoPlaybackDiagnostics
import app.subshell.prototype.media.PlaybackTrackChoice
import app.subshell.prototype.media.PlaybackTrackKey
import app.subshell.prototype.media.PlaybackTrackOptions
import app.subshell.prototype.media.PlaybackTrackSelectionMode
import app.subshell.prototype.media.audioSelectionSummary
import app.subshell.prototype.media.audioSummary
import app.subshell.prototype.media.decoderSummary
import app.subshell.prototype.media.sourceSummary
import app.subshell.prototype.media.textSelectionSummary
import app.subshell.prototype.media.textSummary
import app.subshell.prototype.media.videoSelectionSummary
import app.subshell.prototype.media.videoSummary
import app.subshell.prototype.media.VIDEO_SPEED_CHOICES
import app.subshell.prototype.media.videoSpeedLabel

internal fun playbackAudioChoiceOffered(options: PlaybackTrackOptions): Boolean = options.audio.size > 1

internal fun playbackSubtitleChoiceOffered(options: PlaybackTrackOptions): Boolean = options.text.isNotEmpty()

@Composable
internal fun PlaybackOptionsPanel(
    options: PlaybackTrackOptions,
    automaticVideoQuality: String,
    onQuality: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onTechnicalDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    speedSummary: String? = null,
    onSpeed: () -> Unit = {},
    mediaActionRows: (@Composable () -> Unit)? = null,
    showTechnicalDetails: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackPanelHeader(
                title = "Playback options",
                subtitle = "Choices apply to this video session",
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (mediaActionRows != null) {
                mediaActionRows()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PlaybackOptionNavigationRow(
                title = "Quality",
                summary = options.videoSelectionSummary(automaticVideoQuality),
                onClick = onQuality,
            )
            // VIDEO04_EMPTY_CHOICES_ARE_HIDDEN_V1: Reddit videos rarely carry a
            // second audio track or subtitles; a row with nothing to choose is noise.
            if (playbackAudioChoiceOffered(options)) {
                PlaybackOptionNavigationRow(
                    title = "Audio",
                    summary = options.audioSelectionSummary(),
                    onClick = onAudio,
                )
            }
            if (playbackSubtitleChoiceOffered(options)) {
                PlaybackOptionNavigationRow(
                    title = "Subtitles",
                    summary = options.textSelectionSummary(),
                    onClick = onSubtitles,
                )
            }
            if (speedSummary != null) {
                PlaybackOptionNavigationRow(
                    title = "Speed",
                    summary = speedSummary,
                    onClick = onSpeed,
                )
            }
            // VIDEO11_TECHNICAL_DETAILS_BEHIND_A_SETTING_V1 (owner): developer data.
            if (showTechnicalDetails) {
                PlaybackOptionNavigationRow(
                    title = "Technical details",
                    summary = "Tracks, codecs, decoders and dropped frames",
                    onClick = onTechnicalDetails,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackQualityPanel(
    options: PlaybackTrackOptions,
    automaticVideoQuality: String,
    onAutomaticVideo: () -> Unit,
    onVideoTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = "Quality",
        subtitle = options.videoSelectionSummary(automaticVideoQuality),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = "Auto",
            summary = "Adaptive · $automaticVideoQuality ceiling",
            selected = options.selection.videoMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticVideo,
        )
        options.video.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onVideoTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.video, "video tracks")
    }
}

@Composable
internal fun PlaybackAudioPanel(
    options: PlaybackTrackOptions,
    onAutomaticAudio: () -> Unit,
    onAudioTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = "Audio",
        subtitle = options.audioSelectionSummary(),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = "Auto",
            summary = "Use the source default",
            selected = options.selection.audioMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticAudio,
        )
        options.audio.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onAudioTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.audio, "audio tracks")
    }
}

@Composable
internal fun PlaybackSubtitlesPanel(
    options: PlaybackTrackOptions,
    onAutomaticText: () -> Unit,
    onTextOff: () -> Unit,
    onTextTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = "Subtitles",
        subtitle = options.textSelectionSummary(),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = "Off",
            summary = "Disable text tracks",
            selected = options.selection.textMode == PlaybackTrackSelectionMode.OFF,
            onClick = onTextOff,
        )
        PlaybackOptionRow(
            title = "Auto",
            summary = "Use forced or source-default subtitles",
            selected = options.selection.textMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticText,
        )
        options.text.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onTextTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.text, "subtitle tracks")
    }
}

/** A one-tap action row (Share, Download) with progress while it runs. */
@Composable
internal fun PlaybackActionRow(
    title: String,
    summary: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackSpeedPanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = "Speed",
        subtitle = "For this video",
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        VIDEO_SPEED_CHOICES.forEach { choice ->
            PlaybackOptionRow(
                title = videoSpeedLabel(choice),
                summary = "",
                selected = choice == speed,
                onClick = { onSpeed(choice) },
            )
        }
    }
}

@Composable
private fun PlaybackSelectionPanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(max = 680.dp)
            .fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PlaybackPanelHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
                    // ISSUE219_PLAYBACK_CHOICES_ARE_ONE_RADIO_GROUP_V1
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun PlaybackOptionNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null, // the row reads its title (ISSUE219)
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackPanelHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to playback options",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Close playback options",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlaybackTrackChoiceRow(
    choice: PlaybackTrackChoice,
    onClick: () -> Unit,
) {
    PlaybackOptionRow(
        title = choice.title,
        summary = choice.summary,
        selected = choice.selected,
        onClick = onClick,
    )
}

@Composable
private fun PlaybackOptionRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    // The row already says "selected" (ISSUE219).
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PlaybackTrackAvailability(
    known: Boolean,
    choices: List<PlaybackTrackChoice>,
    name: String,
) {
    if (!known || choices.isNotEmpty()) return
    Text(
        text = "No $name available",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
internal fun PlaybackDiagnosticsPanel(
    diagnostics: VideoPlaybackDiagnostics,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(max = 680.dp)
            .fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PlaybackPanelHeader(
                title = "Technical details",
                subtitle = "Read-only runtime diagnostics",
                onBack = onBack,
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlaybackDiagnosticRow("Source", diagnostics.sourceSummary())
                PlaybackDiagnosticRow("Video tracks", diagnostics.tracks.videoSummary())
                PlaybackDiagnosticRow("Audio tracks", diagnostics.tracks.audioSummary())
                PlaybackDiagnosticRow("Text tracks", diagnostics.tracks.textSummary())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PlaybackDiagnosticRow("Active video", diagnostics.activeVideoFormat.videoSummary())
                PlaybackDiagnosticRow("Video decoder", decoderSummary(
                    diagnostics.videoDecoderName,
                    diagnostics.videoDecoderInitializationMs,
                ))
                PlaybackDiagnosticRow("Active audio", diagnostics.activeAudioFormat.audioSummary())
                PlaybackDiagnosticRow("Audio decoder", decoderSummary(
                    diagnostics.audioDecoderName,
                    diagnostics.audioDecoderInitializationMs,
                ))
                PlaybackDiagnosticRow("Dropped frames", diagnostics.droppedVideoFrames.toString())
                diagnostics.lastCodecEvent?.let { PlaybackDiagnosticRow("Codec event", it) }
            }
        }
    }
}

@Composable
private fun PlaybackDiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
