package app.subshell.prototype.ui.state

import androidx.compose.runtime.mutableStateMapOf
import app.subshell.prototype.ui.normalizeFeedScrollToken

/**
 * Surface tab selections that survive process death: the per-user profile
 * tabs. Extracted verbatim from SubshellState, which holds one instance
 * initialised from the restored process snapshot and delegates to it.
 *
 * LOWUI4_UNREAD_TAB_STATE_RETIRED_V1 (OCB-225/291): the library, You and
 * profile-activity tabs were saved and restored but no screen read them;
 * they are gone, and an older snapshot's records for them are ignored.
 */
internal class SurfaceTabState(
    initialUserProfileTabs: Map<String, Int>,
) {
    val userProfileTabs = mutableStateMapOf<String, Int>().apply {
        putAll(initialUserProfileTabs)
    }

    internal fun userProfileTab(username: String): Int =
        userProfileTabs[normalizeFeedScrollToken(username)] ?: 0

    internal fun selectUserProfileTab(username: String, tab: Int) {
        userProfileTabs[normalizeFeedScrollToken(username)] = tab.coerceAtLeast(0)
    }
}
