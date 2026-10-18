package app.subshell.prototype.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.subshell.prototype.ui.ContentFilterDraftError
import app.subshell.prototype.ui.ContentFilterType
import app.subshell.prototype.ui.ContentFilters
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.SettingsListItem
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.components.settingsRowBleed
import app.subshell.prototype.ui.contentFilterDraftError
import app.subshell.prototype.ui.contentFilterInputWithoutControlCharacters
import app.subshell.prototype.ui.contentFilterSummary
import app.subshell.prototype.ui.symbols.Symbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFiltersScreen(
    filters: ContentFilters,
    onAdd: (ContentFilterType, String, String?) -> Unit,
    onRemove: (ContentFilterType, String, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by rememberSaveable { mutableStateOf<ContentFilterType?>(null) }
    var activeFiltersVisible by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1
    var draftCommunity by rememberSaveable { mutableStateOf("") }

    fun navigateUp() {
        when {
            activeFiltersVisible -> activeFiltersVisible = false
            selectedType != null -> {
                selectedType = null
                draft = ""
                draftCommunity = ""
            }
            else -> onBack()
        }
    }

    BackHandler(enabled = activeFiltersVisible || selectedType != null) {
        navigateUp()
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = {
                Text(
                    when {
                        activeFiltersVisible -> "Active filters"
                        selectedType != null -> selectedType?.label.orEmpty()
                        else -> "Content filters"
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = ::navigateUp) {
                    Icon(Symbols.ArrowBack, contentDescription = "Back")
                }
            },
            scrollBehavior = routeTopBarScroll,
        )

        when {
            activeFiltersVisible -> ActiveFiltersContent(
                filters = filters,
                onRemove = onRemove,
            )

            selectedType != null -> ContentFilterEditor(
                type = checkNotNull(selectedType),
                filters = filters,
                draft = draft,
                onDraftChange = { draft = it },
                draftCommunity = draftCommunity,
                onDraftCommunityChange = { draftCommunity = it },
                onAdd = onAdd,
                onRemove = onRemove,
            )

            else -> ContentFilterOverview(
                filters = filters,
                onOpenActiveFilters = { activeFiltersVisible = true },
                onOpenType = { type ->
                    selectedType = type
                    draft = ""
                    draftCommunity = ""
                },
            )
        }
    }
}

@Composable
private fun ContentFilterOverview(
    filters: ContentFilters,
    onOpenActiveFilters: () -> Unit,
    onOpenType: (ContentFilterType) -> Unit,
) {
    val hasActiveFilters = filters.size > 0

    LazyColumn(
        contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = 32.dp),
    ) {
        item("summary") {
            Surface(
                onClick = onOpenActiveFilters,
                enabled = hasActiveFilters,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (hasActiveFilters) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (hasActiveFilters) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Symbols.FilterAlt, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            contentFilterSummary(filters),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            // LOWUX3_FILTER_OVERVIEW_NAMES_EVERY_PLACE_V1 (OCB-191)
                            "Matching posts are hidden from your feeds, community pages and search on this device. " +
                                "Nothing is changed on Reddit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasActiveFilters) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (hasActiveFilters) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Symbols.ArrowForward,
                            contentDescription = "Show all active filters",
                        )
                    }
                }
            }
        }

        item("filter-by") {
            Text(
                "Filter by",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }

        items(
            items = ContentFilterType.entries,
            key = ContentFilterType::name,
        ) { type ->
            ContentFilterTypeRow(
                type = type,
                activeCount = filterEntryViews(filters, type).size,
                onClick = { onOpenType(type) },
            )
        }
    }
}

@Composable
private fun ContentFilterTypeRow(
    type: ContentFilterType,
    activeCount: Int,
    onClick: () -> Unit,
) {
    // M3225_SETTINGS_ROWS_ARE_LIST_ITEMS_V1
    SettingsListItem(
        title = type.label,
        description = type.description,
        modifier = Modifier
            .settingsRowBleed()
            .clickable(onClick = onClick),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(activeCount.toString())
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Icon(Symbols.ArrowForward, contentDescription = null)
            }
        },
    )
}

