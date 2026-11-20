package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import io.github.brealorg.subshell.language.SELECTABLE_LANGUAGES
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brealorg.subshell.ui.app.onDisk
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.brealorg.subshell.share.ShareIntoSubshellComponent
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.theme.AppTextScale
import io.github.brealorg.subshell.ui.theme.AppUiScale
import io.github.brealorg.subshell.inbox.InboxNotificationCenter
import io.github.brealorg.subshell.inbox.InboxBackgroundSyncScheduler
import io.github.brealorg.subshell.inbox.InboxBackgroundEventStore
import androidx.core.content.ContextCompat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.backup.BackupProtection
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.auth.RedditApiValues
import io.github.brealorg.subshell.auth.RedditCompatibilityConfigurationController
import io.github.brealorg.subshell.media.LegacyVideoDownloadStatus
import io.github.brealorg.subshell.media.LegacyVideoDownloads
import io.github.brealorg.subshell.media.VideoMediaCacheStatus
import io.github.brealorg.subshell.media.VideoMediaDataSource
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.export.RedditDataExportScheduler
import io.github.brealorg.subshell.export.RedditDataExportStatus
import io.github.brealorg.subshell.export.exportSavedText
import io.github.brealorg.subshell.ui.noticeInfo
import io.github.brealorg.subshell.media.VideoMediaCacheLimitStore
import io.github.brealorg.subshell.media.VideoMediaCacheLimit
import io.github.brealorg.subshell.data.account.RedditFavoriteSyncSettingStore
import io.github.brealorg.subshell.shortcuts.LauncherShortcutsEnabledStore
import io.github.brealorg.subshell.shortcuts.rememberLauncherShortcutsEnabled
import io.github.brealorg.subshell.ui.app.predictiveBackPage
import io.github.brealorg.subshell.ui.app.rememberPredictiveBackPage
import io.github.brealorg.subshell.ui.app.rememberRedditFavoriteSyncEnabled
import io.github.brealorg.subshell.media.videoMediaCacheSummary
import io.github.brealorg.subshell.ui.ChromeBehavior
import io.github.brealorg.subshell.ui.CodeFont
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.ui.CommentActionPreference
import io.github.brealorg.subshell.ui.CommentCollapseMode
import io.github.brealorg.subshell.ui.CommentHierarchyStyle
import io.github.brealorg.subshell.ui.CommentIndentSize
import io.github.brealorg.subshell.ui.CommentThreadColours
import io.github.brealorg.subshell.ui.CommentSwipeAction
import io.github.brealorg.subshell.ui.PostSwipeAction
import io.github.brealorg.subshell.ui.CommentTimeFormat
import io.github.brealorg.subshell.ui.CommunityDiscoveryLayout
import io.github.brealorg.subshell.ui.selectableDrawerSidePreferences
import io.github.brealorg.subshell.ui.followLabel
import io.github.brealorg.subshell.ui.FeedPreviewState
import io.github.brealorg.subshell.ui.InteractionSide
import io.github.brealorg.subshell.ui.FullscreenCaptionMode
import io.github.brealorg.subshell.ui.MediaCaptionMode
import io.github.brealorg.subshell.ui.NavigationLabelMode
import io.github.brealorg.subshell.ui.SensitiveMediaPreviewMode
import io.github.brealorg.subshell.ui.DrawerCommunitiesMode
import io.github.brealorg.subshell.ui.SubredditLabelStyle
import io.github.brealorg.subshell.ui.AccountConnectAction
import io.github.brealorg.subshell.ui.accountConnectPresentation
import io.github.brealorg.subshell.ui.accountSwitcherSessions
import io.github.brealorg.subshell.ui.accountSwitcherSessionLabel
import io.github.brealorg.subshell.ui.TabletLayoutMode
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.RedditLinkHandlingStatus
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.VideoAutoplay
import io.github.brealorg.subshell.ui.VideoQuality
import io.github.brealorg.subshell.ui.VideoTimelineSize
import io.github.brealorg.subshell.ui.PlaybackOptionsPlacement
import io.github.brealorg.subshell.ui.VideoAudioDefault
import io.github.brealorg.subshell.ui.VideoSubtitleDefault
import io.github.brealorg.subshell.ui.VoteColorPalette
import io.github.brealorg.subshell.ui.VoteControlMode
import io.github.brealorg.subshell.ui.contentFilterSummary
import io.github.brealorg.subshell.ui.legacyVideoDownloadSummary
import io.github.brealorg.subshell.ui.fontFamily
import io.github.brealorg.subshell.ui.formatVideoDownloadBytes
import io.github.brealorg.subshell.ui.ThemeChoice
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.text.input.clearText
import kotlinx.coroutines.withContext
import io.github.brealorg.subshell.applock.AppLockDelayDialog
import io.github.brealorg.subshell.applock.AppLockEntryPoints
import io.github.brealorg.subshell.applock.LocalAppLock
import io.github.brealorg.subshell.applock.appLockDelayLabel
import io.github.brealorg.subshell.ui.components.MutedCommunityDirectViewSetting
import io.github.brealorg.subshell.ui.components.AccountSettingsRuntime
import io.github.brealorg.subshell.ui.components.OnlineVisibilitySettingRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.brealorg.subshell.ui.components.SettingsListItem
import io.github.brealorg.subshell.ui.components.SettingsNavigationItem
import io.github.brealorg.subshell.ui.components.SettingsSegmentedChoice
import io.github.brealorg.subshell.ui.components.SettingsSwitchItem
import io.github.brealorg.subshell.ui.components.SettingsValueItem
import io.github.brealorg.subshell.ui.components.settingsRowBleed
import io.github.brealorg.subshell.ui.components.SubshellSearchField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    state: SubshellState,
    backupRepository: SettingsBackupRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accountSettingsRuntime: AccountSettingsRuntime = AccountSettingsRuntime(),
    initialDestinationName: String = SettingsDestination.ROOT.name,
    onInitialDestinationConsumed: () -> Unit = {},
    // Tests answer the permission question here; the app uses the activity
    // result registry (RECREATE03_SETTINGS_PERMISSION_ANSWER_SURVIVES_RECREATION_V1).
    onRequestInboxNotificationPermission: (((Boolean) -> Unit) -> Unit)? = null,
    onRedditConfigurationChanged: () -> Unit = {},
    redditLinkHandlingState: RedditLinkHandlingState = RedditLinkHandlingState(),
    onOpenRedditLinkSettings: () -> Unit = {},
    // M3224_SETTINGS_LIST_DETAIL_V1 (#224, owner): on a wide window Settings is
    // two panes of this screen: the areas (LIST) and the chosen area (DETAIL).
    paneRole: SettingsPaneRole = SettingsPaneRole.SINGLE,
    selectedDestinationName: String? = null,
    onOpenDestination: ((String) -> Unit)? = null,
) {
    var destinationName by rememberSaveable {
        mutableStateOf(SettingsDestination.fromName(initialDestinationName).name)
    }
    var returnDestinationName by rememberSaveable { mutableStateOf(SettingsDestination.ROOT.name) }
    var choiceDialogKey by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedSwipeActionsTargetName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val expandedSwipeActionsTarget = remember(expandedSwipeActionsTargetName) {
        expandedSwipeActionsTargetName?.let { stored ->
            SwipeActionSettingsTarget.entries.firstOrNull { it.name == stored }
        }
    }
    var navigationPreviewContextName by rememberSaveable {
        mutableStateOf(NavigationPreviewContext.NAVIGATION.name)
    }
    val navigationPreviewBaseContext = remember(navigationPreviewContextName) {
        NavigationPreviewContext.entries
            .firstOrNull { it.name == navigationPreviewContextName }
            ?.takeUnless { context ->
                context == NavigationPreviewContext.POST_SWIPE ||
                    context == NavigationPreviewContext.COMMENT_SWIPE
            }
            ?: NavigationPreviewContext.NAVIGATION
    }
    // ISSUE16_INLINE_EXPANSION_PREVIEW_CONTEXT_OWNERSHIP_V1
    val navigationPreviewContext = when (expandedSwipeActionsTarget) {
        SwipeActionSettingsTarget.POSTS -> NavigationPreviewContext.POST_SWIPE
        SwipeActionSettingsTarget.COMMENTS -> NavigationPreviewContext.COMMENT_SWIPE
        null -> navigationPreviewBaseContext
    }
    val destination = remember(destinationName) { SettingsDestination.fromName(destinationName) }
    val returnDestination = remember(returnDestinationName) {
        SettingsDestination.fromName(returnDestinationName)
    }
    val backupScreenVisible = destination == SettingsDestination.BACKUP
    var cacheClearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var mediaCacheStatus by remember { mutableStateOf<VideoMediaCacheStatus?>(null) }
    var mediaCacheReadFailed by remember { mutableStateOf(false) }
    var cacheClearInProgress by remember { mutableStateOf(false) }
    var cacheOperationMessage by remember { mutableStateOf<UiText?>(null) }
    // TRANSLATE14: the device-unlock prompts are read here, where text can be resolved.
    val showHiddenFreelyTitle = stringResource(R.string.set_unlock_show_hidden_title)
    val showHiddenFreelySubtitle = stringResource(R.string.set_unlock_show_hidden_subtitle)
    val turnOffAppLockTitle = stringResource(R.string.set_unlock_turn_off_app_lock_title)
    val turnOffAppLockSubtitle = stringResource(R.string.set_unlock_turn_off_app_lock_subtitle)
    var downloadDeleteConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var downloadDeleteInProgress by remember { mutableStateOf(false) }
    var downloadOperationMessage by remember { mutableStateOf<UiText?>(null) }
    var redReaderConfigurationDialogVisible by remember { mutableStateOf(false) }
    // COMPOSE113_TEXT_FIELDS_USE_TEXT_FIELD_STATE_V1 (1.0.5 tool update): the client ID
    // field is M3's OutlinedSecureTextField over a TextFieldState (Compose 1.13 deprecated
    // the String field and PasswordVisualTransformation).
    val redReaderClientIdState = androidx.compose.foundation.text.input.rememberTextFieldState()
    val redReaderClientIdDraft = redReaderClientIdState.text.toString()
    var redReaderCompatibilityConfigured by remember { mutableStateOf(false) }
    var redReaderConfigurationBusy by remember { mutableStateOf(false) }
    var redReaderOperationMessage by remember { mutableStateOf<UiText?>(null) }
    // TRANSLATE14: whether the message above is a failure (was read from its first word).
    var redReaderOperationFailed by remember { mutableStateOf(false) }
    // ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1
    var redditApiValues by remember { mutableStateOf<RedditApiValues?>(null) }
    var redditApiValuesDraft by remember { mutableStateOf<RedditApiValuesDraft?>(null) }
    var redReaderClientIdChanges by remember { mutableStateOf(false) }
    var accountLogoutConfirmationVisible by remember { mutableStateOf(false) }
    var accountLogoutInProgress by remember { mutableStateOf(false) }
    var accountLogoutFailed by remember { mutableStateOf(false) }
    val cacheScope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    // LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1 (OCB-108): the download
    // system is retired; Settings only offers to delete what an older build
    // downloaded, read on IO when Settings opens and after a delete.
    val legacyDownloads = remember(appContext) { LegacyVideoDownloads(appContext) }
    var legacyDownloadStatus by remember { mutableStateOf<LegacyVideoDownloadStatus?>(null) }
    LaunchedEffect(legacyDownloads) {
        legacyDownloadStatus = withContext(Dispatchers.IO) { runCatching { legacyDownloads.status() }.getOrNull() }
    }
    // VIDEOCACHE01_CONFIGURABLE_LIMIT_V1: device-local; applies on next start.
    // LOWUI2_SETTINGS_READS_OFF_MAIN_V1 (OCB-272): the cache-limit file is read
    // on IO; the row shows its value once read.
    var videoCacheLimit by remember { mutableStateOf<VideoMediaCacheLimit?>(null) }
    LaunchedEffect(appContext) {
        videoCacheLimit = withContext(Dispatchers.IO) { VideoMediaCacheLimitStore.read(appContext) }
    }
    val launcherShortcutsEnabled by rememberLauncherShortcutsEnabled()
    val redditFavoriteSyncEnabled by rememberRedditFavoriteSyncEnabled()
    val videoCacheLimitPending = videoCacheLimit?.let { limit ->
        VideoMediaDataSource.activeLimit()?.let { active -> active != limit }
    } == true
    val settingsNoticeHost = LocalViewportErrorHostState.current
    val productAccounts = accountSwitcherSessions(state.accountSessions)
    val activeProductAccount = productAccounts.firstOrNull { account ->
        account.stableId == state.activeAccountId
    }
    val accountConnectPresentation = accountConnectPresentation(
        connectAccountState = accountSettingsRuntime.connectAccountState,
        hasAuthenticatedAccounts = productAccounts.isNotEmpty(),
    )

    LaunchedEffect(initialDestinationName) {
        val requestedDestination = SettingsDestination.fromName(initialDestinationName)
        if (requestedDestination != SettingsDestination.ROOT) {
            state.settingsQuery = ""
            returnDestinationName = SettingsDestination.ROOT.name
            destinationName = requestedDestination.name
            onInitialDestinationConsumed()
        }
    }

    // LOWUI1_FAILED_NOTIFICATION_BASELINE_IS_NOT_A_CRASH_V1 (OCB-266): the
    // background baseline is reset before the setting turns on; a failed write
    // keeps notifications off and says so instead of crashing the tap.
    fun enableInboxNotifications(account: String) {
        val baselineReset = try {
            InboxBackgroundEventStore(appContext).reset(account)
            true
        } catch (_: IllegalStateException) {
            false
        }
        settingsNoticeHost?.clear(INBOX_NOTIFICATIONS_NOTICE_SCOPE)
        if (!baselineReset) {
            settingsNoticeHost?.present(
                scopeKey = INBOX_NOTIFICATIONS_NOTICE_SCOPE,
                presentation = inboxNotificationsNotTurnedOnNotice(),
                failureDetails = null,
                onRetry = null,
            )
            return
        }
        state.updateInboxNotificationsEnabled(true)
        InboxNotificationCenter.ensureChannel(appContext)
        InboxBackgroundSyncScheduler.schedule(appContext, account)
    }

    fun answerInboxNotificationPermission(account: String, granted: Boolean) {
        if (
            granted &&
            state.activeAccountIsAuthenticated &&
            state.activeAccountId == account
        ) {
            enableInboxNotifications(account)
        } else {
            state.updateInboxNotificationsEnabled(false)
        }
    }

    // RECREATE03_SETTINGS_PERMISSION_ANSWER_SURVIVES_RECREATION_V1 (#241): the
    // switch asks through the activity result registry, which hands an answer
    // given after a dark-mode switch to the rebuilt screen; the account it was
    // asked for is saved state. The activity's own callback died with it.
    var inboxNotificationPermissionAccount by rememberSaveable { mutableStateOf<String?>(null) }
    val inboxNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val account = inboxNotificationPermissionAccount ?: return@rememberLauncherForActivityResult
        inboxNotificationPermissionAccount = null
        answerInboxNotificationPermission(account, granted)
    }

    fun setInboxNotificationsEnabled(enabled: Boolean) {
        if (!enabled) {
            state.updateInboxNotificationsEnabled(false)
            // COMMIT01_NOTIFICATION_CLEANUP_OFF_MAIN_V1 (#243): the shown
            // notifications and their records are cleared on IO.
            cacheScope.launch(Dispatchers.IO) {
                runCatching { InboxNotificationCenter.cancelPosted(appContext) }
            }
            return
        }

        val account = state.activeAccountId.trim()
        if (!state.activeAccountIsAuthenticated || account.isEmpty()) return

        InboxNotificationCenter.ensureChannel(appContext)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val testAnswer = onRequestInboxNotificationPermission
            if (testAnswer != null) {
                testAnswer { granted -> answerInboxNotificationPermission(account, granted) }
            } else {
                inboxNotificationPermissionAccount = account
                inboxNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            enableInboxNotifications(account)
        }
    }
    val redditCompatibilityConfiguration = remember(appContext) {
        RedditCompatibilityConfigurationController(appContext)
    }
    fun openSettingsDestination(
        target: SettingsDestination,
        returnTo: SettingsDestination = destination,
    ) {
        if (paneRole == SettingsPaneRole.LIST && onOpenDestination != null) {
            onOpenDestination(target.name)
            return
        }
        state.settingsQuery = ""
        returnDestinationName = returnTo.name
        destinationName = target.name
    }

    LaunchedEffect(paneRole, selectedDestinationName) {
        if (paneRole == SettingsPaneRole.DETAIL && selectedDestinationName != null) {
            returnDestinationName = SettingsDestination.ROOT.name
            destinationName = SettingsDestination.fromName(selectedDestinationName).name
        }
    }

    val returnToSettings = {
        val target = settingsDestinationBackTarget(
            destination = destination,
            returnDestination = returnDestination,
        )
        // Beside the list there is no root page to return to: Back leaves Settings.
        if (paneRole == SettingsPaneRole.DETAIL && target == SettingsDestination.ROOT) {
            onBack()
        } else {
            destinationName = target.name
        }
    }
    // ISSUE249_IN_SCREEN_PAGES_FOLLOW_THE_BACK_GESTURE_V1 (#249): an area of
    // Settings follows the back gesture as a route does. This one handler owns
    // Back for every area; the area screens no longer register their own.
    val pageBack = rememberPredictiveBackPage(
        enabled = backupScreenVisible ||
            (destination != SettingsDestination.ROOT && destination != SettingsDestination.BACKUP),
        onBack = returnToSettings,
    )
    val pageModifier = modifier.predictiveBackPage(pageBack)

    LaunchedEffect(appContext) {
        val status = runCatching {
            withContext(Dispatchers.IO) { VideoMediaDataSource.cacheStatus(appContext) }
        }
        mediaCacheStatus = status.getOrNull()
        mediaCacheReadFailed = status.isFailure
        redReaderCompatibilityConfigured = runCatching {
            withContext(Dispatchers.IO) {
                redditCompatibilityConfiguration.isConfigured()
            }
        }.getOrDefault(false)
    }

    if (backupScreenVisible) {
        BackupRestoreScreen(
            state = state,
            repository = backupRepository,
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.NOTIFICATION_TYPES) {
        // NOTIFY01A1R2_NOTIFICATION_TYPES_CHILD_ROUTE_V1
        NotificationTypesSettingsScreen(
            state = state,
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.NEW_POST_NOTIFICATIONS) {
        NewPostNotificationsSettingsScreen(
            state = state,
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.CONTENT_FILTERS) {
        ContentFiltersScreen(
            filters = state.contentFilters,
            onAdd = state::addContentFilter,
            onRemove = state::removeContentFilter,
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    // ISSUE40_THE_LAB_SCREEN_IS_REACHABLE_ONLY_IN_DEBUG_V1: the row that opens it is debug-only, and this
    // branch is too, so R8 can drop the screen and its data from the release app (0.91 still carried them).
    if (BuildConfig.DEBUG && destination == SettingsDestination.SENSITIVE_MEDIA_LAB) {
        SensitiveMediaLabScreen(
            state = state,
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.DIAGNOSTICS) {
        DiagnosticsScreen(
            onBack = returnToSettings,
            // REDDITCHECK01_CHECK_REDDIT_ACCESS_V1: the account active when the check starts.
            checkRedditAccess = accountSettingsRuntime.checkRedditAccess?.let { check ->
                suspend { check(state.activeAccountId) }
            },
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.UPDATES) {
        io.github.brealorg.subshell.update.UpdatesScreen(
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    if (destination == SettingsDestination.ABOUT) {
        HelpScreen(
            onBack = returnToSettings,
            modifier = pageModifier,
        )
        return
    }

    // The search lives in the list pane; the chosen area is shown whole.
    val query = if (paneRole == SettingsPaneRole.DETAIL) "" else state.settingsQuery.trim()
    // ISSUE38_APP_LOCK_V1
    val appLock = LocalAppLock.current
    // ISSUE268: the "Your reading" switch reads and writes its own store.
    val readingStatsStore = io.github.brealorg.subshell.data.reading.ReadingStats.store(LocalContext.current)
    val readingStatsEnabled by readingStatsStore.enabled.collectAsStateWithLifecycle()
    LaunchedEffect(readingStatsStore) { readingStatsStore.onDisk { load() } }
    // REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1
    val exportContext = LocalContext.current
    val redditDataExportStatus by remember(exportContext, state.activeAccountId) {
        RedditDataExportScheduler.status(exportContext, state.activeAccountId)
    }.collectAsStateWithLifecycle(initialValue = null)
    // LOWUX3_SETTINGS_SEARCH_SKIPS_ROWS_THE_STATE_HIDES_V1 (OCB-267/278): rows the
    // state hides right now are not search hits.
    val searchHiddenKeys = buildSet {
        if (appLock == null) add("app-lock")
        if (appLock == null || !appLock.settings.enabled) {
            add("app-lock-delay")
            add("app-lock-notifications")
        }
        if (activeProductAccount == null) add("online-visibility")
        if (activeProductAccount == null || !state.activeAccountIsAuthenticated) add("export-reddit-data")
        if (legacyDownloadStatus?.hasDownloads != true && downloadOperationMessage == null) add("download-storage")
    }
    val searchResult = remember(query, searchHiddenKeys) { settingsSearchResult(query, searchHiddenKeys) }
    val searchHasVisibleMatches = searchResult.hasMatches &&
        (BuildConfig.DEBUG || searchResult.itemKeys.any { it != "feed-state" })

    when (choiceDialogKey) {
        "app-lock-delay" -> if (appLock != null) {
            AppLockDelayDialog(
                selectedMinutes = appLock.settings.delayMinutes,
                onSelect = { minutes ->
                    appLock.onChange(appLock.settings.copy(delayMinutes = minutes))
                    choiceDialogKey = null
                },
                onDismiss = { choiceDialogKey = null },
            )
        }
        "appearance-theme" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_theme),
            options = ThemeChoice.entries,
            selected = state.themeChoice,
            label = { it.label },
            onSelect = { choice ->
                state.updateThemeChoice(choice)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-ui-size" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_ui_size),
            options = AppUiScale.entries,
            selected = state.appUiScale,
            label = { stringResource(it.labelRes) },
            onSelect = { scale ->
                state.updateAppUiScale(scale)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-text-size" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_text_size),
            options = AppTextScale.entries,
            selected = state.appTextScale,
            label = { it.label },
            onSelect = { scale ->
                state.updateAppTextScale(scale)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "video-cache-limit" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_video_cache_size),
            options = VideoMediaCacheLimit.entries,
            selected = videoCacheLimit ?: VideoMediaCacheLimit.DEFAULT,
            label = { stringResource(it.labelRes) },
            onSelect = { limit ->
                VideoMediaCacheLimitStore.write(appContext, limit)
                videoCacheLimit = limit
                choiceDialogKey = null
                // The row's "applies next start" line is easy to miss; say it
                // in the tray like the app's other notices.
                settingsNoticeHost?.let { host ->
                    host.clear(VIDEO_CACHE_LIMIT_NOTICE_SCOPE)
                    val active = VideoMediaDataSource.activeLimit()
                    if (active != null && active != limit) {
                        host.present(
                            scopeKey = VIDEO_CACHE_LIMIT_NOTICE_SCOPE,
                            presentation = videoCacheLimitRestartNotice(limit),
                            failureDetails = null,
                            onRetry = null,
                            tone = TrayTone.INFO, // ISSUE222: information, not an error
                        )
                    }
                }
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-vote-controls" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_vote_controls),
            options = VoteControlMode.entries,
            selected = state.voteControlMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateVoteControlMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-vote-colours" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_vote_colours),
            options = VoteColorPalette.entries,
            selected = state.voteColorPalette,
            label = { stringResource(it.labelRes) },
            onSelect = { palette ->
                state.updateVoteColorPalette(palette)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-code-font" -> SettingsCodeFontDialog(
            selected = state.codeFont,
            onSelect = { font ->
                state.updateCodeFont(font)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-sensitive-previews" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_sensitive_media_previews),
            options = SensitiveMediaPreviewMode.entries,
            selected = state.sensitiveMediaPreviewMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateSensitiveMediaPreviewMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-autoplay" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_autoplay_videos_and_gifs),
            options = VideoAutoplay.entries,
            selected = state.videoAutoplay,
            label = { stringResource(it.labelRes) },
            onSelect = { autoplay ->
                state.updateVideoAutoplay(autoplay)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-quality-wifi" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_video_quality_on_wi_fi),
            options = VideoQuality.entries,
            selected = state.wifiVideoQuality,
            label = { stringResource(it.labelRes) },
            onSelect = { quality ->
                state.updateWifiVideoQuality(quality)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-quality-mobile" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_video_quality_on_mobile_data),
            options = VideoQuality.entries,
            selected = state.mobileVideoQuality,
            label = { stringResource(it.labelRes) },
            onSelect = { quality ->
                state.updateMobileVideoQuality(quality)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-subtitle-default" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_subtitles),
            options = VideoSubtitleDefault.entries,
            selected = state.videoSubtitleDefault,
            label = { stringResource(it.labelRes) },
            onSelect = { value ->
                state.updateVideoSubtitleDefault(value)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-audio-default" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_audio_language),
            options = VideoAudioDefault.entries,
            selected = state.videoAudioDefault,
            label = { stringResource(it.labelRes) },
            onSelect = { value ->
                state.updateVideoAudioDefault(value)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-playback-options" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_playback_options_button),
            options = PlaybackOptionsPlacement.entries,
            selected = state.playbackOptionsPlacement,
            label = { stringResource(it.labelRes) },
            onSelect = { placement ->
                state.updatePlaybackOptionsPlacement(placement)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-timeline-size" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_video_timeline_size),
            options = VideoTimelineSize.entries,
            selected = state.videoTimelineSize,
            label = { stringResource(it.labelRes) },
            onSelect = { size ->
                state.updateVideoTimelineSize(size)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // M3225_LONG_SINGLE_CHOICE_IS_A_VALUE_ROW_V1 (#225, owner): long or many
        // options are a value row with this radio dialog, not chips.
        "random-nsfw-community" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_random_nsfw_community),
            options = io.github.brealorg.subshell.ui.RandomNsfwCommunityPlacement.entries,
            selected = state.randomNsfwCommunityPlacement,
            label = { stringResource(it.labelRes) },
            onSelect = { placement ->
                state.updateRandomNsfwCommunityPlacement(placement)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // ISSUE234_FEED_POST_LAYOUTS_V1
        "post-layout" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_post_layout),
            options = io.github.brealorg.subshell.ui.PostLayout.entries,
            selected = state.postLayout,
            label = { stringResource(it.labelRes) },
            onSelect = { layout ->
                state.updatePostLayout(layout)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "list-thumbnail-side" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_thumbnail),
            options = io.github.brealorg.subshell.ui.ListThumbnailSide.entries,
            selected = state.listThumbnailSide,
            label = { stringResource(it.labelRes) },
            onSelect = { side ->
                state.updateListThumbnailSide(side)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // ISSUE302_START_FEED_V1 / ISSUE300_DEFAULT_FEED_SORT_V1
        "start-feed" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_start_feed),
            options = io.github.brealorg.subshell.ui.StartFeed.entries,
            selected = state.startFeed,
            label = { stringResource(it.labelRes) },
            onSelect = { feed ->
                state.updateStartFeed(feed)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "default-feed-sort" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_feed_sort),
            options = io.github.brealorg.subshell.ui.FeedSort.entries,
            selected = state.defaultFeedSort,
            label = { stringResource(it.labelRes) },
            onSelect = { sort ->
                state.updateDefaultFeedSort(sort)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "default-community-sort" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_community_sort),
            options = io.github.brealorg.subshell.ui.CommunitySort.entries,
            selected = state.defaultCommunitySort,
            label = { stringResource(it.labelRes) },
            onSelect = { sort ->
                state.updateDefaultCommunitySort(sort)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1
        "resume-feed-position-age" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_keep_my_place_for),
            options = io.github.brealorg.subshell.ui.FeedPositionAge.entries,
            selected = state.resumeFeedPositionAge,
            label = { stringResource(it.labelRes) },
            onSelect = { age ->
                state.updateResumeFeedPositionAge(age)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "resume-feed-position-start" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_when_the_app_starts),
            options = io.github.brealorg.subshell.ui.FeedPositionStart.entries,
            selected = state.resumeFeedPositionStart,
            label = { stringResource(it.labelRes) },
            onSelect = { start ->
                state.updateResumeFeedPositionStart(start)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // ISSUE230_PINNED_POSTS_FIRST_V1
        "pinned-posts" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_pinned_posts),
            options = io.github.brealorg.subshell.ui.PinnedPostsPresentation.entries,
            selected = state.pinnedPostsPresentation,
            label = { stringResource(it.labelRes) },
            onSelect = { presentation ->
                state.updatePinnedPostsPresentation(presentation)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "feed-state" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_feed_preview),
            options = FeedPreviewState.entries,
            selected = state.feedPreviewState,
            label = { stringResource(it.labelRes) },
            onSelect = { preview ->
                state.updateFeedPreviewState(preview)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-captions" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_gallery_captions),
            options = MediaCaptionMode.entries,
            selected = state.mediaCaptionMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateMediaCaptionMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "fullscreen-captions" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_fullscreen_gallery_captions),
            options = FullscreenCaptionMode.entries,
            selected = state.fullscreenCaptionMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateFullscreenCaptionMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-hierarchy-style" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_comment_hierarchy),
            options = CommentHierarchyStyle.entries,
            selected = state.commentHierarchyStyle,
            label = { stringResource(it.labelRes) },
            onSelect = { style ->
                state.updateCommentHierarchyStyle(style)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-thread-colours" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_thread_colours),
            options = CommentThreadColours.entries,
            selected = state.commentThreadColours,
            label = { stringResource(it.labelRes) },
            onSelect = { colours ->
                state.updateCommentThreadColours(colours)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-indent-size" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_reply_indent),
            options = CommentIndentSize.entries,
            selected = state.commentIndentSize,
            label = { stringResource(it.labelRes) },
            onSelect = { size ->
                state.updateCommentIndentSize(size)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "shown-languages" -> ShownLanguagesDialog(
            selected = state.shownLanguages,
            onDone = { languages ->
                state.updateShownLanguages(languages)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-actions" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_action_placement),
            options = CommentActionPreference.entries,
            selected = state.commentActionPreference,
            label = { preference ->
                if (preference == CommentActionPreference.FOLLOW) {
                    state.interactionSide.followLabel().asString()
                } else {
                    stringResource(preference.labelRes)
                }
            },
            onSelect = { preference ->
                state.updateCommentActionPreference(preference)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-time-format" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_comment_time),
            options = CommentTimeFormat.entries,
            selected = state.commentTimeFormat,
            label = { format -> "${stringResource(format.labelRes)} · ${format.example}" },
            onSelect = { format ->
                state.updateCommentTimeFormat(format)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "top-bar-behavior" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_top_bar),
            options = ChromeBehavior.entries,
            selected = state.topBarBehavior,
            label = { stringResource(it.labelRes) },
            onSelect = { behavior ->
                state.updateTopBarBehavior(behavior)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "bottom-navigation-behavior" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_bottom_navigation),
            options = ChromeBehavior.entries,
            selected = state.bottomBarBehavior,
            label = { stringResource(it.labelRes) },
            onSelect = { behavior ->
                state.updateBottomBarBehavior(behavior)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "drawer-communities" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_communities_in_drawer),
            options = DrawerCommunitiesMode.entries,
            selected = state.drawerCommunitiesMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateDrawerCommunitiesMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "navigation-labels" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_navigation_labels),
            options = NavigationLabelMode.entries,
            selected = state.navigationLabelMode,
            label = { stringResource(it.labelRes) },
            onSelect = { mode ->
                state.updateNavigationLabelMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "subreddit-labels" -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.set_communities_labels),
            options = SubredditLabelStyle.entries,
            selected = state.subredditLabelStyle,
            label = { it.example },
            onSelect = { style ->
                state.updateSubredditLabelStyle(style)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
    }

    if (accountLogoutConfirmationVisible && activeProductAccount != null) {
        AlertDialog(
            onDismissRequest = {
                if (!accountLogoutInProgress) {
                    accountLogoutConfirmationVisible = false
                    accountLogoutFailed = false
                }
            },
            title = { Text(stringResource(R.string.set_log_out_of_u, activeProductAccount.username)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.all2_this_removes_this_reddit_account_from_subshell),
                    )
                    // LOWUX1_FAILED_LOGOUT_IS_SAID_V1 (OCB-268): the dialog only
                    // re-enabled after a failed logout.
                    if (accountLogoutFailed) {
                        Text(
                            stringResource(R.string.set_could_not_log_out, activeProductAccount.username),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !accountLogoutInProgress,
                    onClick = {
                        accountLogoutInProgress = true
                        accountLogoutFailed = false
                        accountSettingsRuntime.onLogoutActiveAccount { cleared ->
                            accountLogoutInProgress = false
                            accountLogoutFailed = !cleared
                            if (cleared) accountLogoutConfirmationVisible = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.set_log_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !accountLogoutInProgress,
                    onClick = {
                        accountLogoutConfirmationVisible = false
                        accountLogoutFailed = false
                    },
                ) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    if (redReaderConfigurationDialogVisible) {
        LaunchedEffect(Unit) {
            val values = runCatching {
                withContext(Dispatchers.IO) { redditCompatibilityConfiguration.apiValues() }
            }.getOrNull()
            redditApiValues = values
            redditApiValuesDraft = values?.let(::RedditApiValuesDraft)
        }
        // ISSUE240: a new client ID ends the sessions made with the old one.
        LaunchedEffect(redReaderClientIdDraft) {
            redReaderClientIdChanges =
                redditCompatibilityConfiguration.isValidClientId(redReaderClientIdDraft) &&
                runCatching {
                    withContext(Dispatchers.IO) {
                        redditCompatibilityConfiguration.changesClientId(redReaderClientIdDraft)
                    }
                }.getOrDefault(false)
        }
        AlertDialog(
            onDismissRequest = {
                if (!redReaderConfigurationBusy) {
                    redReaderConfigurationDialogVisible = false
                    redReaderClientIdState.clearText()
                }
            },
            title = { Text(stringResource(R.string.set_redreader_compatibility)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.set_compatibility_mode_explained),
                    )
                    // Typing clears the last operation message, as the old onValueChange did;
                    // a programmatic clear (after save) leaves it.
                    LaunchedEffect(redReaderClientIdState) {
                        androidx.compose.runtime.snapshotFlow { redReaderClientIdState.text.toString() }
                            .drop(1)
                            .collect { text -> if (text.isNotEmpty()) redReaderOperationMessage = null }
                    }
                    androidx.compose.material3.OutlinedSecureTextField(
                        state = redReaderClientIdState,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !redReaderConfigurationBusy,
                        label = { Text(stringResource(R.string.set_oauth_client_id)) },
                        supportingText = if (redReaderCompatibilityConfigured) {
                            { Text(stringResource(R.string.set_leave_empty_to_keep_the_current_one)) }
                        } else {
                            null
                        },
                        // LOWUI3_CLIENT_ID_KEYBOARD_LEARNS_NOTHING_V1 (OCB-269): a password
                        // field, so the keyboard neither suggests nor learns the client ID.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    if (redReaderClientIdChanges) {
                        Text(
                            REDDIT_CLIENT_ID_CHANGE_WARNING,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // LOWUX1_CLIENT_CONFIG_FAILURE_SHOWN_IN_THE_DIALOG_V1 (OCB-270): a
                    // failed save/remove was written only to the row behind the dialog.
                    redReaderOperationMessage?.takeIf { redReaderOperationFailed }?.let { message ->
                        Text(
                            message.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val apiValues = redditApiValues
                    val apiValuesDraft = redditApiValuesDraft
                    if (apiValues != null && apiValuesDraft != null) {
                        RedditApiValuesSection(
                            stored = apiValues,
                            draft = apiValuesDraft,
                            enabled = !redReaderConfigurationBusy,
                            onResetToDefaults = {
                                redReaderConfigurationBusy = true
                                // LOWUI6_CLIENT_CONFIG_CHANGE_FINISHES_V1 (OCB-324)
                                cacheScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    withContext(NonCancellable) {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                redditCompatibilityConfiguration.resetApiValues()
                                            }
                                        }
                                        redReaderConfigurationBusy = false
                                        result.onSuccess {
                                            redReaderConfigurationDialogVisible = false
                                            redReaderClientIdState.clearText()
                                            redReaderOperationFailed = false
                                            redReaderOperationMessage =
                                                UiText.Resource(R.string.set_connection_configuration_saved)
                                            onRedditConfigurationChanged()
                                        }.onFailure {
                                            redReaderOperationFailed = true
                                            redReaderOperationMessage = UiText.Resource(R.string.set_could_not_save_compatibility)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    Text(
                        stringResource(R.string.all2_changes_are_applied_automatically_if_redreader_is),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (redReaderCompatibilityConfigured) {
                        TextButton(
                            enabled = !redReaderConfigurationBusy,
                            onClick = {
                                redReaderConfigurationBusy = true
                                // LOWUI6_CLIENT_CONFIG_CHANGE_FINISHES_V1 (OCB-324): a
                                // recreation must not leave the file changed but the
                                // runtime unreloaded.
                                cacheScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    withContext(NonCancellable) {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                redditCompatibilityConfiguration.remove()
                                            }
                                        }
                                        redReaderConfigurationBusy = false
                                        result.onSuccess {
                                            redReaderCompatibilityConfigured = false
                                            redReaderConfigurationDialogVisible = false
                                            redReaderClientIdState.clearText()
                                            redReaderOperationFailed = false
                                            redReaderOperationMessage =
                                                UiText.Resource(R.string.set_connection_configuration_removed)
                                            onRedditConfigurationChanged()
                                        }.onFailure {
                                            redReaderOperationFailed = true
                                            redReaderOperationMessage = UiText.Resource(R.string.set_could_not_remove_compatibility)
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.set_use_native_configuration))
                        }
                    }
                }
            },
            confirmButton = {
                val apiValues = redditApiValues
                val apiValuesDraft = redditApiValuesDraft
                val saveEnabled = apiValues != null && redditApiSaveEnabled(
                    clientIdDraft = redReaderClientIdDraft,
                    clientIdValid = redditCompatibilityConfiguration.isValidClientId(redReaderClientIdDraft),
                    stored = apiValues,
                    draft = apiValuesDraft,
                )
                TextButton(
                    enabled = !redReaderConfigurationBusy && saveEnabled,
                    onClick = {
                        if (!saveEnabled) {
                            return@TextButton
                        }
                        val editing = apiValuesDraft?.editing == true
                        val redirectUri =
                            if (editing) apiValuesDraft.redirectUri.trim() else apiValues.redirectUri
                        val userAgent =
                            if (editing) apiValuesDraft.userAgent.trim() else apiValues.userAgent
                        redReaderConfigurationBusy = true
                        // LOWUI6_CLIENT_CONFIG_CHANGE_FINISHES_V1 (OCB-324)
                        cacheScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            withContext(NonCancellable) {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        redditCompatibilityConfiguration.save(
                                            clientId = redReaderClientIdDraft,
                                            redirectUri = redirectUri,
                                            userAgent = userAgent,
                                        )
                                    }
                                }
                                redReaderConfigurationBusy = false
                                result.onSuccess {
                                    redReaderCompatibilityConfigured = true
                                    redReaderConfigurationDialogVisible = false
                                    redReaderClientIdState.clearText()
                                    redReaderOperationFailed = false
                                    redReaderOperationMessage =
                                        UiText.Resource(R.string.set_connection_configuration_saved)
                                    onRedditConfigurationChanged()
                                }.onFailure {
                                    redReaderOperationFailed = true
                                            redReaderOperationMessage = UiText.Resource(R.string.set_could_not_save_compatibility)
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        if (redReaderCompatibilityConfigured && redReaderClientIdDraft.isNotBlank()) {
                            stringResource(R.string.all_replace)
                        } else {
                            stringResource(R.string.all_save)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !redReaderConfigurationBusy,
                    onClick = {
                        redReaderConfigurationDialogVisible = false
                        redReaderClientIdState.clearText()
                    },
                ) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    if (cacheClearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { cacheClearConfirmationVisible = false },
            title = { Text(stringResource(R.string.set_clear_media_cache)) },
            text = {
                Text(
                    stringResource(R.string.all2_cached_media_will_be_removed_settings_and),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cacheClearConfirmationVisible = false
                        cacheClearInProgress = true
                        cacheOperationMessage = null
                        cacheScope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    VideoMediaDataSource.clearCache(appContext)
                                }
                            }
                            result.onSuccess { clearResult ->
                                mediaCacheStatus = clearResult.after
                                mediaCacheReadFailed = false
                                cacheOperationMessage =
                                    if (clearResult.after.bytesUsed == 0L) UiText.Resource(R.string.set_cache_cleared)
                                    else UiText.Resource(R.string.set_cache_cleared_cached_again)
                            }.onFailure {
                                mediaCacheReadFailed = true
                                cacheOperationMessage = UiText.Resource(R.string.set_could_not_clear_cache)
                            }
                            cacheClearInProgress = false
                        }
                    },
                    // ISSUE222_DESTRUCTIVE_CONFIRMS_ARE_RED_V1: as Log out and the composers.
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.set_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { cacheClearConfirmationVisible = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    if (downloadDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { downloadDeleteConfirmationVisible = false },
            title = { Text(stringResource(R.string.set_delete_all_downloaded_videos)) },
            text = {
                Text(
                    (legacyDownloadStatus?.let { legacyVideoDownloadSummary(it).asString() }?.let { "$it. " }.orEmpty()) +
                        stringResource(R.string.set_downloads_will_be_removed),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadDeleteConfirmationVisible = false
                        downloadDeleteInProgress = true
                        downloadOperationMessage = null
                        cacheScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { legacyDownloads.deleteAll() }
                            }
                            result.onSuccess { deleted ->
                                downloadOperationMessage = UiText.Plural(
                                    R.plurals.set_deleted_videos_with_size,
                                    deleted.videosRemoved,
                                    deleted.videosRemoved,
                                    formatVideoDownloadBytes(deleted.bytesRemoved),
                                )
                            }.onFailure {
                                downloadOperationMessage = UiText.Resource(R.string.set_could_not_delete_downloads)
                            }
                            legacyDownloadStatus = withContext(Dispatchers.IO) {
                                runCatching { legacyDownloads.status() }.getOrNull()
                            }
                            downloadDeleteInProgress = false
                        }
                    },
                    // ISSUE222_DESTRUCTIVE_CONFIRMS_ARE_RED_V1: as Log out and the composers.
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.set_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadDeleteConfirmationVisible = false }) {
                    Text(stringResource(R.string.set_cancel))
                }
            },
        )
    }

    val settingsListState = rememberLazyListState()

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(pageModifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(stringResource(destination.titleRes)) },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (destination == SettingsDestination.ROOT) {
                            onBack()
                        } else {
                            returnToSettings()
                        }
                    },
                ) {
                    Icon(Symbols.ArrowBack, contentDescription = stringResource(R.string.set_back))
                }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            state = settingsListState,
            contentPadding = PaddingValues(start = ScreenMargin, top = 8.dp, end = ScreenMargin, bottom = 32.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            if (destination == SettingsDestination.ROOT) {
                item("search") {
                    // M3225_ONE_SEARCH_FIELD_STYLE_V1
                    SubshellSearchField(
                        query = state.settingsQuery,
                        onQueryChange = { state.settingsQuery = it },
                        placeholder = stringResource(R.string.set_search_settings),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            if (
                destination == SettingsDestination.ROOT &&
                query.isNotBlank() &&
                !searchHasVisibleMatches
            ) {
                item("search-empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.all_no_settings_found),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.all_try_another_word_or_clear_the_search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { state.settingsQuery = "" }) {
                                Text(stringResource(R.string.set_clear_search))
                            }
                        }
                    }
                }
            }

            if (destination == SettingsDestination.ROOT && query.isBlank()) {
                item("settings-root-label") {
                    Text(
                        stringResource(R.string.all_browse_by_area),
                        modifier = Modifier.padding(top = 5.dp, bottom = 1.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                settingsRootDestinations.forEach { target ->
                    item("settings-destination-${target.name}") {
                        SettingsDestinationRow(
                            icon = settingsDestinationIcon(target),
                            title = stringResource(target.titleRes),
                            description = target.descriptionRes?.let { stringResource(it) } ?: "",
                            selected = paneRole == SettingsPaneRole.LIST &&
                                selectedDestinationName == target.name,
                            onClick = {
                                openSettingsDestination(
                                    target = target,
                                    returnTo = SettingsDestination.ROOT,
                                )
                            },
                        )
                    }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Accounts",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("accounts-title") {
                    SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_accounts)))
                }
                if (searchResult.isItemVisible("accounts")) {
                    productAccounts.forEach { account ->
                        item("account-${account.stableId}") {
                            val selected = account.stableId == state.activeAccountId
                            // USERSTATUS01A1_SETTINGS_ACCOUNT_IDENTITY_AVATAR_V1
                            LaunchedEffect(account.stableId, account.username) {
                                state.ensureUserIdentity(account.username)
                            }
                            val accountAvatarUri =
                                state.userFor(account.username).avatarImageUri
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                            val signInAgain =
                                account.state ==
                                    io.github.brealorg.subshell.data.account.AccountSessionState
                                        .REAUTHENTICATION_REQUIRED
                            AccountNavigationRow(
                                username = account.username,
                                avatarImageUri = accountAvatarUri,
                                description = accountSwitcherSessionLabel(account, selected).asString(),
                                selected = selected,
                                signInAgain = signInAgain,
                                onClick = {
                                    if (!selected) state.switchAccount(account.stableId)
                                    if (signInAgain) {
                                        accountSettingsRuntime.onResetConnectAccount()
                                        accountSettingsRuntime.onReconnectAccount(account.stableId)
                                    }
                                },
                            )
                        }
                    }
                    item("account-add") {
                        NavigationRow(
                            icon = Symbols.Add,
                            title = if (
                                accountConnectPresentation.action == AccountConnectAction.RESET
                            ) {
                                stringResource(R.string.all_add_reddit_account)
                            } else {
                                accountConnectPresentation.headline.asString()
                            },
                            description = if (
                                accountConnectPresentation.action == AccountConnectAction.CONNECT ||
                                accountConnectPresentation.action == AccountConnectAction.RESET
                            ) {
                                stringResource(R.string.all_sign_in_with_another_account)
                            } else {
                                accountConnectPresentation.supporting.asString()
                            },
                            onClick = {
                                when (accountConnectPresentation.action) {
                                    AccountConnectAction.CONNECT ->
                                        accountSettingsRuntime.onConnectAccount()
                                    AccountConnectAction.CANCEL ->
                                        accountSettingsRuntime.onCancelConnectAccount()
                                    AccountConnectAction.RESET -> {
                                        accountSettingsRuntime.onResetConnectAccount()
                                        accountSettingsRuntime.onConnectAccount()
                                    }
                                    AccountConnectAction.NONE -> Unit
                                }
                            },
                        )
                    }
                }
                if (
                    searchResult.isItemVisible("export-reddit-data") &&
                    activeProductAccount != null &&
                    state.activeAccountIsAuthenticated
                ) {
                    item("export-reddit-data") {
                        RedditDataExportRow(
                            status = redditDataExportStatus,
                            onExport = {
                                RedditDataExportScheduler.enqueue(exportContext, activeProductAccount.stableId)
                                settingsNoticeHost.noticeInfo(REDDIT_EXPORT_NOTICE_TITLE, REDDIT_EXPORT_NOTICE_MESSAGE)
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("accounts")) {
                    if (activeProductAccount != null) {
                        item("account-logout") {
                            DestructiveNavigationRow(
                                icon = Symbols.ExitToApp,
                                title = if (accountLogoutInProgress) {
                                    stringResource(R.string.all_logging_out)
                                } else {
                                    stringResource(R.string.all_log_out)
                                },
                                description = stringResource(R.string.set_u, activeProductAccount.username),
                                onClick = {
                                    if (!accountLogoutInProgress) {
                                        accountLogoutConfirmationVisible = true
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (
                activeProductAccount != null &&
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Account privacy",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("account-privacy-title") {
                    SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_account_privacy)))
                }
                if (searchResult.isItemVisible("online-visibility")) {
                    item("online-visibility") {
                        OnlineVisibilitySettingRow(
                            account = activeProductAccount,
                            runtime = accountSettingsRuntime.onlineVisibility,
                            interactionEnabled =
                                !accountSettingsRuntime.connectAccountState.inProgress &&
                                    !accountLogoutInProgress,
                            onReconnect = {
                                accountSettingsRuntime.onlineVisibility.onClose(
                                    activeProductAccount.stableId,
                                )
                                accountSettingsRuntime.onResetConnectAccount()
                                accountSettingsRuntime.onReconnectAccount(
                                    activeProductAccount.stableId,
                                )
                            },
                        )
                    }
                }
            }

            if (destination == SettingsDestination.FEEDS && query.isBlank()) {
                stickyHeader(key = "feeds-live-preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        FeedsSettingsPreview(
                            showUnreadMarker = state.showUnreadMarker,
                            showNsfwPosts = state.showNsfwPosts,
                            mutedCommunityDirectViewMode =
                                state.mutedCommunityDirectViewMode,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Feed",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("feed-title") {
                    SettingsSection(
                        if (destination == SettingsDestination.FEEDS && query.isBlank()) {
                            stringResource(R.string.all_feeds)
                        } else {
                            stringResource(settingsSectionTitle(destination, R.string.all_feed))
                        },
                    )
                }
                // ISSUE302_START_FEED_V1 / ISSUE300_DEFAULT_FEED_SORT_V1 (owner 2026-11-13):
                // the defaults are today's behaviour (Home, Best, Hot).
                if (searchResult.isItemVisible("start-feed")) item("start-feed") {
                    ValueSettingRow(
                        icon = Symbols.Home,
                        title = stringResource(R.string.set_start_feed),
                        description = stringResource(R.string.set_the_feed_the_app_opens_on),
                        value = stringResource(state.startFeed.labelRes),
                        onClick = { choiceDialogKey = "start-feed" },
                    )
                }
                if (searchResult.isItemVisible("default-feed-sort")) item("default-feed-sort") {
                    ValueSettingRow(
                        icon = Symbols.Sort,
                        title = stringResource(R.string.set_feed_sort),
                        description = stringResource(R.string.set_how_home_popular_and_all_are_sorted),
                        value = stringResource(state.defaultFeedSort.labelRes),
                        onClick = { choiceDialogKey = "default-feed-sort" },
                    )
                }
                if (searchResult.isItemVisible("default-community-sort")) item("default-community-sort") {
                    ValueSettingRow(
                        icon = Symbols.Sort,
                        title = stringResource(R.string.set_community_sort),
                        description = stringResource(R.string.set_how_a_community_is_sorted_until_you),
                        value = stringResource(state.defaultCommunitySort.labelRes),
                        onClick = { choiceDialogKey = "default-community-sort" },
                    )
                }
                // ISSUE234_FEED_POST_LAYOUTS_V1
                if (searchResult.isItemVisible("post-layout")) item("post-layout") {
                    ValueSettingRow(
                        icon = Symbols.ViewAgenda,
                        title = stringResource(R.string.set_post_layout),
                        description = stringResource(R.string.set_large_cards_compact_cards_a_list_or),
                        value = stringResource(state.postLayout.labelRes),
                        onClick = { choiceDialogKey = "post-layout" },
                    )
                }
                if (
                    state.postLayout == io.github.brealorg.subshell.ui.PostLayout.LIST &&
                    searchResult.isItemVisible("post-layout")
                ) item("list-thumbnail-side") {
                    ValueSettingRow(
                        icon = Symbols.Image,
                        title = stringResource(R.string.set_thumbnail),
                        description = stringResource(R.string.set_which_side_of_a_list_row_shows),
                        value = stringResource(state.listThumbnailSide.labelRes),
                        onClick = { choiceDialogKey = "list-thumbnail-side" },
                    )
                }
                if (searchResult.isItemVisible("unread-marker")) item("unread-marker") {
                    ToggleRow(
                        icon = Symbols.Visibility,
                        title = stringResource(R.string.set_show_unread_marker),
                        description = stringResource(R.string.set_show_a_small_dot_after_a_post),
                        checked = state.showUnreadMarker,
                        onCheckedChange = state::updateShowUnreadMarker,
                    )
                }
                if (searchResult.isItemVisible("show-nsfw-posts")) item("show-nsfw-posts") {
                    ToggleRow(
                        icon = Symbols.Visibility,
                        title = stringResource(R.string.set_show_nsfw_posts),
                        description = stringResource(R.string.set_show_nsfw_posts_reddit_provides_also_available),
                        checked = state.showNsfwPosts,
                        onCheckedChange = state::updateShowNsfwPosts,
                    )
                }
                // ISSUE230_PINNED_POSTS_FIRST_V1
                if (searchResult.isItemVisible("pinned-posts")) item("pinned-posts") {
                    ValueSettingRow(
                        icon = Symbols.PushPin,
                        title = stringResource(R.string.set_pinned_posts),
                        description = stringResource(R.string.set_how_a_community_s_pinned_posts_show),
                        value = stringResource(state.pinnedPostsPresentation.labelRes),
                        onClick = { choiceDialogKey = "pinned-posts" },
                    )
                }
                // ISSUE290_FEEDS_RESUME_WHERE_YOU_LEFT_OFF_V1: off by default; the two
                // choices show only while it is on.
                if (searchResult.isItemVisible("resume-feed-position")) item("resume-feed-position") {
                    ToggleRow(
                        icon = Symbols.History,
                        title = stringResource(R.string.set_resume_where_you_left_off),
                        description = stringResource(R.string.set_home_and_community_feeds_open_where_you),
                        checked = state.resumeFeedPosition,
                        onCheckedChange = state::updateResumeFeedPosition,
                    )
                }
                if (state.resumeFeedPosition && searchResult.isItemVisible("resume-feed-position-age")) item("resume-feed-position-age") {
                    ValueSettingRow(
                        icon = Symbols.Schedule,
                        title = stringResource(R.string.set_keep_my_place_for),
                        description = stringResource(R.string.set_after_this_long_a_feed_starts_at),
                        value = stringResource(state.resumeFeedPositionAge.labelRes),
                        onClick = { choiceDialogKey = "resume-feed-position-age" },
                    )
                }
                if (state.resumeFeedPosition && searchResult.isItemVisible("resume-feed-position-start")) item("resume-feed-position-start") {
                    ValueSettingRow(
                        icon = Symbols.Refresh,
                        title = stringResource(R.string.set_when_the_app_starts),
                        description = stringResource(R.string.set_show_the_posts_you_were_reading_or),
                        value = stringResource(state.resumeFeedPositionStart.labelRes),
                        onClick = { choiceDialogKey = "resume-feed-position-start" },
                    )
                }
                // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: off by default, only while NSFW is shown.
                if (state.showNsfwPosts && searchResult.isItemVisible("random-nsfw-community")) item("random-nsfw-community") {
                    ValueSettingRow(
                        icon = Symbols.Shuffle,
                        title = stringResource(R.string.set_random_nsfw_community),
                        description = stringResource(R.string.set_where_a_button_opens_a_random_nsfw),
                        value = stringResource(state.randomNsfwCommunityPlacement.labelRes),
                        onClick = { choiceDialogKey = "random-nsfw-community" },
                    )
                }
                if (
                    destination == SettingsDestination.FEEDS &&
                    query.isBlank() &&
                    searchResult.isItemVisible("content-filters")
                ) {
                    item("feed-filtering-title") { SettingsSection(stringResource(R.string.all_filtering)) }
                }
                if (searchResult.isItemVisible("filtered-posts-summary")) item("filtered-posts-summary") {
                    // CONTENTFILTER04_FILTERED_SUMMARY_V1
                    ToggleRow(
                        icon = Symbols.FilterAlt,
                        title = stringResource(R.string.set_show_filtered_post_count),
                        description = stringResource(R.string.set_a_row_at_the_top_of_a),
                        checked = state.filteredPostsSummary,
                        onCheckedChange = state::updateFilteredPostsSummary,
                    )
                }
                // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1: off by default.
                if (searchResult.isItemVisible("scroll-position-indicator")) item("scroll-position-indicator") {
                    ToggleRow(
                        icon = Symbols.UnfoldMore,
                        title = stringResource(R.string.set_scroll_position_indicator),
                        description = stringResource(R.string.set_a_thin_bar_on_the_edge_of),
                        checked = state.scrollPositionIndicator,
                        onCheckedChange = state::updateScrollPositionIndicator,
                    )
                }
                if (searchResult.isItemVisible("content-filters")) item("content-filters") {
                    NavigationRow(
                        icon = Symbols.FilterAlt,
                        title = stringResource(R.string.set_content_filters),
                        description = contentFilterSummary(state.contentFilters).asString(),
                        onClick = {
                            openSettingsDestination(SettingsDestination.CONTENT_FILTERS)
                        },
                    )
                }
                if (searchResult.isItemVisible("muted-community-direct-view")) {
                    item("muted-community-direct-view") {
                        MutedCommunityDirectViewSetting(
                            mode = state.mutedCommunityDirectViewMode,
                            onModeChange =
                                state::updateMutedCommunityDirectViewMode,
                        )
                    }
                }
            }

            if (destination == SettingsDestination.APPEARANCE && query.isBlank()) {
                val appearancePreview: @Composable () -> Unit = {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppearanceSettingsPreview(
                            themeMode = state.themeMode,
                            dynamicColor = state.dynamicColor,
                            pureBlack = state.pureBlackDarkTheme,
                            voteControlMode = state.voteControlMode,
                            voteColorPalette = state.voteColorPalette,
                            codeFont = state.codeFont,
                            reduceMotion = state.motionReduced,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                }
                // ISSUE32_APP_TEXT_SIZE_V1: at a larger text size the preview is
                // tall; it scrolls away instead of covering half the screen.
                if (state.appTextScale.factor > 1f) {
                    item(key = "appearance-live-preview") { appearancePreview() }
                } else {
                    stickyHeader(key = "appearance-live-preview") { appearancePreview() }
                }
            }
            if (destination == SettingsDestination.MEDIA && query.isBlank()) {
                val mediaPreview: @Composable () -> Unit = {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MediaSettingsPreview(
                            sensitiveMediaPreviewMode = state.sensitiveMediaPreviewMode,
                            videoAutoplay = state.videoAutoplay,
                            startVideosMuted = state.startVideosMuted,
                            videoTimelineSize = state.videoTimelineSize,
                            mediaCaptionMode = state.mediaCaptionMode,
                            fullscreenCaptionMode = state.fullscreenCaptionMode,
                            mediaLabelsVisible = state.mediaLabelsVisible,
                            landscapeVideoGesturesEnabled = state.landscapeVideoGesturesEnabled,
                            landscapeBrightnessGestureEnabled = state.landscapeBrightnessGestureEnabled,
                            landscapeVolumeGestureEnabled = state.landscapeVolumeGestureEnabled,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                }
                // Same rule as the appearance preview (ISSUE32_APP_TEXT_SIZE_V1).
                if (state.appTextScale.factor > 1f) {
                    item(key = "media-live-preview") { mediaPreview() }
                } else {
                    stickyHeader(key = "media-live-preview") { mediaPreview() }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Appearance",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("appearance-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_appearance))) }
                if (listOf("ui-size", "text-size", "vote-controls").any(searchResult::isItemVisible)) {
                    item("size-title") { SettingsSection(stringResource(R.string.all_size)) }
                }
                // ISSUE278_UI_SIZE_SCALES_THE_WHOLE_APP_V1 (owner): one size for the
                // whole interface, grouped with the other size choices.
                if (searchResult.isItemVisible("ui-size")) item("ui-size") {
                    ValueSettingRow(
                        icon = Symbols.ZoomIn,
                        title = stringResource(R.string.set_ui_size),
                        // ISSUE278 (owner chose A): below 100 % small buttons in dialogs, sheets
                        // and menus get a smaller tap area, as with Android's Display size.
                        description = stringResource(R.string.all2_makes_bars_buttons_rows_and_text_in),
                        value = stringResource(state.appUiScale.labelRes),
                        onClick = { choiceDialogKey = "appearance-ui-size" },
                    )
                }
                if (searchResult.isItemVisible("text-size")) item("text-size") {
                    ValueSettingRow(
                        icon = Symbols.FormatSize,
                        title = stringResource(R.string.set_text_size),
                        description = stringResource(R.string.set_scales_all_text_in_subshell_on_top),
                        value = state.appTextScale.label,
                        onClick = { choiceDialogKey = "appearance-text-size" },
                    )
                }
                if (searchResult.isItemVisible("vote-controls")) item("vote-controls") {
                    ValueSettingRow(
                        icon = Symbols.ThumbUp,
                        title = stringResource(R.string.set_vote_controls),
                        description = stringResource(R.string.set_thumbs_with_the_score_between_them_or),
                        value = stringResource(state.voteControlMode.labelRes),
                        onClick = { choiceDialogKey = "appearance-vote-controls" },
                    )
                }
                if (listOf("theme", "dynamic", "vote-colours", "code-font").any(searchResult::isItemVisible)) {
                    item("colours-title") { SettingsSection(stringResource(R.string.all_colours_fonts)) }
                }
                if (searchResult.isItemVisible("theme")) item("theme") {
                    ValueSettingRow(
                        icon = Symbols.Palette,
                        title = stringResource(R.string.set_theme),
                        description = stringResource(R.string.set_follow_the_system_or_use_light_dark),
                        value = state.themeChoice.label,
                        onClick = { choiceDialogKey = "appearance-theme" },
                    )
                }
                if (searchResult.isItemVisible("dynamic")) item("dynamic") {
                    ToggleRow(
                        icon = Symbols.Palette,
                        title = stringResource(R.string.set_dynamic_colour),
                        // SETTINGSREVIEW01_DYNAMIC_COLOUR_ON_BY_DEFAULT_V1: the
                        // device palette exists from Android 12.
                        description = if (dynamicColorSupported()) {
                            stringResource(R.string.all_use_the_device_palette_instead_of_subshell)
                        } else {
                            stringResource(R.string.all_needs_android_12_or_later)
                        },
                        checked = state.dynamicColor && dynamicColorSupported(),
                        onCheckedChange = state::updateDynamicColor,
                        enabled = dynamicColorSupported(),
                    )
                }
                if (searchResult.isItemVisible("vote-colours")) item("vote-colours") {
                    ValueSettingRow(
                        icon = Symbols.Palette,
                        title = stringResource(R.string.set_vote_colours),
                        description = stringResource(R.string.set_theme_accent_or_red_upvotes_and_blue),
                        value = stringResource(state.voteColorPalette.labelRes),
                        onClick = { choiceDialogKey = "appearance-vote-colours" },
                    )
                }
                if (searchResult.isItemVisible("code-font")) item("code-font") {
                    ValueSettingRow(
                        icon = Symbols.Code,
                        title = stringResource(R.string.set_code_font),
                        description = stringResource(R.string.set_used_by_inline_code_and_fenced_code),
                        value = state.codeFont.typefaceName,
                        onClick = { choiceDialogKey = "appearance-code-font" },
                    )
                }
            }

            if (destination == SettingsDestination.NAVIGATION && query.isBlank()) {
                stickyHeader(key = "navigation-live-preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        NavigationSettingsPreview(
                            topBarBehavior = state.topBarBehavior,
                            bottomBarBehavior = state.bottomBarBehavior,
                            interactionSide = state.interactionSide,
                            drawerSide = state.resolvedDrawerSide,
                            navigationLabelMode = state.navigationLabelMode,
                            subredditLabelStyle = state.subredditLabelStyle,
                            tabletLayoutMode = state.tabletLayoutMode,
                            communityDiscoveryLayout = state.communityDiscoveryLayout,
                            postSwipeLeftAction = state.postSwipeLeftAction,
                            postSwipeRightAction = state.postSwipeRightAction,
                            commentSwipeLeftAction = state.commentSwipeLeftAction,
                            commentSwipeRightAction = state.commentSwipeRightAction,
                            voteColorPalette = state.voteColorPalette,
                            voteControlMode = state.voteControlMode,
                            reduceMotion = state.motionReduced,
                            previewContext = navigationPreviewContext,
                            pageScrolling = settingsListState.isScrollInProgress,
                            modifier = Modifier.padding(bottom = 9.dp),
                            swipeActionsEnabled = state.swipeActionsEnabled,
                        )
                    }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Home & feeds",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("home-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_home_feeds))) }
                if (searchResult.isItemVisible("top-bar-behavior")) item("top-bar-behavior") {
                    ValueSettingRow(
                        icon = Symbols.Home,
                        title = stringResource(R.string.set_top_bar),
                        description = stringResource(R.string.set_behaviour_while_scrolling),
                        value = stringResource(state.topBarBehavior.labelRes),
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "top-bar-behavior"
                        },
                    )
                }
                if (searchResult.isItemVisible("bottom-navigation-behavior")) item("bottom-navigation-behavior") {
                    ValueSettingRow(
                        icon = Symbols.Swipe,
                        title = stringResource(R.string.set_bottom_navigation),
                        description = stringResource(R.string.set_behaviour_while_scrolling),
                        value = stringResource(state.bottomBarBehavior.labelRes),
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "bottom-navigation-behavior"
                        },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Navigation",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("navigation-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_navigation))) }
                if (query.isBlank() || searchResult.isItemVisible("tablet-layout-mode")) {
                    item("tablet-layout-mode") {
                        SettingsSegmentedChoice(
                            icon = Symbols.Swipe,
                            title = stringResource(R.string.set_tablet_mode),
                            description = stringResource(R.string.set_choose_how_content_is_shown_on_wider),
                            options = TabletLayoutMode.entries,
                            selected = state.tabletLayoutMode,
                            label = { stringResource(it.labelRes) },
                            onSelect = { mode ->
                                navigationPreviewContextName =
                                    NavigationPreviewContext.TABLET_MODE.name
                                state.updateTabletLayoutMode(mode)
                            },
                        )
                    }
                }
                if (query.isBlank() || searchResult.isItemVisible("community-discovery-layout")) {
                    item("community-discovery-layout") {
                        SettingsSegmentedChoice(
                            icon = Symbols.Groups,
                            title = stringResource(R.string.set_community_discovery),
                            description = stringResource(R.string.set_choose_how_popular_communities_are_shown),
                            options = CommunityDiscoveryLayout.entries,
                            selected = state.communityDiscoveryLayout,
                            label = { stringResource(it.labelRes) },
                            onSelect = { layout ->
                                navigationPreviewContextName =
                                    NavigationPreviewContext.COMMUNITY_DISCOVERY.name
                                state.updateCommunityDiscoveryLayout(layout)
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("interaction-side")) item("interaction-side") {
                    SettingsSegmentedChoice(
                        icon = Symbols.TouchApp,
                        title = stringResource(R.string.set_interaction_side),
                        description = stringResource(R.string.set_preferred_side_for_thumb_controls_and_floating),
                        options = InteractionSide.entries,
                        selected = state.interactionSide,
                        label = { stringResource(it.labelRes) },
                        onSelect = { side ->
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            state.updateInteractionSide(side)
                        },
                    )
                }
                // ISSUE16_CONFIGURABLE_SWIPE_ACTIONS_SETTINGS_V1
                // ISSUE16_SINGLE_SHARED_NAVIGATION_PREVIEW_SETTINGS_V1
                // ISSUE16_INLINE_EXPANDABLE_SWIPE_SETTINGS_V1
                if (
                    (destination == SettingsDestination.NAVIGATION && query.isBlank()) ||
                    searchResult.isItemVisible("swipe-actions-enabled") ||
                    searchResult.isItemVisible("post-swipe-left") ||
                    searchResult.isItemVisible("post-swipe-right") ||
                    searchResult.isItemVisible("comment-swipe-left") ||
                    searchResult.isItemVisible("comment-swipe-right")
                ) {
                    item("swipe-actions-summary") {
                        SwipeActionsSettingsSummary(
                            enabled = state.swipeActionsEnabled,
                            expandedTarget = expandedSwipeActionsTarget,
                            postLeftAction = state.postSwipeLeftAction,
                            postRightAction = state.postSwipeRightAction,
                            commentLeftAction = state.commentSwipeLeftAction,
                            commentRightAction = state.commentSwipeRightAction,
                            onEnabledChange = state::updateSwipeActionsEnabled,
                            onExpandedTargetChange = { target ->
                                expandedSwipeActionsTargetName = target?.name
                            },
                            onPostLeftActionChange = state::updatePostSwipeLeftAction,
                            onPostRightActionChange = state::updatePostSwipeRightAction,
                            onCommentLeftActionChange = state::updateCommentSwipeLeftAction,
                            onCommentRightActionChange = state::updateCommentSwipeRightAction,
                        )
                    }
                }
                // SWIPEDISTANCE01_THE_READER_PICKS_HOW_FAR_A_SWIPE_GOES_V1: only while
                // swipe actions are on (or when searched for).
                if (
                    (destination == SettingsDestination.NAVIGATION && query.isBlank() && state.swipeActionsEnabled) ||
                    searchResult.isItemVisible("swipe-distance")
                ) {
                    item("swipe-distance") {
                        SettingsSegmentedChoice(
                            icon = Symbols.Swipe,
                            title = stringResource(R.string.set_swipe_distance),
                            description = stringResource(R.string.set_how_far_to_swipe_before_the_action_runs),
                            options = io.github.brealorg.subshell.ui.SwipeDistance.entries,
                            selected = state.swipeDistance,
                            label = { stringResource(it.labelRes) },
                            onSelect = state::updateSwipeDistance,
                        )
                    }
                    // SWIPEDISTANCE02_A_CARD_TO_TRY_THE_SWIPE_ON_V1: only while swipe actions
                    // are on (review: found by search it swiped a gesture the feed lacks).
                    if (state.swipeActionsEnabled) item("swipe-distance-try") {
                        io.github.brealorg.subshell.ui.components.SwipeDistanceTryCard(
                            state = state,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                if (destination == SettingsDestination.NAVIGATION && query.isBlank()) {
                    item("side-menu-title") { SettingsSection(stringResource(R.string.all_side_menu)) }
                }

                if (searchResult.isItemVisible("drawer-side")) item("drawer-side") {
                    SettingsSegmentedChoice(
                        icon = Symbols.Swipe,
                        title = stringResource(R.string.set_side_menu_position),
                        description = stringResource(R.string.set_choose_which_edge_the_side_menu_opens),
                        options = selectableDrawerSidePreferences,
                        selected = state.drawerSidePreference,
                        label = { stringResource(it.labelRes) },
                        onSelect = { preference ->
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            state.updateDrawerSidePreference(preference)
                        },
                    )
                }

                if (searchResult.isItemVisible("subreddit-labels")) item("subreddit-labels") {
                    ValueSettingRow(
                        icon = Symbols.Label,
                        title = stringResource(R.string.set_communities_labels),
                        description = stringResource(R.string.set_how_community_names_are_written),
                        value = state.subredditLabelStyle.example,
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "subreddit-labels"
                        },
                    )
                }
                if (searchResult.isItemVisible("drawer-communities")) item("drawer-communities") {
                    ValueSettingRow(
                        icon = Symbols.Groups,
                        title = stringResource(R.string.set_communities_in_drawer),
                        description = stringResource(R.string.set_simple_shortcut_dropdown_or_full_list),
                        value = stringResource(state.drawerCommunitiesMode.labelRes),
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "drawer-communities"
                        },
                    )
                }
                if (searchResult.isItemVisible("drawer-recent-communities")) item("drawer-recent-communities") {
                    ToggleRow(
                        icon = Symbols.History,
                        title = stringResource(R.string.set_recent_communities_in_drawer),
                        description = stringResource(R.string.set_show_up_to_five_recently_visited_communities),
                        checked = state.drawerRecentCommunities,
                        onCheckedChange = { enabled ->
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            state.updateDrawerRecentCommunities(enabled)
                        },
                    )
                }
                if (searchResult.isItemVisible("back-twice-to-exit")) item("back-twice-to-exit") {
                    ToggleRow(
                        icon = Symbols.ExitToApp,
                        title = stringResource(R.string.set_press_back_twice_to_exit),
                        description = stringResource(R.string.set_ask_for_a_second_back_before_leaving),
                        checked = state.backTwiceToExit,
                        onCheckedChange = state::updateBackTwiceToExit,
                    )
                }
                if (searchResult.isItemVisible("launcher-shortcuts")) item("launcher-shortcuts") {
                    // ISSUE37_LAUNCHER_SHORTCUTS_V1: device-local, off by default.
                    ToggleRow(
                        icon = Symbols.AddToHomeScreen,
                        title = stringResource(R.string.set_launcher_shortcuts),
                        description = stringResource(R.string.set_favorite_communities_on_the_app_icon_and),
                        checked = launcherShortcutsEnabled,
                        onCheckedChange = { enabled ->
                            LauncherShortcutsEnabledStore.write(appContext, enabled)
                        },
                    )
                }
                if (searchResult.isItemVisible("reddit-favorite-sync")) item("reddit-favorite-sync") {
                    // REDDITFAV01_SHOW_REDDIT_FAVORITES_V1: device-local, off by default.
                    ToggleRow(
                        icon = Symbols.Star,
                        title = stringResource(R.string.set_sync_favorites_with_reddit),
                        description = stringResource(R.string.set_show_your_reddit_favorites_and_star_joined),
                        checked = redditFavoriteSyncEnabled,
                        onCheckedChange = { enabled ->
                            RedditFavoriteSyncSettingStore.write(appContext, enabled)
                        },
                    )
                }
                if (searchResult.isItemVisible("share-into-subshell")) item("share-into-subshell") {
                    // ISSUE33_SHARE_INTO_SUBSHELL_V1: device-local, off by default.
                    // LOWUI2_SETTINGS_READS_OFF_MAIN_V1 (OCB-272): the component state
                    // is a PackageManager call; it is read and written on IO.
                    var shareIntoSubshellEnabled by remember { mutableStateOf<Boolean?>(null) }
                    val shareScope = rememberCoroutineScope()
                    LaunchedEffect(appContext) {
                        shareIntoSubshellEnabled = withContext(Dispatchers.IO) {
                            ShareIntoSubshellComponent.isEnabled(appContext)
                        }
                    }
                    ToggleRow(
                        icon = Symbols.Share,
                        title = stringResource(R.string.set_share_into_subshell),
                        description = stringResource(R.string.set_show_subshell_in_android_s_share_menu),
                        checked = shareIntoSubshellEnabled == true,
                        onCheckedChange = { enabled ->
                            shareIntoSubshellEnabled = null
                            shareScope.launch {
                                shareIntoSubshellEnabled = withContext(Dispatchers.IO) {
                                    ShareIntoSubshellComponent.setEnabled(appContext, enabled)
                                    ShareIntoSubshellComponent.isEnabled(appContext)
                                }
                            }
                        },
                        enabled = shareIntoSubshellEnabled != null,
                    )
                }
                if (searchResult.isItemVisible("navigation-labels")) item("navigation-labels") {
                    ValueSettingRow(
                        icon = Symbols.Swipe,
                        title = stringResource(R.string.set_navigation_labels),
                        description = stringResource(R.string.set_all_labels_selected_only_or_icons_only),
                        value = stringResource(state.navigationLabelMode.labelRes),
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "navigation-labels"
                        },
                    )
                }            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Privacy",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("privacy-title") {
                    SettingsSection(
                        if (destination == SettingsDestination.FEEDS && query.isBlank()) {
                            stringResource(R.string.all_privacy)
                        } else {
                            stringResource(settingsSectionTitle(destination, R.string.all_privacy))
                        },
                    )
                }
                if (searchResult.isItemVisible("hidden-subscriptions-device-unlock")) item("hidden-subscriptions-device-unlock") {
                    ToggleRow(
                        icon = Symbols.Lock,
                        title = stringResource(R.string.set_require_device_unlock_to_view_hidden_communities),
                        // LOWUI8_HIDDEN_UNLOCK_NEEDS_A_SCREEN_LOCK_V1 (OCB-273): like
                        // Lock Subshell, it can be turned on only where there is a
                        // device unlock to ask for (otherwise the list could not open).
                        description = if (
                            appLock == null ||
                            appLock.deviceUnlockAvailable ||
                            state.requireDeviceUnlockForHiddenSubscriptions
                        ) {
                            stringResource(R.string.all_ask_for_biometrics_or_your_device_screen)
                        } else {
                            stringResource(R.string.all_set_a_screen_lock_on_this_device)
                        },
                        checked = state.requireDeviceUnlockForHiddenSubscriptions,
                        onCheckedChange = { required ->
                            if (required) {
                                state.updateRequireDeviceUnlockForHiddenSubscriptions(true)
                            } else {
                                turnOffAfterDeviceUnlock(
                                    appLock = appLock,
                                    title = showHiddenFreelyTitle,
                                    subtitle = showHiddenFreelySubtitle,
                                ) {
                                    state.updateRequireDeviceUnlockForHiddenSubscriptions(false)
                                }
                            }
                        },
                        enabled = appLock == null ||
                            appLock.deviceUnlockAvailable ||
                            state.requireDeviceUnlockForHiddenSubscriptions,
                    )
                }
                // ISSUE38_APP_LOCK_V1: off by default; turning it on asks for the unlock once.
                if (appLock != null && searchResult.isItemVisible("app-lock")) item("app-lock") {
                    ToggleRow(
                        icon = Symbols.Lock,
                        title = stringResource(R.string.set_lock_subshell),
                        description = if (appLock.deviceUnlockAvailable || appLock.settings.enabled) {
                            stringResource(R.string.all_ask_for_biometrics_or_your_device_screen_2)
                        } else {
                            stringResource(R.string.all_set_a_screen_lock_on_this_device)
                        },
                        checked = appLock.settings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                appLock.onRequestUnlock { unlocked ->
                                    if (unlocked) appLock.onChange(appLock.settings.copy(enabled = true))
                                }
                            } else {
                                turnOffAfterDeviceUnlock(
                                    appLock = appLock,
                                    title = turnOffAppLockTitle,
                                    subtitle = turnOffAppLockSubtitle,
                                ) {
                                    appLock.onChange(appLock.settings.copy(enabled = false))
                                }
                            }
                        },
                        enabled = appLock.deviceUnlockAvailable || appLock.settings.enabled,
                    )
                }
                if (
                    appLock != null &&
                    appLock.settings.enabled &&
                    searchResult.isItemVisible("app-lock-delay")
                ) item("app-lock-delay") {
                    ValueSettingRow(
                        icon = Symbols.Schedule,
                        title = stringResource(R.string.set_lock_after),
                        description = stringResource(R.string.set_how_long_subshell_can_be_in_the),
                        value = appLockDelayLabel(appLock.settings.delayMinutes).asString(),
                        onClick = { choiceDialogKey = "app-lock-delay" },
                    )
                }
                if (
                    appLock != null &&
                    appLock.settings.enabled &&
                    searchResult.isItemVisible("app-lock-notifications")
                ) item("app-lock-notifications") {
                    ToggleRow(
                        icon = Symbols.Lock,
                        title = stringResource(R.string.set_hide_notification_content),
                        description = stringResource(R.string.set_while_the_lock_is_on_notifications_only),
                        checked = appLock.settings.hideNotificationContent,
                        onCheckedChange = { hide ->
                            appLock.onChange(appLock.settings.copy(hideNotificationContent = hide))
                        },
                    )
                }
                // ISSUE268_YOUR_READING_IS_OFF_UNTIL_TURNED_ON_V1: device-local,
                // not in backups; turning it off deletes what was counted.
                if (searchResult.isItemVisible("your-reading")) item("your-reading") {
                    ToggleRow(
                        icon = Symbols.Schedule,
                        title = stringResource(R.string.set_your_reading),
                        description = stringResource(R.string.set_count_opens_and_time_per_community_on),
                        checked = readingStatsEnabled == true,
                        onCheckedChange = { enabled -> readingStatsStore.onDisk { setEnabled(enabled) } },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Search",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("search-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_search))) }
                if (searchResult.isItemVisible("search-keyboard")) item("search-keyboard") {
                    ToggleRow(
                        icon = Symbols.Keyboard,
                        title = stringResource(R.string.set_open_keyboard_when_entering_search),
                        description = stringResource(R.string.set_explicit_search_taps_focus_the_field_immediately),
                        checked = state.openKeyboardOnSearch,
                        onCheckedChange = state::updateOpenKeyboardOnSearch,
                    )
                }
                // ISSUE206_SAVED_SEARCHES_V1: off by default.
                if (searchResult.isItemVisible("saved-searches")) item("saved-searches") {
                    ToggleRow(
                        icon = Symbols.Bookmark,
                        title = stringResource(R.string.set_saved_searches),
                        description = stringResource(R.string.set_save_a_search_to_run_it_again),
                        checked = state.savedSearchesEnabled,
                        onCheckedChange = state::updateSavedSearchesEnabled,
                    )
                }
                // ISSUE293_LANGUAGE_FILTER_V1: off by default.
                if (searchResult.isItemVisible("shown-languages")) item("shown-languages") {
                    ValueSettingRow(
                        icon = Symbols.Public,
                        title = stringResource(R.string.set_languages),
                        description = stringResource(R.string.all2_hide_communities_and_search_results_in_other),
                        value = shownLanguagesSummary(state.shownLanguages),
                        onClick = {
                            choiceDialogKey = "shown-languages"
                        },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Media & links",
                    debug = BuildConfig.DEBUG,
                )
                && searchResult.isSectionVisible("Media & links")
            ) {
                if (
                    searchResult.isItemVisible("sensitive-media-previews") ||
                    searchResult.isItemVisible("show-nsfw-media") ||
                    searchResult.isItemVisible("sensitive-media-reddit-lab") ||
                    searchResult.isItemVisible("media-autoplay")
                ) {
                    item("media-preview-playback-title") { SettingsSection(stringResource(R.string.all_preview_playback)) }
                }
                if (searchResult.isItemVisible("sensitive-media-previews")) item("sensitive-media-previews") {
                    ValueSettingRow(
                        icon = Symbols.Visibility,
                        title = stringResource(R.string.set_sensitive_media_previews),
                        // SETTINGSREVIEW01_ROWS_USE_THE_DIALOG_WORDS_V1: Blurred
                        // also loads the image, so the row says so.
                        description = stringResource(R.string.set_blurred_loaded_in_the_background_load_on),
                        value = stringResource(state.sensitiveMediaPreviewMode.labelRes),
                        onClick = { choiceDialogKey = "media-sensitive-previews" },
                    )
                }
                // NSFWMEDIA01_SHOW_NSFW_MEDIA_SETTING_V1: off by default.
                if (searchResult.isItemVisible("show-nsfw-media")) item("show-nsfw-media") {
                    ToggleRow(
                        icon = Symbols.Visibility,
                        title = stringResource(R.string.set_show_nsfw_media),
                        description = stringResource(R.string.set_nsfw_images_gifs_and_videos_show_and),
                        checked = state.showNsfwMedia,
                        onCheckedChange = state::updateShowNsfwMedia,
                    )
                }
                if (BuildConfig.DEBUG && BuildConfig.DEBUG_SLOT == "dev2") {
                    if (searchResult.isItemVisible("sensitive-media-reddit-lab")) item("sensitive-media-reddit-lab") {
                        NavigationRow(
                            icon = Symbols.Visibility,
                            title = stringResource(R.string.set_sensitive_media_reddit_lab),
                            description = stringResource(R.string.set_dev2_only_raw_reddit_json_through_the),
                            onClick = {
                                openSettingsDestination(SettingsDestination.SENSITIVE_MEDIA_LAB)
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("media-autoplay")) item("media-autoplay") {
                    ValueSettingRow(
                        icon = Symbols.Image,
                        title = stringResource(R.string.set_autoplay_videos_and_gifs),
                        description = stringResource(R.string.set_always_wi_fi_only_or_never),
                        value = stringResource(state.videoAutoplay.labelRes),
                        onClick = { choiceDialogKey = "media-autoplay" },
                    )
                }
                // VIDEO05_VIDEO_SETTINGS_GROUP_V1 (#227): every video choice under one heading.
                if (
                    searchResult.isItemVisible("media-muted") ||
                    searchResult.isItemVisible("media-loop") ||
                    searchResult.isItemVisible("video-subtitle-default") ||
                    searchResult.isItemVisible("video-audio-default") ||
                    searchResult.isItemVisible("playback-options-placement") ||
                    searchResult.isItemVisible("video-timeline-size") ||
                    searchResult.isItemVisible("video-quality-wifi") ||
                    searchResult.isItemVisible("video-quality-mobile") ||
                    searchResult.isItemVisible("video-speed-controls") ||
                    searchResult.isItemVisible("video-double-tap-seek") ||
                    searchResult.isItemVisible("video-hold-double-speed") ||
                    searchResult.isItemVisible("video-picture-in-picture") ||
                    searchResult.isItemVisible("video-rotate-fullscreen") ||
                    searchResult.isItemVisible("video-technical-details")
                ) {
                    item("media-video-title") { SettingsSection(stringResource(R.string.all_video)) }
                }
                if (searchResult.isItemVisible("media-muted")) item("media-muted") {
                    ToggleRow(
                        icon = Symbols.VolumeOff,
                        title = stringResource(R.string.set_start_videos_muted),
                        description = stringResource(R.string.set_begin_playback_without_audio),
                        checked = state.startVideosMuted,
                        onCheckedChange = state::updateStartVideosMuted,
                    )
                }
                if (searchResult.isItemVisible("media-loop")) item("media-loop") {
                    ToggleRow(
                        icon = Symbols.History,
                        title = stringResource(R.string.set_loop_videos),
                        description = stringResource(R.string.set_restart_videos_automatically_after_they_finish),
                        checked = state.loopVideos,
                        onCheckedChange = state::updateLoopVideos,
                    )
                }
                if (searchResult.isItemVisible("video-subtitle-default")) item("video-subtitle-default") {
                    ValueSettingRow(
                        icon = Symbols.ClosedCaption,
                        title = stringResource(R.string.set_subtitles),
                        description = stringResource(R.string.set_default_for_new_videos),
                        value = stringResource(state.videoSubtitleDefault.rowLabelRes),
                        onClick = { choiceDialogKey = "media-subtitle-default" },
                    )
                }
                if (searchResult.isItemVisible("video-audio-default")) item("video-audio-default") {
                    ValueSettingRow(
                        icon = Symbols.VolumeUp,
                        title = stringResource(R.string.set_audio_language),
                        description = stringResource(R.string.set_when_a_video_has_several),
                        value = stringResource(state.videoAudioDefault.rowLabelRes),
                        onClick = { choiceDialogKey = "media-audio-default" },
                    )
                }
                if (searchResult.isItemVisible("playback-options-placement")) item("playback-options-placement") {
                    ValueSettingRow(
                        icon = Symbols.Tune,
                        title = stringResource(R.string.set_playback_options_button),
                        description = stringResource(R.string.set_quality_audio_and_subtitles),
                        value = stringResource(state.playbackOptionsPlacement.rowLabelRes),
                        onClick = { choiceDialogKey = "media-playback-options" },
                    )
                }
                if (searchResult.isItemVisible("video-timeline-size")) item("video-timeline-size") {
                    ValueSettingRow(
                        icon = Symbols.Tune,
                        title = stringResource(R.string.set_video_timeline_size),
                        description = stringResource(R.string.set_compact_standard_or_large),
                        value = stringResource(state.videoTimelineSize.labelRes),
                        onClick = { choiceDialogKey = "media-timeline-size" },
                    )
                }
                if (searchResult.isItemVisible("video-quality-wifi")) item("video-quality-wifi") {
                    ValueSettingRow(
                        icon = Symbols.Wifi,
                        title = stringResource(R.string.set_video_quality_on_wi_fi),
                        description = stringResource(R.string.set_adaptive_quality_ceiling),
                        value = stringResource(state.wifiVideoQuality.labelRes),
                        onClick = { choiceDialogKey = "media-quality-wifi" },
                    )
                }
                if (searchResult.isItemVisible("video-quality-mobile")) item("video-quality-mobile") {
                    ValueSettingRow(
                        icon = Symbols.SignalCellularAlt,
                        title = stringResource(R.string.set_video_quality_on_mobile_data),
                        description = stringResource(R.string.set_adaptive_quality_ceiling),
                        value = stringResource(state.mobileVideoQuality.labelRes),
                        onClick = { choiceDialogKey = "media-quality-mobile" },
                    )
                }
                // VIDEO07 (#227, owner): each extra behind its own setting, off by default.
                if (searchResult.isItemVisible("video-speed-controls")) item("video-speed-controls") {
                    ToggleRow(
                        icon = Symbols.Speed,
                        title = stringResource(R.string.set_playback_speed),
                        description = stringResource(R.string.set_full_screen_speed_button_0_25_to),
                        checked = state.videoSpeedControls,
                        onCheckedChange = state::updateVideoSpeedControls,
                    )
                }
                if (searchResult.isItemVisible("video-double-tap-seek")) item("video-double-tap-seek") {
                    ToggleRow(
                        icon = Symbols.Forward10,
                        title = stringResource(R.string.set_double_tap_to_skip),
                        description = stringResource(R.string.set_full_screen_left_side_back_10_s),
                        checked = state.videoDoubleTapSeek,
                        onCheckedChange = state::updateVideoDoubleTapSeek,
                    )
                }
                if (searchResult.isItemVisible("video-hold-double-speed")) item("video-hold-double-speed") {
                    ToggleRow(
                        icon = Symbols.FastForward,
                        title = stringResource(R.string.set_hold_for_2_speed),
                        description = stringResource(R.string.set_full_screen_press_and_hold_the_picture),
                        checked = state.videoHoldForDoubleSpeed,
                        onCheckedChange = state::updateVideoHoldForDoubleSpeed,
                    )
                }
                // ISSUE231_PINCH_TO_ZOOM_VIDEOS_V1 (owner): off by default.
                if (searchResult.isItemVisible("video-pinch-to-zoom")) item("video-pinch-to-zoom") {
                    ToggleRow(
                        icon = Symbols.ZoomIn,
                        title = stringResource(R.string.set_pinch_to_zoom_videos),
                        description = stringResource(R.string.set_full_screen_pinch_to_zoom_in_drag),
                        checked = state.videoPinchToZoom,
                        onCheckedChange = state::updateVideoPinchToZoom,
                    )
                }
                if (searchResult.isItemVisible("video-picture-in-picture")) item("video-picture-in-picture") {
                    ToggleRow(
                        icon = Symbols.PictureInPicture,
                        title = stringResource(R.string.set_picture_in_picture),
                        description = stringResource(R.string.set_a_full_screen_video_keeps_playing_in),
                        checked = state.videoPictureInPicture,
                        onCheckedChange = state::updateVideoPictureInPicture,
                    )
                }
                if (searchResult.isItemVisible("video-rotate-fullscreen")) item("video-rotate-fullscreen") {
                    ToggleRow(
                        icon = Symbols.ScreenRotation,
                        title = stringResource(R.string.set_rotate_for_wide_videos),
                        description = stringResource(R.string.set_full_screen_turns_a_wide_video_to),
                        checked = state.videoRotateFullscreen,
                        onCheckedChange = state::updateVideoRotateFullscreen,
                    )
                }
                if (searchResult.isItemVisible("video-technical-details")) item("video-technical-details") {
                    ToggleRow(
                        icon = Symbols.Tune,
                        title = stringResource(R.string.set_technical_details),
                        description = stringResource(R.string.set_codecs_decoders_and_dropped_frames_in_playback),
                        checked = state.videoTechnicalDetails,
                        onCheckedChange = state::updateVideoTechnicalDetails,
                    )
                }

                if (
                    query.isBlank() ||
                    searchResult.isItemVisible("landscape-video-gestures") ||
                    searchResult.isItemVisible("landscape-brightness-gesture") ||
                    searchResult.isItemVisible("landscape-volume-gesture")
                ) {
                    item("media-landscape-gestures-title") { SettingsSection(stringResource(R.string.all_fullscreen_gestures)) }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-video-gestures")) {
                        item("landscape-video-gestures") {
                            ToggleRow(
                                icon = Symbols.Tune,
                                title = stringResource(R.string.set_landscape_swipe_controls),
                                description = stringResource(R.string.set_swipe_vertically_while_a_video_is_fullscreen),
                                checked = state.landscapeVideoGesturesEnabled,
                                onCheckedChange = state::updateLandscapeVideoGesturesEnabled,
                            )
                        }
                    }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-brightness-gesture")) {
                        item("landscape-brightness-gesture") {
                            ToggleRow(
                                icon = Symbols.Brightness6,
                                title = stringResource(R.string.set_brightness_gesture),
                                description = stringResource(R.string.set_left_side_changes_brightness_for_this_window),
                                checked = state.landscapeBrightnessGestureEnabled,
                                onCheckedChange = state::updateLandscapeBrightnessGestureEnabled,
                                enabled = state.landscapeVideoGesturesEnabled,
                            )
                        }
                    }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-volume-gesture")) {
                        item("landscape-volume-gesture") {
                            ToggleRow(
                                icon = Symbols.VolumeUp,
                                title = stringResource(R.string.set_volume_gesture),
                                description = stringResource(R.string.set_right_side_changes_media_volume),
                                checked = state.landscapeVolumeGestureEnabled,
                                onCheckedChange = state::updateLandscapeVolumeGestureEnabled,
                                enabled = state.landscapeVideoGesturesEnabled,
                            )
                        }
                    }
                }

                if (
                    searchResult.isItemVisible("media-captions") ||
                    searchResult.isItemVisible("fullscreen-captions") ||
                    searchResult.isItemVisible("media-labels")
                ) {
                    item("media-captions-title") { SettingsSection(stringResource(R.string.all_gallery_captions_labels)) }
                }
                if (searchResult.isItemVisible("media-captions")) item("media-captions") {
                    ValueSettingRow(
                        icon = Symbols.Image,
                        title = stringResource(R.string.set_gallery_captions),
                        description = stringResource(R.string.set_overlay_below_media_or_hidden),
                        value = stringResource(state.mediaCaptionMode.labelRes),
                        onClick = { choiceDialogKey = "media-captions" },
                    )
                }
                if (searchResult.isItemVisible("fullscreen-captions")) item("fullscreen-captions") {
                    ValueSettingRow(
                        icon = Symbols.Image,
                        title = stringResource(R.string.set_fullscreen_gallery_captions),
                        description = stringResource(R.string.set_with_controls_or_always_visible),
                        value = stringResource(state.fullscreenCaptionMode.labelRes),
                        onClick = { choiceDialogKey = "fullscreen-captions" },
                    )
                }
                if (searchResult.isItemVisible("media-labels")) item("media-labels") {
                    ToggleRow(
                        icon = Symbols.Image,
                        title = stringResource(R.string.set_media_labels),
                        description = stringResource(R.string.set_show_short_labels_over_images_gifs_and),
                        checked = state.mediaLabelsVisible,
                        onCheckedChange = state::updateMediaLabelsVisible,
                    )
                }

                if (
                    redditLinkHandlingState.supported &&
                    searchResult.isItemVisible("reddit-link-handling")
                ) {
                    item("media-links-title") { SettingsSection(stringResource(R.string.all_links)) }
                    item("reddit-link-handling") {
                        NavigationRow(
                            icon = Symbols.Link,
                            title = stringResource(R.string.set_open_reddit_links),
                            description = when (redditLinkHandlingState.status) {
                                RedditLinkHandlingStatus.ENABLED ->
                                    stringResource(R.string.all_enabled_supported_reddit_links_open_in_subshell)
                                RedditLinkHandlingStatus.PARTIAL ->
                                    stringResource(R.string.set_link_handling_partial, redditLinkHandlingState.selectedHostCount, redditLinkHandlingState.supportedHostCount)
                                RedditLinkHandlingStatus.DISABLED ->
                                    stringResource(R.string.all_off_choose_subshell_for_supported_reddit_links)
                                RedditLinkHandlingStatus.UNSUPPORTED ->
                                    stringResource(R.string.all_managed_by_android)
                            },
                            onClick = onOpenRedditLinkSettings,
                        )
                    }
                }
                // CLEANURL02_CLEAN_LINKS_SETTING_V1
                if (searchResult.isItemVisible("clean-links")) item("clean-links") {
                    ToggleRow(
                        icon = Symbols.Link,
                        title = stringResource(R.string.set_clean_links),
                        description = stringResource(R.string.set_remove_tracking_parameters_utm_fbclid_and_similar),
                        checked = state.cleanLinks,
                        onCheckedChange = state::updateCleanLinks,
                    )
                }

                if (
                    searchResult.isItemVisible("media-cache") ||
                    searchResult.isItemVisible("offline-images") ||
                    searchResult.isItemVisible("video-cache-limit") ||
                    searchResult.isItemVisible("download-storage")
                ) {
                    item("media-storage-title") { SettingsSection(stringResource(R.string.all_storage)) }
                }
                if (searchResult.isItemVisible("media-cache")) item("media-cache") {
                    SettingSurface(
                        icon = Symbols.Storage,
                        title = stringResource(R.string.set_media_cache),
                        description = when {
                            cacheClearInProgress -> stringResource(R.string.all_clearing_cached_media)
                            mediaCacheStatus != null && cacheOperationMessage != null ->
                                "${cacheOperationMessage?.asString()} · ${videoMediaCacheSummary(requireNotNull(mediaCacheStatus))}"
                            mediaCacheStatus != null -> videoMediaCacheSummary(requireNotNull(mediaCacheStatus))
                            mediaCacheReadFailed -> stringResource(R.string.all_media_cache_size_is_unavailable)
                            else -> stringResource(R.string.all_checking_media_cache)
                        },
                    ) {
                        TextButton(
                            enabled = !cacheClearInProgress && mediaCacheStatus?.bytesUsed?.let { it > 0L } == true,
                            onClick = { cacheClearConfirmationVisible = true },
                        ) {
                            Text(stringResource(R.string.set_clear))
                        }
                    }
                }
                // ISSUE265_SAVE_FOR_OFFLINE_V1: images saved for offline reading.
                if (searchResult.isItemVisible("offline-images")) item("offline-images") {
                    OfflineImagesRow()
                }
                if (searchResult.isItemVisible("video-cache-limit")) item("video-cache-limit") {
                    ValueSettingRow(
                        icon = Symbols.Storage,
                        title = stringResource(R.string.set_video_cache_size),
                        description = if (videoCacheLimitPending) {
                            stringResource(R.string.all_applies_the_next_time_subshell_starts)
                        } else {
                            stringResource(R.string.all_how_much_watched_video_to_keep_on)
                        },
                        value = videoCacheLimit?.let { stringResource(it.labelRes) }.orEmpty(),
                        onClick = {
                            if (videoCacheLimit != null) choiceDialogKey = "video-cache-limit"
                        },
                    )
                }
                // Shown while an older build's downloads are left (and for the
                // message right after deleting them).
                if (
                    searchResult.isItemVisible("download-storage") &&
                    (legacyDownloadStatus?.hasDownloads == true || downloadOperationMessage != null)
                ) item("download-storage") {
                    SettingSurface(
                        icon = Symbols.Storage,
                        title = stringResource(R.string.set_downloaded_videos),
                        description = when {
                            downloadDeleteInProgress -> stringResource(R.string.all_deleting_downloaded_videos)
                            downloadOperationMessage != null -> downloadOperationMessage?.asString().orEmpty()
                            else -> legacyDownloadStatus?.let { legacyVideoDownloadSummary(it).asString() }.orEmpty()
                        },
                    ) {
                        TextButton(
                            enabled = !downloadDeleteInProgress && legacyDownloadStatus?.hasDownloads == true,
                            onClick = { downloadDeleteConfirmationVisible = true },
                        ) {
                            Text(stringResource(R.string.set_delete_all))
                        }
                    }
                }


            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Accessibility",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("accessibility-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_accessibility))) }
                if (searchResult.isItemVisible("reduced-motion")) item("reduced-motion") {
                    ToggleRow(
                        icon = Symbols.AccessibilityNew,
                        title = stringResource(R.string.set_reduce_motion),
                        // SETTINGSREVIEW01_REDUCE_MOTION_FOLLOWS_ANDROID_V1
                        description = if (state.systemRemovesAnimations) {
                            stringResource(R.string.all_on_because_android_s_remove_animations_is)
                        } else {
                            stringResource(R.string.all_prefer_immediate_state_changes_over_decorative_movement)
                        },
                        checked = state.motionReduced,
                        onCheckedChange = state::updateReduceMotion,
                        enabled = !state.systemRemovesAnimations,
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Developer states",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("developer-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_developer_states))) }
                if (searchResult.isItemVisible("feed-state")) item("feed-state") {
                    ValueSettingRow(
                        icon = Symbols.CloudOff,
                        title = stringResource(R.string.set_feed_preview),
                        description = stringResource(R.string.set_inspect_loading_offline_error_and_empty_layouts),
                        value = stringResource(state.feedPreviewState.labelRes),
                        onClick = { choiceDialogKey = "feed-state" },
                    )
                }
            }

            if (destination == SettingsDestination.COMMENTS && query.isBlank()) {
                stickyHeader(key = "comments-live-preview") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        CommentsSettingsPreview(
                            highlightNewComments = state.highlightNewComments,
                            showCommentAvatars = state.showCommentAvatars,
                            actionPreference = state.commentActionPreference,
                            interactionSide = state.interactionSide,
                            timeFormat = state.commentTimeFormat,
                            collapseMode = state.commentCollapseMode,
                            hierarchyStyle = state.commentHierarchyStyle,
                            indentStepDp = state.commentIndentSize.stepDp,
                            railFromParent = state.commentRailFromParent,
                            threadColours = if (state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES) {
                                state.commentThreadColours
                            } else {
                                CommentThreadColours.OFF
                            },
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Comments",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("comments-title") {
                    SettingsSection(
                        if (destination == SettingsDestination.COMMENTS && query.isBlank()) {
                            stringResource(R.string.all_comment_presentation)
                        } else {
                            stringResource(settingsSectionTitle(destination, R.string.all_comments))
                        },
                    )
                }
                if (
                    searchResult.isItemVisible("comment-hierarchy-style")
                ) {
                    item("comment-hierarchy-style") {
                        ValueSettingRow(
                            icon = Symbols.Forum,
                            title = stringResource(R.string.set_comment_hierarchy),
                            description = stringResource(R.string.set_how_replies_connect_to_parent_comments),
                            // SETTINGSREVIEW01_ROWS_USE_THE_DIALOG_WORDS_V1
                            value = stringResource(state.commentHierarchyStyle.labelRes),
                            onClick = {
                                choiceDialogKey = "comment-hierarchy-style"
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("comment-thread-colours")) item("comment-thread-colours") {
                    ValueSettingRow(
                        icon = Symbols.Forum,
                        title = stringResource(R.string.set_thread_colours),
                        // ISSUE285_THREAD_COLOURS_ONLY_WITH_GUIDE_LINES_V1: the colours belong to Guide lines;
                        // with Parent connectors the row says so and does nothing, as Android's settings do.
                        description = if (state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES) {
                            stringResource(R.string.set_thread_colours_description)
                        } else {
                            stringResource(R.string.all_needs_the_guide_lines_comment_hierarchy)
                        },
                        value = if (state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES) {
                            stringResource(state.commentThreadColours.labelRes)
                        } else {
                            stringResource(CommentThreadColours.OFF.labelRes)
                        },
                        enabled = state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES,
                        onClick = {
                            choiceDialogKey = "comment-thread-colours"
                        },
                    )
                }
                // REPLYCONTEXT03_INDENT_SIZE_IS_A_SETTING_V1
                if (searchResult.isItemVisible("comment-indent-size")) item("comment-indent-size") {
                    ValueSettingRow(
                        icon = Symbols.Forum,
                        title = stringResource(R.string.set_reply_indent),
                        description = if (state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES) {
                            stringResource(R.string.all_how_far_each_reply_level_steps_to)
                        } else {
                            stringResource(R.string.all_needs_the_guide_lines_comment_hierarchy)
                        },
                        value = stringResource(state.commentIndentSize.labelRes),
                        enabled = state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES,
                        onClick = {
                            choiceDialogKey = "comment-indent-size"
                        },
                    )
                }
                // REPLYCONTEXT04_RAIL_FROM_PARENT_IS_A_SETTING_V1
                if (searchResult.isItemVisible("comment-rail-from-parent")) item("comment-rail-from-parent") {
                    ToggleRow(
                        icon = Symbols.Forum,
                        title = stringResource(R.string.set_reply_lines_start_in_the_parent),
                        description = if (state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES) {
                            stringResource(R.string.all_the_line_a_reply_starts_leaves_the)
                        } else {
                            stringResource(R.string.all_needs_the_guide_lines_comment_hierarchy)
                        },
                        checked = state.commentRailFromParent,
                        onCheckedChange = state::updateCommentRailFromParent,
                        enabled = state.commentHierarchyStyle == CommentHierarchyStyle.GUIDE_LINES,
                    )
                }
                if (searchResult.isItemVisible("highlight-new-comments")) item("highlight-new-comments") {
                    ToggleRow(
                        icon = Symbols.Forum,
                        title = stringResource(R.string.set_highlight_new_comments),
                        description = stringResource(R.string.set_mark_replies_added_since_your_previous_visit),
                        checked = state.highlightNewComments,
                        onCheckedChange = state::updateHighlightNewComments,
                    )
                }
                // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
                if (searchResult.isItemVisible("show-comment-avatars")) item("show-comment-avatars") {
                    ToggleRow(
                        icon = Symbols.AccountCircle,
                        title = stringResource(R.string.set_show_author_avatars),
                        description = stringResource(R.string.set_show_each_commenter_s_reddit_avatar_instead),
                        checked = state.showCommentAvatars,
                        onCheckedChange = state::updateShowCommentAvatars,
                    )
                }
                if (searchResult.isItemVisible("top-level-comment-jump")) item("top-level-comment-jump") {
                    ToggleRow(
                        icon = Symbols.KeyboardArrowDown,
                        title = stringResource(R.string.set_top_level_comment_jump_button),
                        description = stringResource(R.string.set_tap_to_jump_to_the_next_top),
                        checked = state.topLevelCommentJumpButton,
                        onCheckedChange = state::updateTopLevelCommentJumpButton,
                    )
                }
                if (searchResult.isItemVisible("comment-actions")) item("comment-actions") {
                    ValueSettingRow(
                        icon = Symbols.Forum,
                        title = stringResource(R.string.set_action_placement),
                        description = stringResource(R.string.set_place_comment_actions_on_the_preferred_side),
                        value = if (state.commentActionPreference == CommentActionPreference.FOLLOW) {
                            state.interactionSide.followLabel().asString()
                        } else {
                            stringResource(state.commentActionPreference.labelRes)
                        },
                        onClick = { choiceDialogKey = "comment-actions" },
                    )
                }
                if (searchResult.isItemVisible("comment-time-format")) item("comment-time-format") {
                    ValueSettingRow(
                        icon = Symbols.Schedule,
                        title = stringResource(R.string.set_comment_time),
                        description = stringResource(R.string.set_relative_age_or_a_compact_local_timestamp),
                        value = stringResource(state.commentTimeFormat.labelRes),
                        onClick = { choiceDialogKey = "comment-time-format" },
                    )
                }
                if (searchResult.isItemVisible("comment-collapse-mode")) {
                    item("comment-collapse-mode") {
                        SettingsSegmentedChoice(
                            icon = Symbols.TouchApp,
                            title = stringResource(R.string.set_collapse_comments),
                            description = stringResource(R.string.set_choose_the_tap_target_used_to_collapse),
                            options = CommentCollapseMode.entries,
                            selected = state.commentCollapseMode,
                            label = { stringResource(it.labelRes) },
                            onSelect = state::updateCommentCollapseMode,
                        )
                    }
                }
                // ISSUE284_POST_TEXT_COLLAPSES_LIKE_A_COMMENT_V1: off by default.
                if (searchResult.isItemVisible("collapsible-post-text")) item("collapsible-post-text") {
                    ToggleRow(
                        icon = Symbols.TouchApp,
                        title = stringResource(R.string.set_collapse_post_text),
                        description = stringResource(R.string.set_tap_a_post_s_title_to_hide),
                        checked = state.collapsiblePostText,
                        onCheckedChange = state::updateCollapsiblePostText,
                    )
                }
                if (
                    destination == SettingsDestination.COMMENTS &&
                    query.isBlank() &&
                    searchResult.isItemVisible("use-suggested-comment-sort")
                ) {
                    item("comment-behaviour-title") { SettingsSection(stringResource(R.string.all_behaviour)) }
                }
                if (searchResult.isItemVisible("use-suggested-comment-sort")) item("use-suggested-comment-sort") {
                    ToggleRow(
                        icon = Symbols.Sort,
                        title = stringResource(R.string.set_follow_suggested_comment_sort),
                        description = stringResource(R.string.set_use_reddit_s_suggested_order_when_opening),
                        checked = state.useSuggestedCommentSort,
                        onCheckedChange = state::updateUseSuggestedCommentSort,
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Notifications",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                // NOTIFY01A1R2_NOTIFICATION_TYPES_SUBSCREEN_V1
                val notificationCategoryVisible =
                    searchResult.isItemVisible("notification-types") ||
                        searchResult.isItemVisible("notify-replies") ||
                        searchResult.isItemVisible("notify-mentions") ||
                        searchResult.isItemVisible("notify-private-messages") ||
                        searchResult.isItemVisible("notify-reddit-notices") ||
                        searchResult.isItemVisible("notify-modmail")
                val notificationMasterVisible =
                    searchResult.isItemVisible("notifications") ||
                        notificationCategoryVisible
                val notificationTypeCount = listOf(
                    state.notifyReplies,
                    state.notifyMentions,
                    state.notifyPrivateMessages,
                    state.notifyRedditNotices,
                    state.notifyModmail,
                ).count { it }
                val notificationTypeSummary = when {
                    !state.inboxNotificationsEnabled &&
                        notificationTypeCount == 0 ->
                        UiText.Resource(R.string.set_notifications_off_none_selected)

                    !state.inboxNotificationsEnabled ->
                        UiText.Resource(R.string.set_notifications_off_n_selected, notificationTypeCount)

                    notificationTypeCount == 0 ->
                        UiText.Resource(R.string.set_no_notification_types)

                    else ->
                        UiText.Plural(R.plurals.set_notification_types_enabled, notificationTypeCount, notificationTypeCount)
                }

                item("notifications-title") {
                    SettingsSection(
                        stringResource(settingsSectionTitle(destination, R.string.all_notifications)),
                    )
                }

                if (notificationMasterVisible) {
                    item("notifications") {
                        // ISSUE219_INBOX_NOTIFICATIONS_ROW_IS_THE_SWITCH_V1: as ToggleRow (LOWUI7).
                        SettingsSwitchItem(
                            icon = Symbols.Notifications,
                            title = stringResource(R.string.set_inbox_notifications),
                            description = stringResource(R.string.set_check_reddit_every_15_minutes_for_new),
                            checked = state.inboxNotificationsEnabled,
                            onCheckedChange = ::setInboxNotificationsEnabled,
                            enabled = state.activeAccountIsAuthenticated,
                        )
                    }
                }

                // ISSUE239_WATCHED_COMMUNITIES_IN_SETTINGS_V1
                if (searchResult.isItemVisible("new-post-notifications")) {
                    item("new-post-notifications") {
                        NavigationRow(
                            icon = Symbols.NotificationsActive,
                            title = stringResource(R.string.set_new_posts),
                            description = newPostNotificationsSummary(state.watchedCommunityNames.size),
                            onClick = {
                                openSettingsDestination(
                                    SettingsDestination.NEW_POST_NOTIFICATIONS,
                                )
                            },
                        )
                    }
                }

                if (notificationCategoryVisible) {
                    item("notification-types") {
                        NavigationRow(
                            icon = Symbols.Tune,
                            title = stringResource(R.string.set_notification_types),
                            description = notificationTypeSummary.asString(),
                            onClick = {
                                openSettingsDestination(
                                    SettingsDestination.NOTIFICATION_TYPES,
                                )
                            },
                        )
                    }
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Reddit connection",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("reddit-connection-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_reddit_connection))) }
                if (searchResult.isItemVisible("redreader-compatibility")) item("redreader-compatibility") {
                    NavigationRow(
                        icon = Symbols.VpnKey,
                        title = stringResource(R.string.set_redreader_compatibility),
                        description = redReaderOperationMessage?.asString() ?: if (redReaderCompatibilityConfigured) {
                            stringResource(R.string.all_configured_locally_changes_apply_automatically)
                        } else {
                            stringResource(R.string.set_add_oauth_client_id)
                        },
                        onClick = {
                            redReaderClientIdState.clearText()
                            redReaderOperationMessage = null
                            redditApiValues = null
                            redditApiValuesDraft = null
                            redReaderConfigurationDialogVisible = true
                        },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Diagnostics",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("diagnostics-title") {
                    SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_diagnostics)))
                }
                if (searchResult.isItemVisible("diagnostics")) item("diagnostics") {
                    NavigationRow(
                        icon = Symbols.Info,
                        title = stringResource(R.string.set_diagnostics),
                        description = stringResource(R.string.set_inspect_local_app_events_and_create_a),
                        onClick = {
                            openSettingsDestination(SettingsDestination.DIAGNOSTICS)
                        },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Backup & restore",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("backup-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_backup_restore))) }
                if (searchResult.isItemVisible("backup")) item("backup") {
                    NavigationRow(
                        icon = Symbols.Backup,
                        // ISSUE270_BACKUP_SCREEN_SAYS_WHAT_IS_BACKED_UP_V1: one word, backup.
                        title = stringResource(R.string.set_backups),
                        description = when (backupRepository.status().protection) {
                            BackupProtection.PROTECTED -> stringResource(R.string.all_copied_to_your_backup_folder)
                            BackupProtection.WAITING -> stringResource(R.string.all_backup_folder_isn_t_reachable)
                            BackupProtection.INTERNAL_ONLY -> stringResource(R.string.all_only_on_this_device)
                        },
                        onClick = {
                            openSettingsDestination(
                                target = SettingsDestination.BACKUP,
                                returnTo = SettingsDestination.ROOT,
                            )
                        },
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "About",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("about-title") { SettingsSection(stringResource(settingsSectionTitle(destination, R.string.all_about))) }
                if (searchResult.isItemVisible("updates")) item("updates") {
                    NavigationRow(
                        icon = Symbols.Download,
                        title = stringResource(R.string.set_updates),
                        description = SettingsDestination.UPDATES.descriptionRes?.let { stringResource(it) } ?: "",
                        onClick = {
                            openSettingsDestination(
                                target = SettingsDestination.UPDATES,
                                returnTo = SettingsDestination.ROOT,
                            )
                        },
                    )
                }
                if (searchResult.isItemVisible("about")) item("about") {
                    NavigationRow(
                        icon = Symbols.Info,
                        title = stringResource(R.string.set_help_about),
                        description = BuildConfig.VERSION_NAME,
                        onClick = {
                            openSettingsDestination(
                                target = SettingsDestination.ABOUT,
                                returnTo = SettingsDestination.ROOT,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun settingsDestinationIcon(destination: SettingsDestination): ImageVector =
    when (destination) {
        SettingsDestination.ACCOUNTS -> Symbols.AccountCircle
        SettingsDestination.APPEARANCE -> Symbols.Palette
        SettingsDestination.NAVIGATION -> Symbols.Swipe
        SettingsDestination.FEEDS -> Symbols.Home
        SettingsDestination.MEDIA -> Symbols.Image
        SettingsDestination.COMMENTS -> Symbols.Forum
        SettingsDestination.BACKUP -> Symbols.Backup
        SettingsDestination.ADVANCED -> Symbols.Tune
        SettingsDestination.UPDATES -> Symbols.Download
        SettingsDestination.ABOUT -> Symbols.Info
        else -> Symbols.Tune
    }

@Composable
private fun SettingsDestinationRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1: the open area is the selected row of
    // the list (list + detail on wide windows).
    SettingsListItem(
        title = title,
        description = description,
        leading = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        trailing = {
            Icon(Symbols.ArrowForward, contentDescription = null)
        },
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .settingsRowBleed()
            .selectable(selected = selected, onClick = onClick),
    )
}

@Composable
private fun SettingsSection(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private fun dynamicColorSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    // LOWUI7_SWITCH_ROWS_ARE_LABELLED_V1 (OCB-276): the whole row is the switch,
    // so TalkBack reads the title and description with its state, and the row
    // (not only the 48 dp switch) toggles.
    SettingsSwitchItem(
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
        icon = icon,
        enabled = enabled,
    )
}

@Composable
private fun ValueSettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    SettingsValueItem(
        title = title,
        description = description,
        value = value,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
    )
}

@Composable
private fun SettingsCodeFontDialog(
    selected: CodeFont,
    onSelect: (CodeFont) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_code_font)) },
        text = {
            // ISSUE219_CODE_FONT_DIALOG_IS_ONE_RADIO_GROUP_V1: one selectable row per
            // font (as SettingsSingleChoiceDialog, OCB-277), not row + radio.
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CodeFont.entries.forEach { font ->
                    Surface(
                        selected = font == selected,
                        onClick = { onSelect(font) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.RadioButton },
                        shape = MaterialTheme.shapes.small,
                        color = if (font == selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = font == selected,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = font.typefaceName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = font.fontFamily,
                                    ),
                                )
                                Text(
                                    text = stringResource(font.labelRes),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = font.fontFamily,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.set_cancel)) }
        },
    )
}

internal fun languageDisplayName(code: String): String =
    java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH)
        .ifBlank { code }

/** ISSUE293_LANGUAGE_FILTER_V1: "Off", or the chosen languages by name. */
internal fun shownLanguagesSummary(languages: Set<String>): String {
    if (languages.isEmpty()) return "Off"
    val names = SELECTABLE_LANGUAGES.filter { it in languages }.map(::languageDisplayName)
    return if (names.size <= 3) names.joinToString(", ") else names.take(2).joinToString(", ") + " +${names.size - 2}"
}

@Composable
private fun ShownLanguagesDialog(
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(selected.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_languages)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.all2_show_only_these_languages_communities_and_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SELECTABLE_LANGUAGES.forEach { code ->
                    val checked = code in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { on -> chosen = if (on) chosen + code else chosen - code },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(languageDisplayName(code), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(chosen.toSet()) }) { Text(stringResource(R.string.set_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.set_cancel)) }
        },
    )
}

@Composable
private fun <T> SettingsSingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // LOWUI7_SINGLE_CHOICE_ROWS_ARE_RADIO_BUTTONS_V1 (OCB-277): each row
            // tells TalkBack it is a radio button and whether it is selected.
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            ),
                        shape = MaterialTheme.shapes.medium,
                        color = if (option == selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == selected,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.set_cancel))
            }
        },
    )
}

internal const val ACCOUNT_ROW_ARROW_TAG = "account-row-arrow"
internal const val ACCOUNT_ROW_CURRENT_TAG = "account-row-current"
internal fun accountRowTag(username: String) = "account-row:$username"

// ISSUE280_ACCOUNT_ROWS_ARE_A_PICKER_V1 (#280, reader report): the account
// rows pick an account, they lead nowhere. No arrow; the current account has a
// check mark and does nothing on a tap, another account switches. Only a row
// that needs signing in again opens something, so only it keeps the arrow.
@Composable
private fun AccountNavigationRow(
    username: String,
    avatarImageUri: String?,
    description: String,
    selected: Boolean,
    signInAgain: Boolean,
    onClick: () -> Unit,
) {
    val acts = signInAgain || !selected
    SettingsListItem(
        title = "u/$username",
        description = description,
        modifier = Modifier
            .testTag(accountRowTag(username))
            .settingsRowBleed()
            .then(if (acts) Modifier.clickable(onClick = onClick) else Modifier),
        trailing = when {
            signInAgain -> {
                { Icon(Symbols.ArrowForward, contentDescription = null, modifier = Modifier.testTag(ACCOUNT_ROW_ARROW_TAG)) }
            }
            selected -> {
                { Icon(Symbols.Check, contentDescription = null, modifier = Modifier.testTag(ACCOUNT_ROW_CURRENT_TAG)) }
            }
            else -> null
        },
        leading = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                io.github.brealorg.subshell.ui.components.RemoteIdentityImage(
                    uri = avatarImageUri,
                    contentDescription = stringResource(R.string.set_avatar_for_u, username),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    io.github.brealorg.subshell.ui.components.RedditDefaultProfileAvatar(
                        seed = username,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    )
}

@Composable
private fun DestructiveNavigationRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    SettingsNavigationItem(
        title = title,
        description = description,
        onClick = onClick,
        icon = icon,
        destructive = true,
    )
}

// REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1: what is exported, then how the
// newest export of this account went; a tap while one runs does nothing.
// REDDITEXPORT01_EXPORT_YOUR_REDDIT_DATA_V1: the tray shows it whole, without More.
internal val REDDIT_EXPORT_NOTICE_TITLE: UiText = UiText.Resource(R.string.export_notification_running)
internal val REDDIT_EXPORT_NOTICE_MESSAGE: UiText = UiText.Resource(R.string.export_notice_message)

@Composable
internal fun RedditDataExportRow(
    status: RedditDataExportStatus?,
    onExport: () -> Unit,
) {
    NavigationRow(
        icon = Symbols.Download,
        title = stringResource(R.string.set_export_your_reddit_data),
        description = when (status) {
            null -> stringResource(R.string.all_saved_votes_your_posts_and_comments_communities)
            RedditDataExportStatus.Waiting -> stringResource(R.string.all_waiting_for_a_connection)
            is RedditDataExportStatus.Running -> stringResource(R.string.export_running_row, status.done, status.total)
            is RedditDataExportStatus.Saved -> exportSavedText(status.location, status.items, status.incompleteLists).asString()
            is RedditDataExportStatus.Failed -> stringResource(status.reason.textRes)
        },
        onClick = {
            if (status != RedditDataExportStatus.Waiting && status !is RedditDataExportStatus.Running) onExport()
        },
    )
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    SettingsNavigationItem(
        title = title,
        description = description,
        onClick = onClick,
        icon = icon,
    )
}

@Composable
private fun SettingSurface(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    SettingsListItem(
        title = title,
        description = description,
        icon = icon,
        trailing = trailing,
        modifier = modifier.settingsRowBleed(),
    )
}

private const val VIDEO_CACHE_LIMIT_NOTICE_SCOPE = "video-cache-limit"
private const val INBOX_NOTIFICATIONS_NOTICE_SCOPE = "inbox-notifications"

internal fun inboxNotificationsNotTurnedOnNotice(): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.ep_notifications_are_still_off),
    message = UiText.Resource(R.string.ep_subshell_couldn_t_save_the_notification_state),
)

internal fun videoCacheLimitRestartNotice(limit: VideoMediaCacheLimit): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = UiText.Resource(R.string.video_cache_restart_notice_title),
    message = UiText.Resource(R.string.video_cache_restart_notice_message, UiText.Resource(limit.labelRes)),
)

// ISSUE115_TURNING_PROTECTION_OFF_NEEDS_UNLOCK_V1: someone handed the unlocked
// phone could turn a protection off with a tap and then see what it guards.
// Turning it off now takes the device unlock; a phone without a screen lock
// has nothing to confirm with (the app lock already opens then), and outside
// MainActivity (previews, tests) there is no unlock to ask.
internal fun turnOffAfterDeviceUnlock(
    appLock: AppLockEntryPoints?,
    title: String,
    subtitle: String,
    turnOff: () -> Unit,
) {
    // TRANSLATE14: callers pass resolved text (stringResource where they compose).
    if (appLock == null || !appLock.deviceUnlockAvailable) {
        turnOff()
        return
    }
    appLock.onRequestUnlockFor(title, subtitle) { unlocked ->
        if (unlocked) turnOff()
    }
}

/** M3224_SETTINGS_LIST_DETAIL_V1: which pane of a wide-window Settings this is. */
enum class SettingsPaneRole { SINGLE, LIST, DETAIL }

/** ISSUE265_SAVE_FOR_OFFLINE_V1: how much the offline images take, and Clear. */
@Composable
private fun OfflineImagesRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = remember(context) { io.github.brealorg.subshell.offline.OfflineImageStore.forContext(context) }
    var bytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(store) {
        bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.sizeBytes() }
    }
    SettingSurface(
        icon = Symbols.Storage,
        title = stringResource(R.string.set_saved_for_offline),
        description = when (val used = bytes) {
            null -> stringResource(R.string.all_checking_saved_images)
            0L -> stringResource(R.string.all_no_images_saved_use_save_for_offline)
            else -> stringResource(R.string.set_offline_images_saved, android.text.format.Formatter.formatShortFileSize(context, used))
        },
    ) {
        TextButton(
            enabled = (bytes ?: 0L) > 0L,
            onClick = {
                scope.launch {
                    bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        store.clear()
                        store.sizeBytes()
                    }
                }
            },
        ) {
            Text(stringResource(R.string.set_clear))
        }
    }
}
