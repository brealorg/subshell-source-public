package app.subshell.prototype.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.redditSensitiveMediaLabPosts
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.components.PostCard
import app.subshell.prototype.ui.components.RouteTopBar
import app.subshell.prototype.ui.components.rememberRouteTopBarScrollBehavior
import app.subshell.prototype.ui.symbols.Symbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveMediaLabScreen(
    state: SubshellState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posts = remember { redditSensitiveMediaLabPosts() }
    val visiblePosts = posts.filter(state::showsPost)
    val nsfwPosts = posts.filter { post -> post.nsfw }
    val autoplayPost = posts.single { post ->
        post.id == "t3_lab_nsfw_video" && post.kind == PostKind.VIDEO
    }
    val autoplayAllowed = state.sensitiveMediaAutoplayAllowed(autoplayPost)
    val autoplayActive = state.activeAnimatedMediaId == autoplayPost.id

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(routeTopBarScroll.nestedScrollConnection)) {
        RouteTopBar(
            title = { Text("Sensitive media Reddit lab") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Symbols.ArrowBack, contentDescription = "Back")
                }
            },
            scrollBehavior = routeTopBarScroll,
        )

        LazyColumn(
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("mapping-proof") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Symbols.Science, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Production Reddit mapping",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            "Raw Listing JSON → Reddit DTO → toNetworkPage() → Post → " +
                                "local deterministic media → normal PostCard/PostMedia.",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${posts.size} decoded posts · ${nsfwPosts.size} NSFW · " +
                                "${posts.count { it.spoiler }} spoiler",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            item("visibility-control") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    // ISSUE219_LAB_NSFW_ROW_IS_THE_SWITCH_V1: as ToggleRow (LOWUI7).
                    Row(
                        modifier = Modifier
                            .toggleable(
                                value = state.showNsfwPosts,
                                role = Role.Switch,
                                onValueChange = state::updateShowNsfwPosts,
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show NSFW posts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${visiblePosts.size} of ${posts.size} lab posts visible",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.showNsfwPosts,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            item("preview-mode") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Sensitive preview mode", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            SensitiveMediaPreviewMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.sensitiveMediaPreviewMode == mode,
                                    onClick = { state.updateSensitiveMediaPreviewMode(mode) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                        TextButton(onClick = state::resetSensitiveMediaReveals) {
                            Text("Reset revealed media")
                        }
                    }
                }
            }

            item("autoplay-probe") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("NSFW video autoplay gate", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                autoplayActive -> "ACTIVE · the revealed video owns playback"
                                autoplayAllowed -> "ALLOWED · reveal completed; probe may start playback"
                                else -> "BLOCKED · reveal the NSFW video before probing"
                            },
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                state.setActiveAnimatedMedia(
                                    autoplayPost.id.takeIf {
                                        state.sensitiveMediaAutoplayAllowed(autoplayPost)
                                    },
                                )
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Probe autoplay candidate")
                        }
                    }
                }
            }

            items(visiblePosts, key = { post -> post.id }) { post ->
                PostCard(
                    post = post,
                    state = state,
                    onOpen = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
