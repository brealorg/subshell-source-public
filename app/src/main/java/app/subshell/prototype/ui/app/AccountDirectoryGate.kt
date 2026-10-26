package app.subshell.prototype.ui.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.subshell.prototype.data.account.AccountSessionDirectory
import kotlinx.coroutines.CancellationException

/**
 * ISSUE119_COMPOSE_AFTER_THE_ACCOUNT_DIRECTORY_V1: the stored accounts are
 * read (Keystore decrypt) after the activity starts. SubshellApp used to build
 * its state from the placeholder directory, which holds only the local
 * account: it chose and persisted the local account, loaded its data, and
 * then "switched" to the real account when the directory arrived (clearing a
 * restored search, archiving an open draft, resetting votes and feeds). The
 * app is now composed once the directory is read; until then only the window
 * background shows (a short moment, mostly under the system splash).
 * Restored instance state is still applied on that first composition.
 * The startup preference files are read on IO before it too (StartupPreferences).
 */
@Composable
internal fun AccountDirectoryGate(
    directory: AccountSessionDirectory?,
    // Tests replace the startup preference wait.
    preferencesLoaded: () -> Boolean = { StartupPreferences.loaded },
    awaitPreferences: (suspend (Context) -> Unit) = { context -> StartupPreferences.await(context) },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var ready by remember(directory) {
        mutableStateOf((directory == null || directory.isHydrated) && preferencesLoaded())
    }
    if (!ready) {
        LaunchedEffect(directory) {
            try {
                directory?.hydratedSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An unreadable directory keeps its fallback; the app still opens.
            }
            // STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243)
            awaitPreferences(context)
            ready = true
        }
        return
    }
    content()
}
