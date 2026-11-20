package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.model.CommunityWikiPageRef
import io.github.brealorg.subshell.ui.symbols.Symbols

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

internal const val WIKI_CONFIGURATION_TEXT_TEST_TAG = "wiki-configuration-text"

internal fun communityWikiRevisionDate(epochSeconds: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        .format(java.util.Date(epochSeconds * 1_000L))

/** The page (or the Wiki) on reddit.com, which shows Reddit's new Wiki. */
internal fun communityWikiWebUrl(communityName: String, pagePath: String?): String =
    "https://www.reddit.com/r/$communityName/wiki/" + (pagePath?.trim()?.trimStart('/') ?: "")

@Composable
private fun OpenWikiOnRedditButton(url: String) {
    val context = LocalContext.current
    TextButton(onClick = { openRedditPageInBrowser(context, url) }) {
        Text(stringResource(R.string.comp_open_wiki_on_reddit))
    }
}

internal fun communityWikiConfigurationPath(path: String): Boolean {
    val normalized = path.trim().lowercase()
    return normalized.startsWith("config/") ||
        normalized == "automoderator-schedule" ||
        normalized.startsWith("automoderator/") ||
        normalized.startsWith("mod/")
}

internal fun LazyListScope.communityWikiItems(
    state: RedditCommunityWikiUiState,
    // WIKI03_THE_NEW_WIKI_IS_NAMED_V1: for "Open on reddit.com".
    communityName: String,
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
                    stringResource(R.string.all_loading_wiki)
                } else {
                    stringResource(R.string.all_loading_wiki_page)
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
                webUrl = communityWikiWebUrl(communityName, selectedPagePath),
                httpStatusCode = state.failureHttpStatusCode,
                details = state.failureDetails,
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
                CommunityWikiLoading(stringResource(R.string.all_loading_wiki_page))
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
                // WIKI04_THE_REVISION_DATE_IS_SHOWN_V1: the date tells how old the
                // text Reddit's API gave is.
                val revisedOn = page.revisionDateEpochSeconds?.let(::communityWikiRevisionDate)
                val author = page.revisionAuthor
                val revisedLine = when {
                    revisedOn != null && author != null ->
                        stringResource(R.string.comp_last_revised_on_by_u, revisedOn, author)
                    revisedOn != null -> stringResource(R.string.comp_last_revised_on, revisedOn)
                    author != null -> stringResource(R.string.comp_last_revised_by_u, author)
                    else -> null
                }
                revisedLine?.let { line ->
                    Text(
                        text = line,
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
                // WIKI03_THE_NEW_WIKI_IS_NAMED_V1: Reddit's API reads the old Wiki;
                // a page edited on reddit.com after the Wiki moved can differ.
                // WIKI04_A_PAGE_SAYS_IT_MAY_BE_OLDER_V1: seen 2026-11-18, an edit on
                // reddit.com never reached the API (r/astronotyet). Config pages
                // sync to both Wikis and need no warning.
                if (!communityWikiConfigurationPath(page.path)) {
                    Text(
                        text = stringResource(R.string.comp_wiki_page_may_be_older),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OpenWikiOnRedditButton(communityWikiWebUrl(communityName, page.path))
            }
        }

        item("wiki-page-body-${page.path}") {
            if (page.markdown.isBlank()) {
                CommunityWikiNotice(
                    title = stringResource(R.string.comp_empty_wiki_page),
                    message = stringResource(R.string.all_reddit_returned_this_wiki_page_without_markdown),
                )
            } else if (communityWikiConfigurationPath(page.path)) {
                // WIKI03_CONFIGURATION_PAGES_READ_AS_TEXT_V1: AutoModerator's YAML and the
                // other config pages are code, not Markdown: as Markdown a "# comment"
                // became a heading and the indentation was lost.
                SelectionContainer {
                    Text(
                        text = page.markdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WIKI_CONFIGURATION_TEXT_TEST_TAG),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
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
            CommunityWikiLoading(stringResource(R.string.all_loading_wiki))
        }
        return
    }

    if (index.pages.isEmpty()) {
        item("wiki-index-empty") {
            CommunityWikiNotice(
                title = stringResource(R.string.comp_no_wiki_pages),
                message = stringResource(R.string.all_reddit_did_not_return_any_wiki_pages),
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
                text = stringResource(R.string.comp_community_pages),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.comp_choose_a_page_to_read_it_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (groups.pages.isEmpty()) {
        item("wiki-readable-pages-empty") {
            // WIKI03_THE_NEW_WIKI_IS_NAMED_V1: a community whose Wiki lives in Reddit's
            // new Wiki shows only its configuration pages here (seen in r/subshell).
            CommunityWikiNotice(
                title = stringResource(R.string.comp_no_general_wiki_pages),
                message = stringResource(R.string.comp_wiki_index_new_system),
                action = { OpenWikiOnRedditButton(communityWikiWebUrl(communityName, null)) },
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
                        text = stringResource(R.string.comp_configuration_pages),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.wiki_advanced_pages,
                            groups.configuration.size,
                            groups.configuration.size,
                        ),
                    )
                },
                trailingContent = {
                    Text(
                        text = if (showConfigurationPages) stringResource(R.string.all_hide) else stringResource(R.string.all_show),
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
                text = stringResource(R.string.comp_wiki_access),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    stringResource(R.string.comp_reddit_requires_separate_permission_to_read),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (authorizing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.comp_waiting_for_reddit))
                }
            } else {
                Button(onClick = onAuthorize) {
                    Text(stringResource(R.string.comp_enable_wiki_access))
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
    webUrl: String,
    httpStatusCode: Int?,
    // BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1
    details: io.github.brealorg.subshell.data.error.FailureDetails?,
    retryable: Boolean,
    onRetry: (() -> Unit)?,
) {
    val title: String
    val message: String
    when (kind) {
        RedditCommunityWikiUiFailureKind.UNAVAILABLE -> {
            title = stringResource(R.string.comp_wiki_unavailable)
            // WIKI03_THE_NEW_WIKI_IS_NAMED_V1
            message = stringResource(R.string.comp_wiki_page_new_system)
        }
        RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION -> {
            title = stringResource(R.string.comp_wiki_unavailable)
            message = stringResource(R.string.all_wiki_access_is_not_configured_for_this)
        }
        RedditCommunityWikiUiFailureKind.SESSION -> {
            title = stringResource(R.string.comp_wiki_access_needs_attention)
            message = stringResource(R.string.all_the_reddit_session_could_not_be_used)
        }
        RedditCommunityWikiUiFailureKind.HTTP -> {
            title = stringResource(R.string.comp_couldn_t_load_wiki)
            message = io.github.brealorg.subshell.ui.error.redditWikiHttpFailureMessage(httpStatusCode, details).asString()
        }
        RedditCommunityWikiUiFailureKind.TRANSPORT -> {
            title = stringResource(R.string.comp_couldn_t_reach_reddit)
            message = stringResource(R.string.comp_subshell_couldn_t_reach_reddit_while)
        }
        RedditCommunityWikiUiFailureKind.PROTOCOL -> {
            title = stringResource(R.string.comp_couldn_t_read_wiki)
            message =
                stringResource(R.string.all_reddit_returned_a_wiki_response_that_subshell)
        }
    }

    val openOnReddit = kind == RedditCommunityWikiUiFailureKind.UNAVAILABLE
    CommunityWikiNotice(
        title = title,
        message = message,
        action = if ((retryable && onRetry != null) || openOnReddit) {
            {
                Row {
                    if (retryable && onRetry != null) {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.comp_retry))
                        }
                    }
                    if (openOnReddit) OpenWikiOnRedditButton(webUrl)
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
