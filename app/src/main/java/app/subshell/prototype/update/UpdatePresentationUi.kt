package app.subshell.prototype.update

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.subshell.prototype.media.findActivity
import app.subshell.prototype.media.findLifecycleOwner
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.subshell.prototype.BuildConfig

@Composable
internal fun UpdatePresentationHost(
    automaticPresentationEnabled: Boolean,
) {
    val appContext = LocalContext.current.applicationContext
    // LOW_MAIN_UPDATE_LINKS_ARE_TRIPS_SUBSHELL_STARTS_V1 (OCB-391): opened
    // from the activity, so the app lock counts the trip as one Subshell started.
    val activityContext = LocalContext.current
    val store = remember(appContext) { UpdateStateStore(appContext) }
    val changelogLoad = rememberBundledChangelogLoad(appContext)
    val changelog = changelogLoad.document
    val request by UpdatePresentationCoordinator.request.collectAsState()
    val notifyNextTime = rememberUpdateNotificationOptIn(store)

    var pendingWhatsNewVersion by remember(appContext) {
        mutableIntStateOf(store.pendingWhatsNewVersionCode())
    }
    var autoAvailableRelease by remember(appContext) {
        mutableStateOf(
            store.availableRelease(BuildConfig.VERSION_CODE)
                ?.takeIf { release ->
                    shouldAutoPresentUpdate(
                        release = release,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        lastPresentedVersionCode =
                            store.lastPresentedUpdateVersionCode(),
                        lastPresentedAtEpochMillis = store.lastUpdatePresentedAtEpochMillis(),
                    )
                },
        )
    }
    var changelogDialogVisible by remember { mutableStateOf(false) }

    val explicitAvailable =
        (request as? UpdatePresentationRequest.UpdateAvailable)
            ?.takeIf { candidate ->
                candidate.force ||
                    (
                        automaticPresentationEnabled &&
                            shouldAutoPresentUpdate(
                                release = candidate.release,
                                currentVersionCode = BuildConfig.VERSION_CODE,
                                lastPresentedVersionCode =
                                    store.lastPresentedUpdateVersionCode(),
                                lastPresentedAtEpochMillis = store.lastUpdatePresentedAtEpochMillis(),
                            )
                        )
            }

    LaunchedEffect(request, automaticPresentationEnabled) {
        val candidate =
            request as? UpdatePresentationRequest.UpdateAvailable
                ?: return@LaunchedEffect
        if (
            !candidate.force &&
            automaticPresentationEnabled &&
            !shouldAutoPresentUpdate(
                release = candidate.release,
                currentVersionCode = BuildConfig.VERSION_CODE,
                lastPresentedVersionCode =
                    store.lastPresentedUpdateVersionCode(),
                lastPresentedAtEpochMillis = store.lastUpdatePresentedAtEpochMillis(),
            )
        ) {
            UpdatePresentationCoordinator.clear(candidate)
        }
    }

    val previewWhatsNewRelease =
        if (request is UpdatePresentationRequest.PreviewWhatsNew) {
            changelog.latestRelease()
        } else {
            null
        }

    LaunchedEffect(request, previewWhatsNewRelease) {
        val previewRequest = request
        if (
            previewRequest is UpdatePresentationRequest.PreviewWhatsNew &&
            previewWhatsNewRelease == null
        ) {
            UpdatePresentationCoordinator.clear(previewRequest)
        }
    }

    if (changelogDialogVisible) {
        Dialog(
            onDismissRequest = { changelogDialogVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                UpdateChangelogScreen(
                    onBack = { changelogDialogVisible = false },
                )
            }
        }
        return
    }

    explicitAvailable?.let { explicit ->
        UpdateAvailableDialog(
            release = explicit.release,
            advisory = explicit.release.advisoryFor(BuildConfig.VERSION_CODE),
            onNotifyNextTime = notifyNextTime,
            onDismiss = {
                store.markUpdatePresented(explicit.release.versionCode)
                autoAvailableRelease =
                    autoAvailableRelease?.takeUnless { cached ->
                        cached.versionCode == explicit.release.versionCode
                    }
                UpdatePresentationCoordinator.clear(explicit)
            },
            onViewRelease = {
                // UPDATE02: without an app to open the release, keep the
                // dialog (and the update) instead of marking it presented.
                if (openUpdateRelease(activityContext, explicit.release.releaseUrl)) {
                    store.markUpdatePresented(explicit.release.versionCode)
                    autoAvailableRelease =
                        autoAvailableRelease?.takeUnless { cached ->
                            cached.versionCode == explicit.release.versionCode
                        }
                    UpdatePresentationCoordinator.clear(explicit)
                }
            },
        )
        return
    }

    if (request is UpdatePresentationRequest.PreviewWhatsNew) {
        previewWhatsNewRelease?.let { release ->
            WhatsNewDialog(
                release = release,
                onDismiss = {
                    UpdatePresentationCoordinator.clear(request)
                },
                onViewChangelog = {
                    UpdatePresentationCoordinator.clear(request)
                    changelogDialogVisible = true
                },
            )
        }
        return
    }

    val pendingRelease =
        pendingWhatsNewVersion
            .takeIf { versionCode ->
                automaticPresentationEnabled &&
                    versionCode > 0 &&
                    versionCode == BuildConfig.VERSION_CODE
            }
            ?.let(changelog::releaseForVersionCode)

    if (pendingRelease != null) {
        WhatsNewDialog(
            release = pendingRelease,
            onDismiss = {
                store.acknowledgeWhatsNew(pendingRelease.versionCode)
                pendingWhatsNewVersion = 0
            },
            onViewChangelog = {
                store.acknowledgeWhatsNew(pendingRelease.versionCode)
                pendingWhatsNewVersion = 0
                changelogDialogVisible = true
            },
        )
        return
    }

    // OCB-574: while a What's new for this build waits for the changelog, the
    // Update available dialog does not flash up before it.
    val whatsNewPending = automaticPresentationEnabled &&
        pendingWhatsNewVersion > 0 &&
        pendingWhatsNewVersion == BuildConfig.VERSION_CODE
    if (whatsNewPending && !changelogLoad.loaded) return

    autoAvailableRelease
        ?.takeIf { automaticPresentationEnabled }
        ?.let { release ->
        UpdateAvailableDialog(
            release = release,
            advisory = release.advisoryFor(BuildConfig.VERSION_CODE),
            onNotifyNextTime = notifyNextTime,
            onDismiss = {
                store.markUpdatePresented(release.versionCode)
                autoAvailableRelease = null
            },
            onViewRelease = {
                if (openUpdateRelease(activityContext, release.releaseUrl)) {
                    store.markUpdatePresented(release.versionCode)
                    autoAvailableRelease = null
                }
            },
        )
    }
}

