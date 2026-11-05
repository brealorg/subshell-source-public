package io.github.brealorg.subshell.auth

import android.util.Log
import io.github.brealorg.subshell.BuildConfig

// LOW6_IDENTIFIERS_LOGGED_ONLY_IN_DEBUG_BUILDS_V1 (OCB-401/410/422/443): the
// load traces of the auth coordinators name accounts, users, communities,
// search queries and item ids. Release builds keep them out of logcat, which
// other apps with log access and bug reports can read.
internal fun authDebugLog(tag: String, message: String) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
}
