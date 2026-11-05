package io.github.brealorg.subshell.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.theme.SubshellTheme
import kotlinx.coroutines.launch

/**
 * ISSUE267_POSTS_WIDGET_PICKS_ITS_PLACE_V1: where a Posts widget takes its
 * posts from — Home or one of the reader's joined communities (hidden ones
 * left out). Optional when the widget is added (it starts on Home); the
 * launcher offers it again from the widget's "Reconfigure". The launcher
 * starts it, so it is exported; it acts only on a widget of this app's own
 * Posts provider.
 */
class PostsWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (!isOwnPostsWidget(this, appWidgetId)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            SubshellTheme(dynamicColor = true) {
                var communities by remember { mutableStateOf<List<String>?>(null) }
                LaunchedEffect(Unit) {
                    communities = SubshellWidgetData.joinedCommunities(applicationContext)
                        .map { community -> community.name.removePrefix("r/") }
                }
                PostsWidgetSourcePicker(
                    current = PostsWidgetSourceStore.read(applicationContext, appWidgetId),
                    communities = communities,
                    onPick = { source -> choose(appWidgetId, source) },
                )
            }
        }
    }

    private fun choose(appWidgetId: Int, source: PostsWidgetSource) {
        PostsWidgetSourceStore.write(applicationContext, appWidgetId, source)
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
                PostsWidget().update(applicationContext, glanceId)
            }
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

internal fun isOwnPostsWidget(context: Context, appWidgetId: Int): Boolean {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
    return provider == ComponentName(context, PostsWidgetReceiver::class.java)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostsWidgetSourcePicker(
    current: PostsWidgetSource,
    communities: List<String>?,
    onPick: (PostsWidgetSource) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Show posts from") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { value -> filter = value },
                label = { Text("Find a community") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val query = filter.trim().removePrefix("r/").lowercase()
            val shown = communities.orEmpty().filter { name -> query.isEmpty() || query in name.lowercase() }
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isEmpty()) {
                    item(key = "home") {
                        SourceRow(
                            label = "Home",
                            supporting = "Your Home feed",
                            selected = current.community == null,
                            onClick = { onPick(PostsWidgetSource.Home) },
                        )
                        HorizontalDivider()
                    }
                }
                when {
                    communities == null -> item(key = "loading") {
                        ListItem(
                            content = { Text("Loading your communities…") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    shown.isEmpty() -> item(key = "none") {
                        ListItem(
                            content = {
                                Text(if (communities.isEmpty()) "No joined communities stored yet" else "No community matches")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    else -> items(shown, key = { name -> name.lowercase() }) { name ->
                        SourceRow(
                            label = "r/$name",
                            supporting = null,
                            selected = current.community.equals(name, ignoreCase = true),
                            onClick = { onPick(PostsWidgetSource(community = name)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(label: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        content = {
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supporting?.let { text -> { Text(text) } },
        trailingContent = if (selected) ({ Icon(Symbols.Check, contentDescription = "Shown now", tint = MaterialTheme.colorScheme.primary) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
