package io.github.brealorg.subshell.update

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brealorg.subshell.BuildConfig
import io.github.brealorg.subshell.media.findLifecycleOwner
import io.github.brealorg.subshell.ui.ScreenMargin
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.ui.symbols.Symbols
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * ISSUE286_SETTINGS_UPDATES_V1 (#286, owner: in Settings): one place that says
 * which version is installed, which is the newest, when subshell last looked,
 * and lets the reader check now and update. Debug builds never check
 * (UpdateRuntimePolicy), and the screen says so.
 */

internal data class UpdatesScreenState(
    val installedVersionName: String,
    val runtimeEnabled: Boolean,
    val latest: UpdateManifestRelease?,
    val available: UpdateManifestRelease?,
    val lastCheckedEpochMillis: Long,
    val nowEpochMillis: Long,
    val checking: Boolean = false,
    val checkMessage: String? = null,
    val install: UpdateInstallState = UpdateInstallState.Idle,
)

internal const val UPDATES_CHECK_FAILED = "Couldn't reach the update information. Try again later."
internal const val UPDATES_UP_TO_DATE = "You have the latest version."
internal const val UPDATE_WAITING_FOR_YOU = "Android is waiting for you to confirm the update."
internal const val UPDATES_OFF_IN_THIS_BUILD = "Update checks are off in this build."

@Composable
internal fun UpdatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    runtimeEnabled: Boolean = UpdateRuntimePolicy.enabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG),
) {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    val store = remember(appContext) { UpdateStateStore(appContext) }
    val scope = rememberCoroutineScope()
    val install by UpdateInstallCoordinator.state.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var permissionNeededFor by remember { mutableStateOf<UpdateManifestRelease?>(null) }
    var resumeAfterPermission by remember { mutableStateOf<UpdateManifestRelease?>(null) }

    fun startUpdate(release: UpdateManifestRelease) {
        if (updateInstallAllowed(appContext)) {
            UpdateInstallCoordinator.start(appContext, release)
        } else {
            permissionNeededFor = release
        }
    }

    // Back from Android's "Install unknown apps" page: go on if it was allowed.
    val lifecycleOwner = remember(activityContext) { activityContext.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                // ISSUE45_UPDATE_CONFIRMATION_IS_NEVER_LOST_V1: Android's question may not have opened while away.
                UpdateInstallCoordinator.showConfirmation(activityContext, onlyIfNotShown = true)
                resumeAfterPermission?.let { release ->
                    resumeAfterPermission = null
                    if (updateInstallAllowed(appContext)) UpdateInstallCoordinator.start(appContext, release)
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val state = remember(revision, checking, checkMessage, install) {
        UpdatesScreenState(
            installedVersionName = BuildConfig.VERSION_NAME,
            runtimeEnabled = runtimeEnabled,
            latest = store.knownRelease(),
            available = store.availableRelease(BuildConfig.VERSION_CODE),
            lastCheckedEpochMillis = store.lastCheckEpochMillis(),
            nowEpochMillis = System.currentTimeMillis(),
            checking = checking,
            checkMessage = checkMessage,
            install = install,
        )
    }

    UpdatesContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
        onCheckNow = {
            checking = true
            checkMessage = null
            scope.launch {
                val checked = UpdateCheckCoordinator.check(appContext, force = true)
                val available = store.availableRelease(BuildConfig.VERSION_CODE)
                // This screen shows the release itself, so the dialog does not open over it.
                available?.let { release ->
                    UpdatePresentationCoordinator.withdrawAvailable()
                    store.markUpdatePresented(release.versionCode)
                }
                checkMessage = when {
                    !checked -> UPDATES_CHECK_FAILED
                    available == null -> UPDATES_UP_TO_DATE
                    else -> null
                }
                checking = false
                revision++
            }
        },
        onUpdate = ::startUpdate,
        onContinueInstall = { UpdateInstallCoordinator.showConfirmation(activityContext) },
        onViewRelease = { release -> openUpdateRelease(activityContext, release.releaseUrl) },
    )

    permissionNeededFor?.let { release ->
        AlertDialog(
            onDismissRequest = { permissionNeededFor = null },
            title = { Text("Allow subshell to install its update") },
            text = {
                Text(
                    "Android asks once whether subshell may install apps. subshell installs only its own " +
                        "update from the GitHub release, after checking it against the published SHA-256, " +
                        "and Android asks you before it installs.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionNeededFor = null
                        try {
                            activityContext.startActivity(updateInstallPermissionIntent(activityContext))
                            resumeAfterPermission = release
                        } catch (_: ActivityNotFoundException) {
                            // No settings page to open; View release still works.
                        }
                    },
                ) { Text("Open Android settings") }
            },
            dismissButton = {
                TextButton(onClick = { permissionNeededFor = null }) { Text("Not now") }
            },
        )
    }
}

