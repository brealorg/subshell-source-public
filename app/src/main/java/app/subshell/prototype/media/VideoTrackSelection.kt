package app.subshell.prototype.media

import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** Session-scoped selection. Exact overrides are intentionally reset when the source changes. */
enum class PlaybackTrackSelectionMode {
    AUTO,
    FIXED,
    OFF,
}

@Immutable
data class PlaybackTrackKey(
    val groupIndex: Int,
    val trackIndex: Int,
)

@Immutable
data class PlaybackTrackChoice(
    val key: PlaybackTrackKey,
    val title: String,
    val summary: String,
    val selected: Boolean,
    internal val sortOrder: Long = 0L,
)

@Immutable
data class PlaybackTrackSelectionState(
    val videoMode: PlaybackTrackSelectionMode = PlaybackTrackSelectionMode.AUTO,
    val videoKey: PlaybackTrackKey? = null,
    val audioMode: PlaybackTrackSelectionMode = PlaybackTrackSelectionMode.AUTO,
    val audioKey: PlaybackTrackKey? = null,
    val textMode: PlaybackTrackSelectionMode = PlaybackTrackSelectionMode.AUTO,
    val textKey: PlaybackTrackKey? = null,
)

@Immutable
data class PlaybackTrackOptions(
    val known: Boolean = false,
    val video: List<PlaybackTrackChoice> = emptyList(),
    val audio: List<PlaybackTrackChoice> = emptyList(),
    val text: List<PlaybackTrackChoice> = emptyList(),
    val selection: PlaybackTrackSelectionState = PlaybackTrackSelectionState(),
)

internal fun PlaybackTrackOptions.videoSelectionSummary(
    automaticVideoQuality: String,
): String = when (selection.videoMode) {
    PlaybackTrackSelectionMode.AUTO -> "Auto · $automaticVideoQuality ceiling"
    PlaybackTrackSelectionMode.FIXED -> selectedChoiceTitle(video)
    PlaybackTrackSelectionMode.OFF -> "Off"
}

internal fun PlaybackTrackOptions.audioSelectionSummary(): String = when (selection.audioMode) {
    PlaybackTrackSelectionMode.AUTO -> "Auto · source default"
    PlaybackTrackSelectionMode.FIXED -> selectedChoiceTitle(audio)
    PlaybackTrackSelectionMode.OFF -> "Off"
}

internal fun PlaybackTrackOptions.textSelectionSummary(): String = when (selection.textMode) {
    PlaybackTrackSelectionMode.AUTO -> "Auto · forced or source default"
    PlaybackTrackSelectionMode.FIXED -> selectedChoiceTitle(text)
    PlaybackTrackSelectionMode.OFF -> "Off"
}

private fun PlaybackTrackOptions.selectedChoiceTitle(
    choices: List<PlaybackTrackChoice>,
): String = when {
    !known -> "Loading tracks…"
    else -> choices.firstOrNull { it.selected }?.title ?: "Selected track unavailable"
}

internal fun PlaybackTrackSelectionState.withAutomaticVideo(): PlaybackTrackSelectionState = copy(
    videoMode = PlaybackTrackSelectionMode.AUTO,
    videoKey = null,
)

internal fun PlaybackTrackSelectionState.withFixedVideo(
    key: PlaybackTrackKey,
): PlaybackTrackSelectionState = copy(
    videoMode = PlaybackTrackSelectionMode.FIXED,
    videoKey = key,
)

internal fun PlaybackTrackSelectionState.withAutomaticAudio(): PlaybackTrackSelectionState = copy(
    audioMode = PlaybackTrackSelectionMode.AUTO,
    audioKey = null,
)

internal fun PlaybackTrackSelectionState.withFixedAudio(
    key: PlaybackTrackKey,
): PlaybackTrackSelectionState = copy(
    audioMode = PlaybackTrackSelectionMode.FIXED,
    audioKey = key,
)

internal fun PlaybackTrackSelectionState.withAutomaticText(): PlaybackTrackSelectionState = copy(
    textMode = PlaybackTrackSelectionMode.AUTO,
    textKey = null,
)

internal fun PlaybackTrackSelectionState.withFixedText(
    key: PlaybackTrackKey,
): PlaybackTrackSelectionState = copy(
    textMode = PlaybackTrackSelectionMode.FIXED,
    textKey = key,
)

internal fun PlaybackTrackSelectionState.withTextDisabled(): PlaybackTrackSelectionState = copy(
    textMode = PlaybackTrackSelectionMode.OFF,
    textKey = null,
)

