package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 */
@Composable
internal fun AccountDirectoryGate(
    directory: AccountSessionDirectory?,
    content: @Composable () -> Unit,
) {
    var ready by remember(directory) { mutableStateOf(directory == null || directory.isHydrated) }
    if (!ready) {
        LaunchedEffect(directory) {
            try {
                directory?.hydratedSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An unreadable directory keeps its fallback; the app still opens.
            }
            ready = true
        }
        return
    }
    content()
}
