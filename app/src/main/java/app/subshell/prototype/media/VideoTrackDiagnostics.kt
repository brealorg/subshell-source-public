package app.subshell.prototype.media

import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@Immutable
data class VideoTrackTypeInventory(
    val groupCount: Int = 0,
    val trackCount: Int = 0,
    val supportedTrackCount: Int = 0,
    val selectedTrackCount: Int = 0,
)

@Immutable
data class VideoTrackInventory(
    val known: Boolean = false,
    val video: VideoTrackTypeInventory = VideoTrackTypeInventory(),
    val audio: VideoTrackTypeInventory = VideoTrackTypeInventory(),
    val text: VideoTrackTypeInventory = VideoTrackTypeInventory(),
    val adaptiveVideoGroupCount: Int = 0,
)

@Immutable
data class VideoTrackFormatDiagnostics(
    val label: String? = null,
    val sampleMimeType: String? = null,
    val codecs: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val language: String? = null,
    val channelCount: Int? = null,
    val sampleRate: Int? = null,
)

@Immutable
data class VideoPlaybackDiagnostics(
    val mediaId: String? = null,
    val sourceKind: VideoMediaSourceKind? = null,
    val sourceIndex: Int = 0,
    val sourceCount: Int = 0,
    val compositeSource: Boolean = false,
    val tracks: VideoTrackInventory = VideoTrackInventory(),
    val activeVideoFormat: VideoTrackFormatDiagnostics? = null,
    val activeAudioFormat: VideoTrackFormatDiagnostics? = null,
    val videoDecoderName: String? = null,
    val videoDecoderInitializationMs: Long? = null,
    val audioDecoderName: String? = null,
    val audioDecoderInitializationMs: Long? = null,
    val droppedVideoFrames: Int = 0,
    val lastCodecEvent: String? = null,
)

internal fun initialVideoPlaybackDiagnostics(
    media: VideoMedia,
    sourceIndex: Int,
): VideoPlaybackDiagnostics {
    val safeIndex = sourceIndex.coerceIn(media.sources.indices)
    val source = media.sources[safeIndex]
    return VideoPlaybackDiagnostics(
        mediaId = media.mediaId,
        sourceKind = source.kind,
        sourceIndex = safeIndex,
        sourceCount = media.sources.size,
        compositeSource = source.isComposite,
    )
}

@AndroidxOptIn(UnstableApi::class)
internal fun trackInventoryDiagnostics(tracks: Tracks): VideoTrackInventory {
    var video = VideoTrackTypeInventory()
    var audio = VideoTrackTypeInventory()
    var text = VideoTrackTypeInventory()
    var adaptiveVideoGroupCount = 0

    tracks.groups.forEach { group ->
        var supportedTrackCount = 0
        var selectedTrackCount = 0
        repeat(group.length) { trackIndex ->
            if (group.isTrackSupported(trackIndex)) supportedTrackCount += 1
            if (group.isTrackSelected(trackIndex)) selectedTrackCount += 1
        }
        val inventory = VideoTrackTypeInventory(
            groupCount = 1,
            trackCount = group.length,
            supportedTrackCount = supportedTrackCount,
            selectedTrackCount = selectedTrackCount,
        )
        when (group.type) {
            C.TRACK_TYPE_VIDEO -> {
                video += inventory
                if (group.isAdaptiveSupported) adaptiveVideoGroupCount += 1
            }
            C.TRACK_TYPE_AUDIO -> audio += inventory
            C.TRACK_TYPE_TEXT -> text += inventory
        }
    }

    return VideoTrackInventory(
        known = true,
        video = video,
        audio = audio,
        text = text,
        adaptiveVideoGroupCount = adaptiveVideoGroupCount,
    )
}

private operator fun VideoTrackTypeInventory.plus(
    other: VideoTrackTypeInventory,
): VideoTrackTypeInventory = VideoTrackTypeInventory(
    groupCount = groupCount + other.groupCount,
    trackCount = trackCount + other.trackCount,
    supportedTrackCount = supportedTrackCount + other.supportedTrackCount,
    selectedTrackCount = selectedTrackCount + other.selectedTrackCount,
)