@Composable
private fun ActiveFiltersContent(
    filters: ContentFilters,
    onRemove: (ContentFilterType, String, String?) -> Unit,
) {
    val activeTypes = ContentFilterType.entries.filter { type ->
        filterEntryViews(filters, type).isNotEmpty()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = 32.dp),
    ) {
        item("active-explanation") {
            Text(
                "Every local filter currently hiding posts from your feeds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        if (activeTypes.isEmpty()) {
            item("active-empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("No active filters", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Nothing is currently being hidden by a content filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        activeTypes.forEach { type ->
            val entries = filterEntryViews(filters, type)
            item("active-title-${type.name}") {
                Text(
                    "${type.label} · ${entries.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(
                items = entries,
                key = { entry -> "active-${type.name}:${entry.key}" },
            ) { entry ->
                ContentFilterEntryRow(
                    entry = entry,
                    onRemove = { onRemove(type, entry.value, entry.community) },
                )
            }
        }
    }
}

@Composable
private fun ContentFilterEditor(
    type: ContentFilterType,
    filters: ContentFilters,
    draft: String,
    onDraftChange: (String) -> Unit,
    draftCommunity: String,
    onDraftCommunityChange: (String) -> Unit,
    onAdd: (ContentFilterType, String, String?) -> Unit,
    onRemove: (ContentFilterType, String, String?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val entries = filterEntryViews(filters, type)
    // CONTENTFILTER06_SCOPED_RULE_NEEDS_VALID_COMMUNITY_V1
    val draftError = contentFilterDraftError(type, draft, draftCommunity)

    fun addDraft() {
        if (draft.isBlank() || draftError != null) return
        onAdd(type, draft, draftCommunity.takeIf { type.scopable && it.isNotBlank() })
        onDraftChange("")
        focusManager.clearFocus()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = 32.dp),
    ) {
        item("add") {
            OutlinedTextField(
                value = draft,
                onValueChange = { value -> onDraftChange(contentFilterInputWithoutControlCharacters(value)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Add ${type.singularLabel}") },
                placeholder = { Text(type.placeholder) },
                isError = draftError == ContentFilterDraftError.VALUE,
                supportingText = {
                    Text(
                        if (draftError == ContentFilterDraftError.VALUE) {
                            contentFilterValueError(type)
                        } else {
                            type.description
                        },
                    )
                },
                trailingIcon = {
                    IconButton(
                        enabled = draft.isNotBlank() && draftError == null,
                        onClick = ::addDraft,
                    ) {
                        Icon(Symbols.Add, contentDescription = "Add filter")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft() }),
            )
        }

        // CONTENTFILTER03_COMMUNITY_SCOPED_FILTERS_V1
        if (type.scopable) {
            item("add-community") {
                OutlinedTextField(
                    value = draftCommunity,
                    onValueChange = { value -> onDraftCommunityChange(contentFilterInputWithoutControlCharacters(value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Only in community (optional)") },
                    placeholder = { Text("e.g. r/movies") },
                    isError = draftError == ContentFilterDraftError.COMMUNITY,
                    supportingText = {
                        Text(
                            if (draftError == ContentFilterDraftError.COMMUNITY) {
                                COMMUNITY_NAME_ERROR
                            } else {
                                "Leave empty to filter everywhere"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                )
            }
        }

        item("list-title") {
            Text(
                if (entries.isEmpty()) {
                    "No ${type.label.lowercase()} filtered"
                } else {
                    "${type.label} · ${entries.size}"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }

        items(
            items = entries,
            key = { entry -> "${type.name}:${entry.key}" },
        ) { entry ->
            ContentFilterEntryRow(
                entry = entry,
                onRemove = { onRemove(type, entry.value, entry.community) },
            )
        }
    }
}

@Composable
private fun ContentFilterEntryRow(
    entry: ContentFilterEntryView,
    onRemove: () -> Unit,
) {
    SettingsListItem(
        title = entry.value,
        description = entry.community?.let { community -> "Only in r/$community" },
        modifier = Modifier.settingsRowBleed(),
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    Symbols.Delete,
                    contentDescription = "Remove ${entry.label}",
                )
            }
        },
    )
}

/** A filter as the lists show it: everywhere, or only in one community. */
internal data class ContentFilterEntryView(
    val value: String,
    val community: String? = null,
) {
    val key: String get() = community?.let { "$it\t$value" } ?: value
    val label: String get() = community?.let { "$value in r/$it" } ?: value
}

internal fun filterEntryViews(filters: ContentFilters, type: ContentFilterType): List<ContentFilterEntryView> =
    filters.entries(type).sorted().map { value -> ContentFilterEntryView(value) } +
        filters.scopedEntries(type).map { rule -> ContentFilterEntryView(rule.value, rule.community) }

private const val COMMUNITY_NAME_ERROR = "Enter a community name, like r/movies"

/** What a refused value needs, by filter type (OCB-192). */
private fun contentFilterValueError(type: ContentFilterType): String = when (type) {
    ContentFilterType.COMMUNITY -> COMMUNITY_NAME_ERROR
    ContentFilterType.USER -> "Enter a username, like u/someone"
    ContentFilterType.DOMAIN -> "Enter a domain, like example.com"
    ContentFilterType.FLAIR -> "Enter a flair, like Discussion"
    ContentFilterType.WORD -> "Enter a word or phrase"
}
