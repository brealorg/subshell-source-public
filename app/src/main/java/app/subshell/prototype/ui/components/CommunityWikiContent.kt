package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.account.RedditCommunityWikiUiFailureKind
import app.subshell.prototype.data.account.RedditCommunityWikiUiState
import app.subshell.prototype.model.CommunityWikiPageRef
import app.subshell.prototype.ui.symbols.Symbols

// WIKI01A2_COMMUNITY_WIKI_FUNCTIONAL_PRESENTATION_V1
// WIKI01A2R1_DEDICATED_WIKI_WORKSPACE_V1
internal data class CommunityWikiPageGroups(
    val pages: List<CommunityWikiPageRef>,
    val configuration: List<CommunityWikiPageRef>,
)

internal fun communityWikiPageGroups(
    pages: List<CommunityWikiPageRef>,
): CommunityWikiPageGroups {
    val ordered = pages.sortedWith(
        compareBy<CommunityWikiPageRef>(
            { page ->
                if (page.path.equals("index", ignoreCase = true)) 0 else 1
            },
            { page -> page.path.lowercase() },
        ),
    )
    val (configuration, readable) = ordered.partition { page ->
        communityWikiConfigurationPath(page.path)
    }
    return CommunityWikiPageGroups(
        pages = readable,
        configuration = configuration,
    )
}

private fun communityWikiConfigurationPath(path: String): Boolean {
    val normalized = path.trim().lowercase()
    return normalized.startsWith("config/") ||
        normalized == "automoderator-schedule" ||
        normalized.startsWith("automoderator/") ||
        normalized.startsWith("mod/")
}

internal fun LazyListScope.communityWikiItems(
    state: RedditCommunityWikiUiState,
    matches: Boolean,
    selectedPagePath: String?,
    showConfigurationPages: Boolean,
    onSelectedPagePathChange: (String?) -> Unit,
    onShowConfigurationPagesChange: (Boolean) -> Unit,
    onAuthorize: () -> Unit,
    onRetryIndex: () -> Unit,
    onRetryPage: (String) -> Unit,
) {
    if (!matches || state.loading) {
        item("wiki-loading") {
            CommunityWikiLoading(
                label = if (selectedPagePath == null) {
                    "Loading Wiki…"
                } else {
                    "Loading Wiki page…"
                },
            )
        }
        return
    }

    if (state.authorizationRequired) {
        item("wiki-authorization") {
            CommunityWikiAuthorization(
                authorizing = state.authorizing,
                onAuthorize = onAuthorize,
            )
        }
        return
    }

    val failure = state.failureKind
    if (failure != null) {
        item("wiki-failure-${failure.name}") {
            CommunityWikiFailure(
                kind = failure,
                httpStatusCode = state.failureHttpStatusCode,
                retryable = state.failureRetryable,
                onRetry = if (state.failureRetryable) {
                    {
                        if (selectedPagePath == null) {
                            onRetryIndex()
                        } else {
                            onRetryPage(selectedPagePath)
                        }
                    }
                } else {
                    null
                },
            )
        }
        return
    }

    if (selectedPagePath != null) {
        val page = state.page
            ?.takeIf { candidate -> candidate.path == selectedPagePath }

        if (page == null) {
            item("wiki-page-awaiting") {
                CommunityWikiLoading("Loading Wiki page…")
            }
            return
        }

        item("wiki-page-title-${page.path}") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = page.path,
                    style = MaterialTheme.typography.titleLarge,
                )
                page.revisionAuthor?.let { author ->
                    Text(
                        text = "Last revised by u/$author",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                page.revisionReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item("wiki-page-body-${page.path}") {
            if (page.markdown.isBlank()) {
                CommunityWikiNotice(
                    title = "Empty Wiki page",
                    message = "Reddit returned this Wiki page without Markdown content.",
                )
            } else {
                MarkdownText(
                    text = page.markdown,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    val index = state.index
    if (index == null) {
        item("wiki-index-awaiting") {
            CommunityWikiLoading("Loading Wiki…")
        }
        return
    }

    if (index.pages.isEmpty()) {
        item("wiki-index-empty") {
            CommunityWikiNotice(
                title = "No Wiki pages",
                message = "Reddit did not return any Wiki pages for this community.",
            )
        }
        return
    }

    val groups = communityWikiPageGroups(index.pages)

    item("wiki-index-heading") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Community pages",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Choose a page to read it in subshell.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (groups.pages.isEmpty()) {
        item("wiki-readable-pages-empty") {
            CommunityWikiNotice(
                title = "No general Wiki pages",
                message =
                    "This Wiki only returned configuration or moderation pages.",
            )
        }
    } else {
        items(
            items = groups.pages,
            key = { page -> "wiki-page-${page.path.lowercase()}" },
        ) { page ->
            CommunityWikiPageRow(
                page = page,
                onClick = { onSelectedPagePathChange(page.path) },
            )
        }
    }

    if (groups.configuration.isNotEmpty()) {
        item("wiki-configuration-toggle") {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = {
                    Text(
                        text = "Configuration pages",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        text = "${groups.configuration.size} advanced ${
                            if (groups.configuration.size == 1) "page" else "pages"
                        }",
                    )
                },
                trailingContent = {
                    Text(
                        text = if (showConfigurationPages) "Hide" else "Show",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onShowConfigurationPagesChange(!showConfigurationPages)
                    },
            )
            HorizontalDivider()
        }

        if (showConfigurationPages) {
            items(
                items = groups.configuration,
                key = { page ->
                    "wiki-configuration-${page.path.lowercase()}"
                },
            ) { page ->
                CommunityWikiPageRow(
                    page = page,
                    onClick = { onSelectedPagePathChange(page.path) },
                )
            }
        }
    }
}

@Composable
private fun CommunityWikiPageRow(
    page: CommunityWikiPageRef,
    onClick: () -> Unit,
) {
    // WIKI01A2R2_EXPRESSIVE_PAGE_ROW_V1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            content = {
                Text(
                    text = page.path,
                    maxLines = 2,
                    fontWeight =
                        if (page.path.equals("index", ignoreCase = true)) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Symbols.Article,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Symbols.ChevronRight,
                    contentDescription = null, // the row reads the page name (ISSUE219)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun CommunityWikiAuthorization(
    authorizing: Boolean,
    onAuthorize: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Wiki access",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    "Reddit requires separate permission to read community Wiki pages. " +
                        "This permission is read-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (authorizing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text("Waiting for Reddit…")
                }
            } else {
                Button(onClick = onAuthorize) {
                    Text("Enable Wiki access")
                }
            }
        }
    }
}

