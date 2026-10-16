package app.subshell.prototype.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.subshell.prototype.BuildConfig
import kotlinx.coroutines.delay

internal val EASTER_EGG_UNLOCK_PULL = 72.dp
internal val EASTER_EGG_UNLOCK_START_ZONE = 112.dp

@Composable
internal fun EasterEggTerminalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val interactionSource = remember { MutableInteractionSource() }
    val sessionStartedAtMs = remember { SystemClock.elapsedRealtime() }
    val lines = remember {
        mutableStateListOf(
            "subshell://terminal",
            "session opened. type 'help' if you must.",
        )
    }
    val history = remember { mutableStateListOf<String>() }

    var shellDepth by remember { mutableIntStateOf(1) }
    var input by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(0) }
    var collapseRequested by remember { mutableStateOf(false) }
    var matrixMode by remember { mutableStateOf(false) }
    var keyboardKeepAliveToken by remember { mutableIntStateOf(0) }

    fun submit() {
        val command = input.trim()
        if (command.isEmpty()) return

        val prompt = easterEggTerminalPrompt(shellDepth)
        history += command
        historyCursor = history.size
        lines += "$prompt $command"
        input = ""
        keyboardKeepAliveToken++

        val result = executeEasterEggTerminalCommand(
            rawCommand = command,
            shellDepth = shellDepth,
            versionName = BuildConfig.VERSION_NAME,
            uptimeSeconds = (SystemClock.elapsedRealtime() - sessionStartedAtMs) / 1_000L,
            history = history,
        )
        if (result.clear) {
            lines.clear()
        } else {
            lines.addAll(result.output)
        }
        shellDepth = result.nextShellDepth
        result.matrixMode?.let { matrixMode = it }
        if (result.collapse) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            collapseRequested = true
        }
    }

    fun recallHistory(step: Int) {
        if (history.isEmpty()) return
        historyCursor = (historyCursor + step).coerceIn(0, history.size)
        input = if (historyCursor == history.size) "" else history[historyCursor]
    }

    LaunchedEffect(Unit) {
        delay(120L)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(keyboardKeepAliveToken) {
        if (keyboardKeepAliveToken == 0) return@LaunchedEffect
        delay(80L)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    LaunchedEffect(collapseRequested) {
        if (!collapseRequested) return@LaunchedEffect
        delay(320L)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(180L)
        onBack()
    }

    val terminalBackground = if (matrixMode) Color(0xFF000600) else Color(0xFF0B0D0E)
    val terminalForeground = if (matrixMode) Color(0xFF71FF71) else Color(0xFFE7E7E2)
    val promptColor = if (matrixMode) Color(0xFF00FF66) else MaterialTheme.colorScheme.primary
    val terminalStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = terminalForeground,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(terminalBackground)
            .imePadding()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "SUBSHELL",
            color = promptColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Text(
            text = "local session · no network · back closes terminal",
            color = terminalForeground.copy(alpha = 0.55f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))

        SelectionContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line,
                        style = terminalStyle,
                        color = when {
                            line.startsWith("subshell") && line.contains('$') -> promptColor
                            line.startsWith("ENOSPC:") -> MaterialTheme.colorScheme.error
                            else -> terminalForeground
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = easterEggTerminalPrompt(shellDepth),
                style = terminalStyle,
                color = promptColor,
                fontWeight = FontWeight.Bold,
            )
            Text(" ", style = terminalStyle)
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = input,
                    onValueChange = { next ->
                        if ('\n' !in next && '\r' !in next) input = next
                    },
                    // LOWUI7_LABELS_SAY_WHAT_A_CONTROL_DOES_V1 (OCB-215)
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "Terminal command" }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    recallHistory(-1)
                                    true
                                }
                                Key.DirectionDown -> {
                                    recallHistory(1)
                                    true
                                }
                                Key.Enter -> {
                                    submit()
                                    true
                                }
                                else -> false
                            }
                        },
                    singleLine = true,
                    textStyle = terminalStyle,
                    cursorBrush = SolidColor(promptColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
                if (input.isEmpty()) {
                    Text(
                        text = "_",
                        style = terminalStyle,
                        color = promptColor.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}
