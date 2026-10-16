package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.RepositoryLoadState
import app.subshell.prototype.data.RepositoryPagingSnapshot
import app.subshell.prototype.data.error.toSafeTechnicalDetail

@Composable
internal fun RepositoryPagingFooter(
    snapshot: RepositoryPagingSnapshot<*>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = when {
        snapshot.appendLoadState != RepositoryLoadState.Idle -> snapshot.appendLoadState
        snapshot.refreshLoadState != RepositoryLoadState.Idle -> snapshot.refreshLoadState
        snapshot.items.isEmpty() && snapshot.initialLoadState != RepositoryLoadState.Idle ->
            snapshot.initialLoadState
        else -> RepositoryLoadState.Idle
    }
    if (state == RepositoryLoadState.Idle) return
    // LOWUX2_PAGING_FOOTER_NAMES_ITS_LOAD_V1 (OCB-158): a first load that failed
    // read "Could not load more" (nothing was loaded) and a refresh read
    // "Loading more…"; the copy follows which load the footer shows.
    val appending = snapshot.appendLoadState != RepositoryLoadState.Idle
    val refreshing = !appending && snapshot.refreshLoadState != RepositoryLoadState.Idle

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                RepositoryLoadState.Loading -> {
                    InlineProgressIndicator()
                    Text(
                        when {
                            appending -> "Loading more…"
                            refreshing && snapshot.items.isNotEmpty() -> "Refreshing…"
                            else -> "Loading…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is RepositoryLoadState.Offline -> {
                    Column(Modifier.weight(1f)) {
                        Text("Offline", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (snapshot.items.isEmpty()) {
                                "No cached items are available."
                            } else {
                                "Showing stale cached items."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.failureDetails?.toSafeTechnicalDetail()?.let { detail ->
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onRetry) { Text("Retry") }
                }

                is RepositoryLoadState.Error -> {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                appending -> "Could not load more"
                                refreshing && snapshot.items.isNotEmpty() -> "Could not refresh"
                                else -> "Could not load"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.failureDetails?.toSafeTechnicalDetail()?.let { detail ->
                            Text(
                                detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = onRetry) { Text("Retry") }
                }

                RepositoryLoadState.Idle -> Unit
            }
        }
    }
}
