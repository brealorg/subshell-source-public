package io.github.brealorg.subshell.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.UrlCleaner
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.noticeCopied
import io.github.brealorg.subshell.ui.noticeFailure
import io.github.brealorg.subshell.ui.symbols.Symbols
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LINKMENU01_LONG_PRESS_A_LINK_V1 (owner 2026-11-11): a long press on a web link, in text or on a
 * link post's card, opens a small menu: share the link, copy it, open it in the browser, and, when
 * Clean links changed it, copy the original. Without this a link shown short could only be copied
 * as its short text.
 */
internal const val LINK_ORIGINAL_TAG = "subshell-link-original"

/** The address a long press acts on: [original] as written, [link] as it is shared, copied and opened. */
internal data class LinkActionTarget(val original: String, val link: String) {
    val hasCleanedLink: Boolean get() = link != original
}

internal fun linkActionTarget(original: String, cleanLinks: Boolean): LinkActionTarget? {
    val trimmed = original.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
    return LinkActionTarget(original = trimmed, link = if (cleanLinks) UrlCleaner.clean(trimmed) else trimmed)
}

/**
 * The original address of the web link under [position] in a laid-out [text], or null. The press
 * counts as on the link when the character on either side of the caret nearest to it belongs to
 * one: a finger is wider than a glyph, and a layout is not asked for glyph boxes.
 */
internal fun linkOriginalAt(text: AnnotatedString, layout: TextLayoutResult?, position: Offset): String? {
    layout ?: return null
    if (text.isEmpty()) return null
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line)) return null
    val offset = layout.getOffsetForPosition(position)
    return listOf(offset, offset - 1)
        .filter { index -> index in 0 until text.length }
        .firstNotNullOfOrNull { index ->
            text.getStringAnnotations(LINK_ORIGINAL_TAG, index, index + 1).firstOrNull()?.item
        }
}

/** Holds the latest layout of a [LinkText] without recomposing on every layout pass. */
internal class TextLayoutHolder {
    var layout: TextLayoutResult? = null
}

/**
 * Long press on a link inside a Text. A press that does not start on a link is left alone, a
 * tap still reaches the Text's own link handling, and a scroll is not a long press (as
 * [longPressOnly], ISSUE51_SCROLL_IS_NOT_A_LONG_PRESS_V1). Only the timeout running out is one.
 */
internal fun Modifier.linkLongPress(
    text: () -> AnnotatedString,
    layout: () -> TextLayoutResult?,
    onLongPress: (String) -> Unit,
): Modifier = pointerInput(Unit) {
    // The Initial pass comes before the Text's own link handling (which takes the press and the
    // lift of a link for itself) and before a SelectionContainer around it, so a long press on a
    // link can be told apart and its lift can be kept from opening the link.
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val original = linkOriginalAt(text(), layout(), down.position) ?: return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed || (change.position - down.position).getDistance() > slop
            }
            true
        }
        if (endedEarly == null) {
            onLongPress(original)
            // Swallow the rest of this gesture so the lift is not a tap on the link.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change -> change.consume() }
            } while (event.changes.any { change -> change.pressed })
        }
    }
}

/** A [Text] whose links answer a long press with [onLinkLongPress] (the link's original address). */
@Composable
internal fun LinkText(
    text: AnnotatedString,
    onLinkLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    style: TextStyle = TextStyle.Default,
) {
    val holder = remember { TextLayoutHolder() }
    val currentText by rememberUpdatedState(text)
    val currentLongPress by rememberUpdatedState(onLinkLongPress)
    Text(
        text = text,
        modifier = modifier.linkLongPress(
            text = { currentText },
            layout = { holder.layout },
            onLongPress = { original -> currentLongPress(original) },
        ),
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        onTextLayout = { result -> holder.layout = result },
        style = style,
    )
}

/**
 * The card a link post shows (domain, short address, the open-in-new icon). The link menu uses the
 * same card as its header, so the menu says at once what it is about.
 */
@Composable
internal fun LinkCardSurface(
    domain: String,
    address: String,
    modifier: Modifier = Modifier,
    iconDescription: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(domain, style = MaterialTheme.typography.labelLarge)
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Symbols.OpenInNew, contentDescription = iconDescription)
        }
    }
}

/** The host of [link] without "www.", for the card's first line. */
internal fun linkDomain(link: String): String =
    runCatching { java.net.URI(link).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        ?: "External link"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkActionsSheet(
    target: LinkActionTarget,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notices = LocalViewportErrorHostState.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        LinkCardSurface(
            domain = linkDomain(target.link),
            address = UrlCleaner.cardLabel(target.link),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            content = { Text("Share link") },
            leadingContent = { Icon(Symbols.Share, contentDescription = null) },
            modifier = Modifier.clickable {
                onDismiss()
                shareLinkAddress(context, target.link, notices)
            },
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            content = { Text("Copy link") },
            leadingContent = { Icon(Symbols.ContentCopy, contentDescription = null) },
            modifier = Modifier.clickable {
                onDismiss()
                copyLinkAddress(context, target.link, notices)
            },
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            content = { Text("Open in browser") },
            leadingContent = { Icon(Symbols.OpenInNew, contentDescription = null) },
            modifier = Modifier.clickable {
                onDismiss()
                runCatching { uriHandler.openUri(target.link) }.onFailure {
                    notices.noticeFailure("Couldn't open the link", "No app on this device can open it")
                }
            },
        )
        if (target.hasCleanedLink) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Copy original link") },
                supportingContent = { Text("As written, with tracking parameters") },
                leadingContent = { Icon(Symbols.Link, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    copyLinkAddress(context, target.original, notices)
                },
            )
        }
    }
}

internal fun shareLinkAddress(context: Context, link: String, notices: io.github.brealorg.subshell.ui.error.ViewportErrorHostState?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share link"))
    }.onFailure {
        notices.noticeFailure("Couldn't share the link", "No app on this device can share it")
    }
}

internal fun copyLinkAddress(context: Context, link: String, notices: io.github.brealorg.subshell.ui.error.ViewportErrorHostState?) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        notices.noticeFailure("Couldn't copy the link", "The clipboard is unavailable")
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
    notices.noticeCopied("Link")
}
