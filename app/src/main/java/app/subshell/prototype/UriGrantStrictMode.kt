package app.subshell.prototype

import android.os.Build
import android.os.StrictMode

/**
 * SHAREGRANTS01_DEBUG_FLAGS_IMPLICIT_URI_GRANTS_V1: Android 18 stops granting
 * access to a content URI that an outgoing ACTION_SEND or IMAGE_CAPTURE only
 * names in an extra. Debug builds on Android 17+ log every such implicit grant
 * (logcat tag StrictMode), so a share that still relies on it shows up in
 * testing. Release builds are unchanged.
 */
internal fun installDebugUriGrantCheck(debug: Boolean) {
    if (!debug || Build.VERSION.SDK_INT < 37) return
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
            .detectImplicitUriPermissionGrant()
            .penaltyLog()
            .build(),
    )
}
