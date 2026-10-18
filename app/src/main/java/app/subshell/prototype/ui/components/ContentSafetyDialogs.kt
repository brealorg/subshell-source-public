package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.subshell.prototype.data.account.REPORT_REASON_MAX_CHARS
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditReportTargetKind
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.symbols.Symbols

// SAFETY01A3_SHARED_REDDIT_REPORT_DIALOG_V1
@Composable
internal @OptIn(ExperimentalMaterial3Api::class)
fun RedditContentReportDialog(
    targetLabel: String,
    communityName: String,
    targetKind: RedditReportTargetKind,
    state: SubshellState,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit,
) {
    // SAFETY01A3_SHARED_REDDIT_REPORT_DIALOG_V1
    // REPORT01A1_MATERIAL3_NATIVE_REPORT_REASON_SHEET_V1
    var selectedReason by rememberSaveable(communityName, targetKind.name) { mutableStateOf<String?>(null) }
    var otherSelected by rememberSaveable(communityName, targetKind.name) { mutableStateOf(false) }
    var customReason by rememberSaveable(communityName, targetKind.name) { mutableStateOf("") }

    LaunchedEffect(state.activeAccountId, communityName, targetKind) {
        state.ensureReportReasonPolicy(communityName)
    }

    val policy = state.reportReasonPolicy(communityName, targetKind)
    val loading = state.reportReasonPolicyLoading(communityName) ||
        (policy == null && !state.reportReasonPolicyFailed(communityName))
    val failed = state.reportReasonPolicyFailed(communityName)
    val reason = if (otherSelected) customReason.trim() else selectedReason.orEmpty()
    val canReport = reason.isNotBlank() && reason.length <= REPORT_REASON_MAX_CHARS && !loading && !failed

        // REPORT01A1_M3_ALWAYS_EXPANDED_SHEET_V1
    val reportSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

ModalBottomSheet(
        sheetState = reportSheetState,onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Symbols.Flag, contentDescription = null, modifier = Modifier.padding(12.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Report $targetLabel", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Tell Reddit why this $targetLabel should be reported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Column(
                // ISSUE219_REPORT_REASONS_ARE_ONE_RADIO_GROUP_V1: "n of m" for the reasons.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Column {
                            Text("Loading report reasons", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Checking r/$communityName rules on Reddit…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    failed -> Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Couldn't load report reasons", style = MaterialTheme.typography.titleMedium)
                            Text("subshell won't guess whether r/$communityName accepts custom reports.")
                            TextButton(onClick = { state.ensureReportReasonPolicy(communityName, force = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                    policy != null -> {
                        if (policy.options.isNotEmpty()) {
                            Text("Community rules", style = MaterialTheme.typography.titleMedium)
                            policy.options.forEach { option ->
                                val selected = !otherSelected && selectedReason == option.wireReason
                                // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-131)
                                ListItem(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                    ) {
                                        selectedReason = option.wireReason
                                        otherSelected = false
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                    content = { Text(option.label) },
                                    supportingContent = option.description?.let { desc -> { Text(desc, maxLines = 2) } },
                                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                                )
                            }
                        }
                        if (policy.freeFormReportsAllowed) {
                            ListItem(
                                modifier = Modifier.fillMaxWidth().selectable(
                                    selected = otherSelected,
                                    role = Role.RadioButton,
                                ) {
                                    otherSelected = true
                                    selectedReason = null
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (otherSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                content = { Text("Something else") },
                                supportingContent = { Text("Write a custom reason for Reddit.") },
                                leadingContent = { RadioButton(selected = otherSelected, onClick = null) },
                            )
                            if (otherSelected) {
                                OutlinedTextField(
                                    value = customReason,
                                    onValueChange = { next -> if (next.length <= REPORT_REASON_MAX_CHARS) customReason = next },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Report reason") },
                                    supportingText = { Text("${customReason.length}/$REPORT_REASON_MAX_CHARS") },
                                    minLines = 2,
                                    maxLines = 4,
                                )
                            }
                        } else {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(
                                    if (policy.freeFormReportsKnown) "r/$communityName accepts only its listed report reasons."
                                    else "Reddit didn't confirm that r/$communityName accepts custom report reasons.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (policy.options.isEmpty() && !policy.freeFormReportsAllowed) {
                            Text(
                                "No safe report reason is available from Reddit for this $targetLabel.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // REPORT01A1_M3_SYSTEM_INSET_AWARE_ACTION_FOOTER_V1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onReport(reason) }, enabled = canReport) { Text("Report") }
            }
        }
    }
}
