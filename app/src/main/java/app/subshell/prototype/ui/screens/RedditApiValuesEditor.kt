package app.subshell.prototype.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.subshell.prototype.auth.RedditApiValues
import app.subshell.prototype.auth.RedditApiValueRules

// ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1: the redirect URI and user
// agent are shown read only; Edit asks first, then the fields open with their
// own errors. Reset to defaults saves the shipped values at once (the client
// ID stays). The dialog around it (SettingsScreen) saves the drafts.

/** What the reader typed while the API values are open for editing. */
@Stable
internal class RedditApiValuesDraft(stored: RedditApiValues) {
    var warningVisible by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var redirectUri by mutableStateOf(stored.redirectUri)
    var userAgent by mutableStateOf(stored.userAgent)

    val valid: Boolean
        get() = !editing || (redirectUriError(redirectUri) == null && userAgentError(userAgent) == null)

    fun changes(stored: RedditApiValues): Boolean =
        editing && (redirectUri.trim() != stored.redirectUri || userAgent.trim() != stored.userAgent)
}

internal fun redirectUriError(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> "Enter a redirect URI"
        trimmed.substringBefore("://", "").lowercase() in setOf("http", "https") ->
            "A web address can’t be the sign-in callback"
        !RedditApiValueRules.isValidRedirectUri(trimmed) ->
            "Use an app address with ://, like ${RedditApiValueRules.defaultRedirectUri}"
        else -> null
    }
}

internal fun userAgentError(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.isEmpty() -> "Enter a user agent"
        trimmed.length > MAX_API_VALUE_LENGTH -> "At most $MAX_API_VALUE_LENGTH characters"
        !RedditApiValueRules.isValidUserAgent(trimmed) -> "Use letters, numbers and plain punctuation only"
        else -> null
    }
}

/**
 * Save is offered when something valid would change: a new client ID, or,
 * with one stored, edited API values (a blank client ID field keeps it).
 */
internal fun redditApiSaveEnabled(
    clientIdDraft: String,
    clientIdValid: Boolean,
    stored: RedditApiValues,
    draft: RedditApiValuesDraft?,
): Boolean {
    val keepsClientId = clientIdDraft.isBlank() && stored.clientIdConfigured
    if (!clientIdValid && !keepsClientId) return false
    if (draft != null && !draft.valid) return false
    return !keepsClientId || draft?.changes(stored) == true
}

@Composable
internal fun RedditApiValuesSection(
    stored: RedditApiValues,
    draft: RedditApiValuesDraft,
    enabled: Boolean,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Reddit API values", style = MaterialTheme.typography.titleSmall)
        when {
            draft.editing -> {
                ApiValueField(
                    label = "Redirect URI",
                    value = draft.redirectUri,
                    error = redirectUriError(draft.redirectUri),
                    enabled = enabled,
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { draft.redirectUri = it },
                )
                ApiValueField(
                    label = "User agent",
                    value = draft.userAgent,
                    error = userAgentError(draft.userAgent),
                    enabled = enabled,
                    keyboardType = KeyboardType.Ascii,
                    onValueChange = { draft.userAgent = it },
                )
                Row {
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            draft.redirectUri = RedditApiValueRules.defaultRedirectUri
                            draft.userAgent = RedditApiValueRules.defaultUserAgent
                        },
                    ) { Text("Use defaults") }
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            draft.editing = false
                            draft.redirectUri = stored.redirectUri
                            draft.userAgent = stored.userAgent
                        },
                    ) { Text("Stop editing") }
                }
            }
            draft.warningVisible -> {
                Text(
                    "Only change these if sign-in stopped working or you were told to. " +
                        "Wrong values break sign-in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row {
                    TextButton(enabled = enabled, onClick = { draft.warningVisible = false }) {
                        Text("Cancel")
                    }
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            draft.warningVisible = false
                            draft.editing = true
                        },
                    ) { Text("Edit anyway") }
                }
            }
            else -> {
                ApiValueRow(label = "Redirect URI", value = stored.redirectUri)
                ApiValueRow(label = "User agent", value = stored.userAgent)
                if (stored.customized) {
                    Text(
                        "Changed from the defaults",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(enabled = enabled, onClick = { draft.warningVisible = true }) {
                        Text("Edit")
                    }
                    if (stored.customized && stored.clientIdConfigured) {
                        TextButton(enabled = enabled, onClick = onResetToDefaults) {
                            Text("Reset to defaults")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiValueRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ApiValueField(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
    )
}

internal const val REDDIT_CLIENT_ID_CHANGE_WARNING =
    "Accounts signed in with the current client ID will have to sign in again."

private const val MAX_API_VALUE_LENGTH = 512
