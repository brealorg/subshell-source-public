package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/**
 * ISSUE74_POST_VISIT_STATE_IS_PRUNED_V1: every post visit kept its saved
 * screen state ("post-<id>-<visitId>": list position, thread baseline, one
 * entry per composed comment) for the life of the process, also after the
 * visit left the route stack, so the saved-instance bundle grew with every
 * post opened. The state of a visit no longer on the stack is removed.
 */
internal class PostVisitPruningStateHolder(
    private val delegate: SaveableStateHolder,
) : SaveableStateHolder {
    private val usedPostVisitKeys = mutableSetOf<String>()

    @Composable
    override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) {
        if (key is String && postVisitIdOfStateKey(key) != null) usedPostVisitKeys += key
        delegate.SaveableStateProvider(key, content)
    }

    override fun removeState(key: Any) {
        usedPostVisitKeys.remove(key)
        delegate.removeState(key)
    }

    fun pruneVisitsNotIn(liveVisitIds: Set<Int>) {
        usedPostVisitKeys
            .filter { key -> postVisitIdOfStateKey(key) !in liveVisitIds }
            .forEach(::removeState)
    }
}

/** The visit id of a "post-<id>-<visitId>" state key, else null. */
internal fun postVisitIdOfStateKey(key: String): Int? =
    if (key.startsWith("post-")) key.substringAfterLast('-').toIntOrNull() else null

@Composable
internal fun rememberPostVisitPruningStateHolder(liveVisitIds: Set<Int>): SaveableStateHolder {
    val delegate = rememberSaveableStateHolder()
    val holder = remember(delegate) { PostVisitPruningStateHolder(delegate) }
    // Runs after the composition that left the popped visit, so its state is
    // already saved and no longer composed when it is removed.
    LaunchedEffect(holder, liveVisitIds) { holder.pruneVisitsNotIn(liveVisitIds) }
    return holder
}
