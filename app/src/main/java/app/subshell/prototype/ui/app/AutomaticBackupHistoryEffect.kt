package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.ui.PortableBackupState
import app.subshell.prototype.ui.SubshellState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

/**
 * The automatic backup history: records all portable state once on entry,
 * then again after every change, debounced by 30 s, off the main thread.
 * What is portable is SubshellState.capturePortableBackupState's decision;
 * this effect only records it.
 *
 * Moved out of SubshellApp verbatim, with its debounce constant.
 */
@Composable
internal fun AutomaticBackupHistoryEffect(
    state: SubshellState,
    backupRepository: SettingsBackupRepository,
) {
    // BACKUP03C2_ALL_PORTABLE_AUTOMATIC_HISTORY_V1
    LaunchedEffect(state, backupRepository) {
        recordAutomaticBackup(backupRepository, state.capturePortableBackupState())
        snapshotFlow { state.capturePortableBackupState() }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { portableState ->
                delay(AUTOMATIC_BACKUP_DEBOUNCE_MILLIS)
                recordAutomaticBackup(backupRepository, portableState)
            }
    }
}

private const val AUTOMATIC_BACKUP_DEBOUNCE_MILLIS = 30_000L

/**
 * BACKUP05_AUTOMATIC_HISTORY_NEVER_CRASHES_V1
 * Automatic history is best effort: a store failure (disk full, a file that
 * cannot be written or deleted) skips this recording instead of escaping the
 * effect and crashing the app on every start. The next change tries again;
 * manual saves still report their own failures.
 */
internal suspend fun recordAutomaticBackup(
    backupRepository: SettingsBackupRepository,
    portableState: PortableBackupState,
) {
    try {
        withContext(Dispatchers.IO) {
            backupRepository.recordSettingsChange(
                settings = portableState.settings,
                joinedCommunityIdsByAccount = portableState.joinedCommunityIdsByAccount,
                favoriteCommunityNamesByAccount = portableState.favoriteCommunityNamesByAccount,
                favoriteCustomFeedPathsByAccount = portableState.favoriteCustomFeedPathsByAccount,
                hiddenCommunityNamesByAccount = portableState.hiddenCommunityNamesByAccount,
                communityMutesByAccount = portableState.communityMutesByAccount,
                postComposerDraftsByAccount = portableState.postComposerDraftsByAccount,
                moderatorSavedResponses = portableState.moderatorSavedResponses,
                userTagsByAccount = portableState.userTagsByAccount,
                savedSearchesByAccount = portableState.savedSearchesByAccount,
                watchedCommunityNamesByAccount = portableState.watchedCommunityNamesByAccount,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Skipped; see above.
    }
}
