package app.subshell.prototype.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * FEEDS04_CACHED_COMMUNITY_IDENTITY_OFF_MAIN_V1: surfaces that list favorite
 * or recent communities load their stored identities off the main thread
 * while shown; each name is read once per process.
 */
@Composable
internal fun LoadCachedCommunityListIdentities(state: SubshellState) {
    LaunchedEffect(state) {
        snapshotFlow {
            state.effectiveFavoriteCommunityNames to
                state.recentCommunityVisits.map { visit -> visit.communityName }
        }
            .distinctUntilChanged()
            .collect { state.loadCachedCommunityListIdentities() }
    }
}
