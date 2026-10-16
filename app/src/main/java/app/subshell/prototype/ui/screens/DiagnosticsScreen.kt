package app.subshell.prototype.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.diagnostics.DiagnosticEvent
import app.subshell.prototype.data.diagnostics.DiagnosticEventStore
import app.subshell.prototype.data.diagnostics.DiagnosticEvents
import app.subshell.prototype.data.diagnostics.DiagnosticLevel
import app.subshell.prototype.data.diagnostics.DiagnosticOperation
import app.subshell.prototype.data.diagnostics.DiagnosticSnapshot
import app.subshell.prototype.data.diagnostics.DiagnosticSubsystem
import app.subshell.prototype.data.diagnostics.StagedDiagnosticReport
import app.subshell.prototype.data.diagnostics.SubshellDiagnosticSupport
import app.subshell.prototype.data.diagnostics.CrashRecordStore
import app.subshell.prototype.data.diagnostics.buildDiagnosticReport
import app.subshell.prototype.data.diagnostics.copyDiagnosticReport
import app.subshell.prototype.data.diagnostics.diagnosticReportEnvironment
import app.subshell.prototype.data.diagnostics.openPrefilledDiagnosticGitHubIssue
import app.subshell.prototype.data.diagnostics.shareDiagnosticReport
import app.subshell.prototype.data.diagnostics.shareDiagnosticReportByEmail
import app.subshell.prototype.data.diagnostics.stageDiagnosticReport
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.theme.groupedRowShape
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.subshell.prototype.ui.components.InlineProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember(appContext) { DiagnosticEventStore(appContext) }
    val crashStore = remember(appContext) { CrashRecordStore(appContext) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val reportSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val subsystemSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val supportConfiguration = SubshellDiagnosticSupport.configuration

    var snapshot by remember { mutableStateOf(DiagnosticSnapshot()) }
    var levelFilterName by rememberSaveable { mutableStateOf(ALL_FILTER) }
    var subsystemFilterName by rememberSaveable { mutableStateOf(ALL_FILTER) }
    var reportBusy by remember { mutableStateOf(false) }
    var stagedReport by remember { mutableStateOf<StagedDiagnosticReport?>(null) }
    var clearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var overflowVisible by rememberSaveable { mutableStateOf(false) }
    var subsystemSheetVisible by rememberSaveable { mutableStateOf(false) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun createDiagnosticReport(markUserIncident: Boolean) {
        reportBusy = true
        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (markUserIncident) {
                        store.recordUserIncident()
                    }
                    val updated = store.record(DiagnosticEvents.reportCreated())
                    val reportText = buildDiagnosticReport(
                        snapshot = updated,
                        environment = diagnosticReportEnvironment(appContext),
                        crashes = crashStore.records(),
                    )
                    val staged = stageDiagnosticReport(appContext, reportText)
                    updated to staged
                }
            }
            // LOWUI6_CANCELLATION_IS_RETHROWN_V1 (OCB-212): leaving the screen is
            // not a failed report.
            result.exceptionOrNull()?.let { error -> if (error is CancellationException) throw error }
            reportBusy = false
            result.onSuccess { (updated, staged) ->
                snapshot = updated
                stagedReport = staged
            }.onFailure {
                showMessage(
                    if (markUserIncident) {
                        "Couldn't create problem report"
                    } else {
                        "Couldn't create diagnostic report"
                    },
                )
            }
        }
    }

    // LOWUI8_DIAGNOSTICS_OPENED_ONCE_PER_VISIT_V1 (OCB-213): a recreation
    // composes the screen again; the visit is recorded once and later
    // compositions only read the events.
    var openedRecorded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(store) {
        snapshot = withContext(Dispatchers.IO) {
            if (openedRecorded) {
                store.snapshot()
            } else {
                store.record(DiagnosticEvents.diagnosticsOpened())
            }
        }
        openedRecorded = true
    }

    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text("Clear diagnostic history?") },
            text = {
                Text(
                    "This deletes the local diagnostic event history and crash records. " +
                        "It does not change your Reddit data or app settings.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmationVisible = false
                        coroutineScope.launch {
                            snapshot = withContext(Dispatchers.IO) {
                                store.clear()
                                crashStore.clear()
                                store.snapshot()
                            }
                            showMessage("Diagnostic history cleared")
                        }
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (subsystemSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { subsystemSheetVisible = false },
            sheetState = subsystemSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .selectableGroup(),
            ) {
                Text(
                    text = "Filter by subsystem",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                SubsystemSheetRow(
                    selected = subsystemFilterName == ALL_FILTER,
                    label = "All subsystems",
                    onClick = {
                        subsystemFilterName = ALL_FILTER
                        subsystemSheetVisible = false
                    },
                )
                DiagnosticSubsystem.entries.forEach { subsystem ->
                    SubsystemSheetRow(
                        selected = subsystemFilterName == subsystem.name,
                        label = diagnosticSubsystemLabel(subsystem),
                        onClick = {
                            subsystemFilterName = subsystem.name
                            subsystemSheetVisible = false
                        },
                    )
                }
            }
        }
    }

    stagedReport?.let { report ->
        ModalBottomSheet(
            onDismissRequest = { stagedReport = null },
            sheetState = reportSheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        ) {
            val environment = diagnosticReportEnvironment(appContext)
            DiagnosticReportPreviewSheet(
                report = report,
                canonicalDestinationsConfigured = supportConfiguration.hasCanonicalDestinations,
                onClose = { stagedReport = null },
                onGitHubIssue = supportConfiguration.githubRepository?.let { repository ->
                    {
                        val opened = openPrefilledDiagnosticGitHubIssue(
                            context = context,
                            repository = repository,
                            environment = environment,
                        )
                        showMessage(
                            if (opened) {
                                "GitHub issue opened in your browser"
                            } else {
                                "No browser is available to open the GitHub issue"
                            },
                        )
                    }
                },
                onEmail = supportConfiguration.emailAddress?.let { emailAddress ->
                    {
                        val shared = shareDiagnosticReportByEmail(
                            context = context,
                            report = report,
                            emailAddress = emailAddress,
                            environment = environment,
                        )
                        showMessage(
                            if (shared) {
                                "Diagnostic report ready to email"
                            } else {
                                "No email app is available to send the report"
                            },
                        )
                    }
                },
                onCopy = {
                    val copied = copyDiagnosticReport(context, report)
                    showMessage(
                        if (copied) {
                            "Diagnostics copied"
                        } else {
                            "Clipboard unavailable"
                        },
                    )
                },
                onShare = {
                    val shared = shareDiagnosticReport(context, report)
                    showMessage(
                        if (shared) {
                            "Diagnostic report ready to share"
                        } else {
                            "No app is available to share the report"
                        },
                    )
                },
            )
        }
    }

    val levelFilter = levelFilterName
        .takeUnless { it == ALL_FILTER }
        ?.let { runCatching { DiagnosticLevel.valueOf(it) }.getOrNull() }
    val subsystemFilter = subsystemFilterName
        .takeUnless { it == ALL_FILTER }
        ?.let { runCatching { DiagnosticSubsystem.valueOf(it) }.getOrNull() }
    val visibleEvents = remember(snapshot, levelFilter, subsystemFilter) {
        snapshot.events.asReversed().filter { event ->
            (levelFilter == null || event.level == levelFilter) &&
                (subsystemFilter == null || event.subsystem == subsystemFilter)
        }
    }

    Box(modifier.fillMaxSize()) {
        val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
        Column(Modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
            RouteTopBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { overflowVisible = true },
                            enabled = snapshot.events.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = overflowVisible,
                            onDismissRequest = { overflowVisible = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear diagnostic history") },
                                onClick = {
                                    overflowVisible = false
                                    clearConfirmationVisible = true
                                },
                                enabled = snapshot.events.isNotEmpty(),
                            )
                        }
                    }
                },
                scrollBehavior = routeTopBarScroll,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            ) {
                item("diagnostics-status") {
                    DiagnosticsStatusCard(
                        snapshot = snapshot,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                item("diagnostics-report-action") {
                    Button(
                        onClick = {
                            createDiagnosticReport(markUserIncident = false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(bottom = 10.dp),
                        enabled = !reportBusy,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        if (reportBusy) {
                            InlineProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (reportBusy) "Creating report…" else "Create diagnostic report")
                    }
                }

                item("diagnostics-user-incident") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Something went wrong?",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Mark this moment and open a diagnostic report preview. " +
                                    "No screenshot, typed description or Reddit content is captured.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    createDiagnosticReport(markUserIncident = true)
                                },
                                enabled = !reportBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Report a problem")
                            }
                        }
                    }
                }

                item("diagnostics-privacy") {
                    PrivacyBoundaryCard(
                        expanded = privacyExpanded,
                        onToggle = { privacyExpanded = !privacyExpanded },
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                item("diagnostics-filters") {
                    Column(
                        modifier = Modifier.padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Severity",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // LOWUI7_PREVIEWS_GROW_WITH_LARGE_TEXT_V1 (OCB-316): the chips
                        // scroll sideways instead of being cut off at large text.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DiagnosticFilterChip(
                                selected = levelFilterName == ALL_FILTER,
                                label = "All",
                                onClick = { levelFilterName = ALL_FILTER },
                            )
                            DiagnosticLevel.entries.forEach { level ->
                                DiagnosticFilterChip(
                                    selected = levelFilterName == level.name,
                                    label = diagnosticLevelLabel(level),
                                    onClick = { levelFilterName = level.name },
                                )
                            }
                        }

                        SubsystemFilterSurface(
                            selectedLabel = subsystemFilter?.let(::diagnosticSubsystemLabel)
                                ?: "All subsystems",
                            onClick = { subsystemSheetVisible = true },
                        )
                    }
                }

                item("diagnostics-event-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Event history",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (levelFilter == null && subsystemFilter == null) {
                                    "Newest events first"
                                } else {
                                    "Filtered event history"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (levelFilter == null && subsystemFilter == null) {
                                "${snapshot.events.size} events"
                            } else {
                                "${visibleEvents.size} shown"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (visibleEvents.isEmpty()) {
                    item("diagnostics-empty") {
                        DiagnosticsEmptyState(
                            filtered = snapshot.events.isNotEmpty(),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = visibleEvents,
                        key = { _, event -> event.id },
                    ) { index, event ->
                        DiagnosticEventRow(
                            event = event,
                            index = index,
                            total = visibleEvents.size,
                            modifier = Modifier.padding(
                                bottom = if (index == visibleEvents.lastIndex) 0.dp else 1.dp,
                            ),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DiagnosticsStatusCard(
    snapshot: DiagnosticSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Diagnostics active",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Always on · stored locally",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiagnosticMetric(
                    value = "${snapshot.events.size}",
                    label = "Events",
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    value = formatDiagnosticBytes(snapshot.bytesUsed),
                    label = "Stored",
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    value = "On",
                    label = "Auto-prune",
                    modifier = Modifier.weight(1f),
                )
            }

            if (snapshot.invalidRecordCount > 0 || snapshot.droppedEventCount > 0L) {
                Text(
                    buildString {
                        if (snapshot.droppedEventCount > 0L) {
                            append("${snapshot.droppedEventCount} old events pruned")
                        }
                        if (snapshot.droppedEventCount > 0L && snapshot.invalidRecordCount > 0) {
                            append(" · ")
                        }
                        if (snapshot.invalidRecordCount > 0) {
                            append("${snapshot.invalidRecordCount} invalid records ignored")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyBoundaryCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Private by design",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "No Reddit content or credentials are collected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "Hide" else "Details")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "subshell stores structured app events only. OAuth tokens, cookies, " +
                        "usernames, request/response bodies and message text are excluded from " +
                        "the diagnostic buffer.",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 18.dp else 10.dp,
        label = "diagnostic-filter-shape",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(cornerRadius),
    )
}

@Composable
private fun SubsystemFilterSurface(
    selectedLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.FilterAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Subsystem",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selectedLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubsystemSheetRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // ISSUE219_SUBSYSTEM_FILTER_SAYS_WHICH_IS_SELECTED_V1: one radio row each
    // (LOWUI7), so TalkBack reads "selected" and the position in the list.
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        content = { Text(label) },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
            )
        },
    )
}

@Composable
private fun DiagnosticsEmptyState(filtered: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (filtered) Icons.Rounded.FilterAlt else Icons.Rounded.History,
                        contentDescription = null,
                    )
                }
            }
            Text(
                text = if (filtered) "No matching events" else "No diagnostic events yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (filtered) {
                    "Change the filters to see more of the stored history."
                } else {
                    "subshell will add structured events here as the app runs."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticEventRow(
    event: DiagnosticEvent,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val shape = groupedRowShape(
        index = index,
        count = total,
        outer = MaterialTheme.shapes.extraLarge,
        inner = MaterialTheme.shapes.extraSmall,
    )
    val levelColors = diagnosticLevelColors(event.level)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = levelColors.container,
                contentColor = levelColors.content,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (event.level) {
                            DiagnosticLevel.INFO -> Icons.Rounded.Info
                            DiagnosticLevel.WARNING, DiagnosticLevel.ERROR -> Icons.Rounded.ErrorOutline
                        },
                        contentDescription = diagnosticLevelLabel(event.level),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        diagnosticOperationLabel(event),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        diagnosticEventTime(event.timestampEpochMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    "${diagnosticSubsystemLabel(event.subsystem)} · " +
                        event.outcome.name.lowercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.failure?.let { failure ->
                    val detail = buildList {
                        failure.httpStatusCode?.let { add("HTTP $it") }
                        failure.transportKind?.let { add("Network $it") }
                        failure.protocolKind?.let { add("Response $it") }
                        failure.jsonPath?.let { add("Field $it") }
                        failure.localKind?.let { add("Local $it") }
                        failure.providerCode?.let { add("Provider $it") }
                        failure.mediaCode?.let { add("Media $it") }
                        failure.retryable?.let { add("Retryable $it") }
                        failure.retryAfterMs?.let { add("Retry after ${it}ms") }
                    }.joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColors.content,
                        )
                    }
                }
                event.scopeFingerprint?.let { scope ->
                    Text(
                        "Scope $scope",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticReportPreviewSheet(
    report: StagedDiagnosticReport,
    canonicalDestinationsConfigured: Boolean,
    onClose: () -> Unit,
    onGitHubIssue: (() -> Unit)?,
    onEmail: (() -> Unit)?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val reportVerticalScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Diagnostic report",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Preview · shared report contains the full event history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SelectionContainer {
                Text(
                    text = diagnosticReportPreview(report.text),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(reportVerticalScroll)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = true,
                )
            }
        }

        onGitHubIssue?.let { openIssue ->
            Button(
                onClick = openIssue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Open prefilled GitHub issue")
            }
        }

        onEmail?.let { emailSupport ->
            Button(
                onClick = emailSupport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Send report by email")
            }
        }

        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Copy diagnostics")
        }

        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                Icons.Rounded.Storage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(if (canonicalDestinationsConfigured) "Share another way" else "Share report")
        }
    }
}

private data class DiagnosticLevelColorTokens(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
)

@Composable
private fun diagnosticLevelColors(level: DiagnosticLevel): DiagnosticLevelColorTokens = when (level) {
    DiagnosticLevel.INFO -> DiagnosticLevelColorTokens(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    DiagnosticLevel.WARNING -> DiagnosticLevelColorTokens(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    DiagnosticLevel.ERROR -> DiagnosticLevelColorTokens(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

private fun diagnosticOperationLabel(event: DiagnosticEvent): String = when (event.operation) {
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.APP_SESSION_STARTED -> "App session started"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.VIEWPORT_ERROR_PRESENTED -> "Viewport error presented"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.BLOCKING_ERROR_PRESENTED -> "Blocking error presented"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.DEGRADED_ERROR_PRESENTED -> "Degraded warning presented"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.INLINE_ERROR_PRESENTED -> "Inline error presented"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.DIALOG_ERROR_PRESENTED -> "Error dialog presented"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.BACKGROUND_SYNC_FAILED -> "Background sync failed"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.BACKGROUND_AUTHORIZATION_REQUIRED -> "Background authorization required"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.NOTIFICATION_POST_FAILED -> "Notification post failed"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.DIAGNOSTICS_OPENED -> "Diagnostics opened"
    app.subshell.prototype.data.diagnostics.DiagnosticOperation.REPORT_CREATED -> "Diagnostic report created"
    DiagnosticOperation.USER_INCIDENT_MARKED -> "User reported a problem"
    DiagnosticOperation.APP_CRASHED -> "subshell closed unexpectedly"
    DiagnosticOperation.APP_NATIVE_CRASHED -> "subshell closed unexpectedly (native)"
    DiagnosticOperation.APP_NOT_RESPONDING -> "subshell stopped responding"
}

private fun diagnosticLevelLabel(level: DiagnosticLevel): String = when (level) {
    DiagnosticLevel.INFO -> "Info"
    DiagnosticLevel.WARNING -> "Warning"
    DiagnosticLevel.ERROR -> "Error"
}

private fun diagnosticSubsystemLabel(subsystem: DiagnosticSubsystem): String =
    subsystem.name
        .lowercase(Locale.ROOT)
        .replaceFirstChar { char -> char.titlecase(Locale.ROOT) }

// LOWUX3_DIAGNOSTIC_EVENTS_CARRY_THEIR_DATE_V1 (OCB-214): an event from
// yesterday read like one from today; events not from today show the date.
internal fun diagnosticEventTime(epochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
    val format = if (day == today) {
        DateFormat.getTimeInstance(DateFormat.MEDIUM)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }
    return format.format(Date(epochMs))
}

private fun formatDiagnosticBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "$bytes B"
}

private fun diagnosticReportPreview(text: String): String =
    if (text.length <= MAX_REPORT_PREVIEW_CHARS) {
        text
    } else {
        text.take(MAX_REPORT_PREVIEW_CHARS) +
            "\n\n… preview truncated; the shared report contains the full event history."
    }

private const val MAX_REPORT_PREVIEW_CHARS = 60_000
private const val ALL_FILTER = "ALL"