@AndroidxOptIn(UnstableApi::class)
internal fun playbackTrackOptions(
    tracks: Tracks,
    selection: PlaybackTrackSelectionState,
): PlaybackTrackOptions {
    val video = mutableListOf<PlaybackTrackChoice>()
    val audio = mutableListOf<PlaybackTrackChoice>()
    val text = mutableListOf<PlaybackTrackChoice>()
    var videoOrdinal = 0
    var audioOrdinal = 0
    var textOrdinal = 0

    tracks.groups.forEachIndexed { groupIndex, group ->
        repeat(group.length) { trackIndex ->
            if (!group.isTrackSupported(trackIndex)) return@repeat
            val key = PlaybackTrackKey(groupIndex, trackIndex)
            val format = group.getTrackFormat(trackIndex)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    videoOrdinal += 1
                    video += PlaybackTrackChoice(
                        key = key,
                        title = videoTrackChoiceTitle(format, videoOrdinal),
                        summary = format.toVideoTrackFormatDiagnostics().videoSummary(),
                        selected = selection.videoMode == PlaybackTrackSelectionMode.FIXED &&
                            selection.videoKey == key,
                        sortOrder = videoTrackSortOrder(format, videoOrdinal),
                    )
                }

                C.TRACK_TYPE_AUDIO -> {
                    audioOrdinal += 1
                    audio += PlaybackTrackChoice(
                        key = key,
                        title = audioTrackChoiceTitle(format, audioOrdinal),
                        summary = format.toVideoTrackFormatDiagnostics().audioSummary(),
                        selected = selection.audioMode == PlaybackTrackSelectionMode.FIXED &&
                            selection.audioKey == key,
                    )
                }

                C.TRACK_TYPE_TEXT -> {
                    textOrdinal += 1
                    text += PlaybackTrackChoice(
                        key = key,
                        title = textTrackChoiceTitle(format, textOrdinal),
                        summary = textTrackChoiceSummary(format),
                        selected = selection.textMode == PlaybackTrackSelectionMode.FIXED &&
                            selection.textKey == key,
                    )
                }
            }
        }
    }

    return PlaybackTrackOptions(
        known = true,
        video = video.sortedBy { it.sortOrder },
        audio = audio,
        text = text,
        selection = selection,
    )
}

// VIDEO04_QUALITY_NAMED_BY_THE_SHORT_EDGE_V1 (#227): "720p" means the short
// edge; a portrait 720×1280 video was shown as "1280p".
internal fun videoTrackChoiceTitle(
    format: VideoTrackFormatDiagnostics,
    ordinal: Int,
): String = format.label?.takeUnless { it.isBlank() }
    ?: videoShortEdge(format.width, format.height)?.let { "${it}p" }
    ?: format.width?.let { "${it}px video" }
    ?: "Video $ordinal"

internal fun videoShortEdge(width: Int?, height: Int?): Int? = when {
    width != null && height != null && width > 0 && height > 0 -> minOf(width, height)
    else -> height?.takeIf { it > 0 }
}

internal fun audioTrackChoiceTitle(
    format: VideoTrackFormatDiagnostics,
    ordinal: Int,
): String = format.label?.takeUnless { it.isBlank() }
    ?: format.language?.takeUnless { it.isBlank() }?.uppercase(Locale.ROOT)
    ?: "Audio $ordinal"

internal fun textTrackChoiceTitle(
    format: VideoTrackFormatDiagnostics,
    ordinal: Int,
): String = format.label?.takeUnless { it.isBlank() }
    ?: format.language?.takeUnless { it.isBlank() }?.uppercase(Locale.ROOT)
    ?: "Subtitles $ordinal"

private fun videoTrackChoiceTitle(format: Format, ordinal: Int): String =
    videoTrackChoiceTitle(format.toVideoTrackFormatDiagnostics(), ordinal)

private fun audioTrackChoiceTitle(format: Format, ordinal: Int): String =
    audioTrackChoiceTitle(format.toVideoTrackFormatDiagnostics(), ordinal)

private fun textTrackChoiceTitle(format: Format, ordinal: Int): String =
    textTrackChoiceTitle(format.toVideoTrackFormatDiagnostics(), ordinal)

private fun textTrackChoiceSummary(format: Format): String = listOfNotNull(
    format.language?.takeUnless { it.isBlank() },
    format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT),
).ifEmpty { listOf("Text track") }.joinToString(" · ")

@AndroidxOptIn(UnstableApi::class)
private fun videoTrackSortOrder(format: Format, ordinal: Int): Long {
    val pixels = format.width.takeIf { it > 0 }?.toLong()?.times(
        format.height.takeIf { it > 0 }?.toLong() ?: 1L,
    ) ?: 0L
    val bitrate = format.bitrate.takeIf { it > 0 }?.toLong() ?: 0L
    return pixels * 10_000_000L + bitrate + ordinal
}
