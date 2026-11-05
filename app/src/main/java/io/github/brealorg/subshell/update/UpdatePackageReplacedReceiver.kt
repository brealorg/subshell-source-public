package io.github.brealorg.subshell.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.brealorg.subshell.BuildConfig

internal class UpdatePackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // ISSUE52: the one-time step after the rename reads and writes files, so it runs
        // off the main thread; goAsync keeps the broadcast (and the process) until it is done.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            val appContext = context.applicationContext
            Thread({
                try {
                    io.github.brealorg.subshell.AfterTheRename.runOnce(appContext)
                } finally {
                    pending.finish()
                }
            }, "subshell-after-the-rename-update").start()
        }
        handle(context, intent)
    }

    // STEP3_UPDATE_RUNTIME_FENCE_IS_A_PARAMETER_V1: the release fence and the
    // version are defaults; a test passes its own.
    internal fun handle(
        context: Context,
        intent: Intent,
        runtimeEnabled: Boolean = UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG),
        versionCode: Int = BuildConfig.VERSION_CODE,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!runtimeEnabled) return

        val appContext = context.applicationContext
        UpdateStateStore(appContext).recordPackageReplaced(versionCode)
        UpdateCheckScheduler.schedule(appContext, runtimeEnabled)
    }
}
