package app.subshell.prototype.ui

import android.Manifest
import android.os.Build

/**
 * LOWUI_LEGACY_DOWNLOADS_ASK_FOR_STORAGE_V1 (OCB-084): before Android 10 a
 * file only reaches the public Downloads folder with WRITE_EXTERNAL_STORAGE
 * (declared with maxSdkVersion 28). The activity asks for it the first time
 * media is downloaded; without it the file goes to Subshell's own folder.
 */
internal fun interface LegacyStorageWritePermissionRequester {
    /** True when the permission is held or was just granted. Main thread. */
    suspend fun requestLegacyStorageWrite(): Boolean
}

internal const val LEGACY_STORAGE_WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

internal fun legacyStorageWritePermissionNeeded(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt < Build.VERSION_CODES.Q
