package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.subshell.prototype.ui.symbols.Symbols

// M3225_FORM_DIALOGS_ARE_FULL_SCREEN_ON_PHONES_V1 (#225, foundations-14, reading-07,
// modinbox-05/07, owner): a form or multi-step task opens as an M3 full-screen
// dialog below 600 dp (top bar: close X, title, text action) and as a basic
// dialog at most 560 dp wide on wider windows, instead of a hand-built
// near-full-screen card.
internal val FullScreenDialogMaxWidthDp = 600
private val WideDialogMaxWidth = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveFullScreenDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    closeEnabled: Boolean = true,
    closeLabel: String = "Close",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val fullScreen = LocalConfiguration.current.screenWidthDp < FullScreenDialogMaxWidthDp
    Dialog(
        onDismissRequest = { if (closeEnabled) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = !fullScreen,
        ),
    ) {
        val bar: @Composable () -> Unit = {
            TopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = navigationIcon ?: {
                    IconButton(enabled = closeEnabled, onClick = onDismissRequest) {
                        Icon(Symbols.Close, contentDescription = closeLabel)
                    }
                },
                actions = actions,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
        if (fullScreen) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    bar()
                    content()
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(max = WideDialogMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .imePadding(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        bar()
                        content()
                    }
                }
            }
        }
    }
}
