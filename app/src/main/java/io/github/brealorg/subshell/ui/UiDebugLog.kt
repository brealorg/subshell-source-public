package io.github.brealorg.subshell.ui

import android.util.Log
import io.github.brealorg.subshell.BuildConfig

// LOWUI3_UI_TRACES_LOGGED_ONLY_IN_DEBUG_BUILDS_V1 (OCB-120/242): the ui/
// traces name posts, comments, authors, communities, accounts and query keys.
// Release builds keep them out of logcat, which bug reports and adb can read
// (the same rule as authDebugLog in auth/).
internal fun uiDebugLog(tag: String, message: String) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
}