/**
 * UPDATE08_UPDATE_DIALOG_ASKS_FOR_NOTIFICATIONS_V1: null while Subshell may
 * post notifications; otherwise what "Notify me next time" does now. Read
 * again on every resume, so a choice made in the system settings shows.
 */
@Composable
private fun rememberUpdateNotificationOptIn(store: UpdateStateStore): (() -> Unit)? {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        store.markNotificationPermissionAsked()
        revision++
    }
    val optIn = remember(context, revision) {
        val activity = context.findActivity()
        updateNotificationOptIn(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            askedBefore = store.notificationPermissionAsked(),
            showRationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
        )
    }
    return when (optIn) {
        UpdateNotificationOptIn.NONE -> null
        UpdateNotificationOptIn.ASK -> {
            { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        UpdateNotificationOptIn.OPEN_SETTINGS -> {
            {
                try {
                    context.startActivity(appNotificationSettingsIntent(context))
                } catch (_: ActivityNotFoundException) {
                    // No settings screen to open; the in-app dialog still works.
                }
            }
        }
    }
}

@Composable
internal fun UpdateAvailableDialog(
    release: UpdateManifestRelease,
    onDismiss: () -> Unit,
    onViewRelease: () -> Unit,
    onNotifyNextTime: (() -> Unit)? = null,
    advisory: UpdateAdvisory? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (advisory != null) "Known problem in your version" else "subshell update available")
        },
        text = {
            // ISSUE209_UPDATE_DIALOGS_SCROLL_V1 (#209): AlertDialog does not
            // scroll its text, so long highlights cut off the rest.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // UPDATE09_KNOWN_BAD_VERSION_ADVISORY_V1: the problem first, in
                // the error container, before what the new release brings.
                advisory?.let { known ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            known.message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    if (advisory != null) "${release.versionName} fixes it" else release.versionName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    release.headline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                release.highlights.forEach { highlight ->
                    Text(
                        "• $highlight",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // UPDATE04_SHOW_APK_SHA256_V1: lets a careful user compare
                // the downloaded APK with the published hash.
                SelectionContainer {
                    Text(
                        updateApkHashLine(release),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // UPDATE08_UPDATE_DIALOG_ASKS_FOR_NOTIFICATIONS_V1
                onNotifyNextTime?.let { notify ->
                    TextButton(onClick = notify) {
                        Text("Notify me next time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onViewRelease) {
                Text("View release")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}

@Composable
internal fun WhatsNewDialog(
    release: UpdateChangelogRelease,
    onDismiss: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in subshell") },
        text = {
            // ISSUE209_UPDATE_DIALOGS_SCROLL_V1 (#209): AlertDialog does not
            // scroll its text, so long highlights cut off the rest.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    release.versionName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    release.headline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                release.highlights.take(5).forEach { highlight ->
                    Text(
                        "• $highlight",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        dismissButton = {
            TextButton(onClick = onViewChangelog) {
                Text("View changelog")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateChangelogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val changelog = rememberBundledChangelog(appContext)

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = { Text("Changelog") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (changelog.releases.isEmpty()) {
            Text(
                "No bundled changelog is available in this build.",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(
            contentPadding =
                PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(
                items = changelog.releases,
                key = { release -> release.versionCode },
            ) { release ->
                UpdateChangelogReleaseItem(
                    release = release,
                )
            }
        }
    }
}

@Composable
internal fun UpdateChangelogReleaseItem(
    release: UpdateChangelogRelease,
    modifier: Modifier = Modifier,
) {
    // LOW_MAIN_UPDATE_LINKS_ARE_TRIPS_SUBSHELL_STARTS_V1 (OCB-391): opened
    // from the activity, so the app lock counts the trip as one Subshell started.
    val activityContext = LocalContext.current

    // UPDATE01A1B2_MATERIAL3_CHANGELOG_RELEASE_CARD_V1
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                release.versionName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                release.date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                release.headline,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (release.highlights.isNotEmpty()) {
                HorizontalDivider()
                release.highlights.forEach { highlight ->
                    Text(
                        "• $highlight",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            release.releaseUrl?.let { url ->
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = { openUpdateRelease(activityContext, url) },
                ) {
                    Text("View GitHub release")
                }
            }
        }
    }
}

/** Opens the release page in a browser; false when nothing could open it. */
private fun openUpdateRelease(
    context: Context,
    url: String,
): Boolean =
    try {
        context.startActivity(updateReleaseViewIntent(url))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

internal fun updateReleaseViewIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun updateApkHashLine(release: UpdateManifestRelease): String =
    "APK SHA-256: ${release.apkSha256}"