@AndroidxOptIn(UnstableApi::class)
internal fun Format.toVideoTrackFormatDiagnostics(): VideoTrackFormatDiagnostics =
    VideoTrackFormatDiagnostics(
        label = label?.takeUnless { it.isBlank() },
        sampleMimeType = sampleMimeType,
        codecs = codecs,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        frameRate = frameRate.takeIf { it > 0f },
        bitrate = bitrate.takeIf { it > 0 },
        language = language?.takeUnless { it.isBlank() },
        channelCount = channelCount.takeIf { it > 0 },
        sampleRate = sampleRate.takeIf { it > 0 },
    )

internal fun VideoPlaybackDiagnostics.sourceSummary(): String {
    if (sourceKind == null || sourceCount <= 0) return "Pending"
    val sourcePosition = "${sourceIndex + 1}/$sourceCount"
    val sourceType = when (sourceKind) {
        VideoMediaSourceKind.PROGRESSIVE -> "Progressive"
        VideoMediaSourceKind.DASH -> "DASH"
        VideoMediaSourceKind.HLS -> "HLS"
    }
    return buildString {
        append(sourceType)
        append(" · source ")
        append(sourcePosition)
        if (compositeSource) append(" · split A/V")
    }
}

internal fun VideoTrackInventory.videoSummary(): String =
    trackTypeSummary(video, adaptiveVideoGroupCount.takeIf { it > 0 }?.let { "$it adaptive" })

internal fun VideoTrackInventory.audioSummary(): String = trackTypeSummary(audio)

internal fun VideoTrackInventory.textSummary(): String = trackTypeSummary(text)

private fun VideoTrackInventory.trackTypeSummary(
    inventory: VideoTrackTypeInventory,
    suffix: String? = null,
): String {
    if (!known) return "Pending"
    if (inventory.trackCount == 0) return "None"
    return buildString {
        append(inventory.selectedTrackCount)
        append(" selected · ")
        append(inventory.supportedTrackCount)
        append('/')
        append(inventory.trackCount)
        append(" supported")
        if (suffix != null) {
            append(" · ")
            append(suffix)
        }
    }
}

internal fun VideoTrackFormatDiagnostics?.videoSummary(): String {
    val format = this ?: return "Pending"
    return listOfNotNull(
        format.resolutionSummary(),
        format.frameRate?.let { "${formatDecimal(it)} fps" },
        format.bitrate?.let(::formatBitrate),
        format.codecSummary(),
    ).ifEmpty { listOf("Unknown") }.joinToString(" · ")
}

internal fun VideoTrackFormatDiagnostics?.audioSummary(): String {
    val format = this ?: return "None or pending"
    return listOfNotNull(
        format.channelCount?.let { "$it ch" },
        format.sampleRate?.let { "${formatSampleRate(it)} kHz" },
        format.bitrate?.let(::formatBitrate),
        format.language,
        format.codecSummary(),
    ).ifEmpty { listOf("Unknown") }.joinToString(" · ")
}

internal fun decoderSummary(name: String?, initializationMs: Long?): String {
    if (name == null) return "Pending"
    return if (initializationMs != null) "$name · ${initializationMs} ms init" else name
}

private fun VideoTrackFormatDiagnostics.resolutionSummary(): String? =
    if (width != null && height != null) "${width}×${height}" else null

private fun VideoTrackFormatDiagnostics.codecSummary(): String? =
    codecs?.takeUnless { it.isBlank() }
        ?: sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT)

private fun formatDecimal(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.ROOT, "%.2f", value)

private fun formatBitrate(bitrate: Int): String = when {
    bitrate >= 1_000_000 -> String.format(Locale.ROOT, "%.2f Mbps", bitrate / 1_000_000.0)
    bitrate >= 1_000 -> String.format(Locale.ROOT, "%.0f kbps", bitrate / 1_000.0)
    else -> "$bitrate bps"
}

private fun formatSampleRate(sampleRate: Int): String =
    String.format(Locale.ROOT, "%.1f", sampleRate / 1_000.0)