@Composable
private fun CommunityWikiLoading(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineProgressIndicator()
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommunityWikiFailure(
    kind: RedditCommunityWikiUiFailureKind,
    httpStatusCode: Int?,
    retryable: Boolean,
    onRetry: (() -> Unit)?,
) {
    val title: String
    val message: String
    when (kind) {
        RedditCommunityWikiUiFailureKind.UNAVAILABLE -> {
            title = "Wiki unavailable"
            message =
                "This community Wiki is unavailable through Reddit's API " +
                    "or the requested page does not exist."
        }
        RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION -> {
            title = "Wiki unavailable"
            message = "Wiki access is not configured for this Reddit account."
        }
        RedditCommunityWikiUiFailureKind.SESSION -> {
            title = "Wiki access needs attention"
            message = "The Reddit session could not be used to load this Wiki."
        }
        RedditCommunityWikiUiFailureKind.HTTP -> {
            title = "Couldn't load Wiki"
            message = httpStatusCode
                ?.let { status ->
                    "Reddit returned HTTP $status for this Wiki request."
                }
                ?: "Reddit rejected this Wiki request."
        }
        RedditCommunityWikiUiFailureKind.TRANSPORT -> {
            title = "Couldn't reach Reddit"
            message = "subshell couldn't reach Reddit while loading this Wiki."
        }
        RedditCommunityWikiUiFailureKind.PROTOCOL -> {
            title = "Couldn't read Wiki"
            message =
                "Reddit returned a Wiki response that subshell couldn't read."
        }
    }

    CommunityWikiNotice(
        title = title,
        message = message,
        action = if (retryable && onRetry != null) {
            {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CommunityWikiNotice(
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
