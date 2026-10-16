package app.subshell.prototype.media

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.WorkManager
import java.io.File

// LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1 (OCB-108): the per-post video
// download button was removed on 2026-09-19 and the owner retired the whole
// download system (store, WorkManager queue, worker, playback lookup). What a
// device downloaded before stays in no-backup storage until the reader deletes
// it in Settings; nothing plays from it any more.
internal const val LEGACY_VIDEO_DOWNLOAD_DIRECTORY = "media-downloads"
private const val LEGACY_VIDEO_DOWNLOAD_WORK_TAG = "subshell-media-download"

internal data class LegacyVideoDownloadStatus(
    val videoCount: Int,
    val videoBytes: Long,
    /** Anything left in the folder: videos, partial files or the index. */
    val hasDownloads: Boolean,
)

internal data class LegacyVideoDownloadDeleteResult(
    val videosRemoved: Int,
    val bytesRemoved: Long,
)

internal class LegacyVideoDownloads(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, LEGACY_VIDEO_DOWNLOAD_DIRECTORY)

    @WorkerThread
    fun status(): LegacyVideoDownloadStatus {
        val videos = videoFiles()
        return LegacyVideoDownloadStatus(
            videoCount = videos.size,
            videoBytes = videos.sumOf(File::length),
            hasDownloads = directory.listFiles()?.isNotEmpty() == true,
        )
    }

    /** Cancels any work an older build left queued and removes the folder. */
    @WorkerThread
    fun deleteAll(): LegacyVideoDownloadDeleteResult {
        runCatching {
            WorkManager.getInstance(appContext).cancelAllWorkByTag(LEGACY_VIDEO_DOWNLOAD_WORK_TAG)
        }
        val before = status()
        check(!directory.exists() || directory.deleteRecursively()) {
            "Could not delete the downloaded videos"
        }
        return LegacyVideoDownloadDeleteResult(
            videosRemoved = before.videoCount,
            bytesRemoved = before.videoBytes,
        )
    }

    private fun videoFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".media") }
            ?.toList()
            .orEmpty()
}
