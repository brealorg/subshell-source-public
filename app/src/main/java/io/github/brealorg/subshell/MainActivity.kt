package io.github.brealorg.subshell

import io.github.brealorg.subshell.media.RemoteImageLoader
import io.github.brealorg.subshell.offline.OfflineImageStore
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.brealorg.subshell.data.diagnostics.CrashRecorder
import io.github.brealorg.subshell.ui.app.JANK_SCREEN_STATE
import io.github.brealorg.subshell.data.diagnostics.JankTally
import io.github.brealorg.subshell.data.diagnostics.DiagnosticEventStore
import androidx.metrics.performance.JankStats
import io.github.brealorg.subshell.data.diagnostics.ListingSkipReports
import io.github.brealorg.subshell.inbox.takeInboxNotificationTarget
import io.github.brealorg.subshell.inbox.takeModmailNotificationTarget
import io.github.brealorg.subshell.inbox.InboxNotificationTarget
import io.github.brealorg.subshell.inbox.InboxNotificationCenter
import io.github.brealorg.subshell.inbox.ModmailNotificationTarget
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import io.github.brealorg.subshell.auth.AuthRuntimeKey
import io.github.brealorg.subshell.auth.CommunitiesViewModel
import io.github.brealorg.subshell.auth.InboxViewModel
import io.github.brealorg.subshell.auth.ModerationViewModel
import io.github.brealorg.subshell.auth.PostComposerViewModel
import io.github.brealorg.subshell.auth.ReadingViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.brealorg.subshell.applock.AppLockController
import io.github.brealorg.subshell.applock.AppUnlockPromptGate
import io.github.brealorg.subshell.applock.AppLockEntryPoints
import io.github.brealorg.subshell.applock.AppLockOverlay
import io.github.brealorg.subshell.applock.AppLockSettings
import io.github.brealorg.subshell.applock.AppLockSettingsStore
import io.github.brealorg.subshell.applock.AppLockViewModel
import io.github.brealorg.subshell.applock.LocalAppLock
import io.github.brealorg.subshell.applock.LocalAppLocked
import io.github.brealorg.subshell.applock.LocalAppLockedNow
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import io.github.brealorg.subshell.ui.theme.SubshellThemeSettings
import io.github.brealorg.subshell.ui.theme.UiScaleContext
import io.github.brealorg.subshell.ui.theme.setScaledContent
import io.github.brealorg.subshell.auth.takeAuthCallbackResult
import io.github.brealorg.subshell.auth.AuthBrowserLauncher
import io.github.brealorg.subshell.auth.dispatchAuthBrowserLaunch
import io.github.brealorg.subshell.auth.dispatchAuthTabResult
import io.github.brealorg.subshell.auth.processAuthCallbackOffMain
import io.github.brealorg.subshell.auth.AuthRuntimePreparation
import io.github.brealorg.subshell.auth.AuthStartGate
import io.github.brealorg.subshell.auth.AuthConnectViewModel
import io.github.brealorg.subshell.data.account.TextPostWriteRequest
import io.github.brealorg.subshell.auth.toResultToken
import io.github.brealorg.subshell.data.auth.AuthBrowserLaunchResult
import io.github.brealorg.subshell.media.VideoPlaybackViewModel
import io.github.brealorg.subshell.ui.DebugFocusedCommentLaunch
import io.github.brealorg.subshell.ui.RedditIntentLaunch
import io.github.brealorg.subshell.ui.LEGACY_STORAGE_WRITE_PERMISSION
import io.github.brealorg.subshell.ui.LegacyStorageWritePermissionRequester
import io.github.brealorg.subshell.ui.legacyStorageWritePermissionNeeded
import io.github.brealorg.subshell.ui.RedditLinkHandlingPromptStore
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.RedditLinkHandlingStatus
import io.github.brealorg.subshell.ui.RedditExternalPollHandoffStore
import io.github.brealorg.subshell.ui.openRedditLinkHandlingSettings
import io.github.brealorg.subshell.ui.readRedditLinkHandlingState
import io.github.brealorg.subshell.ui.redditViewIntentLaunchOrNull
import io.github.brealorg.subshell.ui.SubshellApp
import io.github.brealorg.subshell.ui.debugFocusedCommentLaunchOrNull
import io.github.brealorg.subshell.ui.app.AccountDirectoryGate
import io.github.brealorg.subshell.ui.app.ModmailEntryPoints
import io.github.brealorg.subshell.ui.app.ModerationQueueEntryPoints
import io.github.brealorg.subshell.ui.app.InboxEntryPoints
import io.github.brealorg.subshell.ui.app.CustomFeedsEntryPoints
import io.github.brealorg.subshell.shortcuts.LauncherShortcutTarget
import io.github.brealorg.subshell.shortcuts.takeLauncherShortcutTarget
import io.github.brealorg.subshell.ui.app.MessagingEntryPoints
import io.github.brealorg.subshell.ui.app.CommunityEntryPoints
import io.github.brealorg.subshell.ui.app.FeedEntryPoints
import io.github.brealorg.subshell.ui.app.CommentLoadingEntryPoints
import io.github.brealorg.subshell.ui.app.CommentMutationEntryPoints
import io.github.brealorg.subshell.ui.app.PostMutationEntryPoints
import io.github.brealorg.subshell.ui.app.ModeratorToolsEntryPoints
import io.github.brealorg.subshell.ui.app.AccountConnectEntryPoints
import io.github.brealorg.subshell.data.account.CustomFeedCreateRequest
import io.github.brealorg.subshell.data.account.CustomFeedDeleteRequest
import io.github.brealorg.subshell.data.account.CustomFeedUpdateRequest
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import io.github.brealorg.subshell.inbox.startNotificationReadReconciliation
import io.github.brealorg.subshell.update.UpdateCheckScheduler
import io.github.brealorg.subshell.update.UpdateRuntimePolicy
import io.github.brealorg.subshell.update.UpdateStateStore
import io.github.brealorg.subshell.update.consumeUpdatePresentationIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.brealorg.subshell.ui.app.StartupPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : FragmentActivity(), LegacyStorageWritePermissionRequester {
    private val videoPlaybackViewModel: VideoPlaybackViewModel by lazy {
        ViewModelProvider(this, VideoPlaybackViewModel.Factory)[VideoPlaybackViewModel::class.java]
    }
    // AUTHSPLIT01_MODERATION_VIEWMODEL_V1: the area ViewModels live in the same
    // store and share the runtime AuthConnectViewModel owns. They are made with
    // it, so their capability callback routes are registered before any
    // callback can be routed.
    // AUTHSTART01_EVERY_WINDOW_BUILDS_ITS_RUNTIME_OFF_MAIN_V1: set only while onCreate
    // makes the ViewModel with the runtime it got on IO.
    private var authRuntimeForTheViewModel: io.github.brealorg.subshell.auth.AuthApplicationRuntime? = null
    private val authConnectViewModel: AuthConnectViewModel by lazy {
        ViewModelProvider(
            this,
            AuthConnectViewModel.factory(authRuntimeForTheViewModel),
        )[AuthConnectViewModel::class.java]
            .also { auth ->
                areaViewModels(auth, ModerationViewModel.Factory)[ModerationViewModel::class]
                areaViewModels(auth, InboxViewModel.Factory)[InboxViewModel::class]
                areaViewModels(auth, PostComposerViewModel.Factory)[PostComposerViewModel::class]
                areaViewModels(auth, CommunitiesViewModel.Factory)[CommunitiesViewModel::class]
                areaViewModels(auth, ReadingViewModel.Factory)[ReadingViewModel::class]
            }
    }
    private val moderationViewModel: ModerationViewModel by lazy {
        areaViewModels(authConnectViewModel, ModerationViewModel.Factory)[ModerationViewModel::class]
    }
    private val inboxViewModel: InboxViewModel by lazy {
        areaViewModels(authConnectViewModel, InboxViewModel.Factory)[InboxViewModel::class]
    }
    private val postComposerViewModel: PostComposerViewModel by lazy {
        areaViewModels(authConnectViewModel, PostComposerViewModel.Factory)[PostComposerViewModel::class]
    }
    private val communitiesViewModel: CommunitiesViewModel by lazy {
        areaViewModels(authConnectViewModel, CommunitiesViewModel.Factory)[CommunitiesViewModel::class]
    }
    private val readingViewModel: ReadingViewModel by lazy {
        areaViewModels(authConnectViewModel, ReadingViewModel.Factory)[ReadingViewModel::class]
    }

    private fun areaViewModels(
        auth: AuthConnectViewModel,
        factory: ViewModelProvider.Factory,
    ): ViewModelProvider =
        ViewModelProvider.create(
            store = viewModelStore,
            factory = factory,
            extras = MutableCreationExtras(defaultViewModelCreationExtras).apply {
                set(AuthRuntimeKey, auth.sharedRuntime)
            },
        )
    // ISSUE38_APP_LOCK_V1
    private val appLock: AppLockController by lazy {
        ViewModelProvider(this)[AppLockViewModel::class.java].controller(applicationContext)
    }
    private var deviceUnlockAvailable by mutableStateOf(false)
    private var appThemeSettings by mutableStateOf(SubshellThemeSettings())
    private var uiScaleContext: UiScaleContext? = null
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
            // ISSUE267_INBOX_AND_POSTS_WIDGETS_V1: what the reader read, opened
            // or switched to here shows on the widgets once they are back home.
            if (!isChangingConfigurations) {
                io.github.brealorg.subshell.widget.SubshellWidgets.requestRefresh(applicationContext)
            }
        }
    }
    // ISSUE248_JANK_SUMMARY_PER_FOREGROUND_SESSION_V1 (#248): JankStats counts
    // slow frames while the app is in front; each foreground session that had
    // any is written to the local diagnostics log when the app leaves the front.
    private val jankTally = JankTally()
    private var jankStats: JankStats? = null
    private val jankLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            jankStats?.isTrackingEnabled = true
        }

        override fun onPause(owner: LifecycleOwner) {
            jankStats?.isTrackingEnabled = false
        }

        override fun onStop(owner: LifecycleOwner) {
            val summary = jankTally.drain() ?: return
            val appContext = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { DiagnosticEventStore(appContext).recordJankSummary(summary) }
            }
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
    private val shareImport: io.github.brealorg.subshell.share.ShareImportViewModel by lazy {
        ViewModelProvider(this)[io.github.brealorg.subshell.share.ShareImportViewModel::class.java]
    }
    private var shareIntoSubshellLaunch: io.github.brealorg.subshell.share.ShareIntoSubshellLaunch?
        get() = shareImport.launch
        set(value) {
            shareImport.launch = value
        }
    // REDDITURL03_SHARE_LINK_REDIRECT_V1
    private val redditShareLinkResolver by lazy {
        io.github.brealorg.subshell.data.network.reddit.RedditShareLinkResolver()
    }

    private val redditLinkHandlingPromptStore by lazy {
        RedditLinkHandlingPromptStore(applicationContext)
    }
    private var redditLinkHandlingState by
        mutableStateOf(RedditLinkHandlingState())
    private var redditLinkHandlingPromptSeen by
        mutableStateOf(true)
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
        }
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
        // COMMIT01_APP_LOCK_SETTINGS_WRITE_OFF_MAIN_V1 (#243): the controller
        // holds the new settings at once; the file write and the redaction of
        // shown notifications run on one background thread, in tap order.
        val saved = controller.settings
        lifecycleScope.launch(APP_LOCK_SETTINGS_WRITES) {
            AppLockSettingsStore.write(applicationContext, saved)
        }
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
        // CRASH01_CRASHES_ARE_RECORDED_LOCALLY_V1: first, so a crash anywhere
        // after this point is recorded (once per process).
        CrashRecorder.install(applicationContext)
        // ISSUE265_SAVE_FOR_OFFLINE_V1
        RemoteImageLoader.offlineImageStore = OfflineImageStore.forContext(applicationContext)
        ListingSkipReports.install(applicationContext)
        installDebugUriGrantCheck(BuildConfig.DEBUG)
        installDebugMainThreadCheck(BuildConfig.DEBUG)
        // STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243)
        StartupPreferences.start(applicationContext)
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
        // A window without its AuthConnectViewModel gets the runtime on IO first,
        // makes the ViewModel with it and the rest of the start waits; a recreated
        // activity keeps its ViewModel and goes on at once. The launch intent is
        // kept from here, and intents arriving meanwhile are handled afterwards.
        // AUTHSTART01_EVERY_WINDOW_BUILDS_ITS_RUNTIME_OFF_MAIN_V1: whether this
        // window has its ViewModel is asked of its own store (AuthStartGate).
        val launchIntent: Intent = intent
        val authStart = ViewModelProvider(this)[AuthStartGate::class.java]
        if (authStart.authViewModelMade) {
            onAuthRuntimeReady(savedInstanceState, launchIntent)
        } else {
            lifecycleScope.launch {
                val runtime = AuthRuntimePreparation.obtain(applicationContext)
                // STRICTMODE02_PREFERENCES_LOAD_BEFORE_THE_FIRST_FRAME_V1 (#243):
                // the launch intent's handling reads the poll handoff file.
                StartupPreferences.await(applicationContext)
                authRuntimeForTheViewModel = runtime
                authConnectViewModel
                authRuntimeForTheViewModel = null
                authStart.authViewModelMade = true
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
            inboxStates = inboxViewModel.redditInboxUiState,
            modmailStates = moderationViewModel.redditModmailUiState,
        )

        // ISSUE278_EVERY_WINDOW_FOLLOWS_THE_UI_SIZE_V1: the root view carries UI size
        // and Text size in its context, so dialogs, sheets and menus inherit them.
        val scaleContext = UiScaleContext(this)
        uiScaleContext = scaleContext
        setScaledContent(scaleContext) {
            val connectAccountState by authConnectViewModel.uiState.collectAsStateWithLifecycle()
            val onlineVisibilityState by authConnectViewModel.onlineVisibilityUiState.collectAsStateWithLifecycle()
            val customFeedsState by communitiesViewModel.customFeedsUiState.collectAsStateWithLifecycle()
            val customFeedPostsState by communitiesViewModel.customFeedPostsUiState.collectAsStateWithLifecycle()
            val redditFeedState by readingViewModel.redditFeedUiState.collectAsStateWithLifecycle()
            val redditAccountHistoryState by
                readingViewModel.redditAccountHistoryUiState.collectAsStateWithLifecycle()
            val redditSavedHistoryState by
                readingViewModel.redditSavedHistoryUiState.collectAsStateWithLifecycle()
            val redditCommentsState by
                readingViewModel.redditCommentsUiState.collectAsStateWithLifecycle()
            val redditSubscriptionsState by
                communitiesViewModel.redditSubscriptionsUiState.collectAsStateWithLifecycle()
            val redditCommunityDiscoveryState by
                communitiesViewModel.redditCommunityDiscoveryUiState.collectAsStateWithLifecycle()
            val redditMentionSearchState by
                communitiesViewModel.redditMentionSearchUiState.collectAsStateWithLifecycle()
            val redditCommunityInfoState by
                communitiesViewModel.redditCommunityInfoUiState.collectAsStateWithLifecycle()
            // ISSUE230_PINNED_POSTS_FIRST_V1
            val redditPinnedPostsState by
                communitiesViewModel.redditCommunityPinnedPostsUiState.collectAsStateWithLifecycle()
            val redditUserFlairState by
                postComposerViewModel.userFlairUiState.collectAsStateWithLifecycle()
            val redditCommunityWikiState by
                communitiesViewModel.redditCommunityWikiUiState.collectAsStateWithLifecycle()
            val redditModerationState by
                moderationViewModel.redditModerationUiState.collectAsStateWithLifecycle()
            val moderationActionState by
                moderationViewModel.moderationActionUiState.collectAsStateWithLifecycle()
            val removalReasonState by
                moderationViewModel.removalReasonUiState.collectAsStateWithLifecycle()
            val redditModmailState by
                moderationViewModel.redditModmailUiState.collectAsStateWithLifecycle()
            val communityMembershipState by
                communitiesViewModel.communityMembershipUiState.collectAsStateWithLifecycle()
            val redditFavoriteState by
                communitiesViewModel.redditFavoriteUiState.collectAsStateWithLifecycle()
            val postVoteState by
                readingViewModel.postVoteUiState.collectAsStateWithLifecycle()
            val postSaveState by
                readingViewModel.postSaveUiState.collectAsStateWithLifecycle()
            val postFollowState by
                readingViewModel.postFollowUiState.collectAsStateWithLifecycle()
            val contentSafetyState by
                readingViewModel.contentSafetyUiState.collectAsStateWithLifecycle()
            val commentWriteState by
                readingViewModel.commentWriteUiState.collectAsStateWithLifecycle()
            val commentMediaPolicies by
                postComposerViewModel.commentMediaPolicies.collectAsStateWithLifecycle()
            val commentEditState by
                readingViewModel.commentEditUiState.collectAsStateWithLifecycle()
            val commentDeleteState by
                readingViewModel.commentDeleteUiState.collectAsStateWithLifecycle()
            val postEditState by
                postComposerViewModel.postEditUiState.collectAsStateWithLifecycle()
            val postDeleteState by
                postComposerViewModel.postDeleteUiState.collectAsStateWithLifecycle()
            val postManageState by
                postComposerViewModel.postManageUiState.collectAsStateWithLifecycle()
            val textPostWriteState by
                postComposerViewModel.textPostWriteUiState.collectAsStateWithLifecycle()
            val crosspostReviewState by postComposerViewModel.crosspostReviewUiState.collectAsStateWithLifecycle()
            val composerPolicy by postComposerViewModel.composerPolicy.collectAsStateWithLifecycle()
            val composerCommunitySearch by
                postComposerViewModel.composerCommunitySearch.collectAsStateWithLifecycle()
            val redditInboxState by
                inboxViewModel.redditInboxUiState.collectAsStateWithLifecycle()
            val messageReplyState by
                inboxViewModel.messageReplyUiState.collectAsStateWithLifecycle()
            val directChatStartState by
                inboxViewModel.directChatStartUiState.collectAsStateWithLifecycle()
            val directChatLiveState by
                inboxViewModel.directChatLiveUiState.collectAsStateWithLifecycle()
            val platformUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val inAppUriHandler = androidx.compose.runtime.remember(platformUriHandler) {
                io.github.brealorg.subshell.ui.InAppRedditUriHandler(platformUriHandler) { uri ->
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
                io.github.brealorg.subshell.ui.LocalPostFollowRuntime provides
                    io.github.brealorg.subshell.ui.PostFollowRuntimeEntryPoints(
                        state = postFollowState,
                        onSetFollowed = readingViewModel::followPost,
                        onRetry = readingViewModel::retryPostFollow,
                        onAuthorize = ::launchPostFollowAuthorization,
                    ),
            ) {
                // ISSUE119_COMPOSE_AFTER_THE_ACCOUNT_DIRECTORY_V1
                AccountDirectoryGate(authConnectViewModel.accountSessionDirectory) {
                SubshellApp(
                    videoPlaybackCoordinator = videoPlaybackViewModel.coordinator,
                    onThemeSettingsChanged = { settings ->
                        appThemeSettings = settings
                        scaleContext.update(settings.uiScale, settings.textScale)
                    },
                    accountConnect = AccountConnectEntryPoints(
                        accountSessionDirectory = authConnectViewModel.accountSessionDirectory,
                        redditUserAgent = authConnectViewModel.redditUserAgent,
                        readNsfwAccountPreferences = communitiesViewModel::readNsfwAccountPreferences,
                        checkRedditAccess = communitiesViewModel::checkRedditAccess,
                        onLogoutActiveAccount = authConnectViewModel::logoutActiveAccount,
                        onReconnectAccount = ::launchReconnectAccount,
                        onlineVisibilityRuntime = io.github.brealorg.subshell.ui.components.OnlineVisibilityRuntime(
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
                        onLoadCustomFeeds = communitiesViewModel::loadCustomFeeds,
                        onLoadCustomFeedPosts = communitiesViewModel::loadCustomFeedPosts,
                        onResolveCustomFeed = communitiesViewModel::resolveCustomFeed,
                        onAppendCustomFeedPosts = communitiesViewModel::appendCustomFeedPosts,
                        onCreateCustomFeed = ::launchCustomFeedCreation,
                        onUpdateCustomFeed = ::launchCustomFeedUpdate,
                        onDeleteCustomFeed = ::launchCustomFeedDeletion,
                        onResetCustomFeedCreate = communitiesViewModel::resetCustomFeedCreateState,
                        onResetCustomFeedManage = communitiesViewModel::resetCustomFeedManageState,
                        launcherShortcutTarget =
                            launcherShortcutTarget as? LauncherShortcutTarget.CustomFeed,
                        onLauncherShortcutConsumed = { launcherShortcutTarget = null },
                    ),
                    redditFeed = FeedEntryPoints(
                        redditFeedState = redditFeedState,
                        onRefreshRedditFeed = readingViewModel::refreshRedditFeed,
                        onAppendRedditFeed = readingViewModel::appendRedditFeed,
                        onRefreshRedditSearch = readingViewModel::refreshRedditSearch,
                        onAppendRedditSearch = readingViewModel::appendRedditSearch,
                        redditSubscriptionsState = redditSubscriptionsState,
                        onLoadRedditSubscriptions =
                            communitiesViewModel::loadRedditSubscriptions,
                        onLoadCachedRedditSubscriptions =
                            communitiesViewModel::loadCachedRedditSubscriptions,
                    ),
                    redditCommunity = CommunityEntryPoints(
                        onViewQuarantinedCommunity = communitiesViewModel::optInToQuarantinedCommunity,
                        randomNsfwCommunity = communitiesViewModel::randomNsfwCommunity,
                        onRefreshRedditCommunityFeed =
                            readingViewModel::refreshRedditCommunityFeed,
                        onAppendRedditCommunityFeed =
                            readingViewModel::appendRedditCommunityFeed,
                        redditCommunityWikiState = redditCommunityWikiState,
                        onLoadRedditCommunityWikiIndex =
                            communitiesViewModel::loadRedditCommunityWikiIndex,
                        onLoadRedditCommunityWikiPage =
                            communitiesViewModel::loadRedditCommunityWikiPage,
                        onAuthorizeRedditCommunityWiki =
                            ::launchCommunityWikiAuthorization,
                        redditCommunityInfoState = redditCommunityInfoState,
                        onLoadRedditCommunityInfo =
                            communitiesViewModel::loadRedditCommunityInfo,
                        redditPinnedPostsState = redditPinnedPostsState,
                        onLoadRedditPinnedPosts = communitiesViewModel::loadRedditCommunityPinnedPosts,
                        communityMembershipState = communityMembershipState,
                        onChangeCommunityMembership =
                            communitiesViewModel::updateCommunityMembership,
                        onAuthorizeCommunityMembership =
                            ::launchCommunityMembershipAuthorization,
                        redditCommunityDiscoveryState = redditCommunityDiscoveryState,
                        onLoadRedditCommunityDiscovery =
                            communitiesViewModel::loadRedditCommunityDiscovery,
                        redditMentionSearchState = redditMentionSearchState,
                        onSearchMentions = communitiesViewModel::loadRedditMentionSearch,
                        redditFavoriteState = redditFavoriteState,
                        onUpdateRedditFavorite =
                            communitiesViewModel::updateRedditFavorite,
                        onDismissRedditFavoriteFailure =
                            communitiesViewModel::dismissRedditFavoriteFailure,
                        onAuthorizeRedditFavorite =
                            ::launchRedditFavoriteAuthorization,
                        onUpdateRedditCustomFeedFavorite =
                            communitiesViewModel::updateRedditCustomFeedFavorite,
                    ),
                    onRefreshRedditUserSubmitted =
                        readingViewModel::refreshRedditUserSubmitted,
                    onAppendRedditUserSubmitted =
                        readingViewModel::appendRedditUserSubmitted,
                    redditAccountHistoryState = redditAccountHistoryState,
                    onRefreshRedditAccountHistory =
                        readingViewModel::refreshRedditAccountHistory,
                    onAppendRedditAccountHistory =
                        readingViewModel::appendRedditAccountHistory,
                    redditSavedHistoryState = redditSavedHistoryState,
                    onRefreshRedditSavedHistory =
                        readingViewModel::refreshRedditSavedHistory,
                    onAppendRedditSavedHistory =
                        readingViewModel::appendRedditSavedHistory,
                    onResolveRedditProfileCommentTarget =
                        readingViewModel::resolveRedditProfileCommentTarget,
                    onResolveRedditSavedCommentTarget =
                        readingViewModel::resolveRedditSavedCommentTarget,
                    onAuthorizeRedditProfileHistory =
                        ::launchProfileHistoryAuthorization,
                    redditComments = CommentLoadingEntryPoints(
                        redditCommentsState = redditCommentsState,
                        onLoadRedditComments = readingViewModel::loadRedditComments,
                        onLoadCachedRedditComments =
                            readingViewModel::loadCachedRedditComments,
                        onRefreshRedditCommentsPreservingFocusedTarget =
                            readingViewModel::refreshRedditCommentsPreservingFocusedTarget,
                        onLoadFocusedRedditComment =
                            readingViewModel::loadFocusedRedditComment,
                        onLoadMoreRedditComments =
                            readingViewModel::loadMoreRedditComments,
                    ),
                    redditUserFlairState = redditUserFlairState,
                    onLoadRedditUserFlair = postComposerViewModel::loadUserFlair,
                    onSetRedditUserFlair = postComposerViewModel::setUserFlair,
                    redditModeration = ModerationQueueEntryPoints(
                        redditModerationState = redditModerationState,
                        onLoadRedditModQueue =
                            moderationViewModel::loadRedditModQueue,
                        onAppendRedditModQueue =
                            moderationViewModel::appendRedditModQueue,
                        onResolveRedditModerationTarget =
                            moderationViewModel::resolveRedditModerationTarget,
                        moderationActionState = moderationActionState,
                        onModerateRedditQueueItem =
                            moderationViewModel::moderateRedditQueueItem,
                        onRetryRedditModerationAction =
                            moderationViewModel::retryRedditModerationAction,
                        onAuthorizeRedditModerationAction =
                            ::launchRedditModerationActionAuthorization,
                        removalReasonState = removalReasonState,
                        onLoadRedditRemovalReasons =
                            moderationViewModel::loadRedditRemovalReasons,
                        onAuthorizeRedditRemovalReasons =
                            ::launchRedditRemovalReasonAuthorization,
                    ),
                    moderatorTools = moderatorToolsEntryPoints(
                        moderationViewModel = moderationViewModel,
                        launchRedditModeratorLogAuthorization = ::launchRedditModeratorLogAuthorization,
                        launchRedditModeratorUserManagementAuthorization =
                            ::launchRedditModeratorUserManagementAuthorization,
                    ),
                    // MODERATION02A5A6_MODERATOR_TEAM_RUNTIME_BUNDLE_CALLSITE_V1
                    moderatorTeamRuntime = moderatorTeamRuntimeEntryPoints(
                        moderationViewModel = moderationViewModel,
                        launchRedditModeratorInviteAuthorization = ::launchRedditModeratorInviteAuthorization,
                        launchRedditModeratorPermissionAuthorization =
                            ::launchRedditModeratorPermissionAuthorization,
                        launchRedditModeratorRemovalAuthorization = ::launchRedditModeratorRemovalAuthorization,
                    ),
                    redditModmail = ModmailEntryPoints(
                        redditModmailState = redditModmailState,
                        onLoadRedditModmail = moderationViewModel::loadRedditModmail,
                        onAppendRedditModmail = moderationViewModel::appendRedditModmail,
                        onSelectRedditModmailListMode = moderationViewModel::selectRedditModmailListMode,
                        onOpenRedditModmailConversation =
                            moderationViewModel::openRedditModmailConversation,
                        onSendRedditModmailMessage =
                            moderationViewModel::sendRedditModmailMessage,
                        onSetRedditModmailArchived = moderationViewModel::setRedditModmailArchived,
                        onSetRedditModmailUnread = moderationViewModel::setRedditModmailUnread,
                        onVerifyRedditModmailReadState =
                            moderationViewModel::verifyRedditModmailReadState,
                        onApplyRedditModmailTriage = moderationViewModel::applyRedditModmailTriage,
                        onCloseRedditModmailConversation =
                            moderationViewModel::closeRedditModmailConversation,
                        onAuthorizeRedditModmail = ::launchRedditModmailAuthorization,
                        modmailNotificationTarget = modmailNotificationTarget,
                        onModmailNotificationConsumed = { modmailNotificationTarget = null },
                    ),
                    postVoteState = postVoteState,
                    onVotePost = readingViewModel::votePost,
                    onRetryPostVote = readingViewModel::retryPostVote,
                    onAuthorizePostVote = ::launchPostVoteAuthorization,
                    postSaveState = postSaveState,
                    onSavePost = readingViewModel::savePost,
                    onRetryPostSave = readingViewModel::retryPostSave,
                    onAuthorizePostSave = ::launchPostSaveAuthorization,
                    contentSafetyState = contentSafetyState,
                    onSetPostHidden = readingViewModel::setPostHidden,
                    onReportContent = readingViewModel::reportContent,
                    onSetUserBlocked = readingViewModel::setUserBlocked,
                    onRefreshBlockedAccounts = readingViewModel::refreshBlockedAccounts,
                    onAuthorizeBlockedAccounts = ::launchBlockedAccountsAuthorization,
                    onAppendBlockedAccounts = readingViewModel::appendBlockedAccounts,
                    onRetryContentSafety = readingViewModel::retryContentSafetyMutation,
                    onAuthorizeContentSafety = ::launchContentSafetyAuthorization,
                    commentMutations = CommentMutationEntryPoints(
                        commentWriteState = commentWriteState,
                        onSubmitComment = readingViewModel::submitComment,
                        onAuthorizeCommentWrite = ::launchCommentAuthorization,
                        onCancelCommentWriteAuthorization =
                            readingViewModel::cancelCommentAuthorization,
                        onResetCommentWriteState = readingViewModel::resetCommentWriteState,
                        commentEditState = commentEditState,
                        onEditComment = readingViewModel::editComment,
                        onAuthorizeCommentEdit = ::launchCommentEditAuthorization,
                        onCancelCommentEditAuthorization =
                            readingViewModel::cancelCommentEditAuthorization,
                        onResetCommentEditState = readingViewModel::resetCommentEditState,
                        onCheckCommentEdit = readingViewModel::checkCommentEdit,
                        commentDeleteState = commentDeleteState,
                        onDeleteComment = readingViewModel::deleteComment,
                        onAuthorizeCommentDelete = ::launchCommentDeleteAuthorization,
                        onCancelCommentDeleteAuthorization = readingViewModel::cancelCommentDeleteAuthorization,
                        onResetCommentDeleteState = readingViewModel::resetCommentDeleteState,
                        commentMediaPolicies = commentMediaPolicies,
                        onLoadCommentMediaPolicy = postComposerViewModel::loadCommentMediaPolicy,
                    ),
                    postMutations = PostMutationEntryPoints(
                        postEditState = postEditState,
                        onEditPost = postComposerViewModel::editPost,
                        onAuthorizePostEdit = ::launchPostEditAuthorization,
                        onCancelPostEditAuthorization =
                            postComposerViewModel::cancelPostEditAuthorization,
                        onResetPostEditState = postComposerViewModel::resetPostEditState,
                        onCheckPostEdit = postComposerViewModel::checkPostEdit,
                        postDeleteState = postDeleteState,
                        onDeletePost = postComposerViewModel::deletePost,
                        onAuthorizePostDelete = ::launchPostDeleteAuthorization,
                        onCancelPostDeleteAuthorization =
                            postComposerViewModel::cancelPostDeleteAuthorization,
                        onResetPostDeleteState = postComposerViewModel::resetPostDeleteState,
                        onCheckPostDelete = postComposerViewModel::checkPostDelete,
                        postManageState = postManageState,
                        onLoadPostManagement = postComposerViewModel::loadPostManagement,
                        onMutatePostManagement = postComposerViewModel::mutatePostManagement,
                        onAuthorizePostManagement = ::launchPostManageAuthorization,
                        onCancelPostManageAuthorization =
                            postComposerViewModel::cancelPostManageAuthorization,
                        onResetPostManageState = postComposerViewModel::resetPostManageState,
                    ),
                    postComposerWriteRuntime =
                        io.github.brealorg.subshell.ui.PostComposerWriteRuntimeEntryPoints(
                            textPostWriteState = textPostWriteState,
                            crosspostReviewState = crosspostReviewState,
                            onReviewCrosspost =
                                postComposerViewModel::reviewCrosspostDraft,
                            onPublishReviewedCrosspost =
                                postComposerViewModel::publishReviewedCrosspost,
                            onInvalidateCrosspostReview =
                                postComposerViewModel::invalidateCrosspostReview,
                        ),
                    composerPolicy = composerPolicy,
                    onLoadComposerPolicy = postComposerViewModel::loadComposerPolicy,
                    composerCommunitySearch = composerCommunitySearch,
                    onSearchComposerCommunities =
                        postComposerViewModel::searchComposerCommunities,
                    onSubmitTextPost = postComposerViewModel::submitTextPost,
                    onReconcileTextPostSubmit = postComposerViewModel::reconcileTextPostSubmit,
                    onAuthorizeTextPostWrite = ::launchTextPostAuthorization,
                    onAuthorizeComposerPostRequirements =
                        ::launchComposerPostRequirementsAuthorization,
                    onAuthorizeComposerFlair = ::launchComposerFlairAuthorization,
                    onAuthorizeRedditUserFlair = ::launchUserFlairAuthorization,
                    onCancelTextPostWriteAuthorization =
                        postComposerViewModel::cancelTextPostAuthorization,
                    onResetTextPostWriteState = postComposerViewModel::resetTextPostWriteState,
                    redditInbox = InboxEntryPoints(
                        redditInboxState = redditInboxState,
                        onLoadRedditInbox = inboxViewModel::loadRedditInbox,
                        onAppendRedditInbox = inboxViewModel::appendRedditInbox,
                        onMarkRedditInboxRead = inboxViewModel::markRedditInboxRead,
                        onResolveRedditInboxTarget = inboxViewModel::resolveRedditInboxTarget,
                        onAuthorizeRedditInbox = ::launchInboxAuthorization,
                        onCancelRedditInboxAuthorization =
                            inboxViewModel::cancelInboxAuthorization,
                        inboxNotificationTarget = inboxNotificationTarget,
                        onInboxNotificationConsumed = { inboxNotificationTarget = null },
                        launcherShortcutTarget = launcherShortcutTarget as? LauncherShortcutTarget.Inbox,
                        onLauncherShortcutConsumed = { launcherShortcutTarget = null },
                    ),
                    redditMessaging = MessagingEntryPoints(
                        messageReplyState = messageReplyState,
                        directChatLiveState = directChatLiveState,
                        onSendRedditMessageReply = inboxViewModel::sendRedditMessageReply,
                        onResetRedditMessageReplyState =
                            inboxViewModel::resetRedditMessageReplyState,
                        onStartRedditDirectChatLive =
                            inboxViewModel::startRedditDirectChatLive,
                        onStopRedditDirectChatLive =
                            inboxViewModel::stopRedditDirectChatLive,
                        onUpdateRedditDirectChatTyping =
                            inboxViewModel::updateRedditDirectChatTyping,
                        onDeleteRedditDirectChatMessage =
                            inboxViewModel::deleteRedditDirectChatMessage,
                        onResetRedditDirectChatDeleteFailure =
                            inboxViewModel::resetRedditDirectChatDeleteFailure,
                        onReactRedditDirectChatMessage =
                            inboxViewModel::reactRedditDirectChatMessage,
                        onResetRedditDirectChatReactionFailure =
                            inboxViewModel::resetRedditDirectChatReactionFailure,
                    ),
                    directChatStartState = directChatStartState,
                    onStartRedditDirectChat = inboxViewModel::startRedditDirectChat,
                    onResetRedditDirectChatStartState =
                        inboxViewModel::resetRedditDirectChatStartState,
                    onResolveRedditNavigationTarget =
                        readingViewModel::resolveRedditNavigationTargetPost,
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
                    uiScale = appThemeSettings.uiScale,
                    manageSystemBarContrast = false,
                ) {
                    AppLockOverlay(
                        onUnlock = ::requestAppUnlock,
                        onLeave = { moveTaskToBack(true) },
                    )
                }
            }
        }
        if (jankStats == null) {
            jankStats = JankStats.createAndTrack(window) { frame ->
                jankTally.add(
                    durationNanos = frame.frameDurationUiNanos,
                    isJank = frame.isJank,
                    screen = frame.states.firstOrNull { state -> state.key == JANK_SCREEN_STATE }?.value,
                )
            }
            lifecycle.addObserver(jankLifecycleObserver)
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
        if (recentsCardHiddenBySecureFlag) {
            recentsCardHiddenBySecureFlag = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        refreshRedditLinkHandlingState()
    }

    // SECAUDIT10_RECENTS_CARD_HIDDEN_BELOW_ANDROID_13_V1 (security audit #10):
    // below Android 13 there is no setRecentsScreenshotEnabled, so the recents
    // card showed the last screen while the app lock's delay ran. The window is
    // secure while Subshell is in the background and plain again on return;
    // picture-in-picture keeps its picture. Only the flag set here is cleared.
    override fun onPause() {
        if (
            appLockHidesRecentsWithSecureFlag(
                sdkInt = Build.VERSION.SDK_INT,
                appLockEnabled = appLock.settings.enabled,
                inPictureInPicture = isInPictureInPictureMode,
            ) &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0
        ) {
            recentsCardHiddenBySecureFlag = true
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onPause()
    }

    private var recentsCardHiddenBySecureFlag = false

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
        // Android hands the root view its own, unscaled configuration; the scaled
        // one follows so LocalConfiguration agrees with the density.
        window.decorView.post { uiScaleContext?.dispatch() }
        enableEdgeToEdge()
        videoPlaybackViewModel.coordinator.onHostConfigurationChanged(newConfig)
    }

    // STRICTMODE02_LINK_HANDLING_READ_OFF_MAIN_V1 (#243): the system's link
    // verification state and the prompt store are read on IO; until then the
    // prompt counts as seen (no prompt shows).
    private fun refreshRedditLinkHandlingState() {
        lifecycleScope.launch {
            val (current, promptSeen) = withContext(Dispatchers.IO) {
                val current = readRedditLinkHandlingState(applicationContext)
                val promptSeen = if (!current.supported) {
                    true
                } else {
                    if (current.status == RedditLinkHandlingStatus.ENABLED) {
                        redditLinkHandlingPromptStore.markSeen()
                    }
                    redditLinkHandlingPromptStore.wasSeen()
                }
                current to promptSeen
            }
            redditLinkHandlingState = current
            redditLinkHandlingPromptSeen = promptSeen
            if (BuildConfig.DEBUG) {
                Log.d(
                    "SubshellDeepLink",
                    "phase=link_handling_state supported=${current.supported} " +
                        "status=${current.status} selected=${current.selectedHostCount}/" +
                        "${current.supportedHostCount} promptSeen=$promptSeen",
                )
            }
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
            { postComposerViewModel.beginPostManageAuthorization() },
        ) {
            postComposerViewModel.postManageAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostDeleteAuthorization() {
        launchAuthBrowser(
            { postComposerViewModel.beginPostDeleteAuthorization() },
        ) {
            postComposerViewModel.postDeleteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostEditAuthorization() {
        launchAuthBrowser(
            { postComposerViewModel.beginPostEditAuthorization() },
        ) {
            postComposerViewModel.postEditAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentDeleteAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginCommentDeleteAuthorization() },
        ) {
            readingViewModel.commentDeleteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentEditAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginCommentEditAuthorization() },
        ) {
            readingViewModel.commentEditAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCommentAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginCommentAuthorization() },
        ) {
            readingViewModel.commentAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchTextPostAuthorization(request: TextPostWriteRequest) {
        launchAuthBrowser(
            { postComposerViewModel.beginTextPostAuthorization(request) },
        ) {
            postComposerViewModel.textPostAuthorizationBrowserLaunchFailed()
        }
    }


    // The answer is "asked to begin"; the outcome follows in the composer's
    // requirements state (no caller reads the Boolean).
    private fun launchComposerPostRequirementsAuthorization(
        accountStableId: String,
    ): Boolean {
        lifecycleScope.launch {
            dispatchComposerPostRequirementsAuthorization(
                postComposerViewModel.beginComposerPostRequirementsAuthorization(accountStableId),
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
                postComposerViewModel
                    .composerPostRequirementsAuthorizationBrowserLaunchFailed()
                false
            },
        )
        AuthBrowserLaunchResult.NotStarted -> false
    }

    private fun launchComposerFlairAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { postComposerViewModel.beginComposerFlairAuthorization(accountStableId) },
        ) {
            postComposerViewModel.composerFlairAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchUserFlairAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { postComposerViewModel.beginUserFlairAuthorization(accountStableId) },
        ) {
            postComposerViewModel.userFlairAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchCustomFeedCreation(request: CustomFeedCreateRequest) {
        launchCustomFeedAuthorization { communitiesViewModel.beginCustomFeedCreation(request) }
    }

    private fun launchCustomFeedUpdate(request: CustomFeedUpdateRequest) {
        launchCustomFeedAuthorization { communitiesViewModel.beginCustomFeedUpdate(request) }
    }

    private fun launchCustomFeedDeletion(request: CustomFeedDeleteRequest) {
        launchCustomFeedAuthorization { communitiesViewModel.beginCustomFeedDeletion(request) }
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
                    communitiesViewModel.customFeedAuthorizationBrowserLaunchFailed()
                }
            }
            AuthBrowserLaunchResult.NotStarted -> Unit
        }
    }

    private fun launchCommunityMembershipAuthorization() {
        launchAuthBrowser(
            { communitiesViewModel.beginCommunityMembershipAuthorization() },
        ) {
            communitiesViewModel.communityMembershipAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchRedditFavoriteAuthorization() {
        launchAuthBrowser(
            { communitiesViewModel.beginRedditFavoriteAuthorization() },
        ) {
            communitiesViewModel.redditFavoriteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostVoteAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginPostVoteAuthorization() },
        ) {
            readingViewModel.postVoteAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostSaveAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginPostSaveAuthorization() },
        ) {
            readingViewModel.postSaveAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchPostFollowAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginPostFollowAuthorization() },
        ) {
            readingViewModel.postFollowAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A2_MODERATION_ACTION_AUTH_BROWSER_V1
    private fun launchRedditModerationActionAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModerationActionAuthorization() },
        ) {
            moderationViewModel.redditModerationActionAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A21_REMOVAL_REASON_AUTH_BROWSER_V1
    private fun launchRedditRemovalReasonAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditRemovalReasonAuthorization() },
        ) {
            moderationViewModel.redditRemovalReasonAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A4_MOD_LOG_AUTH_BROWSER_V1
    private fun launchRedditModeratorLogAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModeratorLogAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModeratorLogAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A1_USER_MANAGEMENT_AUTH_BROWSER_V1
    private fun launchRedditModeratorUserManagementAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModeratorUserManagementAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModeratorUserManagementAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A5_MODERATOR_TEAM_AUTH_BROWSER_V1
    private fun launchRedditModeratorInviteAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModeratorInviteAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModeratorInviteAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A6_MODERATOR_PERMISSION_AUTH_BROWSER_V1
    private fun launchRedditModeratorPermissionAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModeratorPermissionAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModeratorPermissionAuthorizationBrowserLaunchFailed()
        }
    }

    // MODERATION02A5A7_MODERATOR_REMOVAL_AUTH_BROWSER_V1
    private fun launchRedditModeratorRemovalAuthorization() {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModeratorRemovalAuthorization() },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModeratorRemovalAuthorizationBrowserLaunchFailed()
        }
    }

    // MODMAIL01A1_ACCOUNT_SAFE_BROWSER_AFFINITY_V1
    private fun launchRedditModmailAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { moderationViewModel.beginRedditModmailAuthorization(accountStableId) },
            propagateFreshAccountSession = true,
        ) {
            moderationViewModel.redditModmailAuthorizationBrowserLaunchFailed()
        }
    }

    // SAFETY01A3_CONTENT_SAFETY_AUTH_BROWSER_V1
    // OCB414_BLOCKED_LIST_CAN_ASK_FOR_AUTHORIZATION_V1
    private fun launchBlockedAccountsAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { readingViewModel.beginBlockedAccountsAuthorization(accountStableId) },
        ) {
            readingViewModel.blockedAccountsAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchContentSafetyAuthorization() {
        launchAuthBrowser(
            { readingViewModel.beginContentSafetyAuthorization() },
        ) {
            readingViewModel.contentSafetyAuthorizationBrowserLaunchFailed()
        }
    }

    // WIKI01A2_COMMUNITY_WIKI_AUTH_BROWSER_V1
    private fun launchCommunityWikiAuthorization(
        accountStableId: String,
        communityName: String,
    ) {
        launchAuthBrowser(
            { communitiesViewModel.beginRedditCommunityWikiAuthorization(
                accountStableId = accountStableId,
                rawCommunityName = communityName,
            ) },
        ) {
            communitiesViewModel.redditCommunityWikiAuthorizationBrowserLaunchFailed()
        }
    }

    private fun launchProfileHistoryAuthorization(
        accountStableId: String,
        username: String,
    ) {
        launchAuthBrowser(
            { readingViewModel.beginProfileHistoryAuthorization(
                accountStableId = accountStableId,
                username = username,
            ) },
        ) {
            readingViewModel.profileHistoryAuthorizationBrowserLaunchFailed()
        }
    }

    // MOD01A221_ACCOUNT_SAFE_INBOX_BROWSER_AFFINITY_V1
    private fun launchInboxAuthorization(accountStableId: String) {
        launchAuthBrowser(
            { inboxViewModel.beginInboxAuthorization(accountStableId) },
            propagateFreshAccountSession = true,
        ) {
            inboxViewModel.inboxAuthorizationBrowserLaunchFailed()
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
        val target = takeInboxNotificationTarget(applicationContext, candidate) ?: return false
        inboxNotificationTarget = target
        return true
    }

    private fun consumeModmailNotificationIntent(candidate: Intent?): Boolean {
        val target = takeModmailNotificationTarget(applicationContext, candidate) ?: return false
        modmailNotificationTarget = target
        return true
    }

    // ISSUE37_LAUNCHER_SHORTCUT_TARGETS_V1
    // ISSUE33_SHARE_INTO_SUBSHELL_V1: a share opens the composer; the copy of
    // any shared images runs off the main thread. The action is cleared so a
    // recreated activity does not take the same share twice.
    private fun consumeShareIntent(candidate: Intent?): Boolean {
        if (candidate == null || !io.github.brealorg.subshell.share.isShareIntent(candidate)) return false
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
        val resultToken = takeAuthCallbackResult(candidate) ?: return false
        authConnectViewModel.handleCallbackResultToken(resultToken)
        return true
    }
}

private const val LEGACY_STORAGE_WRITE_REQUEST_CODE = 0x0084
private const val DEBUG_FOCUSED_POST_ID_EXTRA = "subshell.debug.focused_post_id"
private const val DEBUG_FOCUSED_COMMENT_ID_EXTRA = "subshell.debug.focused_comment_id"
private const val DEBUG_FOCUSED_USERNAME_EXTRA = "subshell.debug.focused_username"

/** SECAUDIT10_RECENTS_CARD_HIDDEN_BELOW_ANDROID_13_V1: Android 13+ blanks the card with setRecentsScreenshotEnabled. */
internal fun appLockHidesRecentsWithSecureFlag(
    sdkInt: Int,
    appLockEnabled: Boolean,
    inPictureInPicture: Boolean,
): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU && appLockEnabled && !inPictureInPicture

private val APP_LOCK_SETTINGS_WRITES = Dispatchers.IO.limitedParallelism(1)
