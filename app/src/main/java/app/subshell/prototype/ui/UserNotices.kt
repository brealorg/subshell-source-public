package app.subshell.prototype.ui

import android.os.Build
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.error.presentNotice

// ISSUE222_NO_TOASTS_V1: one-off feedback goes to the viewport tray (the
// app's one message surface), not to Toasts. A failure keeps the error tone;
// a confirmation uses the neutral tone (owner decision).
internal const val USER_NOTICE_SCOPE = "user-notice"

internal fun ViewportErrorHostState?.noticeFailure(title: String, message: String) {
    this?.presentNotice(USER_NOTICE_SCOPE, title, message, TrayTone.ERROR)
}

internal fun ViewportErrorHostState?.noticeInfo(title: String, message: String) {
    this?.presentNotice(USER_NOTICE_SCOPE, title, message, TrayTone.INFO)
}

/**
 * Android 13 and newer confirm a copy themselves (Google: do not add your
 * own confirmation there); older versions get a neutral tray notice.
 */
internal fun ViewportErrorHostState?.noticeCopied(what: String, sdkInt: Int = Build.VERSION.SDK_INT) {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        noticeInfo("$what copied", "It's on the clipboard")
    }
}
