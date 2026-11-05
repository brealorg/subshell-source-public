package io.github.brealorg.subshell

import android.app.Application
import androidx.work.Configuration

/**
 * ISSUE55_WORK_FROM_BEFORE_THE_RENAME_RUNS_V1: WorkManager is set up on demand with
 * [RenamedWorkerFactory], so work an older version stored under the old class names still runs.
 * The manifest leaves out WorkManager's own startup initializer for this.
 */
class SubshellApplication : Application(), Configuration.Provider {
    /**
     * ISSUE52_THE_STEP_AFTER_THE_RENAME_RUNS_AT_THE_FIRST_START_V1: the update's
     * MY_PACKAGE_REPLACED is lost when Android removes the app's task from Recents (the old
     * MainActivity name is gone) and kills the process it started for it (seen on the Pixel 6).
     * Every process start runs the one-time step too, off the main thread; its marker makes this
     * a single file check once done.
     */
    override fun onCreate() {
        super.onCreate()
        AfterTheRename.runOnceInTheBackground(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(RenamedWorkerFactory)
            .build()
}
