package io.github.brealorg.subshell.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.communityposts.CommunityPostCheckScheduler
import io.github.brealorg.subshell.ui.SubshellState

/**
 * ISSUE239_BACKGROUND_CHECK_FOR_NEW_POSTS_V1: the background check runs while
 * the active signed-in account watches at least one community; it follows
 * account switches, sign-out and the last community being unwatched.
 */
@Composable
internal fun CommunityPostCheckEffect(state: SubshellState) {
    val context = LocalContext.current.applicationContext
    val account = state.activeAccountId
    val watching = state.communityPostNotificationsAvailable && state.watchedCommunityNames.isNotEmpty()
    LaunchedEffect(context, account, watching) {
        keepCommunityPostCheckScheduled(context, account, watching)
    }
}

internal fun keepCommunityPostCheckScheduled(context: Context, accountStableId: String, watching: Boolean) {
    runCatching {
        if (watching) {
            CommunityPostCheckScheduler.schedule(context, accountStableId)
        } else {
            CommunityPostCheckScheduler.cancel(context)
        }
    }
}
