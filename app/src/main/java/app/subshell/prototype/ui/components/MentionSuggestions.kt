package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditPeopleSearchUiState
import app.subshell.prototype.ui.MENTION_REMOTE_MIN_CHARS
import app.subshell.prototype.ui.applyMention
import app.subshell.prototype.ui.mentionQueryAt
import app.subshell.prototype.ui.mentionSuggestions
import kotlinx.coroutines.delay

/**
 * ISSUE229_MENTION_AUTOCOMPLETE_V1: what a composer needs to suggest people
 * while `@` or `u/` is being typed. [thread] comes from the open thread
 * without network; [remote] is the reader's own account's people search,
 * asked through [onSearch] after a short pause.
 */
@Immutable
data class ComposerMentions(
    val thread: List<String>,
    val remote: RedditPeopleSearchUiState = RedditPeopleSearchUiState(),
    val searchEnabled: Boolean = false,
    val onSearch: (String) -> Unit = {},
    val avatarFor: (String) -> String? = { null },
)

internal const val MENTION_SEARCH_DEBOUNCE_MS = 300L

@Composable
internal fun MentionSuggestions(
    state: TextFieldState,
    mentions: ComposerMentions,
    modifier: Modifier = Modifier,
) {
    val query by remember(state) {
        derivedStateOf {
            val selection = state.selection
            if (selection.collapsed) mentionQueryAt(state.text, selection.start) else null
        }
    }
    val currentMentions by rememberUpdatedState(mentions)
    val partial = query?.partial
    LaunchedEffect(partial) {
        val typed = partial ?: return@LaunchedEffect
        if (!currentMentions.searchEnabled || typed.length < MENTION_REMOTE_MIN_CHARS) return@LaunchedEffect
        delay(MENTION_SEARCH_DEBOUNCE_MS)
        currentMentions.onSearch(typed)
    }
    val active = query ?: return
    val remote = mentions.remote.takeIf { results ->
        mentions.searchEnabled &&
            results.searchQuery?.equals(active.partial, ignoreCase = true) == true
    }
    val remoteNames = remote?.items.orEmpty().map { it.username }
    val suggestions = mentionSuggestions(active.partial, mentions.thread, remoteNames)
    if (suggestions.isEmpty()) return
    val remoteAvatars = remote?.items.orEmpty().associate { it.username.lowercase() to it.avatarImageUri }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 192.dp)) {
            items(suggestions, key = { it.lowercase() }) { username ->
                MentionSuggestionRow(
                    username = username,
                    avatarUri = remoteAvatars[username.lowercase()] ?: mentions.avatarFor(username),
                    onPick = {
                        val current = mentionQueryAt(state.text, state.selection.start)
                            ?: return@MentionSuggestionRow
                        val edit = applyMention(state.text.toString(), current, username)
                        state.edit {
                            replace(0, length, edit.text)
                            selection = TextRange(edit.selectionStart, edit.selectionEnd)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MentionSuggestionRow(
    username: String,
    avatarUri: String?,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onPick)
            .semantics { contentDescription = "Mention u/$username" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Decorative: the name beside it says who it is.
        RemoteIdentityImage(
            uri = avatarUri,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        ) {
            Text(
                text = username.first().uppercase(),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "u/$username",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
