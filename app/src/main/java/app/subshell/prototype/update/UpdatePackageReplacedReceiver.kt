package app.subshell.prototype.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.subshell.prototype.BuildConfig

internal class UpdatePackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
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
