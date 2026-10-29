package app.subshell.prototype.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.applock.LocalAppLocked
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.ui.MainDestination
import app.subshell.prototype.ui.SubshellRoute
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.CommunityMuteSheet
import app.subshell.prototype.ui.components.MutedCommunitiesSheet
import app.subshell.prototype.ui.screens.BackupFolderOnboarding
import app.subshell.prototype.ui.SecondaryDestination
import app.subshell.prototype.ui.screens.SettingsDestination
import app.subshell.prototype.update.UpdatePresentationHost

/**
 * The sheets and onboarding hosts SubshellApp keeps after the scaffold: the
 * account switcher, the muted-communities list and the community mute editor,
 * the interaction-side and backup-folder onboarding, and the update
 * presentation host. The account-session directory effects live here too
 * because the switcher is what they open.
 *
 * Moved out of SubshellApp verbatim; the four states it writes are hoisted
 * there and passed in, because the drawer and the scaffold write them too.
 */
@Composable
internal fun AppSheetsHost(
    state: SubshellState,
    accountConnect: AccountConnectEntryPoints,
    backupRepository: SettingsBackupRepository,
    accountSwitcherVisibleState: MutableState<Boolean>,
    requestedSettingsDestinationNameState: MutableState<String>,
    mutedCommunitiesSheetVisibleState: MutableState<Boolean>,
    communityMuteEditorNameState: MutableState<String?>,
) {
    val accountSessionDirectory = accountConnect.accountSessionDirectory
    var accountSwitcherVisible by accountSwitcherVisibleState
    var requestedSettingsDestinationName by requestedSettingsDestinationNameState
    var mutedCommunitiesSheetVisible by mutedCommunitiesSheetVisibleState
    var communityMuteEditorName by communityMuteEditorNameState
    val accountSessionDirectoryRevision =
        accountSessionDirectory
            ?.revision
            ?.collectAsStateWithLifecycle()
            ?.value
            ?: 0L
    LaunchedEffect(accountSessionDirectoryRevision) {
        runCatching {
            accountSessionDirectory?.snapshot()
        }.getOrNull()?.let(state::synchronizeAccountSessions)
    }
    val activeAuthenticatedAccountNeedsReauthentication =
        state.accountSessions.any { account ->
            account.stableId == state.activeAccountId &&
                account.source == AccountSessionSource.AUTHENTICATED &&
                account.state ==
                AccountSessionState.REAUTHENTICATION_REQUIRED
        }
    LaunchedEffect(activeAuthenticatedAccountNeedsReauthentication) {
        if (activeAuthenticatedAccountNeedsReauthentication) {
            accountSwitcherVisible = true
        }
    }

    if (accountSwitcherVisible) {
        AccountSwitcherSheet(
            state = state,
            accountConnect = accountConnect,
            onManageAccounts = {
                accountSwitcherVisible = false
                requestedSettingsDestinationName =
                    SettingsDestination.ACCOUNTS.name
                state.openSecondary(SecondaryDestination.SETTINGS)
            },
            onDismiss = { accountSwitcherVisible = false },
        )
    }

    if (mutedCommunitiesSheetVisible) {
        MutedCommunitiesSheet(
            mutes = state.activeCommunityMuteEntries(),
            nowEpochMillis = state.communityMuteClockEpochMillis,
            communityLabel = state::subredditLabel,
            onDismiss = { mutedCommunitiesSheetVisible = false },
            onChange = { communityName ->
                mutedCommunitiesSheetVisible = false
                communityMuteEditorName = communityName
            },
            onUnmute = { communityName ->
                state.unmuteCommunity(communityName)
            },
        )
    }

    communityMuteEditorName?.let { communityName ->
        val currentMute = state.communityMuteFor(communityName)
        CommunityMuteSheet(
            communityLabel = state.subredditLabel(communityName),
            nowEpochMillis = System.currentTimeMillis(),
            onDismiss = { communityMuteEditorName = null },
            onMute = { expiresAtEpochMillis ->
                state.muteCommunity(communityName, expiresAtEpochMillis)
            },
            currentMute = currentMute,
            onUnmute = if (currentMute != null) {
                { state.unmuteCommunity(communityName) }
            } else {
                null
            },
        )
    }

    // UPDATE01A1B1_PRESENTATION_READINESS_V1
    val updatePresentationHomeReady =
        (state.currentNavigationRoute as? SubshellRoute.Main)
            ?.destination == MainDestination.HOME
    var backupFolderOnboardingVisible by remember(backupRepository) {
        mutableStateOf(backupRepository.shouldShowFolderOnboarding())
    }

    // MED6_NO_SELF_OPENED_DIALOG_OVER_THE_LOCK_V1 (OCB-394): the app lock's
    // window covers what was open when it locked, but a dialog that opens
    // afterwards (What's new after an update, onboarding) was drawn above it.
    // The dialogs the app opens by itself wait until it is unlocked.
    val appLocked = LocalAppLocked.current
    if (!state.commentActionOnboardingVisible && !appLocked) {
        BackupFolderOnboarding(
            repository = backupRepository,
            onVisibilityChanged = { visible ->
                backupFolderOnboardingVisible = visible
            },
        )
        if (!backupFolderOnboardingVisible) {
            // UPDATE01A1B_MAIN_PRESENTATION_HOST_V1
            UpdatePresentationHost(
                automaticPresentationEnabled = updatePresentationHomeReady,
            )
        }
    }
}
