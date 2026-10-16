package app.subshell.prototype.ui

import app.subshell.prototype.media.LegacyVideoDownloadStatus
import java.util.Locale

// LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1: what Settings says about the
// videos a device downloaded before the download system was retired.
fun formatVideoDownloadBytes(bytes: Long): String {
    require(bytes >= 0L) { "Download bytes must be non-negative" }
    val kibibyte = 1024L
    val mebibyte = kibibyte * 1024L
    val gibibyte = mebibyte * 1024L
    return when {
        bytes == 0L -> "0 B"
        // LOWUI_BYTE_SIZES_ROUND_BEFORE_THE_UNIT_V1 (OCB-106): the unit and
        // precision follow the rounded value, so no "1024 KB", "1024 MB" or
        // "10.0 MB".
        bytes < mebibyte && (bytes + kibibyte - 1L) / kibibyte < 1024L ->
            "${(bytes + kibibyte - 1L) / kibibyte} KB"
        bytes < gibibyte && roundedSize(bytes.toDouble() / mebibyte.toDouble()) < 1024.0 -> {
            val value = roundedSize(bytes.toDouble() / mebibyte.toDouble())
            val pattern = if (value >= 10.0 || value % 1.0 == 0.0) "%.0f MB" else "%.1f MB"
            String.format(Locale.US, pattern, value)
        }
        else -> {
            val value = roundedSize(bytes.toDouble() / gibibyte.toDouble())
            val pattern = if (value >= 10.0 || value % 1.0 == 0.0) "%.0f GB" else "%.1f GB"
            String.format(Locale.US, pattern, value)
        }
    }
}

/** One decimal below 10, whole numbers from 10 up: the value as it will be shown. */
private fun roundedSize(value: Double): Double =
    if (value < 10.0) Math.round(value * 10.0) / 10.0 else Math.round(value).toDouble()

internal fun legacyVideoDownloadSummary(status: LegacyVideoDownloadStatus): String {
    val videos = if (status.videoCount == 1) "1 downloaded video" else "${status.videoCount} downloaded videos"
    return "$videos · ${formatVideoDownloadBytes(status.videoBytes)}"
}
