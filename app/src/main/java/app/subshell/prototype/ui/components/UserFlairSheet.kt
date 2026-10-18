package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.error.ReportFailureDiagnostic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.UserFlairFailureKind
import app.subshell.prototype.data.account.USER_FLAIR_TEXT_MAX_CHARS
import app.subshell.prototype.data.account.UserFlairOption
import app.subshell.prototype.data.account.UserFlairUiState
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.symbols.Symbols

// USERFLAIR01A1_MATERIAL3_OWN_COMMUNITY_FLAIR_SHEET_V1
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFlairSheet(
    state: SubshellState,
    communityName: String,
    onDismiss: () -> Unit,
) {
    // LOWUI6_FLAIR_SHEET_SHOWS_ONLY_ITS_OWN_STATE_V1 (OCB-166): the coordinator
    // holds the last flair loaded (any community, any account); until this
    // sheet's own load arrives it shows loading, never another one's flair.
    val uiState = state.userFlairUiState
        .takeIf { loaded -> loaded.matches(state.activeAccountId, communityName) }
        ?: UserFlairUiState(loading = true)
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var selectedId by remember(communityName, uiState.contentRevision) {
        mutableStateOf(uiState.snapshot?.currentTemplateId)
    }
    var editedText by remember(communityName, uiState.contentRevision, selectedId) {
        val selected = uiState.snapshot?.options?.firstOrNull { it.templateId == selectedId }
        mutableStateOf(
            if (selectedId == uiState.snapshot?.currentTemplateId) {
                uiState.snapshot?.currentText ?: selected?.text.orEmpty()
            } else {
                selected?.text.orEmpty()
            },
        )
    }

    LaunchedEffect(state.activeAccountId, communityName) {
        state.loadUserFlair(communityName)
    }

    val selected: UserFlairOption? =
        uiState.snapshot?.options?.firstOrNull { it.templateId == selectedId }
    val canApply = selected != null && !uiState.loading && !uiState.mutating
    val applyFailed = uiState.failureKind != null && uiState.loaded && uiState.snapshot != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Symbols.Person, contentDescription = null)
                Column {
                    Text("User flair", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose how you appear in r/$communityName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val currentOption = uiState.snapshot
                ?.options
                ?.firstOrNull {
                    it.templateId == uiState.snapshot.currentTemplateId
                }
            val currentText = uiState.snapshot?.currentText
                ?: currentOption?.text
                .orEmpty()
            val currentRichText = uiState.snapshot
                ?.currentRichText
                .orEmpty()
                .ifEmpty {
                    currentOption?.richText.orEmpty()
                }
            val currentTextColor =
                uiState.snapshot?.currentTextColor
                    ?: currentOption?.textColor
            val currentBackgroundColor =
                uiState.snapshot?.currentBackgroundColor
                    ?: currentOption?.backgroundColor

            if (
                currentText.isNotBlank() ||
                currentRichText.isNotEmpty()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Current flair",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ServerStyledFlairLabel(
                            text = currentText,
                            richText = currentRichText,
                            textColor = currentTextColor,
                            backgroundColor = currentBackgroundColor,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            emojiSize = 22.dp,
                        )
                    }
                }
            }

            HorizontalDivider()

            when {
                uiState.loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading Reddit flair options…")
                }
                uiState.authorizationRequired -> {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Flair access required", style = MaterialTheme.typography.titleMedium)
                            Text("subshell needs Reddit's flair permission for this account.")
                            Button(onClick = { state.authorizeUserFlair() }) {
                                Text("Allow flair access")
                            }
                        }
                    }
                }
                // LOWUX1_FLAIR_APPLY_FAILURE_KEEPS_THE_CHOICE_V1 (OCB-167): with
                // the options loaded, a failure is a failed Apply; the list and
                // the reader's choice and text stay, the error shows by Apply.
                uiState.failureKind != null && !applyFailed -> {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Couldn't load user flair", style = MaterialTheme.typography.titleMedium)
                            Text("Failure: ${uiState.failureKind}")
                            TextButton(onClick = { state.loadUserFlair(communityName, force = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                uiState.loaded -> {
                    if (uiState.snapshot?.options.isNullOrEmpty()) {
                        Text(
                            "This community returned no self-selectable user flairs.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-168)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = uiState.snapshot.options.orEmpty(),
                                key = { option -> option.templateId },
                            ) { option ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedId == option.templateId,
                                            role = Role.RadioButton,
                                        ) {
                                            selectedId = option.templateId
                                            editedText = option.text
                                        },
                                    shape = MaterialTheme.shapes.large,
                                    color = if (selectedId == option.templateId) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ) {
                                    ListItem(
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        content = {
                                            ServerStyledFlairLabel(
                                                text = option.text.ifBlank {
                                                    "Editable flair"
                                                },
                                                richText = option.richText,
                                                textColor = option.textColor,
                                                backgroundColor =
                                                    option.backgroundColor,
                                                textStyle =
                                                    MaterialTheme.typography.labelLarge,
                                                emojiSize = 20.dp,
                                            )
                                        },
                                        supportingContent = if (option.editable) {
                                            { Text("Custom text allowed") }
                                        } else null,
                                        leadingContent = {
                                            RadioButton(
                                                selected = selectedId == option.templateId,
                                                onClick = null,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (selected?.editable == true) {
                        OutlinedTextField(
                            value = editedText,
                            onValueChange = {
                                if (it.length <= USER_FLAIR_TEXT_MAX_CHARS) editedText = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Flair text") },
                            supportingText = {
                                Text("${editedText.length}/$USER_FLAIR_TEXT_MAX_CHARS")
                            },
                            singleLine = true,
                        )
                    }
                }
            }

            ReportFailureDiagnostic(
                scopeKey = if (applyFailed) "user_flair_apply" else "user_flair_load",
                active = uiState.failureKind != null,
                failureDetails = uiState.failureDetails,
            )
            if (applyFailed) {
                Text(
                    text = when (uiState.failureKind) {
                        UserFlairFailureKind.OUTCOME_UNKNOWN ->
                            "Reddit may have changed your flair. Check the community before applying again."
                        UserFlairFailureKind.TRANSPORT -> "Couldn't apply the flair: no connection to Reddit."
                        else -> "Reddit didn't apply the flair. Your choice is kept; try again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Removal intentionally deferred until legacy clear semantics are qualified.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canApply,
                    onClick = {
                        val option = selected ?: return@Button
                        state.setUserFlair(
                            communityName = communityName,
                            templateId = option.templateId,
                            text = if (option.editable) editedText else null,
                        )
                    },
                ) {
                    Text(if (uiState.mutating) "Applying…" else "Apply")
                }
            }
        }
    }
}