@Composable
internal fun UpdatesContent(
    state: UpdatesScreenState,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    onUpdate: (UpdateManifestRelease) -> Unit,
    onViewRelease: (UpdateManifestRelease) -> Unit,
    modifier: Modifier = Modifier,
    onContinueInstall: () -> Unit = {},
) {
    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("Updates") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
            },
            scrollBehavior = routeTopBarScroll,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenMargin, top = 20.dp, end = ScreenMargin, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("versions") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VersionLine("Installed", state.installedVersionName)
                    if (state.runtimeEnabled) {
                        VersionLine("Latest", state.latest?.versionName ?: "Not checked yet")
                        if (state.lastCheckedEpochMillis > 0L) {
                            VersionLine(
                                "Last checked",
                                DateUtils.getRelativeTimeSpanString(
                                    state.lastCheckedEpochMillis,
                                    state.nowEpochMillis,
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                            )
                        }
                    }
                }
            }

            if (!state.runtimeEnabled) {
                item("off") {
                    Text(
                        UPDATES_OFF_IN_THIS_BUILD,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }

            state.available?.let { release ->
                item("available") {
                    AvailableRelease(
                        release = release,
                        install = state.install.takeIf { it.versionCode() == release.versionCode }
                            ?: UpdateInstallState.Idle,
                        onUpdate = { onUpdate(release) },
                        onContinueInstall = onContinueInstall,
                        onViewRelease = { onViewRelease(release) },
                    )
                }
            }

            item("check") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheckNow, enabled = !state.checking) {
                        Text(if (state.checking) "Checking…" else "Check now")
                    }
                    state.checkMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AvailableRelease(
    release: UpdateManifestRelease,
    install: UpdateInstallState,
    onUpdate: () -> Unit,
    onContinueInstall: () -> Unit,
    onViewRelease: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("subshell ${release.versionName} is available", style = MaterialTheme.typography.titleMedium)
            Text(release.headline, style = MaterialTheme.typography.bodyMedium)
            release.highlights.forEach { highlight ->
                Text("• $highlight", style = MaterialTheme.typography.bodyMedium)
            }
            SelectionContainer {
                Text(
                    updateApkHashLine(release),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (install) {
                is UpdateInstallState.Downloading -> {
                    if (install.total > 0L) {
                        LinearProgressIndicator(
                            progress = { (install.downloaded.toFloat() / install.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(updateDownloadProgressText(install), style = MaterialTheme.typography.bodyMedium)
                }
                is UpdateInstallState.Installing ->
                    Text(
                        if (install.waitingForYou) UPDATE_WAITING_FOR_YOU else "Android is installing the update…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                is UpdateInstallState.Failed ->
                    Text(
                        install.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                UpdateInstallState.Idle -> Unit
            }
            val waiting = install is UpdateInstallState.Installing && install.waitingForYou
            val busy = install is UpdateInstallState.Downloading ||
                (install is UpdateInstallState.Installing && !install.waitingForYou)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (waiting) {
                    Button(onClick = onContinueInstall) { Text("Continue") }
                } else {
                    Button(onClick = onUpdate, enabled = !busy) {
                        Text(if (install is UpdateInstallState.Failed) "Try again" else "Update")
                    }
                }
                TextButton(onClick = onViewRelease) { Text("View release") }
            }
        }
    }
}

private fun UpdateInstallState.versionCode(): Int? = when (this) {
    is UpdateInstallState.Downloading -> versionCode
    is UpdateInstallState.Installing -> versionCode
    is UpdateInstallState.Failed -> versionCode
    UpdateInstallState.Idle -> null
}

internal fun updateDownloadProgressText(state: UpdateInstallState.Downloading): String {
    fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))
    return if (state.total > 0L) {
        "Downloading… ${mb(state.downloaded)} of ${mb(state.total)} MB"
    } else {
        "Downloading…"
    }
}
