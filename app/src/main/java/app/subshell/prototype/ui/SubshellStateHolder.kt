package app.subshell.prototype.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.subshell.prototype.data.SubshellRepository
import app.subshell.prototype.data.account.AccountSessionDirectory

// ISSUE245_STATE_SURVIVES_CONFIGURATION_CHANGES_V1 (#245): a dark-mode
// switch, font size, language or keyboard recreates MainActivity. SubshellState
// used to be rebuilt from its process snapshot then, so everything the snapshot
// does not carry (loaded feeds and threads, in-flight work) was read again,
// while the video coordinator (a ViewModel) lived on. Google keeps screen state
// in a ViewModel: the activity's ViewModelStore now holds the state across a
// recreation, and the snapshot (rememberSaveable) is read only after process
// death, when this holder is new. SubshellState holds no activity: its
// requesters and followers are set again by the new composition.

/** Holds the process's SubshellState for one activity's ViewModelStore. */
internal class SubshellStateHolder : ViewModel() {
    private var held: Held? = null

    private class Held(
        val repository: SubshellRepository,
        val directory: AccountSessionDirectory?,
        val state: SubshellState,
    )

    /** The state held for these stores, or null (another repository or directory gets a new one). */
    fun stateFor(repository: SubshellRepository, directory: AccountSessionDirectory?): SubshellState? =
        held?.takeIf { it.repository === repository && it.directory === directory }?.state

    fun hold(repository: SubshellRepository, directory: AccountSessionDirectory?, state: SubshellState) {
        if (held?.state !== state) held = Held(repository, directory, state)
    }
}

/** Tests give each simulated process its own store; the app uses its activity's. */
internal val LocalSubshellStateStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner?> { null }

@Composable
internal fun subshellStateHolder(): SubshellStateHolder? {
    val owner = LocalSubshellStateStoreOwner.current ?: LocalContext.current.viewModelStoreOwner()
    return owner?.let { ViewModelProvider(it)[SubshellStateHolder::class.java] }
}

private tailrec fun Context.viewModelStoreOwner(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.viewModelStoreOwner()
    else -> null
}
