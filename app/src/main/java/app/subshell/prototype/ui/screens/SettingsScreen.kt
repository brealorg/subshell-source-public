package app.subshell.prototype.ui.screens

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.subshell.prototype.share.ShareIntoSubshellComponent
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.error.TrayTone
import app.subshell.prototype.ui.theme.AppTextScale
import androidx.compose.material.icons.rounded.FormatSize
import app.subshell.prototype.inbox.InboxNotificationCenter
import app.subshell.prototype.inbox.InboxBackgroundSyncScheduler
import app.subshell.prototype.inbox.InboxBackgroundEventStore
import androidx.core.content.ContextCompat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.subshell.prototype.backup.BackupProtection
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.media.LegacyVideoDownloadStatus
import app.subshell.prototype.media.LegacyVideoDownloads
import app.subshell.prototype.media.VideoMediaCacheStatus
import app.subshell.prototype.media.VideoMediaDataSource
import app.subshell.prototype.ui.error.ErrorPresentationKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.media.VideoMediaCacheLimitStore
import app.subshell.prototype.media.VideoMediaCacheLimit
import app.subshell.prototype.data.account.RedditFavoriteSyncSettingStore
import app.subshell.prototype.shortcuts.LauncherShortcutsEnabledStore
import app.subshell.prototype.shortcuts.rememberLauncherShortcutsEnabled
import app.subshell.prototype.ui.app.rememberRedditFavoriteSyncEnabled
import app.subshell.prototype.media.videoMediaCacheSummary
import app.subshell.prototype.ui.ChromeBehavior
import app.subshell.prototype.ui.CodeFont
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.ui.CommentActionPreference
import app.subshell.prototype.ui.CommentCollapseMode
import app.subshell.prototype.ui.CommentHierarchyStyle
import app.subshell.prototype.ui.CommentSwipeAction
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.CommentTimeFormat
import app.subshell.prototype.ui.CommunityDiscoveryLayout
import app.subshell.prototype.ui.selectableDrawerSidePreferences
import app.subshell.prototype.ui.followLabel
import app.subshell.prototype.ui.FeedPreviewState
import app.subshell.prototype.ui.InteractionSide
import app.subshell.prototype.ui.FullscreenCaptionMode
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.NavigationLabelMode
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.DrawerCommunitiesMode
import app.subshell.prototype.ui.SubredditLabelStyle
import app.subshell.prototype.ui.AccountConnectAction
import app.subshell.prototype.ui.accountConnectPresentation
import app.subshell.prototype.ui.accountSwitcherSessions
import app.subshell.prototype.ui.accountSwitcherSessionLabel
import app.subshell.prototype.ui.TabletLayoutMode
import app.subshell.prototype.ui.RedditLinkHandlingState
import app.subshell.prototype.ui.RedditLinkHandlingStatus
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VideoAutoplay
import app.subshell.prototype.ui.VideoQuality
import app.subshell.prototype.ui.VideoTimelineSize
import app.subshell.prototype.ui.PlaybackOptionsPlacement
import app.subshell.prototype.ui.VideoAudioDefault
import app.subshell.prototype.ui.VideoSubtitleDefault
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.contentFilterSummary
import app.subshell.prototype.ui.legacyVideoDownloadSummary
import app.subshell.prototype.ui.fontFamily
import app.subshell.prototype.ui.formatVideoDownloadBytes
import app.subshell.prototype.ui.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.rounded.Lock
import app.subshell.prototype.applock.AppLockDelayDialog
import app.subshell.prototype.applock.AppLockEntryPoints
import app.subshell.prototype.applock.LocalAppLock
import app.subshell.prototype.applock.appLockDelayLabel
import androidx.compose.material.icons.rounded.Link
import app.subshell.prototype.ui.components.MutedCommunityDirectViewSetting
import app.subshell.prototype.ui.components.AccountSettingsRuntime
import app.subshell.prototype.ui.components.OnlineVisibilitySettingRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import app.subshell.prototype.ui.components.SettingsListItem
import app.subshell.prototype.ui.components.SettingsNavigationItem
import app.subshell.prototype.ui.components.SettingsSegmentedChoice
import app.subshell.prototype.ui.components.SettingsSwitchItem
import app.subshell.prototype.ui.components.SettingsValueItem
import app.subshell.prototype.ui.components.settingsRowBleed
import app.subshell.prototype.ui.components.SubshellSearchField

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
    onRequestInboxNotificationPermission: ((Boolean) -> Unit) -> Unit =
        { onResult -> onResult(false) },
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
    var cacheOperationMessage by remember { mutableStateOf<String?>(null) }
    var downloadDeleteConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var downloadDeleteInProgress by remember { mutableStateOf(false) }
    var downloadOperationMessage by remember { mutableStateOf<String?>(null) }
    var redReaderConfigurationDialogVisible by remember { mutableStateOf(false) }
    var redReaderClientIdDraft by remember { mutableStateOf("") }
    var redReaderCompatibilityConfigured by remember { mutableStateOf(false) }
    var redReaderConfigurationBusy by remember { mutableStateOf(false) }
    var redReaderOperationMessage by remember { mutableStateOf<String?>(null) }
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

    fun setInboxNotificationsEnabled(enabled: Boolean) {
        if (!enabled) {
            state.updateInboxNotificationsEnabled(false)
            InboxNotificationCenter.cancelPosted(appContext)
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
            onRequestInboxNotificationPermission { granted ->
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
    BackHandler(enabled = backupScreenVisible, onBack = returnToSettings)
    BackHandler(
        enabled = destination != SettingsDestination.ROOT &&
            destination != SettingsDestination.BACKUP,
        onBack = returnToSettings,
    )

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
            modifier = modifier,
        )
        return
    }

    if (destination == SettingsDestination.NOTIFICATION_TYPES) {
        // NOTIFY01A1R2_NOTIFICATION_TYPES_CHILD_ROUTE_V1
        NotificationTypesSettingsScreen(
            state = state,
            onBack = returnToSettings,
            modifier = modifier,
        )
        return
    }

    if (destination == SettingsDestination.CONTENT_FILTERS) {
        ContentFiltersScreen(
            filters = state.contentFilters,
            onAdd = state::addContentFilter,
            onRemove = state::removeContentFilter,
            onBack = returnToSettings,
            modifier = modifier,
        )
        return
    }

    if (destination == SettingsDestination.SENSITIVE_MEDIA_LAB) {
        SensitiveMediaLabScreen(
            state = state,
            onBack = returnToSettings,
            modifier = modifier,
        )
        return
    }

    if (destination == SettingsDestination.DIAGNOSTICS) {
        DiagnosticsScreen(
            onBack = returnToSettings,
            modifier = modifier,
        )
        return
    }

    if (destination == SettingsDestination.ABOUT) {
        HelpScreen(
            onBack = returnToSettings,
            modifier = modifier,
        )
        return
    }

    // The search lives in the list pane; the chosen area is shown whole.
    val query = if (paneRole == SettingsPaneRole.DETAIL) "" else state.settingsQuery.trim()
    // ISSUE38_APP_LOCK_V1
    val appLock = LocalAppLock.current
    // LOWUX3_SETTINGS_SEARCH_SKIPS_ROWS_THE_STATE_HIDES_V1 (OCB-267/278): rows the
    // state hides right now are not search hits.
    val searchHiddenKeys = buildSet {
        if (appLock == null) add("app-lock")
        if (appLock == null || !appLock.settings.enabled) {
            add("app-lock-delay")
            add("app-lock-notifications")
        }
        if (activeProductAccount == null) add("online-visibility")
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
            title = "Theme",
            options = ThemeChoice.entries,
            selected = state.themeChoice,
            label = ThemeChoice::label,
            onSelect = { choice ->
                state.updateThemeChoice(choice)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-text-size" -> SettingsSingleChoiceDialog(
            title = "Text size",
            options = AppTextScale.entries,
            selected = state.appTextScale,
            label = AppTextScale::label,
            onSelect = { scale ->
                state.updateAppTextScale(scale)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "video-cache-limit" -> SettingsSingleChoiceDialog(
            title = "Video cache size",
            options = VideoMediaCacheLimit.entries,
            selected = videoCacheLimit ?: VideoMediaCacheLimit.DEFAULT,
            label = VideoMediaCacheLimit::label,
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
            title = "Vote controls",
            options = VoteControlMode.entries,
            selected = state.voteControlMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateVoteControlMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "appearance-vote-colours" -> SettingsSingleChoiceDialog(
            title = "Vote colours",
            options = VoteColorPalette.entries,
            selected = state.voteColorPalette,
            label = { it.label },
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
            title = "Sensitive media previews",
            options = SensitiveMediaPreviewMode.entries,
            selected = state.sensitiveMediaPreviewMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateSensitiveMediaPreviewMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-autoplay" -> SettingsSingleChoiceDialog(
            title = "Autoplay videos and GIFs",
            options = VideoAutoplay.entries,
            selected = state.videoAutoplay,
            label = { it.label },
            onSelect = { autoplay ->
                state.updateVideoAutoplay(autoplay)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-quality-wifi" -> SettingsSingleChoiceDialog(
            title = "Video quality on Wi-Fi",
            options = VideoQuality.entries,
            selected = state.wifiVideoQuality,
            label = { it.label },
            onSelect = { quality ->
                state.updateWifiVideoQuality(quality)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-quality-mobile" -> SettingsSingleChoiceDialog(
            title = "Video quality on mobile data",
            options = VideoQuality.entries,
            selected = state.mobileVideoQuality,
            label = { it.label },
            onSelect = { quality ->
                state.updateMobileVideoQuality(quality)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-subtitle-default" -> SettingsSingleChoiceDialog(
            title = "Subtitles",
            options = VideoSubtitleDefault.entries,
            selected = state.videoSubtitleDefault,
            label = { it.label },
            onSelect = { value ->
                state.updateVideoSubtitleDefault(value)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-audio-default" -> SettingsSingleChoiceDialog(
            title = "Audio language",
            options = VideoAudioDefault.entries,
            selected = state.videoAudioDefault,
            label = { it.label },
            onSelect = { value ->
                state.updateVideoAudioDefault(value)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-playback-options" -> SettingsSingleChoiceDialog(
            title = "Playback options button",
            options = PlaybackOptionsPlacement.entries,
            selected = state.playbackOptionsPlacement,
            label = { it.label },
            onSelect = { placement ->
                state.updatePlaybackOptionsPlacement(placement)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-timeline-size" -> SettingsSingleChoiceDialog(
            title = "Video timeline size",
            options = VideoTimelineSize.entries,
            selected = state.videoTimelineSize,
            label = { it.label },
            onSelect = { size ->
                state.updateVideoTimelineSize(size)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        // M3225_LONG_SINGLE_CHOICE_IS_A_VALUE_ROW_V1 (#225, owner): long or many
        // options are a value row with this radio dialog, not chips.
        "random-nsfw-community" -> SettingsSingleChoiceDialog(
            title = "Random NSFW community",
            options = app.subshell.prototype.ui.RandomNsfwCommunityPlacement.entries,
            selected = state.randomNsfwCommunityPlacement,
            label = { it.label },
            onSelect = { placement ->
                state.updateRandomNsfwCommunityPlacement(placement)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "feed-state" -> SettingsSingleChoiceDialog(
            title = "Feed preview",
            options = FeedPreviewState.entries,
            selected = state.feedPreviewState,
            label = { it.label },
            onSelect = { preview ->
                state.updateFeedPreviewState(preview)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "media-captions" -> SettingsSingleChoiceDialog(
            title = "Gallery captions",
            options = MediaCaptionMode.entries,
            selected = state.mediaCaptionMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateMediaCaptionMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "fullscreen-captions" -> SettingsSingleChoiceDialog(
            title = "Fullscreen gallery captions",
            options = FullscreenCaptionMode.entries,
            selected = state.fullscreenCaptionMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateFullscreenCaptionMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-hierarchy-style" -> SettingsSingleChoiceDialog(
            title = "Comment hierarchy",
            options = CommentHierarchyStyle.entries,
            selected = state.commentHierarchyStyle,
            label = CommentHierarchyStyle::label,
            onSelect = { style ->
                state.updateCommentHierarchyStyle(style)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-actions" -> SettingsSingleChoiceDialog(
            title = "Action placement",
            options = CommentActionPreference.entries,
            selected = state.commentActionPreference,
            label = { preference ->
                if (preference == CommentActionPreference.FOLLOW) {
                    state.interactionSide.followLabel()
                } else {
                    preference.label
                }
            },
            onSelect = { preference ->
                state.updateCommentActionPreference(preference)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "comment-time-format" -> SettingsSingleChoiceDialog(
            title = "Comment time",
            options = CommentTimeFormat.entries,
            selected = state.commentTimeFormat,
            label = { format -> "${format.label} · ${format.example}" },
            onSelect = { format ->
                state.updateCommentTimeFormat(format)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "top-bar-behavior" -> SettingsSingleChoiceDialog(
            title = "Top bar",
            options = ChromeBehavior.entries,
            selected = state.topBarBehavior,
            label = { it.label },
            onSelect = { behavior ->
                state.updateTopBarBehavior(behavior)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "bottom-navigation-behavior" -> SettingsSingleChoiceDialog(
            title = "Bottom navigation",
            options = ChromeBehavior.entries,
            selected = state.bottomBarBehavior,
            label = { it.label },
            onSelect = { behavior ->
                state.updateBottomBarBehavior(behavior)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "drawer-communities" -> SettingsSingleChoiceDialog(
            title = "Communities in drawer",
            options = DrawerCommunitiesMode.entries,
            selected = state.drawerCommunitiesMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateDrawerCommunitiesMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "navigation-labels" -> SettingsSingleChoiceDialog(
            title = "Navigation labels",
            options = NavigationLabelMode.entries,
            selected = state.navigationLabelMode,
            label = { it.label },
            onSelect = { mode ->
                state.updateNavigationLabelMode(mode)
                choiceDialogKey = null
            },
            onDismiss = { choiceDialogKey = null },
        )
        "subreddit-labels" -> SettingsSingleChoiceDialog(
            title = "Communities labels",
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
            title = { Text("Log out of u/${activeProductAccount.username}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This removes this Reddit account from subshell on this device. " +
                            "You can connect it again later.",
                    )
                    // LOWUX1_FAILED_LOGOUT_IS_SAID_V1 (OCB-268): the dialog only
                    // re-enabled after a failed logout.
                    if (accountLogoutFailed) {
                        Text(
                            "Couldn't log out of u/${activeProductAccount.username} on this device. Try again.",
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
                    Text("Log out", color = MaterialTheme.colorScheme.error)
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
                    Text("Cancel")
                }
            },
        )
    }

    if (redReaderConfigurationDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!redReaderConfigurationBusy) {
                    redReaderConfigurationDialogVisible = false
                    redReaderClientIdDraft = ""
                }
            },
            title = { Text("RedReader compatibility") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This explicit compatibility mode uses a locally supplied OAuth client ID. " +
                            "The value is encrypted in no-backup storage and is never added to saved setups.",
                    )
                    OutlinedTextField(
                        value = redReaderClientIdDraft,
                        onValueChange = { value ->
                            redReaderClientIdDraft = value
                            redReaderOperationMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !redReaderConfigurationBusy,
                        singleLine = true,
                        label = { Text("OAuth client ID") },
                        visualTransformation = PasswordVisualTransformation(),
                        // LOWUI3_CLIENT_ID_KEYBOARD_LEARNS_NOTHING_V1 (OCB-269): a password
                        // field, so the keyboard neither suggests nor learns the client ID.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    // LOWUX1_CLIENT_CONFIG_FAILURE_SHOWN_IN_THE_DIALOG_V1 (OCB-270): a
                    // failed save/remove was written only to the row behind the dialog.
                    redReaderOperationMessage?.takeIf { message -> message.startsWith("Couldn") }?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Changes are applied automatically. " +
                            "If RedReader is still installed, Android may ask which app should open the callback; choose subshell.",
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
                                cacheScope.launch(NonCancellable) {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            redditCompatibilityConfiguration.remove()
                                        }
                                    }
                                    redReaderConfigurationBusy = false
                                    result.onSuccess {
                                        redReaderCompatibilityConfigured = false
                                        redReaderConfigurationDialogVisible = false
                                        redReaderClientIdDraft = ""
                                        redReaderOperationMessage =
                                            "Reddit connection configuration removed"
                                        onRedditConfigurationChanged()
                                    }.onFailure {
                                        redReaderOperationMessage = "Couldn’t remove compatibility configuration"
                                    }
                                }
                            },
                        ) {
                            Text("Use native configuration")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !redReaderConfigurationBusy &&
                        redditCompatibilityConfiguration.isValidClientId(redReaderClientIdDraft),
                    onClick = {
                        if (!redditCompatibilityConfiguration.isValidClientId(redReaderClientIdDraft)) {
                            return@TextButton
                        }
                        redReaderConfigurationBusy = true
                        // LOWUI6_CLIENT_CONFIG_CHANGE_FINISHES_V1 (OCB-324)
                        cacheScope.launch(NonCancellable) {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    redditCompatibilityConfiguration.save(redReaderClientIdDraft)
                                }
                            }
                            redReaderConfigurationBusy = false
                            result.onSuccess {
                                redReaderCompatibilityConfigured = true
                                redReaderConfigurationDialogVisible = false
                                redReaderClientIdDraft = ""
                                redReaderOperationMessage =
                                    "Reddit connection configuration saved"
                                onRedditConfigurationChanged()
                            }.onFailure {
                                redReaderOperationMessage = "Couldn’t save compatibility configuration"
                            }
                        }
                    },
                ) {
                    Text(if (redReaderCompatibilityConfigured) "Replace" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !redReaderConfigurationBusy,
                    onClick = {
                        redReaderConfigurationDialogVisible = false
                        redReaderClientIdDraft = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (cacheClearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { cacheClearConfirmationVisible = false },
            title = { Text("Clear media cache?") },
            text = {
                Text(
                    "Cached media will be removed. Settings and saved setups are not affected. " +
                        "Media can be streamed and cached again when needed.",
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
                                    if (clearResult.after.bytesUsed == 0L) "Cache cleared"
                                    else "Cache cleared; active media cached data again"
                            }.onFailure {
                                mediaCacheReadFailed = true
                                cacheOperationMessage = "Couldn’t clear cache"
                            }
                            cacheClearInProgress = false
                        }
                    },
                    // ISSUE222_DESTRUCTIVE_CONFIRMS_ARE_RED_V1: as Log out and the composers.
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { cacheClearConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (downloadDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { downloadDeleteConfirmationVisible = false },
            title = { Text("Delete all downloaded videos?") },
            text = {
                Text(
                    (legacyDownloadStatus?.let(::legacyVideoDownloadSummary)?.let { "$it. " }.orEmpty()) +
                        "Downloaded files and temporary files will be removed. " +
                        "The playback cache is separate and will not be cleared.",
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
                                downloadOperationMessage =
                                    "Deleted ${deleted.videosRemoved} videos · " +
                                        formatVideoDownloadBytes(deleted.bytesRemoved)
                            }.onFailure {
                                downloadOperationMessage = "Couldn’t delete downloads"
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
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadDeleteConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    val settingsListState = rememberLazyListState()

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text(destination.title) },
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                        placeholder = "Search settings",
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
                                "No settings found",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Try another word or clear the search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { state.settingsQuery = "" }) {
                                Text("Clear search")
                            }
                        }
                    }
                }
            }

            if (destination == SettingsDestination.ROOT && query.isBlank()) {
                item("settings-root-label") {
                    Text(
                        "Browse by area",
                        modifier = Modifier.padding(top = 5.dp, bottom = 1.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                settingsRootDestinations.forEach { target ->
                    item("settings-destination-${target.name}") {
                        SettingsDestinationRow(
                            icon = settingsDestinationIcon(target),
                            title = target.title,
                            description = target.description,
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
                    SettingsSection(settingsSectionTitle(destination, "Accounts"))
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
                            AccountNavigationRow(
                                username = account.username,
                                avatarImageUri = accountAvatarUri,
                                description = accountSwitcherSessionLabel(account, selected),
                                onClick = {
                                    if (!selected) state.switchAccount(account.stableId)
                                    if (
                                        account.state ==
                                        app.subshell.prototype.data.account.AccountSessionState
                                            .REAUTHENTICATION_REQUIRED
                                    ) {
                                        accountSettingsRuntime.onResetConnectAccount()
                                        accountSettingsRuntime.onReconnectAccount(account.stableId)
                                    }
                                },
                            )
                        }
                    }
                    item("account-add") {
                        NavigationRow(
                            icon = Icons.Rounded.Add,
                            title = if (
                                accountConnectPresentation.action == AccountConnectAction.RESET
                            ) {
                                "Add Reddit account"
                            } else {
                                accountConnectPresentation.headline
                            },
                            description = if (
                                accountConnectPresentation.action == AccountConnectAction.CONNECT ||
                                accountConnectPresentation.action == AccountConnectAction.RESET
                            ) {
                                "Sign in with another account"
                            } else {
                                accountConnectPresentation.supporting
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
                    if (activeProductAccount != null) {
                        item("account-logout") {
                            DestructiveNavigationRow(
                                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                                title = if (accountLogoutInProgress) {
                                    "Logging out…"
                                } else {
                                    "Log out"
                                },
                                description = "u/${activeProductAccount.username}",
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
                    SettingsSection(settingsSectionTitle(destination, "Account privacy"))
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
                            "Feed appearance"
                        } else {
                            settingsSectionTitle(destination, "Feed")
                        },
                    )
                }
                if (searchResult.isItemVisible("unread-marker")) item("unread-marker") {
                    ToggleRow(
                        icon = Icons.Rounded.Visibility,
                        title = "Show unread marker",
                        description = "Show a small dot after a post’s age until you open it",
                        checked = state.showUnreadMarker,
                        onCheckedChange = state::updateShowUnreadMarker,
                    )
                }
                if (searchResult.isItemVisible("show-nsfw-posts")) item("show-nsfw-posts") {
                    ToggleRow(
                        icon = Icons.Rounded.Visibility,
                        title = "Show NSFW posts",
                        description = "Show NSFW posts Reddit provides; also available in the navigation menu",
                        checked = state.showNsfwPosts,
                        onCheckedChange = state::updateShowNsfwPosts,
                    )
                }
                // ISSUE211_RANDOM_NSFW_COMMUNITY_V1: off by default, only while NSFW is shown.
                if (state.showNsfwPosts && searchResult.isItemVisible("random-nsfw-community")) item("random-nsfw-community") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Shuffle,
                        title = "Random NSFW community",
                        description = "Where a button opens a random NSFW community from Reddit's own listings",
                        value = state.randomNsfwCommunityPlacement.label,
                        onClick = { choiceDialogKey = "random-nsfw-community" },
                    )
                }
                if (
                    destination == SettingsDestination.FEEDS &&
                    query.isBlank() &&
                    searchResult.isItemVisible("content-filters")
                ) {
                    item("feed-filtering-title") { SettingsSection("Filtering") }
                }
                if (searchResult.isItemVisible("filtered-posts-summary")) item("filtered-posts-summary") {
                    // CONTENTFILTER04_FILTERED_SUMMARY_V1
                    ToggleRow(
                        icon = Icons.Rounded.FilterAlt,
                        title = "Show filtered post count",
                        description = "A row at the top of a feed says how many posts your filters hide, with a way to show them",
                        checked = state.filteredPostsSummary,
                        onCheckedChange = state::updateFilteredPostsSummary,
                    )
                }
                // ISSUE205_PASSIVE_SCROLL_POSITION_INDICATOR_V1: off by default.
                if (searchResult.isItemVisible("scroll-position-indicator")) item("scroll-position-indicator") {
                    ToggleRow(
                        icon = Icons.Rounded.UnfoldMore,
                        title = "Scroll position indicator",
                        description = "A thin bar on the edge of feeds and comment threads shows where you are while you scroll",
                        checked = state.scrollPositionIndicator,
                        onCheckedChange = state::updateScrollPositionIndicator,
                    )
                }
                if (searchResult.isItemVisible("content-filters")) item("content-filters") {
                    NavigationRow(
                        icon = Icons.Rounded.FilterAlt,
                        title = "Content filters",
                        description = contentFilterSummary(state.contentFilters),
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
                            reduceMotion = state.reduceMotion,
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
                item("appearance-title") { SettingsSection(settingsSectionTitle(destination, "Appearance")) }
                if (searchResult.isItemVisible("theme")) item("theme") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Palette,
                        title = "Theme",
                        description = "Follow the system, or use light, dark or pure black",
                        value = state.themeChoice.label,
                        onClick = { choiceDialogKey = "appearance-theme" },
                    )
                }
                if (searchResult.isItemVisible("dynamic")) item("dynamic") {
                    ToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = "Dynamic colour",
                        description = "Use the device palette instead of subshell orange",
                        checked = state.dynamicColor,
                        onCheckedChange = state::updateDynamicColor,
                    )
                }
                if (searchResult.isItemVisible("text-size")) item("text-size") {
                    ValueSettingRow(
                        icon = Icons.Rounded.FormatSize,
                        title = "Text size",
                        description = "Scales all text in subshell, on top of the system font size",
                        value = state.appTextScale.label,
                        onClick = { choiceDialogKey = "appearance-text-size" },
                    )
                }
                if (searchResult.isItemVisible("vote-controls")) item("vote-controls") {
                    ValueSettingRow(
                        icon = Icons.Rounded.ThumbUp,
                        title = "Vote controls",
                        description = "Comfortable thumbs with score, or compact arrow controls",
                        value = state.voteControlMode.label,
                        onClick = { choiceDialogKey = "appearance-vote-controls" },
                    )
                }
                if (searchResult.isItemVisible("vote-colours")) item("vote-colours") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Palette,
                        title = "Vote colours",
                        description = "Theme accent, or red upvotes and blue downvotes",
                        value = state.voteColorPalette.label,
                        onClick = { choiceDialogKey = "appearance-vote-colours" },
                    )
                }
                if (searchResult.isItemVisible("code-font")) item("code-font") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Code,
                        title = "Code font",
                        description = "Used by inline code and fenced code blocks",
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
                            reduceMotion = state.reduceMotion,
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
                item("home-title") { SettingsSection(settingsSectionTitle(destination, "Home & feeds")) }
                if (searchResult.isItemVisible("top-bar-behavior")) item("top-bar-behavior") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Home,
                        title = "Top bar",
                        description = "Behaviour while scrolling",
                        value = state.topBarBehavior.label,
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "top-bar-behavior"
                        },
                    )
                }
                if (searchResult.isItemVisible("bottom-navigation-behavior")) item("bottom-navigation-behavior") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Navigation,
                        title = "Bottom navigation",
                        description = "Behaviour while scrolling",
                        value = state.bottomBarBehavior.label,
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
                item("navigation-title") { SettingsSection(settingsSectionTitle(destination, "Navigation")) }
                if (query.isBlank() || searchResult.isItemVisible("tablet-layout-mode")) {
                    item("tablet-layout-mode") {
                        SettingsSegmentedChoice(
                            icon = Icons.Rounded.Navigation,
                            title = "Tablet mode",
                            description = "Choose how content is shown on wider screens",
                            options = TabletLayoutMode.entries,
                            selected = state.tabletLayoutMode,
                            label = { it.label },
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
                            icon = Icons.Rounded.Groups,
                            title = "Community discovery",
                            description = "Choose how popular communities are shown",
                            options = CommunityDiscoveryLayout.entries,
                            selected = state.communityDiscoveryLayout,
                            label = { it.label },
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
                        icon = Icons.Rounded.TouchApp,
                        title = "Interaction side",
                        description = "Preferred side for thumb controls and floating actions",
                        options = InteractionSide.entries,
                        selected = state.interactionSide,
                        label = { it.label },
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

                if (destination == SettingsDestination.NAVIGATION && query.isBlank()) {
                    item("side-menu-title") { SettingsSection("Side menu") }
                }

                if (searchResult.isItemVisible("drawer-side")) item("drawer-side") {
                    SettingsSegmentedChoice(
                        icon = Icons.Rounded.Navigation,
                        title = "Side menu position",
                        description = "Choose which edge the side menu opens from",
                        options = selectableDrawerSidePreferences,
                        selected = state.drawerSidePreference,
                        label = { it.label },
                        onSelect = { preference ->
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            state.updateDrawerSidePreference(preference)
                        },
                    )
                }

                if (searchResult.isItemVisible("subreddit-labels")) item("subreddit-labels") {
                    ValueSettingRow(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = "Communities labels",
                        description = "How community names are written",
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
                        icon = Icons.Rounded.Groups,
                        title = "Communities in drawer",
                        description = "Simple shortcut, dropdown, or full list",
                        value = state.drawerCommunitiesMode.label,
                        onClick = {
                            navigationPreviewContextName =
                                NavigationPreviewContext.NAVIGATION.name
                            choiceDialogKey = "drawer-communities"
                        },
                    )
                }
                if (searchResult.isItemVisible("drawer-recent-communities")) item("drawer-recent-communities") {
                    ToggleRow(
                        icon = Icons.Rounded.History,
                        title = "Recent communities in drawer",
                        description = "Show up to five recently visited communities",
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
                        icon = Icons.AutoMirrored.Rounded.ExitToApp,
                        title = "Press Back twice to exit",
                        description = "Ask for a second Back before leaving the app",
                        checked = state.backTwiceToExit,
                        onCheckedChange = state::updateBackTwiceToExit,
                    )
                }
                if (searchResult.isItemVisible("launcher-shortcuts")) item("launcher-shortcuts") {
                    // ISSUE37_LAUNCHER_SHORTCUTS_V1: device-local, off by default.
                    ToggleRow(
                        icon = Icons.AutoMirrored.Rounded.AddToHomeScreen,
                        title = "Launcher shortcuts",
                        description = "Favorite communities on the app icon, and Add to home screen for a community",
                        checked = launcherShortcutsEnabled,
                        onCheckedChange = { enabled ->
                            LauncherShortcutsEnabledStore.write(appContext, enabled)
                        },
                    )
                }
                if (searchResult.isItemVisible("reddit-favorite-sync")) item("reddit-favorite-sync") {
                    // REDDITFAV01_SHOW_REDDIT_FAVORITES_V1: device-local, off by default.
                    ToggleRow(
                        icon = Icons.Rounded.Star,
                        title = "Sync favorites with Reddit",
                        description = "Show your Reddit favorites, and star joined communities and your custom feeds on Reddit too",
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
                        icon = Icons.Rounded.Share,
                        title = "Share into subshell",
                        description = "Show subshell in Android's share menu to start a post from a link, text or images",
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
                        icon = Icons.Rounded.Navigation,
                        title = "Navigation labels",
                        description = "All labels, selected only, or icons only",
                        value = state.navigationLabelMode.label,
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
                            "Privacy"
                        } else {
                            settingsSectionTitle(destination, "Privacy")
                        },
                    )
                }
                if (searchResult.isItemVisible("hidden-subscriptions-device-unlock")) item("hidden-subscriptions-device-unlock") {
                    ToggleRow(
                        icon = Icons.Rounded.Lock,
                        title = "Require device unlock to view hidden communities",
                        // LOWUI8_HIDDEN_UNLOCK_NEEDS_A_SCREEN_LOCK_V1 (OCB-273): like
                        // Lock Subshell, it can be turned on only where there is a
                        // device unlock to ask for (otherwise the list could not open).
                        description = if (
                            appLock == null ||
                            appLock.deviceUnlockAvailable ||
                            state.requireDeviceUnlockForHiddenSubscriptions
                        ) {
                            "Ask for biometrics or your device screen lock before revealing hidden community names"
                        } else {
                            "Set a screen lock on this phone first"
                        },
                        checked = state.requireDeviceUnlockForHiddenSubscriptions,
                        onCheckedChange = { required ->
                            if (required) {
                                state.updateRequireDeviceUnlockForHiddenSubscriptions(true)
                            } else {
                                turnOffAfterDeviceUnlock(
                                    appLock = appLock,
                                    title = "Show hidden communities freely",
                                    subtitle = "Confirm your device unlock to stop protecting hidden community names",
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
                        icon = Icons.Rounded.Lock,
                        title = "Lock subshell",
                        description = if (appLock.deviceUnlockAvailable || appLock.settings.enabled) {
                            "Ask for biometrics or your device screen lock when you open subshell"
                        } else {
                            "Set a screen lock on this phone first"
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
                                    title = "Turn off app lock",
                                    subtitle = "Confirm your device unlock to stop locking subshell",
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
                        icon = Icons.Rounded.Schedule,
                        title = "Lock after",
                        description = "How long subshell can be in the background before it locks",
                        value = appLockDelayLabel(appLock.settings.delayMinutes),
                        onClick = { choiceDialogKey = "app-lock-delay" },
                    )
                }
                if (
                    appLock != null &&
                    appLock.settings.enabled &&
                    searchResult.isItemVisible("app-lock-notifications")
                ) item("app-lock-notifications") {
                    ToggleRow(
                        icon = Icons.Rounded.Lock,
                        title = "Hide notification content",
                        description = "While the lock is on, notifications only say there is new activity",
                        checked = appLock.settings.hideNotificationContent,
                        onCheckedChange = { hide ->
                            appLock.onChange(appLock.settings.copy(hideNotificationContent = hide))
                        },
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
                item("search-title") { SettingsSection(settingsSectionTitle(destination, "Search")) }
                if (searchResult.isItemVisible("search-keyboard")) item("search-keyboard") {
                    ToggleRow(
                        icon = Icons.Rounded.Keyboard,
                        title = "Open keyboard when entering Search",
                        description = "Explicit Search taps focus the field immediately",
                        checked = state.openKeyboardOnSearch,
                        onCheckedChange = state::updateOpenKeyboardOnSearch,
                    )
                }
                // ISSUE206_SAVED_SEARCHES_V1: off by default.
                if (searchResult.isItemVisible("saved-searches")) item("saved-searches") {
                    ToggleRow(
                        icon = Icons.Rounded.BookmarkBorder,
                        title = "Saved searches",
                        description = "Save a search to run it again from the Search screen; included in backups",
                        checked = state.savedSearchesEnabled,
                        onCheckedChange = state::updateSavedSearchesEnabled,
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
                    searchResult.isItemVisible("sensitive-media-reddit-lab") ||
                    searchResult.isItemVisible("media-autoplay")
                ) {
                    item("media-preview-playback-title") { SettingsSection("Preview & playback") }
                }
                if (searchResult.isItemVisible("sensitive-media-previews")) item("sensitive-media-previews") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Visibility,
                        title = "Sensitive media previews",
                        description = "Blur, load on tap, or hide",
                        value = state.sensitiveMediaPreviewMode.label,
                        onClick = { choiceDialogKey = "media-sensitive-previews" },
                    )
                }
                if (BuildConfig.DEBUG && BuildConfig.DEBUG_SLOT == "dev2") {
                    if (searchResult.isItemVisible("sensitive-media-reddit-lab")) item("sensitive-media-reddit-lab") {
                        NavigationRow(
                            icon = Icons.Rounded.Visibility,
                            title = "Sensitive media Reddit lab",
                            description = "DEV2 only · raw Reddit JSON through the production mapper and media UI",
                            onClick = {
                                openSettingsDestination(SettingsDestination.SENSITIVE_MEDIA_LAB)
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("media-autoplay")) item("media-autoplay") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Image,
                        title = "Autoplay videos and GIFs",
                        description = "Always, Wi-Fi only, or never",
                        value = state.videoAutoplay.label,
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
                    item("media-video-title") { SettingsSection("Video") }
                }
                if (searchResult.isItemVisible("media-muted")) item("media-muted") {
                    ToggleRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeOff,
                        title = "Start videos muted",
                        description = "Begin playback without audio",
                        checked = state.startVideosMuted,
                        onCheckedChange = state::updateStartVideosMuted,
                    )
                }
                if (searchResult.isItemVisible("media-loop")) item("media-loop") {
                    ToggleRow(
                        icon = Icons.Rounded.History,
                        title = "Loop videos",
                        description = "Restart videos automatically after they finish",
                        checked = state.loopVideos,
                        onCheckedChange = state::updateLoopVideos,
                    )
                }
                if (searchResult.isItemVisible("video-subtitle-default")) item("video-subtitle-default") {
                    ValueSettingRow(
                        icon = Icons.Rounded.ClosedCaption,
                        title = "Subtitles",
                        description = "Default for new videos",
                        value = state.videoSubtitleDefault.rowLabel,
                        onClick = { choiceDialogKey = "media-subtitle-default" },
                    )
                }
                if (searchResult.isItemVisible("video-audio-default")) item("video-audio-default") {
                    ValueSettingRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = "Audio language",
                        description = "When a video has several",
                        value = state.videoAudioDefault.rowLabel,
                        onClick = { choiceDialogKey = "media-audio-default" },
                    )
                }
                if (searchResult.isItemVisible("playback-options-placement")) item("playback-options-placement") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Tune,
                        title = "Playback options button",
                        description = "Quality, audio and subtitles",
                        value = state.playbackOptionsPlacement.rowLabel,
                        onClick = { choiceDialogKey = "media-playback-options" },
                    )
                }
                if (searchResult.isItemVisible("video-timeline-size")) item("video-timeline-size") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Tune,
                        title = "Video timeline size",
                        description = "Compact, standard, or large",
                        value = state.videoTimelineSize.label,
                        onClick = { choiceDialogKey = "media-timeline-size" },
                    )
                }
                if (searchResult.isItemVisible("video-quality-wifi")) item("video-quality-wifi") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Wifi,
                        title = "Video quality on Wi-Fi",
                        description = "Adaptive quality ceiling",
                        value = state.wifiVideoQuality.label,
                        onClick = { choiceDialogKey = "media-quality-wifi" },
                    )
                }
                if (searchResult.isItemVisible("video-quality-mobile")) item("video-quality-mobile") {
                    ValueSettingRow(
                        icon = Icons.Rounded.SignalCellularAlt,
                        title = "Video quality on mobile data",
                        description = "Adaptive quality ceiling",
                        value = state.mobileVideoQuality.label,
                        onClick = { choiceDialogKey = "media-quality-mobile" },
                    )
                }
                // VIDEO07 (#227, owner): each extra behind its own setting, off by default.
                if (searchResult.isItemVisible("video-speed-controls")) item("video-speed-controls") {
                    ToggleRow(
                        icon = Icons.Rounded.Speed,
                        title = "Playback speed",
                        description = "Full screen speed button, 0.25× to 2×",
                        checked = state.videoSpeedControls,
                        onCheckedChange = state::updateVideoSpeedControls,
                    )
                }
                if (searchResult.isItemVisible("video-double-tap-seek")) item("video-double-tap-seek") {
                    ToggleRow(
                        icon = Icons.Rounded.Forward10,
                        title = "Double-tap to skip",
                        description = "Full screen · left side back 10 s, right side forward",
                        checked = state.videoDoubleTapSeek,
                        onCheckedChange = state::updateVideoDoubleTapSeek,
                    )
                }
                if (searchResult.isItemVisible("video-hold-double-speed")) item("video-hold-double-speed") {
                    ToggleRow(
                        icon = Icons.Rounded.FastForward,
                        title = "Hold for 2× speed",
                        description = "Full screen · press and hold the picture",
                        checked = state.videoHoldForDoubleSpeed,
                        onCheckedChange = state::updateVideoHoldForDoubleSpeed,
                    )
                }
                if (searchResult.isItemVisible("video-picture-in-picture")) item("video-picture-in-picture") {
                    ToggleRow(
                        icon = Icons.Rounded.PictureInPicture,
                        title = "Picture-in-picture",
                        description = "A full-screen video keeps playing in a small window when you leave",
                        checked = state.videoPictureInPicture,
                        onCheckedChange = state::updateVideoPictureInPicture,
                    )
                }
                if (searchResult.isItemVisible("video-rotate-fullscreen")) item("video-rotate-fullscreen") {
                    ToggleRow(
                        icon = Icons.Rounded.ScreenRotation,
                        title = "Rotate for wide videos",
                        description = "Full screen turns a wide video to landscape",
                        checked = state.videoRotateFullscreen,
                        onCheckedChange = state::updateVideoRotateFullscreen,
                    )
                }
                if (searchResult.isItemVisible("video-technical-details")) item("video-technical-details") {
                    ToggleRow(
                        icon = Icons.Rounded.Tune,
                        title = "Technical details",
                        description = "Codecs, decoders and dropped frames in playback options",
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
                    item("media-landscape-gestures-title") { SettingsSection("Fullscreen gestures") }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-video-gestures")) {
                        item("landscape-video-gestures") {
                            ToggleRow(
                                icon = Icons.Rounded.Tune,
                                title = "Landscape swipe controls",
                                description = "Swipe vertically while a video is fullscreen in landscape",
                                checked = state.landscapeVideoGesturesEnabled,
                                onCheckedChange = state::updateLandscapeVideoGesturesEnabled,
                            )
                        }
                    }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-brightness-gesture")) {
                        item("landscape-brightness-gesture") {
                            ToggleRow(
                                icon = Icons.Rounded.Brightness6,
                                title = "Brightness gesture",
                                description = "Left side · changes brightness for this window only",
                                checked = state.landscapeBrightnessGestureEnabled,
                                onCheckedChange = state::updateLandscapeBrightnessGestureEnabled,
                                enabled = state.landscapeVideoGesturesEnabled,
                            )
                        }
                    }
                    if (query.isBlank() || searchResult.isItemVisible("landscape-volume-gesture")) {
                        item("landscape-volume-gesture") {
                            ToggleRow(
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                title = "Volume gesture",
                                description = "Right side · changes media volume",
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
                    item("media-captions-title") { SettingsSection("Gallery captions & labels") }
                }
                if (searchResult.isItemVisible("media-captions")) item("media-captions") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Image,
                        title = "Gallery captions",
                        description = "Overlay, below media, or hidden",
                        value = state.mediaCaptionMode.label,
                        onClick = { choiceDialogKey = "media-captions" },
                    )
                }
                if (searchResult.isItemVisible("fullscreen-captions")) item("fullscreen-captions") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Image,
                        title = "Fullscreen gallery captions",
                        description = "With controls or always visible",
                        value = state.fullscreenCaptionMode.label,
                        onClick = { choiceDialogKey = "fullscreen-captions" },
                    )
                }
                if (searchResult.isItemVisible("media-labels")) item("media-labels") {
                    ToggleRow(
                        icon = Icons.Rounded.Image,
                        title = "Media labels",
                        description = "Show short labels over images, GIFs and videos",
                        checked = state.mediaLabelsVisible,
                        onCheckedChange = state::updateMediaLabelsVisible,
                    )
                }

                if (
                    redditLinkHandlingState.supported &&
                    searchResult.isItemVisible("reddit-link-handling")
                ) {
                    item("media-links-title") { SettingsSection("Links") }
                    item("reddit-link-handling") {
                        NavigationRow(
                            icon = Icons.Rounded.Link,
                            title = "Open Reddit links",
                            description = when (redditLinkHandlingState.status) {
                                RedditLinkHandlingStatus.ENABLED ->
                                    "Enabled · supported Reddit links open in subshell"
                                RedditLinkHandlingStatus.PARTIAL ->
                                    "Partial · ${redditLinkHandlingState.selectedHostCount} of ${redditLinkHandlingState.supportedHostCount} domains selected"
                                RedditLinkHandlingStatus.DISABLED ->
                                    "Off · choose subshell for supported Reddit links in Android"
                                RedditLinkHandlingStatus.UNSUPPORTED ->
                                    "Managed by Android"
                            },
                            onClick = onOpenRedditLinkSettings,
                        )
                    }
                }

                if (
                    searchResult.isItemVisible("media-cache") ||
                    searchResult.isItemVisible("video-cache-limit") ||
                    searchResult.isItemVisible("download-storage")
                ) {
                    item("media-storage-title") { SettingsSection("Storage") }
                }
                if (searchResult.isItemVisible("media-cache")) item("media-cache") {
                    SettingSurface(
                        icon = Icons.Rounded.Storage,
                        title = "Media cache",
                        description = when {
                            cacheClearInProgress -> "Clearing cached media…"
                            mediaCacheStatus != null && cacheOperationMessage != null ->
                                "${cacheOperationMessage} · ${videoMediaCacheSummary(requireNotNull(mediaCacheStatus))}"
                            mediaCacheStatus != null -> videoMediaCacheSummary(requireNotNull(mediaCacheStatus))
                            mediaCacheReadFailed -> "Media cache size is unavailable"
                            else -> "Checking media cache…"
                        },
                    ) {
                        TextButton(
                            enabled = !cacheClearInProgress && mediaCacheStatus?.bytesUsed?.let { it > 0L } == true,
                            onClick = { cacheClearConfirmationVisible = true },
                        ) {
                            Text("Clear")
                        }
                    }
                }
                if (searchResult.isItemVisible("video-cache-limit")) item("video-cache-limit") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Storage,
                        title = "Video cache size",
                        description = if (videoCacheLimitPending) {
                            "Applies the next time subshell starts"
                        } else {
                            "How much watched video to keep on this device"
                        },
                        value = videoCacheLimit?.label.orEmpty(),
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
                        icon = Icons.Rounded.Storage,
                        title = "Downloaded videos",
                        description = when {
                            downloadDeleteInProgress -> "Deleting downloaded videos…"
                            downloadOperationMessage != null -> downloadOperationMessage.orEmpty()
                            else -> legacyDownloadStatus?.let(::legacyVideoDownloadSummary).orEmpty()
                        },
                    ) {
                        TextButton(
                            enabled = !downloadDeleteInProgress && legacyDownloadStatus?.hasDownloads == true,
                            onClick = { downloadDeleteConfirmationVisible = true },
                        ) {
                            Text("Delete all")
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
                item("accessibility-title") { SettingsSection(settingsSectionTitle(destination, "Accessibility")) }
                if (searchResult.isItemVisible("reduced-motion")) item("reduced-motion") {
                    ToggleRow(
                        icon = Icons.Rounded.AccessibilityNew,
                        title = "Reduce motion",
                        description = "Prefer immediate state changes over decorative movement",
                        checked = state.reduceMotion,
                        onCheckedChange = state::updateReduceMotion,
                    )
                }
            }

            if (
                settingsSectionVisible(
                    destination = destination,
                    query = query,
                    searchResult = searchResult,
                    section = "Prototype states",
                    debug = BuildConfig.DEBUG,
                )
            ) {
                item("prototype-title") { SettingsSection(settingsSectionTitle(destination, "Prototype states")) }
                if (searchResult.isItemVisible("feed-state")) item("feed-state") {
                    ValueSettingRow(
                        icon = Icons.Rounded.CloudOff,
                        title = "Feed preview",
                        description = "Inspect loading, offline, error and empty layouts",
                        value = state.feedPreviewState.label,
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
                            "Comment presentation"
                        } else {
                            settingsSectionTitle(destination, "Comments")
                        },
                    )
                }
                if (
                    searchResult.isItemVisible("comment-hierarchy-style")
                ) {
                    item("comment-hierarchy-style") {
                        ValueSettingRow(
                            icon = Icons.Rounded.Forum,
                            title = "Comment hierarchy",
                            description = "How replies connect to parent comments",
                            value = when (state.commentHierarchyStyle) {
                                CommentHierarchyStyle.GUIDE_LINES -> "Guides"
                                CommentHierarchyStyle.PARENT_CONNECTORS -> "Connectors"
                            },
                            onClick = {
                                choiceDialogKey = "comment-hierarchy-style"
                            },
                        )
                    }
                }
                if (searchResult.isItemVisible("highlight-new-comments")) item("highlight-new-comments") {
                    ToggleRow(
                        icon = Icons.Rounded.Forum,
                        title = "Highlight new comments",
                        description = "Mark replies added since your previous visit and reveal their parent path",
                        checked = state.highlightNewComments,
                        onCheckedChange = state::updateHighlightNewComments,
                    )
                }
                // ISSUE22_COMMENT_AUTHOR_AVATARS_SETTING_V1
                if (searchResult.isItemVisible("show-comment-avatars")) item("show-comment-avatars") {
                    ToggleRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = "Show author avatars",
                        description = "Show each commenter's Reddit avatar instead of a letter",
                        checked = state.showCommentAvatars,
                        onCheckedChange = state::updateShowCommentAvatars,
                    )
                }
                if (searchResult.isItemVisible("top-level-comment-jump")) item("top-level-comment-jump") {
                    ToggleRow(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        title = "Top-level comment jump button",
                        description = "Tap to jump to the next top-level comment, hold for the previous",
                        checked = state.topLevelCommentJumpButton,
                        onCheckedChange = state::updateTopLevelCommentJumpButton,
                    )
                }
                if (searchResult.isItemVisible("comment-actions")) item("comment-actions") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Forum,
                        title = "Action placement",
                        description = "Place comment actions on the preferred side or balance them",
                        value = if (state.commentActionPreference == CommentActionPreference.FOLLOW) {
                            state.interactionSide.followLabel()
                        } else {
                            state.commentActionPreference.label
                        },
                        onClick = { choiceDialogKey = "comment-actions" },
                    )
                }
                if (searchResult.isItemVisible("comment-time-format")) item("comment-time-format") {
                    ValueSettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Comment time",
                        description = "Relative age or a compact local timestamp",
                        value = state.commentTimeFormat.label,
                        onClick = { choiceDialogKey = "comment-time-format" },
                    )
                }
                if (searchResult.isItemVisible("comment-collapse-mode")) {
                    item("comment-collapse-mode") {
                        SettingsSegmentedChoice(
                            icon = Icons.Rounded.TouchApp,
                            title = "Collapse comments",
                            description = "Choose the tap target used to collapse a comment",
                            options = CommentCollapseMode.entries,
                            selected = state.commentCollapseMode,
                            label = { it.label },
                            onSelect = state::updateCommentCollapseMode,
                        )
                    }
                }
                if (
                    destination == SettingsDestination.COMMENTS &&
                    query.isBlank() &&
                    searchResult.isItemVisible("use-suggested-comment-sort")
                ) {
                    item("comment-behaviour-title") { SettingsSection("Behaviour") }
                }
                if (searchResult.isItemVisible("use-suggested-comment-sort")) item("use-suggested-comment-sort") {
                    ToggleRow(
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        title = "Follow suggested comment sort",
                        description = "Use Reddit's suggested order when opening a post",
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
                        "Notifications off · no types selected"

                    !state.inboxNotificationsEnabled ->
                        "Notifications off · $notificationTypeCount selected"

                    notificationTypeCount == 0 ->
                        "No types enabled"

                    notificationTypeCount == 1 ->
                        "1 type enabled"

                    else ->
                        "$notificationTypeCount types enabled"
                }

                item("notifications-title") {
                    SettingsSection(
                        settingsSectionTitle(
                            destination,
                            "Notifications",
                        ),
                    )
                }

                if (notificationMasterVisible) {
                    item("notifications") {
                        // ISSUE219_INBOX_NOTIFICATIONS_ROW_IS_THE_SWITCH_V1: as ToggleRow (LOWUI7).
                        SettingsSwitchItem(
                            icon = Icons.Rounded.Notifications,
                            title = "Inbox notifications",
                            description = "Check Reddit every 15 minutes for new notifications",
                            checked = state.inboxNotificationsEnabled,
                            onCheckedChange = ::setInboxNotificationsEnabled,
                            enabled = state.activeAccountIsAuthenticated,
                        )
                    }
                }

                if (notificationCategoryVisible) {
                    item("notification-types") {
                        NavigationRow(
                            icon = Icons.Rounded.Tune,
                            title = "Notification types",
                            description = notificationTypeSummary,
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
                item("reddit-connection-title") { SettingsSection(settingsSectionTitle(destination, "Reddit connection")) }
                if (searchResult.isItemVisible("redreader-compatibility")) item("redreader-compatibility") {
                    NavigationRow(
                        icon = Icons.Rounded.VpnKey,
                        title = "RedReader compatibility",
                        description = redReaderOperationMessage ?: if (redReaderCompatibilityConfigured) {
                            "Configured locally · changes apply automatically"
                        } else {
                            "Add a user-supplied OAuth client ID in encrypted no-backup storage"
                        },
                        onClick = {
                            redReaderClientIdDraft = ""
                            redReaderOperationMessage = null
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
                    SettingsSection(settingsSectionTitle(destination, "Diagnostics"))
                }
                if (searchResult.isItemVisible("diagnostics")) item("diagnostics") {
                    NavigationRow(
                        icon = Icons.Rounded.Info,
                        title = "Diagnostics",
                        description = "Inspect local app events and create a privacy-safe report",
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
                item("backup-title") { SettingsSection(settingsSectionTitle(destination, "Backup & restore")) }
                if (searchResult.isItemVisible("backup")) item("backup") {
                    NavigationRow(
                        icon = Icons.Rounded.Backup,
                        title = "Saved setups",
                        description = when (backupRepository.status().protection) {
                            BackupProtection.PROTECTED -> "Automatic history is protected outside the app"
                            BackupProtection.WAITING -> "Internal history saved; external backup is waiting"
                            BackupProtection.INTERNAL_ONLY -> "Automatic history is only stored inside this installation"
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
                item("about-title") { SettingsSection(settingsSectionTitle(destination, "About")) }
                if (searchResult.isItemVisible("about")) item("about") {
                    NavigationRow(
                        icon = Icons.Rounded.Info,
                        title = "Help & about",
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
        SettingsDestination.ACCOUNTS -> Icons.Rounded.AccountCircle
        SettingsDestination.APPEARANCE -> Icons.Rounded.Palette
        SettingsDestination.NAVIGATION -> Icons.Rounded.Navigation
        SettingsDestination.FEEDS -> Icons.Rounded.Home
        SettingsDestination.MEDIA -> Icons.Rounded.Image
        SettingsDestination.COMMENTS -> Icons.Rounded.Forum
        SettingsDestination.BACKUP -> Icons.Rounded.Backup
        SettingsDestination.ADVANCED -> Icons.Rounded.Tune
        SettingsDestination.ABOUT -> Icons.Rounded.Info
        else -> Icons.Rounded.Tune
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
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
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
) {
    SettingsValueItem(
        title = title,
        description = description,
        value = value,
        onClick = onClick,
        icon = icon,
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
        title = { Text("Code font") },
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
                                    text = font.label,
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun <T> SettingsSingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
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
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AccountNavigationRow(
    username: String,
    avatarImageUri: String?,
    description: String,
    onClick: () -> Unit,
) {
    SettingsNavigationItem(
        title = "u/$username",
        description = description,
        onClick = onClick,
        leading = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                app.subshell.prototype.ui.components.RemoteIdentityImage(
                    uri = avatarImageUri,
                    contentDescription = "Avatar for u/$username",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    app.subshell.prototype.ui.components.RedditDefaultProfileAvatar(
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
    title = "Notifications are still off",
    message = "subshell couldn't save the notification state on this device. Try again",
)

internal fun videoCacheLimitRestartNotice(limit: VideoMediaCacheLimit): ErrorPresentation = ErrorPresentation(
    kind = ErrorPresentationKind.DEGRADED,
    title = "Restart to apply",
    message = "The ${limit.label} video cache is used the next time subshell starts",
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
