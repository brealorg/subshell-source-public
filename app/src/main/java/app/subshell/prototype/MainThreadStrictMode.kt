package app.subshell.prototype

import android.os.StrictMode

/**
 * STRICTMODE01_DEBUG_LOGS_MAIN_THREAD_IO_V1 (#243): Google's core app quality
 * (StrictMode_Compliance) asks debug builds to watch the main thread. Debug
 * builds log every disk read, disk write and network call made on it (logcat
 * tag StrictMode, with the stack), so main-thread I/O shows up in testing
 * instead of as jank on a slow phone. Log only, never a crash or a dialog;
 * release builds are unchanged.
 */
internal fun installDebugMainThreadCheck(debug: Boolean) {
    if (!debug) return
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy())
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build(),
    )
}
