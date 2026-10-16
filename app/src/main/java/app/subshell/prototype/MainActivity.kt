package app.subshell.prototype

import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.lifecycle.lifecycleScope
import app.subshell.prototype.inbox.takeInboxNotificationTarget
import app.subshell.prototype.inbox.takeModmailNotificationTarget
import app.subshell.prototype.inbox.InboxNotificationTarget
import app.subshell.prototype.inbox.InboxNotificationCenter
import app.subshell.prototype.inbox.ModmailNotificationTarget
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.subshell.prototype.applock.AppLockController
import app.subshell.prototype.applock.AppUnlockPromptGate
import app.subshell.prototype.applock.AppLockEntryPoints
import app.subshell.prototype.applock.AppLockOverlay
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.applock.AppLockSettingsStore
import app.subshell.prototype.applock.AppLockViewModel
import app.subshell.prototype.applock.LocalAppLock
import app.subshell.prototype.applock.LocalAppLocked
import app.subshell.prototype.applock.LocalAppLockedNow
import app.subshell.prototype.ui.theme.SubshellTheme
import app.subshell.prototype.ui.theme.SubshellThemeSettings
import app.subshell.prototype.auth.AUTH_CALLBACK_RESULT_EXTRA
import app.subshell.prototype.auth.AuthBrowserLauncher
import app.subshell.prototype.auth.dispatchAuthBrowserLaunch
import app.subshell.prototype.auth.dispatchAuthTabResult
import app.subshell.prototype.auth.processAuthCallbackOffMain
import app.subshell.prototype.auth.AuthRuntimePreparation
import app.subshell.prototype.auth.AuthConnectViewModel
import app.subshell.prototype.data.account.TextPostWriteRequest
import app.subshell.prototype.auth.toResultToken
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.media.VideoPlaybackViewModel
import app.subshell.prototype.ui.DebugFocusedCommentLaunch
import app.subshell.prototype.ui.RedditIntentLaunch
import app.subshell.prototype.ui.LEGACY_STORAGE_WRITE_PERMISSION
import app.subshell.prototype.ui.LegacyStorageWritePermissionRequester
import app.subshell.prototype.ui.legacyStorageWritePermissionNeeded
import app.subshell.prototype.ui.RedditLinkHandlingPromptStore
import app.subshell.prototype.ui.RedditLinkHandlingState
import app.subshell.prototype.ui.RedditLinkHandlingStatus
import app.subshell.prototype.ui.RedditExternalPollHandoffStore
import app.subshell.prototype.ui.openRedditLinkHandlingSettings
import app.subshell.prototype.ui.readRedditLinkHandlingState
import app.subshell.prototype.ui.redditViewIntentLaunchOrNull
import app.subshell.prototype.ui.SubshellApp
import app.subshell.prototype.ui.debugFocusedCommentLaunchOrNull
import app.subshell.prototype.ui.app.AccountDirectoryGate
import app.subshell.prototype.ui.app.ModmailEntryPoints
import app.subshell.prototype.ui.app.ModerationQueueEntryPoints
import app.subshell.prototype.ui.app.InboxEntryPoints
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import app.subshell.prototype.shortcuts.LauncherShortcutTarget
import app.subshell.prototype.shortcuts.takeLauncherShortcutTarget
import app.subshell.prototype.ui.app.MessagingEntryPoints
import app.subshell.prototype.ui.app.CommunityEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import app.subshell.prototype.ui.app.CommentLoadingEntryPoints
import app.subshell.prototype.ui.app.CommentMutationEntryPoints
import app.subshell.prototype.ui.app.PostMutationEntryPoints
import app.subshell.prototype.ui.app.ModeratorToolsEntryPoints
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import app.subshell.prototype.data.account.CustomFeedCreateRequest
import app.subshell.prototype.data.account.CustomFeedDeleteRequest
import app.subshell.prototype.data.account.CustomFeedUpdateRequest
import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import app.subshell.prototype.inbox.startNotificationReadReconciliation
import app.subshell.prototype.update.UpdateCheckScheduler
import app.subshell.prototype.update.UpdateRuntimePolicy
import app.subshell.prototype.update.UpdateStateStore
import app.subshell.prototype.update.consumeUpdatePresentationIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : FragmentActivity(), LegacyStorageWritePermissionRequester {
    private val videoPlaybackViewModel: VideoPlaybackViewModel by lazy {
        ViewModelProvider(this)[VideoPlaybackViewModel::class.java]
    }
    private val authConnectViewModel: AuthConnectViewModel by lazy {
        ViewModelProvider(this)[AuthConnectViewModel::class.java]
    }
    // ISSUE38_APP_LOCK_V1
    private val appLock: AppLockController by lazy {
        ViewModelProvider(this)[AppLockViewModel::class.java].controller(applicationContext)
    }
    private var deviceUnlockAvailable by mutableStateOf(false)
    private var appThemeSettings by mutableStateOf(SubshellThemeSettings())
    private val appUnlockPromptGate = AppUnlockPromptGate()
    // MEDIA07_APP_LOCK_READ_LIVE_AT_START_V1 (#150): read when called, not when composed.
    private val isAppLockedNow: () -> Boolean = { appLock.locked }

    private val appLockLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appLock.onStarted(SystemClock.elapsedRealtime())
        }

        override fun onResume(owner: LifecycleOwner) {
            appLock.onResumed()
            deviceUnlockAvailable = canUseDeviceUnlock()
            promptAppUnlockIfLocked()
        }

        override fun onStop(owner: LifecycleOwner) {
            appUnlockPromptGate.onStopped()
            appLock.onStopped(
                SystemClock.elapsedRealtime(),
                changingConfigurations = isChangingConfigurations,
            )
        }
    }
    // NOTIFY01A2_ACTIVITY_OWNED_RECONCILIATION_SCOPE_V2
    private val notificationReadReconciliationJob = SupervisorJob()
    private val notificationReadReconciliationScope =
        CoroutineScope(
            notificationReadReconciliationJob +
                Dispatchers.Main.immediate,
        )
    private var inboxNotificationTarget by mutableStateOf<InboxNotificationTarget?>(null)
    private var modmailNotificationTarget by mutableStateOf<ModmailNotificationTarget?>(null)
    private var debugFocusedCommentLaunch by mutableStateOf<DebugFocusedCommentLaunch?>(null)
    private var redditIntentLaunch by mutableStateOf<RedditIntentLaunch?>(null)
    private var launcherShortcutTarget by mutableStateOf<LauncherShortcutTarget?>(null)
    // ISSUE33_SHARE_INTO_SUBSHELL_V1
    private val shareImport: app.subshell.prototype.share.ShareImportViewModel by lazy {
        ViewModelProvider(this)[app.subshell.prototype.share.ShareImportViewModel::class.java]
    }
    private var shareIntoSubshellLaunch: app.subshell.prototype.share.ShareIntoSubshellLaunch?
        get() = shareImport.launch
        set(value) {
            shareImport.launch = value
        }
    // REDDITURL03_SHARE_LINK_REDIRECT_V1
    private val redditShareLinkResolver by lazy {
        app.subshell.prototype.data.network.reddit.RedditShareLinkResolver()
    }

    private val redditLinkHandlingPromptStore by lazy {
        RedditLinkHandlingPromptStore(applicationContext)
    }
    private var redditLinkHandlingState by
        mutableStateOf(RedditLinkHandlingState())
    private var redditLinkHandlingPromptSeen by
        mutableStateOf(true)
    private var inboxNotificationPermissionResult: ((Boolean) -> Unit)? = null
    private var legacyStorageWriteResult: ((Boolean) -> Unit)? = null
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(this) { result ->
            lifecycleScope.launch {
                dispatchAuthTabResult(
                    resultCode = result.resultCode,
                    resultUri = result.resultUri,
                    processCallback = { callbackUri ->
                        processAuthCallbackOffMain(applicationContext, callbackUri).toResultToken()
                    },
                    deliver = authConnectViewModel::handleCallbackResultToken,
                )
            }
        }

    private fun requestInboxNotificationPermission(
        onResult: (Boolean) -> Unit,
    ) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
            return
        }

        // LOW_MAIN_REPLACED_PERMISSION_CALLBACK_GETS_AN_ANSWER_V1 (OCB-390): a
        // second request does not leave the first caller waiting for ever.
        inboxNotificationPermissionResult?.invoke(false)
        inboxNotificationPermissionResult = onResult
        runCatching {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                INBOX_NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }.onFailure {
            inboxNotificationPermissionResult = null
            onResult(false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LEGACY_STORAGE_WRITE_REQUEST_CODE) {
            val storageCallback = legacyStorageWriteResult ?: return
            legacyStorageWriteResult = null
            storageCallback(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            return
        }
        if (requestCode != INBOX_NOTIFICATION_PERMISSION_REQUEST_CODE) return
        val callback = inboxNotificationPermissionResult ?: return
        inboxNotificationPermissionResult = null
        callback(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    // LOWUI_LEGACY_DOWNLOADS_ASK_FOR_STORAGE_V1 (OCB-084): Android 9 and older
    // save a download into the public Downloads folder only with this grant.
    override suspend fun requestLegacyStorageWrite(): Boolean {
        if (!legacyStorageWritePermissionNeeded()) return false
        if (
            ContextCompat.checkSelfPermission(this, LEGACY_STORAGE_WRITE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        if (isFinishing || isDestroyed) return false
        return suspendCancellableCoroutine { continuation ->
            legacyStorageWriteResult?.invoke(false)
            val callback: (Boolean) -> Unit = { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
            legacyStorageWriteResult = callback
            continuation.invokeOnCancellation {
                if (legacyStorageWriteResult === callback) legacyStorageWriteResult = null
            }
            runCatching {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(LEGACY_STORAGE_WRITE_PERMISSION),
                    LEGACY_STORAGE_WRITE_REQUEST_CODE,
                )
            }.onFailure {
                legacyStorageWriteResult = null
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun requestHiddenSubscriptionsUnlock(
        onResult: (Boolean) -> Unit,
    ) {
        requestDeviceUnlock(
            title = "Unlock hidden subscriptions",
            subtitle = "Confirm your device unlock to reveal hidden community names",
            onResult = onResult,
        )
    }

    private fun canUseDeviceUnlock(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(applicationContext).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            applicationContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }

    // ISSUE38_APP_LOCK_V1: once per lock and return; the lock screen's button asks again.
    private fun promptAppUnlockIfLocked() {
        if (!appUnlockPromptGate.shouldPrompt(appLock.locked)) return
        requestAppUnlock()
    }

    /** With no screen lock left on the phone the app lock cannot be enforced; it opens. */
    private fun requestAppUnlock() {
        appUnlockPromptGate.onPromptStarted()
        if (!canUseDeviceUnlock()) {
            appUnlockPromptGate.onPromptFinished(unlocked = true)
            appLock.unlock()
            return
        }
        requestDeviceUnlock(
            title = "Unlock subshell",
            subtitle = "Confirm your device unlock to open subshell",
        ) { unlocked ->
            appUnlockPromptGate.onPromptFinished(unlocked)
            if (unlocked) {
                appLock.unlock()
            }
        }
    }

    private fun changeAppLockSettings(settings: AppLockSettings) {
        val controller = appLock
        controller.updateSettings(settings)
        AppLockSettingsStore.write(this, controller.settings)
        applyAppLockToRecents()
    }

    /** Android 13+: a blank recents card while the app lock is on; screenshots still work. */
    private fun applyAppLockToRecents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!appLock.settings.enabled)
        }
    }

    // ISSUE38_APP_LOCK_V1: every activity Subshell starts itself (browser login,
    // pickers, backup files, share sheets) is an exempt trip out of the app.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        appLock.onExternalTripStarted(SystemClock.elapsedRealtime())
        try {
            super.startActivityForResult(intent, requestCode, options)
        } catch (failure: RuntimeException) {
            // LOW_MAIN_FAILED_START_IS_NO_TRIP_V1 (OCB-392): nothing was opened, so
            // the next real trip away must not be taken as one Subshell started.
            appLock.onExternalTripFailed()
            throw failure
        }
    }

    private fun requestDeviceUnlock(
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit,
    ) {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (
                BiometricManager.from(this).canAuthenticate(authenticators) !=
                BiometricManager.BIOMETRIC_SUCCESS
            ) {
                onResult(false)
                return
            }
            builder.setAllowedAuthenticators(authenticators)
        } else {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager?.isDeviceSecure != true) {
                onResult(false)
                return
            }
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        pendingDeviceUnlock?.invoke(false)
        pendingDeviceUnlock = onResult
        deviceUnlockPrompt.authenticate(builder.build())
    }

    // LOW_MAIN_ONE_UNLOCK_PROMPT_PER_ACTIVITY_V1 (OCB-576): biometric 1.1.0 keeps
    // the prompt's callback in an activity-scoped ViewModel, so a prompt built per
    // request left the old activity (and its composition) reachable and handed a
    // result open across a recreation to that disposed instance. One prompt is
    // built in onCreate; its callback answers this instance's pending request. A
    // result with no request here (opened before a recreation) can only be the
    // app unlock, which this instance finishes on the retained controller.
    private var pendingDeviceUnlock: ((Boolean) -> Unit)? = null
    private lateinit var deviceUnlockPrompt: BiometricPrompt

    private fun deliverDeviceUnlock(unlocked: Boolean) {
        val pending = pendingDeviceUnlock
        pendingDeviceUnlock = null
        if (pending != null) {
            pending(unlocked)
            return
        }
        appUnlockPromptGate.onPromptFinished(unlocked)
        if (unlocked && appLock.locked) appLock.unlock()
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // SUBS01B8A_DISABLE_COMPOSE_CONTENT_CAPTURE_V1
        androidx.compose.ui.contentcapture.ContentCaptureManager.isEnabled = false
        super.onCreate(savedInstanceState)
        deviceUnlockPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(result)
                    deliverDeviceUnlock(true)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    deliverDeviceUnlock(false)
                }
            },
        )
        // ISSUE38_APP_LOCK_V1: registered before any composition, so the lock
        // state is current before video and other observers see ON_START.
        lifecycle.addObserver(appLockLifecycleObserver)
        deviceUnlockAvailable = canUseDeviceUnlock()
        applyAppLockToRecents()
        // UPDATE01A1A_GITHUB_BETA_UPDATE_AWARENESS_FOUNDATION_V1
        if (UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) {
            UpdateStateStore(applicationContext).observeProcessStart(BuildConfig.VERSION_CODE)
            UpdateCheckScheduler.schedule(applicationContext)
        }
        refreshRedditLinkHandlingState()
        enableEdgeToEdge()

        // AUTHSTART01_RUNTIME_PREPARED_OFF_MAIN_V1 (OCB-474): the auth runtime
        // reads the stored OAuth client selection (Keystore) when it is built.
        // Without a live AuthConnectViewModel it is built on IO first and the
        // rest of the start waits for it; a recreated activity keeps its
        // ViewModel and goes on at once. The launch intent is kept from here,
        // and intents arriving meanwhile are handled afterwards.
        val launchIntent: Intent = intent
        if (AuthRuntimePreparation.liveViewModels.get() > 0) {
            onAuthRuntimeReady(savedInstanceState, launchIntent)
        } else {
            lifecycleScope.launch {
                runCatching { AuthRuntimePreparation.prepare(applicationContext).await() }
                onAuthRuntimeReady(savedInstanceState, launchIntent)
            }
        }
    }

    private var authRuntimeReady = false
    private val intentsBeforeAuthRuntime = mutableListOf<Intent>()

    private fun onAuthRuntimeReady(savedInstanceState: Bundle?, launchIntent: Intent) {
        authRuntimeReady = true
        // NOTIFY01A2_ACTIVITY_OWNED_RECONCILIATION_START_V2
        startNotificationReadReconciliation(
            scope = notificationReadReconciliationScope,
            context = applicationContext,
            inboxStates = authConnectViewModel.redditInboxUiState,
            modmailStates = authConnectViewModel.redditModmailUiState,
        )

        setContent {
            val connectAccountState by authConnectViewModel.uiState.collectAsState()
            val onlineVisibilityState by authConnectViewModel.onlineVisibilityUiState.collectAsState()
            val customFeedsState by authConnectViewModel.customFeedsUiState.collectAsState()
            val customFeedPostsState by authConnectViewModel.customFeedPostsUiState.collectAsState()
            val redditFeedState by authConnectViewModel.redditFeedUiState.collectAsState()
            val redditAccountHistoryState by
                authConnectViewModel.redditAccountHistoryUiState.collectAsState()
            val redditSavedHistoryState by
                authConnectViewModel.redditSavedHistoryUiState.collectAsState()
            val redditCommentsState by
                authConnectViewModel.redditCommentsUiState.collectAsState()
            val redditSubscriptionsState by
                authConnectViewModel.redditSubscriptionsUiState.collectAsState()
            val redditCommunityDiscoveryState by
                authConnectViewModel.redditCommunityDiscoveryUiState.collectAsState()
            val redditMentionSearchState by
                authConnectViewModel.redditMentionSearchUiState.collectAsState()
            val redditCommunityInfoState by
                authConnectViewModel.redditCommunityInfoUiState.collectAsState()
            val redditUserFlairState by
                authConnectViewModel.userFlairUiState.collectAsState()
            val redditCommunityWikiState by
                authConnectViewModel.redditCommunityWikiUiState.collectAsState()
            val redditModerationState by
                authConnectViewModel.redditModerationUiState.collectAsState()
            val moderationActionState by
                authConnectViewModel.moderationActionUiState.collectAsState()
            val removalReasonState by
                authConnectViewModel.removalReasonUiState.collectAsState()
            val redditModmailState by
                authConnectViewModel.redditModmailUiState.collectAsState()
            val communityMembershipState by
                authConnectViewModel.communityMembershipUiState.collectAsState()
            val redditFavoriteState by
                authConnectViewModel.redditFavoriteUiState.collectAsState()
            val postVoteState by
                authConnectViewModel.postVoteUiState.collectAsState()
            val postSaveState by
                authConnectViewModel.postSaveUiState.collectAsState()
            val postFollowState by
                authConnectViewModel.postFollowUiState.collectAsState()
            val contentSafetyState by
                authConnectViewModel.contentSafetyUiState.collectAsState()
            val commentWriteState by
                authConnectViewModel.commentWriteUiState.collectAsState()
            val commentMediaPolicies by
                authConnectViewModel.commentMediaPolicies.collectAsState()
            val commentEditState by
                authConnectViewModel.commentEditUiState.collectAsState()
            val commentDeleteState by
                authConnectViewModel.commentDeleteUiState.collectAsState()
            val postEditState by
                authConnectViewModel.postEditUiState.collectAsState()
            val postDeleteState by
                authConnectViewModel.postDeleteUiState.collectAsState()
            val postManageState by
                authConnectViewModel.postManageUiState.collectAsState()
            val textPostWriteState by
                authConnectViewModel.textPostWriteUiState.collectAsState()
            val crosspostReviewState by authConnectViewModel.crosspostReviewUiState.collectAsState()
            val composerPolicy by authConnectViewModel.composerPolicy.collectAsState()
            val composerCommunitySearch by
                authConnectViewModel.composerCommunitySearch.collectAsState()
            val redditInboxState by
                authConnectViewModel.redditInboxUiState.collectAsState()
            val messageReplyState by
                authConnectViewModel.messageReplyUiState.collectAsState()
            val directChatStartState by
                authConnectViewModel.directChatStartUiState.collectAsState()
            val directChatLiveState by
                authConnectViewModel.directChatLiveUiState.collectAsState()
            val platformUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val inAppUriHandler = androidx.compose.runtime.remember(platformUriHandler) {
                app.subshell.prototype.ui.InAppRedditUriHandler(platformUriHandler) { uri ->
                    redditIntentLaunch = RedditIntentLaunch(
                        requestId = SystemClock.elapsedRealtimeNanos(),
                        rawUri = uri,
                    )
                }
            }
            // ISSUE38_APP_LOCK_V1: while locked the app stays composed (scroll,
            // drafts, video state survive) but is covered and hidden from
            // accessibility.
            Box(
                modifier = if (appLock.locked) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                },
            ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalUriHandler provides inAppUriHandler,
                LocalAppLock provides AppLockEntryPoints(
                    settings = appLock.settings,
                    deviceUnlockAvailable = deviceUnlockAvailable,
                    onChange = ::changeAppLockSettings,
                    onRequestUnlock = { onResult ->
                        requestDeviceUnlock(
                            title = "Turn on app lock",
                            subtitle = "Confirm your device unlock to lock subshell",
                            onResult = onResult,
                        )
                    },
                    onRequestUnlockFor = { title, subtitle, onResult ->
                        requestDeviceUnlock(title = title, subtitle = subtitle, onResult = onResult)
                    },
                ),
                LocalAppLocked provides appLock.locked,
                LocalAppLockedNow provides isAppLockedNow,
                app.subshell.prototype.ui.LocalPostFollowRuntime provides
                    app.subshell.prototype.ui.PostFollowRuntimeEntryPoints(
                        state = postFollowState,
                        onSetFollowed = authConnectViewModel::followPost,
                        onRetry = authConnectViewModel::retryPostFollow,
                        onAuthorize = ::launchPostFollowAuthorization,
                    ),
            ) {
                // ISSUE119_COMPOSE_AFTER_THE_ACCOUNT_DIRECTORY_V1
                AccountDirectoryGate(authConnectViewModel.accountSessionDirectory) {
                SubshellApp(
                    videoPlaybackCoordinator = videoPlaybackViewModel.coordinator,
                    onThemeSettingsChanged = { appThemeSettings = it },
                    accountConnect = AccountConnectEntryPoints(
                        accountSessionDirectory = authConnectViewModel.accountSessionDirectory,
                        readNsfwAccountPreferences = authConnectViewModel::readNsfwAccountPreferences,
                        onLogoutActiveAccount = authConnectViewModel::logoutActiveAccount,
                        onReconnectAccount = ::launchReconnectAccount,
                        onlineVisibilityRuntime = app.subshell.prototype.ui.components.OnlineVisibilityRuntime(
                            state = onlineVisibilityState,
                            onOpen = authConnectViewModel::openOnlineVisibility,
                            onClose = authConnectViewModel::closeOnlineVisibility,
                            onRefresh = authConnectViewModel::refreshOnlineVisibility,
                            onPropose = authConnectViewModel::proposeOnlineVisibility,
                            onConfirm = authConnectViewModel::confirmOnlineVisibility,
                            onCancel = authConnectViewModel::cancelOnlineVisibilityConfirmation,
                        ),
                        connectAccountState = connectAccountState,
                        onConnectAccount = ::launchConnectAccount,
                        onCancelConnectAccount = authConnectViewModel::cancelConnect,
                        onResetConnectAccount = authConnectViewModel::resetConnectState,
                    ),
                    customFeeds = CustomFeedsEntryPoints(
                        customFeedsState = customFeedsState,
                        customFeedPostsState = customFeedPostsState,
                        onRequestHiddenSubscriptionsUnlock = ::requestHiddenSubscriptionsUnlock,
                        onLoadCustomFeeds = authConnectViewModel::loadCustomFeeds,
                        onLoadCustomFeedPosts = authConnectViewModel::loadCustomFeedPosts,
                        onResolveCustomFeed = authConnectViewModel::resolveCustomFeed,
                        onAppendCustomFeedPosts = authConnectViewModel::appendCustomFeedPosts,
                        onCreateCustomFeed = ::launchCustomFeedCreation,
                        onUpdateCustomFeed = ::launchCustomFeedUpdate,
                        onDeleteCustomFeed = ::launchCustomFeedDeletion,
                        onResetCustomFeedCreate = authConnectViewModel::resetCustomFeedCreateState,
                        onResetCustomFeedManage = authConnectViewModel::resetCustomFeedManageState,
                        launcherShortcutTarget =
                            launcherShortcutTarget as? LauncherShortcutTarget.CustomFeed,
                        onLauncherShortcutConsumed = { launcherShortcutTarget = null },
                    ),
                    redditFeed = FeedEntryPoints(
                        redditFeedState = redditFeedState,
                        onRefreshRedditFeed = authConnectViewModel::refreshRedditFeed,
                        onAppendRedditFeed = authConnectViewModel::appendRedditFeed,
                        onRefreshRedditSearch = authConnectViewModel::refreshRedditSearch,
                        onAppendRedditSearch = authConnectViewModel::appendRedditSearch,
                        redditSubscriptionsState = redditSubscriptionsState,
                        onLoadRedditSubscriptions =
                            authConnectViewModel::loadRedditSubscriptions,
                        onLoadCachedRedditSubscriptions =
                            authConnectViewModel::loadCachedRedditSubscriptions,
                    ),
                    redditCommunity = CommunityEntryPoints(
                        onViewQuarantinedCommunity = authConnectViewModel::optInToQuarantinedCommunity,
                        randomNsfwCommunity = authConnectViewModel::randomNsfwCommunity,
                        onRefreshRedditCommunityFeed =
                            authConnectViewModel::refreshRedditCommunityFeed,
                        onAppendRedditCommunityFeed =
                            authConnectViewModel::appendRedditCommunityFeed,
                        redditCommunityWikiState = redditCommunityWikiState,
                        onLoadRedditCommunityWikiIndex =
                            authConnectViewModel::loadRedditCommunityWikiIndex,
                        onLoadRedditCommunityWikiPage =
                            authConnectViewModel::loadRedditCommunityWikiPage,
                        onAuthorizeRedditCommunityWiki =
                            ::launchCommunityWikiAuthorization,
                        redditCommunityInfoState = redditCommunityInfoState,
                        onLoadRedditCommunityInfo =
                            authConnectViewModel::loadRedditCommunityInfo,
                        communityMembershipState = communityMembershipState,
                        onChangeCommunityMembership =
                            authConnectViewModel::updateCommunityMembership,
                        onAuthorizeCommunityMembership =
                            ::launchCommunityMembershipAuthorization,
                        redditCommunityDiscoveryState = redditCommunityDiscoveryState,
                        onLoadRedditCommunityDiscovery =
                            authConnectViewModel::loadRedditCommunityDiscovery,
                        redditMentionSearchState = redditMentionSearchState,
                        onSearchMentions = authConnectViewModel::loadRedditMentionSearch,
                        redditFavoriteState = redditFavoriteState,
                        onUpdateRedditFavorite =
                            authConnectViewModel::updateRedditFavorite,
                        onDismissRedditFavoriteFailure =
                            authConnectViewModel::dismissRedditFavoriteFailure,
                        onAuthorizeRedditFavorite =
                            ::launchRedditFavoriteAuthorization,
                        onUpdateRedditCustomFeedFavorite =
                            authConnectViewModel::updateRedditCustomFeedFavorite,
                    ),
                    onRefreshRedditUserSubmitted =
                        authConnectViewModel::refreshRedditUserSubmitted,
                    onAppendRedditUserSubmitted =
                        authConnectViewModel::appendRedditUserSubmitted,
                    redditAccountHistoryState = redditAccountHistoryState,
                    onRefreshRedditAccountHistory =
                        authConnectViewModel::refreshRedditAccountHistory,
                    onAppendRedditAccountHistory =
                        authConnectViewModel::appendRedditAccountHistory,
                    redditSavedHistoryState = redditSavedHistoryState,
                    onRefreshRedditSavedHistory =
                        authConnectViewModel::refreshRedditSavedHistory,
                    onAppendRedditSavedHistory =
                        authConnectViewModel::appendRedditSavedHistory,
                    onResolveRedditProfileCommentTarget =
                        authConnectViewModel::resolveRedditProfileCommentTarget,
                    onResolveRedditSavedCommentTarget =
                        authConnectViewModel::resolveRedditSavedCommentTarget,
                    onAuthorizeRedditProfileHistory =
                        ::launchProfileHistoryAuthorization,
                    redditComments = CommentLoadingEntryPoints(
                        redditCommentsState = redditCommentsState,
                        onLoadRedditComments = authConnectViewModel::loadRedditComments,
                        onLoadCachedRedditComments =
                            authConnectViewModel::loadCachedRedditComments,
                        onRefreshRedditCommentsPreservingFocusedTarget =
                            authConnectViewModel::refreshRedditCommentsPreservingFocusedTarget,
                        onLoadFocusedRedditComment =
                            authConnectViewModel::loadFocusedRedditComment,
                        onLoadMoreRedditComments =
                            authConnectViewModel::loadMoreRedditComments,
                    ),
                    redditUserFlairState = redditUserFlairState,
                    onLoadRedditUserFlair = authConnectViewModel::loadUserFlair,
                    onSetRedditUserFlair = authConnectViewModel::setUserFlair,
                    redditModeration = ModerationQueueEntryPoints(
                        redditModerationState = redditModerationState,
                        onLoadRedditModQueue =
                            authConnectViewModel::loadRedditModQueue,
                        onAppendRedditModQueue =
                            authConnectViewModel::appendRedditModQueue,
                        onResolveRedditModerationTarget =
                            authConnectViewModel::resolveRedditModerationTarget,
                        moderationActionState = moderationActionState,
                        onModerateRedditQueueItem =
                            authConnectViewModel::moderateRedditQueueItem,
                        onRetryRedditModerationAction =
                            authConnectViewModel::retryRedditModerationAction,
                        onAuthorizeRedditModerationAction =
                            ::launchRedditModerationActionAuthorization,
                        removalReasonState = removalReasonState,
                        onLoadRedditRemovalReasons =
                            authConnectViewModel::loadRedditRemovalReasons,
                        onAuthorizeRedditRemovalReasons =
                            ::launchRedditRemovalReasonAuthorization,
                    ),
                    moderatorTools = moderatorToolsEntryPoints(
                        authConnectViewModel = authConnectViewModel,
                        launchRedditModeratorLogAuthorization = ::launchRedditModeratorLogAuthorization,
                        launchRedditModeratorUserManagementAuthorization =
                            ::launchRedditModeratorUserManagementAuthorization,
                    ),
                    // MODERATION02A5A6_MODERATOR_TEAM_RUNTIME_BUNDLE_CALLSITE_V1
                    moderatorTeamRuntime = moderatorTeamRuntimeEntryPoints(
                        authConnectViewModel = authConnectViewModel,
                        launchRedditModeratorInviteAuthorization = ::launchRedditModeratorInviteAuthorization,
                        launchRedditModeratorPermissionAuthorization =
                            ::launchRedditModeratorPermissionAuthorization,
                        launchRedditModeratorRemovalAuthorization = ::launchRedditModeratorRemovalAuthorization,
                    ),
                    redditModmail = ModmailEntryPoints(
                        redditModmailState = redditModmailState,
                        onLoadRedditModmail = authConnectViewModel::loadRedditModmail,
                        onAppendRedditModmail = authConnectViewModel::appendRedditModmail,
                        onSelectRedditModmailListMode = authConnectViewModel::selectRedditModmailListMode,
                        onOpenRedditModmailConversation =
                            authConnectViewModel::openRedditModmailConversation,
                        onSendRedditModmailMessage =
                            authConnectViewModel::sendRedditModmailMessage,
                        onSetRedditModmailArchived = authConnectViewModel::setRedditModmailArchived,
                        onSetRedditModmailUnread = authConnectViewModel::setRedditModmailUnread,
                        onVerifyRedditModmailReadState =
                            authConnectViewModel::verifyRedditModmailReadState,
                        onApplyRedditModmailTriage = authConnectViewModel::applyRedditModmailTriage,
                        onCloseRedditModmailConversation =
                            authConnectViewModel::closeRedditModmailConversation,
                        onAuthorizeRedditModmail = ::launchRedditModmailAuthorization,
                        modmailNotificationTarget = modmailNotificationTarget,
                        onModmailNotificationConsumed = { modmailNotificationTarget = null },
                    ),
                    postVoteState = postVoteState,
                    onVotePost = authConnectViewModel::votePost,
                    onRetryPostVote = authConnectViewModel::retryPostVote,
                    onAuthorizePostVote = ::launchPostVoteAuthorization,
                    postSaveState = postSaveState,
                    onSavePost = authConnectViewModel::savePost,
                    onRetryPostSave = authConnectViewModel::retryPostSave,
                    onAuthorizePostSave = ::launchPostSaveAuthorization,
                    contentSafetyState = contentSafetyState,
                    onSetPostHidden = authConnectViewModel::setPostHidden,
                    onReportContent = authConnectViewModel::reportContent,
                    onSetUserBlocked = authConnectViewModel::setUserBlocked,
                    onRefreshBlockedAccounts = authConnectViewModel::refreshBlockedAccounts,
                    onAuthorizeBlockedAccounts = ::launchBlockedAccountsAuthorization,
                    onAppendBlockedAccounts = authConnectViewModel::appendBlockedAccounts,
                    onRetryContentSafety = authConnectViewModel::retryContentSafetyMutation,
                    onAuthorizeContentSafety = ::launchContentSafetyAuthorization,
                    commentMutations = CommentMutationEntryPoints(
                        commentWriteState = commentWriteState,
                        onSubmitComment = authConnectViewModel::submitComment,
                        onAuthorizeCommentWrite = ::launchCommentAuthorization,
                        onCancelCommentWriteAuthorization =
                            authConnectViewModel::cancelCommentAuthorization,
                        onResetCommentWriteState = authConnectViewModel::resetCommentWriteState,
                        commentEditState = commentEditState,
                        onEditComment = authConnectViewModel::editComment,
                        onAuthorizeCommentEdit = ::launchCommentEditAuthorization,
                        onCancelCommentEditAuthorization =
                            authConnectViewModel::cancelCommentEditAuthorization,
                        onResetCommentEditState = authConnectViewModel::resetCommentEditState,
                        onCheckCommentEdit = authConnectViewModel::checkCommentEdit,
                        commentDeleteState = commentDeleteState,
                        onDeleteComment = authConnectViewModel::deleteComment,
                        onAuthorizeCommentDelete = ::launchCommentDeleteAuthorization,
                        onCancelCommentDeleteAuthorization = authConnectViewModel::cancelCommentDeleteAuthorization,
                        onResetCommentDeleteState = authConnectViewModel::resetCommentDeleteState,
                        commentMediaPolicies = commentMediaPolicies,
                        onLoadCommentMediaPolicy = authConnectViewModel::loadCommentMediaPolicy,
                    ),
                    postMutations = PostMutationEntryPoints(
                        postEditState = postEditState,
                        onEditPost = authConnectViewModel::editPost,
                        onAuthorizePostEdit = ::launchPostEditAuthorization,
                        onCancelPostEditAuthorization =
                            authConnectViewModel::cancelPostEditAuthorization,
                        onResetPostEditState = authConnectViewModel::resetPostEditState,
                        onCheckPostEdit = authConnectViewModel::checkPostEdit,
                        postDeleteState = postDeleteState,
                        onDeletePost = authConnectViewModel::deletePost,
                        onAuthorizePostDelete = ::launchPostDeleteAuthorization,
                        onCancelPostDeleteAuthorization =
                            authConnectViewModel::cancelPostDeleteAuthorization,
                        onResetPostDeleteState = authConnectViewModel::resetPostDeleteState,
                        onCheckPostDelete = authConnectViewModel::checkPostDelete,
                        postManageState = postManageState,
                        onLoadPostManagement = authConnectViewModel::loadPostManagement,
                        onMutatePostManagement = authConnectViewModel::mutatePostManagement,
                        onAuthorizePostManagement = ::launchPostManageAuthorization,
                        onCancelPostManageAuthorization =
                            authConnectViewModel::cancelPostManageAuthorization,
                        onResetPostManageState = authConnectViewModel::resetPostManageState,
                    ),
                    postComposerWriteRuntime =
                        app.subshell.prototype.ui.PostComposerWriteRuntimeEntryPoints(
                            textPostWriteState = textPostWriteState,
                            crosspostReviewState = crosspostReviewState,
                            onReviewCrosspost =
                                authConnectViewModel::reviewCrosspostDraft,
                            onPublishReviewedCrosspost =
                                authConnectViewModel::publishReviewedCrosspost,
                            onInvalidateCrosspostReview =
                                authConnectViewModel::invalidateCrosspostReview,
                        ),
                    composerPolicy = composerPolicy,
                    onLoadComposerPolicy = authConnectViewModel::loadComposerPolicy,
                    composerCommunitySearch = composerCommunitySearch,
                    onSearchComposerCommunities =
                        authConnectViewModel::searchComposerCommunities,
                    onSubmitTextPost = authConnectViewModel::submitTextPost,
                    onReconcileTextPostSubmit = authConnectViewModel::reconcileTextPostSubmit,
                    onAuthorizeTextPostWrite = ::launchTextPostAuthorization,
                    onAuthorizeComposerPostRequirements =
                        ::launchComposerPostRequirementsAuthorization,
                    onAuthorizeComposerFlair = ::launchComposerFlairAuthorization,
                    onAuthorizeRedditUserFlair = ::launchUserFlairAuthorization,
                    onCancelTextPostWriteAuthorization =
                        authConnectViewModel::cancelTextPostAuthorization,
                    onResetTextPostWriteState = authConnectViewModel::resetTextPostWriteState,
                    redditInbox = InboxEntryPoints(
                        redditInboxState = redditInboxState,
                        onLoadRedditInbox = authConnectViewModel::loadRedditInbox,
                        onAppendRedditInbox = authConnectViewModel::appendRedditInbox,
                        onMarkRedditInboxRead = authConnectViewModel::markRedditInboxRead,
                        onResolveRedditInboxTarget = authConnectViewModel::resolveRedditInboxTarget,
                        onAuthorizeRedditInbox = ::launchInboxAuthorization,
                        onCancelRedditInboxAuthorization =
                            authConnectViewModel::cancelInboxAuthorization,
                        inboxNotificationTarget = inboxNotificationTarget,
                        onInboxNotificationConsumed = { inboxNotificationTarget = null },
                        launcherShortcutTarget = launcherShortcutTarget as? LauncherShortcutTarget.Inbox,
                        onLauncherShortcutConsumed = { launcherShortcutTarget = null },
                    ),
                    redditMessaging = MessagingEntryPoints(
                        messageReplyState = messageReplyState,
                        directChatLiveState = directChatLiveState,
                        onSendRedditMessageReply = authConnectViewModel::sendRedditMessageReply,
                        onResetRedditMessageReplyState =
                            authConnectViewModel::resetRedditMessageReplyState,
                        onStartRedditDirectChatLive =
                            authConnectViewModel::startRedditDirectChatLive,
                        onStopRedditDirectChatLive =
                            authConnectViewModel::stopRedditDirectChatLive,
                        onUpdateRedditDirectChatTyping =
                            authConnectViewModel::updateRedditDirectChatTyping,
                        onDeleteRedditDirectChatMessage =
                            authConnectViewModel::deleteRedditDirectChatMessage,
                        onResetRedditDirectChatDeleteFailure =
                            authConnectViewModel::resetRedditDirectChatDeleteFailure,
                        onReactRedditDirectChatMessage =
                            authConnectViewModel::reactRedditDirectChatMessage,
                        onResetRedditDirectChatReactionFailure =
                            authConnectViewModel::resetRedditDirectChatReactionFailure,
                    ),
                    directChatStartState = directChatStartState,
                    onStartRedditDirectChat = authConnectViewModel::startRedditDirectChat,
                    onResetRedditDirectChatStartState =
                        authConnectViewModel::resetRedditDirectChatStartState,
                    onResolveRedditNavigationTarget =
                        authConnectViewModel::resolveRedditNavigationTargetPost,
                    onRequestInboxNotificationPermission = ::requestInboxNotificationPermission,
                    onRedditConfigurationChanged =
                        ::reloadRedditConfigurationHost,
                    debugFocusedCommentLaunch = debugFocusedCommentLaunch,
                    onDebugFocusedCommentConsumed = { debugFocusedCommentLaunch = null },
                    redditIntentLaunch = redditIntentLaunch,
                    onRedditIntentConsumed = { redditIntentLaunch = null },
                    shareIntoSubshellLaunch = shareIntoSubshellLaunch,
                    onShareIntoSubshellConsumed = { shareIntoSubshellLaunch = null },
                    onResolveRedditShareLink = { url, onResolved ->
                        lifecycleScope.launch { onResolved(redditShareLinkResolver.resolve(url)) }
                    },
                    redditLinkHandlingState = redditLinkHandlingState,
                    redditLinkHandlingPromptSeen = redditLinkHandlingPromptSeen,
                    onOpenRedditLinkSettings = ::openRedditLinkSettings,
                    onDismissRedditLinkPrompt = ::dismissRedditLinkPrompt,
                )
                }
            }
            }
            if (appLock.locked) {
                SubshellTheme(
                    mode = appThemeSettings.mode,
                    dynamicColor = appThemeSettings.dynamicColor,
                    pureBlack = appThemeSettings.pureBlack,
                    textScale = appThemeSettings.textScale,
                    manageSystemBarContrast = false,
                ) {
                    AppLockOverlay(
                        onUnlock = ::requestAppUnlock,
                        onLeave = { moveTaskToBack(true) },
                    )
                }
            }
        }
        // INTENT01_RESTORED_ACTIVITY_SKIPS_LAUNCH_INTENT_V1 (#122): the consumers
        // mark an intent as handled only on the in-process copy. After process
        // death the restored activity gets the original launch intent back, so
        // a notification, shortcut, share, link or auth token fired again on
        // top of the restored screen. A restored activity has handled it.
        val launchIntentHandled = savedInstanceState != null
        if (!launchIntentHandled) {
            consumeUpdatePresentationIntent(
                context = this,
                intent = launchIntent,
                currentVersionCode = BuildConfig.VERSION_CODE,
                debug = BuildConfig.DEBUG,
            )
            consumeDebugFocusedCommentIntent(launchIntent)
            consumeInboxNotificationIntent(launchIntent)
            consumeModmailNotificationIntent(launchIntent)
            consumeLauncherShortcutIntent(launchIntent)
            consumeShareIntent(launchIntent)
            consumeRedditViewIntent(launchIntent, delivery = "cold")
        }
        if (launchIntentHandled || !consumeAuthCallbackIntent(launchIntent)) {
            authConnectViewModel.resumeStoredCallbackIfPresent()
        }
        val queued = intentsBeforeAuthRuntime.toList()
        intentsBeforeAuthRuntime.clear()
        queued.forEach(::handleNewIntent)
    }

    override fun onStart() {
        super.onStart()
        // UPDATE07_CHECK_ON_EVERY_RETURN_V1 (owner): not only on a cold start;
        // a return from the background checks too once the last check is stale.
        UpdateCheckScheduler.enqueueForegroundCheckIfStale(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        refreshRedditLinkHandlingState()
    }

    // NOTIFY01A2_ACTIVITY_OWNED_RECONCILIATION_DESTROY_V2
    override fun onDestroy() {
        notificationReadReconciliationJob.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // AUTHSTART01: handled once the auth runtime is ready.
        if (!authRuntimeReady) {
            intentsBeforeAuthRuntime += intent
            return
        }
        handleNewIntent(intent)
    }

    private fun handleNewIntent(intent: Intent) {
        if (!consumeUpdatePresentationIntent(
                context = this,
                intent = intent,
                currentVersionCode = BuildConfig.VERSION_CODE,
                debug = BuildConfig.DEBUG,
            ) &&
            !consumeDebugFocusedCommentIntent(intent) &&
            !consumeInboxNotificationIntent(intent) &&
            !consumeModmailNotificationIntent(intent) &&
            !consumeLauncherShortcutIntent(intent) &&
            !consumeShareIntent(intent) &&
            !consumeRedditViewIntent(intent, delivery = "warm")
        ) {
            consumeAuthCallbackIntent(intent)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
        videoPlaybackViewModel.coordinator.onHostConfigurationChanged(newConfig)
    }

    private fun refreshRedditLinkHandlingState() {
        val current =
            readRedditLinkHandlingState(applicationContext)
        redditLinkHandlingState = current
        if (!current.supported) {
            redditLinkHandlingPromptSeen = true
        } else {
            if (current.status == RedditLinkHandlingStatus.ENABLED) {
                redditLinkHandlingPromptStore.markSeen()
            }
            redditLinkHandlingPromptSeen =
                redditLinkHandlingPromptStore.wasSeen()
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                "SubshellDeepLink",
                "phase=link_handling_state supported=${current.supported} " +
                    "status=${current.status} selected=${current.selectedHostCount}/" +
                    "${current.supportedHostCount} promptSeen=$redditLinkHandlingPromptSeen",
            )
        }
    }

    private fun openRedditLinkSettings() {
        openRedditLinkHandlingSettings(this)
    }

    private fun dismissRedditLinkPrompt() {
        redditLinkHandlingPromptStore.markSeen()
        redditLinkHandlingPromptSeen = true
    }

    private fun reloadRedditConfigurationHost() {
        // AUTH_BUG01R1_FRESH_CONFIGURATION_HOST_V1
        startActivity(
            Intent.makeRestartActivityTask(componentName),
        )
    }

    // AUTHLAUNCH01_SINGLE_AUTH_BROWSER_LAUNCH_SHAPE_V1: every authorization
    // launch opens the auth tab the same way; only the begin call, the
    // failure callback and whether a fresh account session is propagated
    // differ. Without propagation the browser gets the launcher's default
    // (false), exactly as the individual launches did.
    // LAUNCHASYNC01_AUTHORIZE_TAPS_OFF_MAIN_V1 (OCB-474): an authorization
    // begins in a coroutine; its store work (scope checks, the encrypted
    // pending record) runs on IO, the state changes and the browser on Main.
    private fun launchAuthBrowser(
        begin: suspend () -> AuthBrowserLaunchResult,
        propagateFreshAccountSession: Boolean = false,
        onLaunchFailed: () -> Unit,
    ) {
        lifecycleScope.launch { dispatchLaunchedAuthBrowser(begin(), propagateFreshAccountSession, onLaunchFailed) }
    }

    private fun dispatchLaunchedAuthBrowser(
        launch: AuthBrowserLaunchResult,
        propagateFreshAccountSession: Boolean,
        onLaunchFailed: () -> Unit,
    ) {
        dispatchAuthBrowserLaunch(launch, onLaunchFailed) { launch ->
            AuthBrowserLauncher.launch(
                context = this,
                launcher = authTabLauncher,
                authorizationUrl = launch.authorizationUrl,
                clientMode = launch.clientMode,
                freshAccountSession = launch.freshAccountSession && propagateFreshAccountSession,
            )
        }
    }

    private fun launchReconnectAccount(
        accountStableId: String,
    ) {
        launchAuthBrowser(
            { authConnectViewModel.beginReconnect(accountStableId) },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.browserLaunchFailed()
        }
    }

    private fun launchConnectAccount() {
        launchAuthBrowser(
            { authConnectViewModel.beginConnect() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.browserLaunchFailed()
        }
    }

    private fun launchPostManageAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostManageAuthorization() },
        ) {
            authConnectViewModel.postManageAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostDeleteAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostDeleteAuthorization() },
        ) {
            authConnectViewModel.postDeleteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostEditAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostEditAuthorization() },
        ) {
            authConnectViewModel.postEditAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentDeleteAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginCommentDeleteAuthorization() },
        ) {
            authConnectViewModel.commentDeleteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentEditAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginCommentEditAuthorization() },
        ) {
            authConnectViewModel.commentEditAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginCommentAuthorization() },
        ) {
            authConnectViewModel.commentAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchTextPostAuthorization(request: TextPostWriteRequest) {
        launchAuthBrowser(
            { authConnectViewModel.beginTextPostAuthorization(request) },
        ) {
            authConnectViewModel.textPostAuthorizationBrowserLaunchFailed()
        }
    }


    // The answer is "asked to begin"; the outcome follows in the composer's
    // requirements state (no caller reads the Boolean).
    private fun launchComposerPostRequirementsAuthorization(
        accountStableId: String,
    ): Boolean {
        lifecycleScope.launch {
            dispatchComposerPostRequirementsAuthorization(
                authConnectViewModel.beginComposerPostRequirementsAuthorization(accountStableId),
            )
        }
        return true
    }

    private fun dispatchComposerPostRequirementsAuthorization(
        launch: AuthBrowserLaunchResult,
    ): Boolean = when (launch) {
        is AuthBrowserLaunchResult.Ready -> runCatching {
            AuthBrowserLauncher.launch(
                context = this,
                launcher = authTabLauncher,
                authorizationUrl = launch.authorizationUrl,
                clientMode = launch.clientMode,
            )
        }.fold(
            onSuccess = { true },
            onFailure = {
                authConnectViewModel
                    .composerPostRequirementsAuthorizationBrowserLaunchFailed()
                false
            },
        )
        AuthBrowserLaunchResult.NotStarted -> false
    }

    private fun launchComposerFlairAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { authConnectViewModel.beginComposerFlairAuthorization(accountStableId) },
        ) {
            authConnectViewModel.composerFlairAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchUserFlairAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { authConnectViewModel.beginUserFlairAuthorization(accountStableId) },
        ) {
            authConnectViewModel.userFlairAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCustomFeedCreation(request: CustomFeedCreateRequest) {
        launchCustomFeedAuthorization { authConnectViewModel.beginCustomFeedCreation(request) }
    }

    private fun launchCustomFeedUpdate(request: CustomFeedUpdateRequest) {
        launchCustomFeedAuthorization { authConnectViewModel.beginCustomFeedUpdate(request) }
    }

    private fun launchCustomFeedDeletion(request: CustomFeedDeleteRequest) {
        launchCustomFeedAuthorization { authConnectViewModel.beginCustomFeedDeletion(request) }
    }

    private fun launchCustomFeedAuthorization(begin: suspend () -> AuthBrowserLaunchResult) {
        lifecycleScope.launch { dispatchCustomFeedAuthorization(begin()) }
    }

    private fun dispatchCustomFeedAuthorization(launch: AuthBrowserLaunchResult) {
        when (launch) {
            is AuthBrowserLaunchResult.Ready -> {
                runCatching {
                    AuthBrowserLauncher.launch(
                        context = this,
                        launcher = authTabLauncher,
                        authorizationUrl = launch.authorizationUrl,
                        clientMode = launch.clientMode,
                    )
                }.onFailure {
                    authConnectViewModel.customFeedAuthorizationBrowserLaunchFailed()
                }
            }
            AuthBrowserLaunchResult.NotStarted -> Unit
        }
    }

    private fun launchCommunityMembershipAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginCommunityMembershipAuthorization() },
        ) {
            authConnectViewModel.communityMembershipAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchRedditFavoriteAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditFavoriteAuthorization() },
        ) {
            authConnectViewModel.redditFavoriteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostVoteAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostVoteAuthorization() },
        ) {
            authConnectViewModel.postVoteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostSaveAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostSaveAuthorization() },
        ) {
            authConnectViewModel.postSaveAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostFollowAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginPostFollowAuthorization() },
        ) {
            authConnectViewModel.postFollowAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A2_MODERATION_ACTION_AUTH_BROWSER_V1
    private fun launchRedditModerationActionAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModerationActionAuthorization() },
        ) {
            authConnectViewModel.redditModerationActionAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A21_REMOVAL_REASON_AUTH_BROWSER_V1
    private fun launchRedditRemovalReasonAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditRemovalReasonAuthorization() },
        ) {
            authConnectViewModel.redditRemovalReasonAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A4_MOD_LOG_AUTH_BROWSER_V1
    private fun launchRedditModeratorLogAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModeratorLogAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModeratorLogAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A1_USER_MANAGEMENT_AUTH_BROWSER_V1
    private fun launchRedditModeratorUserManagementAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModeratorUserManagementAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModeratorUserManagementAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A5_MODERATOR_TEAM_AUTH_BROWSER_V1
    private fun launchRedditModeratorInviteAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModeratorInviteAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModeratorInviteAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A6_MODERATOR_PERMISSION_AUTH_BROWSER_V1
    private fun launchRedditModeratorPermissionAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModeratorPermissionAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModeratorPermissionAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A7_MODERATOR_REMOVAL_AUTH_BROWSER_V1
    private fun launchRedditModeratorRemovalAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModeratorRemovalAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModeratorRemovalAuthorizationBrowserLaunchFailed()
        }
    }

    // MODMAIL01A1_ACCOUNT_SAFE_BROWSER_AFFINITY_V1
    private fun launchRedditModmailAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditModmailAuthorization(accountStableId) },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.redditModmailAuthorizationBrowserLaunchFailed()
        }
    }

    // SAFETY01A3_CONTENT_SAFETY_AUTH_BROWSER_V1
    // OCB414_BLOCKED_LIST_CAN_ASK_FOR_AUTHORIZATION_V1
    private fun launchBlockedAccountsAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { authConnectViewModel.beginBlockedAccountsAuthorization(accountStableId) },
        ) {
            authConnectViewModel.blockedAccountsAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchContentSafetyAuthorization() {
        launchAuthBrowser(
            { authConnectViewModel.beginContentSafetyAuthorization() },
        ) {
            authConnectViewModel.contentSafetyAuthorizationBrowserLaunchFailed()
        }
    }

    // WIKI01A2_COMMUNITY_WIKI_AUTH_BROWSER_V1
    private fun launchCommunityWikiAuthorization(
        accountStableId: String,
        communityName: String,
    ) {
        launchAuthBrowser(
            { authConnectViewModel.beginRedditCommunityWikiAuthorization(
                accountStableId = accountStableId,
                rawCommunityName = communityName,
            ) },
        ) {
            authConnectViewModel.redditCommunityWikiAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchProfileHistoryAuthorization(
        accountStableId: String,
        username: String,
    ) {
        launchAuthBrowser(
            { authConnectViewModel.beginProfileHistoryAuthorization(
                accountStableId = accountStableId,
                username = username,
            ) },
        ) {
            authConnectViewModel.profileHistoryAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A221_ACCOUNT_SAFE_INBOX_BROWSER_AFFINITY_V1
    private fun launchInboxAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { authConnectViewModel.beginInboxAuthorization(accountStableId) },
            propagateFreshAccountSession = true,
        ) {
            authConnectViewModel.inboxAuthorizationBrowserLaunchFailed()
        }
    }

    private fun consumeDebugFocusedCommentIntent(candidate: Intent?): Boolean {
        if (!BuildConfig.DEBUG) return false
        val target = debugFocusedCommentLaunchOrNull(
            postId = candidate?.getStringExtra(DEBUG_FOCUSED_POST_ID_EXTRA),
            commentId = candidate?.getStringExtra(DEBUG_FOCUSED_COMMENT_ID_EXTRA),
            username = candidate?.getStringExtra(DEBUG_FOCUSED_USERNAME_EXTRA),
            requestId = SystemClock.elapsedRealtimeNanos(),
        ) ?: return false
        candidate?.removeExtra(DEBUG_FOCUSED_POST_ID_EXTRA)
        candidate?.removeExtra(DEBUG_FOCUSED_COMMENT_ID_EXTRA)
        candidate?.removeExtra(DEBUG_FOCUSED_USERNAME_EXTRA)
        debugFocusedCommentLaunch = target
        Log.d(
            "SubshellCommentNav",
            "phase=adb_launch_received post=${target.postId} target=${target.commentId} " +
                "username=${target.username} request=${target.requestId}",
        )
        return true
    }

    private fun consumeRedditViewIntent(
        candidate: Intent?,
        delivery: String,
    ): Boolean {
        val rawUri = candidate?.dataString
        if (RedditExternalPollHandoffStore(this).consumeIfMatching(rawUri)) {
            candidate?.data = null
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SubshellDeepLink",
                    "phase=external_poll_rebound_suppressed delivery=$delivery uri=$rawUri",
                )
            }
            window.decorView.post {
                moveTaskToBack(true)
            }
            return true
        }

        val target = redditViewIntentLaunchOrNull(
            action = candidate?.action,
            rawUri = rawUri,
            requestId = SystemClock.elapsedRealtimeNanos(),
        ) ?: return false
        redditIntentLaunch = target
        if (BuildConfig.DEBUG) {
            Log.d(
                "SubshellDeepLink",
                "phase=intent_received delivery=$delivery request=${target.requestId} uri=${target.rawUri}",
            )
        }
        return true
    }

    private fun consumeInboxNotificationIntent(candidate: Intent?): Boolean {
        val target = takeInboxNotificationTarget(candidate) ?: return false
        inboxNotificationTarget = target
        return true
    }

    private fun consumeModmailNotificationIntent(candidate: Intent?): Boolean {
        val target = takeModmailNotificationTarget(candidate) ?: return false
        modmailNotificationTarget = target
        return true
    }

    // ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1
    // ISSUE33_SHARE_INTO_SUBSHELL_V1: a share opens the composer; the copy of
    // any shared images runs off the main thread. The action is cleared so a
    // recreated activity does not take the same share twice.
    private fun consumeShareIntent(candidate: Intent?): Boolean {
        if (candidate == null || !app.subshell.prototype.share.isShareIntent(candidate)) return false
        val shared = Intent(candidate)
        candidate.action = null
        // OCB-572: a share relaunched from Recents after process death was
        // already taken; the restored screen stays.
        if (shared.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return false
        // OCB-393: read by a ViewModel, so a recreation or a bad share neither
        // loses it nor crashes the app.
        shareImport.read(applicationContext, shared, requestId = SystemClock.elapsedRealtimeNanos())
        return true
    }

    private fun consumeLauncherShortcutIntent(candidate: Intent?): Boolean {
        val target = takeLauncherShortcutTarget(
            candidate = candidate,
            requestId = SystemClock.elapsedRealtimeNanos(),
        ) ?: return false
        launcherShortcutTarget = target
        return true
    }

    private fun consumeAuthCallbackIntent(candidate: Intent?): Boolean {
        val resultToken = candidate
            ?.getStringExtra(AUTH_CALLBACK_RESULT_EXTRA)
            ?.takeIf(String::isNotBlank)
            ?: return false
        candidate.removeExtra(AUTH_CALLBACK_RESULT_EXTRA)
        authConnectViewModel.handleCallbackResultToken(resultToken)
        return true
    }
}

private const val INBOX_NOTIFICATION_PERMISSION_REQUEST_CODE = 0x1B22
private const val LEGACY_STORAGE_WRITE_REQUEST_CODE = 0x0084
private const val DEBUG_FOCUSED_POST_ID_EXTRA = "subshell.debug.focused_post_id"
private const val DEBUG_FOCUSED_COMMENT_ID_EXTRA = "subshell.debug.focused_comment_id"
private const val DEBUG_FOCUSED_USERNAME_EXTRA = "subshell.debug.focused_username"
