package app.subshell.prototype

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.subshell.prototype.share.ShareIntoSubshellComponent
import app.subshell.prototype.share.sharedStreams
import java.util.UUID

/**
 * LOW_EXTERNAL_LAUNCHES_USE_SUBSHELLS_OWN_TASK_V1 (OCB-577, owner chose one
 * Subshell): a share or a Reddit link from another app started a second
 * MainActivity inside that app's task, with its own account state, runtime
 * and app lock. External launches now land here (no UI, no history) and are
 * forwarded to the MainActivity in Subshell's own task, which takes them in
 * onNewIntent like any other link.
 */
class ExternalLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { source ->
            runCatching { startActivity(externalLaunchForward(this, source)) }
        }
        finish()
    }
}

/**
 * Proves a share was forwarded from the share target in this process: the
 * value is new per process and never leaves it, so another app sending
 * ACTION_SEND to the exported MainActivity cannot carry it (OCB-570 kept).
 */
internal object ExternalLaunchToken {
    val value: String = UUID.randomUUID().toString()
}

internal const val EXTRA_EXTERNAL_LAUNCH_TOKEN = "app.subshell.prototype.extra.EXTERNAL_LAUNCH_TOKEN"

internal fun externalLaunchForward(context: Context, source: Intent): Intent {
    val throughShareTarget = source.component?.className == ShareIntoSubshellComponent.ALIAS_CLASS &&
        (source.action == Intent.ACTION_SEND || source.action == Intent.ACTION_SEND_MULTIPLE)
    return Intent(source).apply {
        component = ComponentName(context, MainActivity::class.java)
        removeExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN)
        if (throughShareTarget) putExtra(EXTRA_EXTERNAL_LAUNCH_TOKEN, ExternalLaunchToken.value)
        // Subshell's own task, its existing MainActivity (onNewIntent); a shared
        // image keeps the read grant this activity was given, and a replay
        // from history stays marked as one (LOW_SHARE_IS_NOT_REPLAYED, OCB-572).
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (source.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        // SHAREGRANTS01_FORWARDED_SHARE_CARRIES_ITS_URIS_V1: the read grant
        // travels with the intent's ClipData. Android has copied EXTRA_STREAM
        // into it implicitly; from Android 18 it no longer does, so the forward
        // names the shared files itself when the sender did not.
        if (clipData == null) sharedStreamClipData(source)?.let { clipData = it }
    }
}

internal fun sharedStreamClipData(source: Intent): ClipData? {
    if (source.action != Intent.ACTION_SEND && source.action != Intent.ACTION_SEND_MULTIPLE) return null
    val uris = sharedStreams(source)
    if (uris.isEmpty()) return null
    return ClipData.newRawUri(null, uris.first()).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
}
