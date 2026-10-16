package app.subshell.prototype.ui.components

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.FlairRichTextSegment

private val leadingRedditFlairEmojiTokens =
    Regex("""^(?::[A-Za-z0-9_+.-]+:\s*)+""")

// USERFLAIR01A3B_SERVER_COLOR_POLICY_V1
internal data class RedditFlairServerColors(
    val backgroundArgb: Long,
    val foregroundArgb: Long,
)

private fun redditFlairBackgroundArgb(
    value: String?,
): Long? {
    val raw = value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    if (!raw.startsWith("#")) return null

    val hex = raw.substring(1)

    return when (hex.length) {
        6 -> hex
            .toLongOrNull(16)
            ?.let { rgb ->
                0xFF000000L or rgb
            }

        8 -> hex.toLongOrNull(16)

        else -> null
    }
}

internal fun redditFlairServerColors(
    backgroundColor: String?,
    textColor: String?,
): RedditFlairServerColors? {
    val background = redditFlairBackgroundArgb(
        backgroundColor,
    ) ?: return null

    val foreground = when (
        textColor
            ?.trim()
            ?.lowercase()
    ) {
        "light" -> 0xFFFFFFFFL
        "dark" -> 0xFF000000L
        else -> return null
    }

    return RedditFlairServerColors(
        backgroundArgb = background,
        foregroundArgb = foreground,
    )
}

// USERFLAIR01A3A_RAW_EMOJI_TOKEN_FALLBACK_V1
internal fun redditFlairFallbackText(value: String): String =
    leadingRedditFlairEmojiTokens
        .replace(value.trim(), "")
        .trim()

private fun FlairRichTextSegment.isRenderableFlairSegment(): Boolean =
    when (elementType.trim().lowercase()) {
        "emoji" -> !imageUrl.isNullOrBlank()
        else -> !text.isNullOrBlank()
    }

// USERFLAIR01A3A_SHARED_RICHTEXT_RENDERER_V1
@Composable
internal fun FlairRichTextLabel(
    text: String,
    richText: List<FlairRichTextSegment>,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    emojiSize: Dp = 16.dp,
) {
    val style = textStyle ?: MaterialTheme.typography.labelSmall
    val segments = richText
        .asSequence()
        .filter(FlairRichTextSegment::isRenderableFlairSegment)
        .take(20)
        .toList()
    val fallback = redditFlairFallbackText(text)

    if (segments.isEmpty() && fallback.isBlank()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (segments.isEmpty()) {
            Text(
                text = fallback,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            segments.forEach { segment ->
                when (segment.elementType.trim().lowercase()) {
                    "emoji" -> {
                        val imageUrl = segment.imageUrl
                            ?.takeIf(String::isNotBlank)
                            ?: return@forEach
                        RemoteImage(
                            uri = imageUrl,
                            label = segment.emojiRef
                                ?.takeIf(String::isNotBlank)
                                ?: "Flair emoji",
                            modifier = Modifier.size(emojiSize),
                            aspectRatio = null,
                            containerColor = Color.Transparent,
                            showFailureDetails = false,
                        )
                    }

                    else -> segment.text
                        ?.takeIf(String::isNotBlank)
                        ?.let { label ->
                            Text(
                                text = label,
                                style = style,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
            }
        }
    }
}

// USERFLAIR01A3B_SHARED_SERVER_STYLED_LABEL_V1
@Composable
internal fun ServerStyledFlairLabel(
    text: String,
    richText: List<FlairRichTextSegment>,
    textColor: String?,
    backgroundColor: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    emojiSize: Dp = 16.dp,
    fallbackBackground: Color? = null,
    fallbackForeground: Color? = null,
) {
    val serverColors = redditFlairServerColors(
        backgroundColor = backgroundColor,
        textColor = textColor,
    )

    val resolvedBackground = serverColors
        ?.backgroundArgb
        ?.let(::Color)
        ?: fallbackBackground

    val resolvedForeground = serverColors
        ?.foregroundArgb
        ?.let(::Color)
        ?: fallbackForeground

    val baseStyle = textStyle
        ?: MaterialTheme.typography.labelSmall

    val resolvedStyle = if (resolvedForeground != null) {
        baseStyle.copy(
            color = resolvedForeground,
        )
    } else {
        baseStyle
    }

    val resolvedModifier = if (resolvedBackground != null) {
        modifier
            .background(
                color = resolvedBackground,
                shape = MaterialTheme.shapes.small,
            )
            .padding(
                horizontal = 5.dp,
                vertical = 1.dp,
            )
    } else {
        modifier
    }

    FlairRichTextLabel(
        text = text,
        richText = richText,
        modifier = resolvedModifier,
        textStyle = resolvedStyle,
        emojiSize = emojiSize,
    )
}

// USERFLAIR01A2_AUTHOR_FLAIR_BADGE_V1
@Composable
internal fun AuthorFlairBadge(
    text: String,
    richText: List<FlairRichTextSegment> = emptyList(),
    textColor: String? = null,
    backgroundColor: String? = null,
    modifier: Modifier = Modifier,
) {
    val fallback = redditFlairFallbackText(text)
    val hasRenderableRichText =
        richText.any(FlairRichTextSegment::isRenderableFlairSegment)

    if (fallback.isBlank() && !hasRenderableRichText) return

    ServerStyledFlairLabel(
        text = text,
        richText = richText,
        textColor = textColor,
        backgroundColor = backgroundColor,
        modifier = modifier,
        textStyle = MaterialTheme.typography.labelSmall,
        emojiSize = 14.dp,
        fallbackBackground =
            MaterialTheme.colorScheme.secondaryContainer,
        fallbackForeground =
            MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

/**
 * For a flair that shares a line with the author: when the space left is
 * narrower than [minWidth], the flair takes no space and is not drawn,
 * instead of shrinking to a sliver of its background.
 */
internal fun Modifier.hiddenWhenNarrowerThan(minWidth: Dp): Modifier = layout { measurable, constraints ->
    if (constraints.maxWidth < minWidth.roundToPx()) {
        layout(0, 0) {}
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

internal val AuthorFlairMinVisibleWidth = 48.dp
