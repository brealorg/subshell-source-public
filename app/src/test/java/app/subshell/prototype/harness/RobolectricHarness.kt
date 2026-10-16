package app.subshell.prototype.harness

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * HARNESS01 helpers for tests that render SubshellApp on the JVM. The app
 * initialises WorkManager itself in a path Robolectric does not run, and
 * WorkManager may only be initialised once per JVM, so tests in one run
 * share this guard.
 */
internal fun ensureWorkManagerInitialized(context: Context) {
    if (!WorkManager.isInitialized()) {
        WorkManager.initialize(context, Configuration.Builder().build())
    }
}
