package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import app.subshell.prototype.ui.symbols.Symbols

// M3225_ONE_SEARCH_FIELD_STYLE_V1 (#225, owner: one search-field style app-wide):
// every search or filter field looks like the M3 search bar's input field
// (pill shape, 56 dp, search IME action) on the search bar container colour.
// The Search workspace and Communities discovery use it as their search bar;
// lists and dialogs use it as an inline filter.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubshellSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = {
        Icon(Symbols.Search, contentDescription = null)
    },
    trailingIcon: (@Composable () -> Unit)? = null,
    clearLabel: String = "Clear search",
    containerColor: Color = SearchBarDefaults.colors().containerColor,
    supportingText: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SearchInputSurface(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            onSearch = onSearch,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            clearLabel = clearLabel,
            containerColor = containerColor,
            fieldModifier = fieldModifier,
        )
        if (supportingText != null) {
            Box(Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    supportingText()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputSurface(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: (String) -> Unit,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    clearLabel: String,
    containerColor: Color,
    fieldModifier: Modifier,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SearchBarDefaults.inputFieldShape,
        color = containerColor,
    ) {
        // SEARCHFIELD01_AN_M3_TEXT_FIELD_V1 (owner 2026-10-17): material3 1.5
        // keeps only the search field tied to a SearchBarState, which expands a
        // search view on focus. The owner tried that view on Search and Discover
        // and chose the plain field, so every search field is an M3 TextField in
        // the search field's shape, height, colours and Search key, described
        // "Search" as Material's field was.
        TextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            modifier = fieldModifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search" },
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon ?: if (query.isNotEmpty() && enabled) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Symbols.Close, contentDescription = clearLabel)
                    }
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            shape = SearchBarDefaults.inputFieldShape,
            colors = SearchBarDefaults.inputFieldColors(),
        )
    }
}

/**
 * A collapsed search bar: looks like the search field and opens the search
 * where the typing happens (reading-21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchEntryBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SearchBarDefaults.InputFieldHeight),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Symbols.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                supportingText?.let { supporting ->
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
