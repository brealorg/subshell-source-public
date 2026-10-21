package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import app.subshell.prototype.model.RedditMediaPlaceholder
import app.subshell.prototype.model.parseRedditMediaPlaceholders
import app.subshell.prototype.model.redditMediaPlaceholderAt
import app.subshell.prototype.ui.LocalCodeFontFamily
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import app.subshell.prototype.model.MarkdownLinkTarget
import app.subshell.prototype.model.markdownLinkTargetEnd
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import app.subshell.prototype.model.normalizedMarkdownLinkUrl
import app.subshell.prototype.model.plainLinkTokenRanges

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Spoiler(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?) : MarkdownBlock
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClick: ((String) -> Unit)? = null,
    highlight: MarkdownHighlight? = null,
    selectable: Boolean = false,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val quoteBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val quoteBar = MaterialTheme.colorScheme.primary
    val codeFontFamily = LocalCodeFontFamily.current
    val blocks = promoteMarkdownHeadings(
        parseMarkdownBlocks(text),
    )
    var revealedSpoilers by remember(text) { mutableStateOf(emptySet<String>()) }
    val revealSpoiler: (String) -> Unit = { spoiler -> revealedSpoilers = revealedSpoilers + spoiler }

    if (maxLines != Int.MAX_VALUE) {
        Text(
            text = markdownAnnotatedString(
                source = markdownPreviewSource(blocks),
                linkColor = linkColor,
                codeBackground = codeBackground,
                codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick,
                highlight = highlight,
                revealedSpoilers = revealedSpoilers,
                onRevealSpoiler = revealSpoiler,
            ),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = markdownEmoteInlineContent(text),
        )
        return
    }

    // ISSUE235_LONG_PRESS_SELECTS_TEXT_V1: where the full text is read (open
    // post, comments, messages) a long press selects it and Android's own
    // menu offers Copy, Share, Search and Translate. Previews stay plain.
    SelectableWhen(selectable) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> Text(
                        text = markdownAnnotatedString(block.text, linkColor, codeBackground, codeFontFamily, onLinkClick = onLinkClick, highlight = highlight, revealedSpoilers = revealedSpoilers, onRevealSpoiler = revealSpoiler),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        maxLines = maxLines,
                        overflow = overflow,
                        inlineContent = markdownEmoteInlineContent(block.text),
                    )
                    is MarkdownBlock.Heading -> Text(
                        text = markdownAnnotatedString(
                            block.text,
                            linkColor,
                            codeBackground,
                            codeFontFamily,
                                                onLinkClick = onLinkClick,
                            highlight = highlight,
                            revealedSpoilers = revealedSpoilers,
                            onRevealSpoiler = revealSpoiler,
                        ),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }.copy(
                            fontWeight =
                                androidx.compose.ui.text.font.FontWeight.SemiBold,
                        ),
                        color = color,
                        maxLines = maxLines,
                        overflow = overflow,
                        inlineContent = markdownEmoteInlineContent(block.text),
                    )
                    is MarkdownBlock.HorizontalRule -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    is MarkdownBlock.Quote -> Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = quoteBackground,
                    ) {
                        Text(
                            text = markdownAnnotatedString(block.text, linkColor, codeBackground, codeFontFamily, onLinkClick = onLinkClick, highlight = highlight, revealedSpoilers = revealedSpoilers, onRevealSpoiler = revealSpoiler),
                            modifier = Modifier
                                .drawBehind {
                                    drawRect(
                                        color = quoteBar,
                                        size = size.copy(width = 3.dp.toPx()),
                                    )
                                }
                                .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            maxLines = maxLines,
                            overflow = overflow,
                            inlineContent = markdownEmoteInlineContent(block.text),
                        )
                    }
                    is MarkdownBlock.Spoiler -> {
                        var revealed by remember(block.text) { mutableStateOf(false) }
                        val spoilerShape = MaterialTheme.shapes.extraSmall
                        // ISSUE219_SPOILER_SAYS_ITS_STATE_V1: TalkBack hears "Hidden"/"Revealed"
                        // and what a double tap does.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(spoilerShape)
                                .clickable(
                                    onClickLabel = if (revealed) "Hide spoiler" else "Reveal spoiler",
                                    role = Role.Button,
                                ) { revealed = !revealed }
                                .semantics { stateDescription = if (revealed) "Revealed" else "Hidden" },
                            shape = spoilerShape,
                            color = if (revealed) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            val spoilerText: @Composable () -> Unit = {
                                Text(
                                    text = if (revealed) {
                                        markdownAnnotatedString(block.text, linkColor, codeBackground, codeFontFamily, onLinkClick = onLinkClick)
                                    } else {
                                        AnnotatedString("Spoiler · tap to reveal")
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (revealed) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    inlineContent = markdownEmoteInlineContent(block.text),
                                )
                            }
                            // ISSUE235: a hidden spoiler's label is not part of the text.
                            if (revealed) spoilerText() else DisableSelection(spoilerText)
                        }
                    }
                    is MarkdownBlock.Code -> MarkdownCodeBlock(
                        block = block,
                        color = color,
                        maxLines = maxLines,
                        codeFontFamily = codeFontFamily,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableWhen(selectable: Boolean, content: @Composable () -> Unit) {
    if (selectable) SelectionContainer(content = content) else content()
}

private val MARKDOWN_ATX_HEADING =
    Regex("""^(#{1,6})\s*(.+?)\s*#*\s*$""")
private val MARKDOWN_HORIZONTAL_RULE =
    Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")

internal fun promoteMarkdownHeadings(
    blocks: List<MarkdownBlock>,
): List<MarkdownBlock> {
    val promoted = mutableListOf<MarkdownBlock>()
    blocks.forEach { block ->
        if (block !is MarkdownBlock.Paragraph) {
            promoted += block
            return@forEach
        }

        val paragraphLines = mutableListOf<String>()

        fun flushParagraphLines() {
            if (paragraphLines.isNotEmpty()) {
                promoted += MarkdownBlock.Paragraph(
                    paragraphLines.joinToString("\n"),
                )
                paragraphLines.clear()
            }
        }

        block.text.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (MARKDOWN_HORIZONTAL_RULE.matches(trimmedLine)) {
                flushParagraphLines()
                promoted += MarkdownBlock.HorizontalRule
                return@forEach
            }

            val match = MARKDOWN_ATX_HEADING.matchEntire(trimmedLine)
            val headingText = match
                ?.groupValues
                ?.getOrNull(2)
                ?.trim()
                ?.takeIf(String::isNotEmpty)

            if (match != null && headingText != null) {
                flushParagraphLines()
                promoted += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = headingText,
                )
            } else {
                paragraphLines += line
            }
        }

        flushParagraphLines()
    }
    return promoted
}

internal fun markdownPreviewSource(blocks: List<MarkdownBlock>): String =
    blocks.joinToString(separator = "\n") { block ->
        when (block) {
            is MarkdownBlock.Paragraph -> block.text
            is MarkdownBlock.Heading -> block.text
            is MarkdownBlock.HorizontalRule -> "---"
            is MarkdownBlock.Quote -> block.text
            // ISSUE81_SPOILERS_HIDDEN_UNTIL_TAPPED_V1: a whole-line spoiler stays a
            // spoiler in a preview (it printed its text in feed cards).
            is MarkdownBlock.Spoiler -> ">!${block.text}!<"
            is MarkdownBlock.Code -> block.code
        }
    }

@Composable
private fun MarkdownCodeBlock(
    block: MarkdownBlock.Code,
    color: Color,
    maxLines: Int,
    codeFontFamily: FontFamily,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = block.language?.replaceFirstChar(Char::uppercase) ?: "Code",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Code", block.code))
                    },
                ) {
                    Text("Copy")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = block.code.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                    color = color,
                    softWrap = false,
                    maxLines = maxLines,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun markdownAnnotatedString(
    source: String,
    linkColor: Color,
    codeBackground: Color,
    codeFontFamily: FontFamily,
    onLinkClick: ((String) -> Unit)? = null,
    highlight: MarkdownHighlight? = null,
    revealedSpoilers: Set<String> = emptySet(),
    onRevealSpoiler: ((String) -> Unit)? = null,
): AnnotatedString {
    val rendered = markdownStyledAnnotatedString(
        source = source,
        linkColor = linkColor,
        codeBackground = codeBackground,
        codeFontFamily = codeFontFamily,
        revealedSpoilers = revealedSpoilers,
    )
    val spoilers = if (onRevealSpoiler != null) rendered.hiddenMarkdownSpoilers() else emptyList()
    val links = markdownLinkTargetsForRenderedText(
        source = source,
        renderedText = rendered.text,
        renderedMarkdownLinks = rendered.renderedMarkdownLinks(),
    )

    if (links.isEmpty() && spoilers.isEmpty()) return rendered.withMarkdownHighlight(highlight)

    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )

    return buildAnnotatedString {
        append(rendered)

        links.forEach { link ->
            addLink(
                LinkAnnotation.Url(
                    url = link.url,
                    styles = linkStyles,
                    linkInteractionListener = onLinkClick?.let { handler ->
                        LinkInteractionListener { annotation ->
                            if (annotation is LinkAnnotation.Url) {
                                handler(annotation.url)
                            }
                        }
                    },
                ),
                start = link.start,
                end = link.end,
            )
        }
        spoilers.forEach { spoiler ->
            addLink(
                LinkAnnotation.Clickable(
                    tag = MARKDOWN_SPOILER_TAG,
                    linkInteractionListener = { onRevealSpoiler?.invoke(spoiler.item) },
                ),
                start = spoiler.start,
                end = spoiler.end,
            )
        }
    }.withMarkdownHighlight(highlight)
}

internal const val MARKDOWN_LINK_TARGET_TAG = "subshell-markdown-link"

/** Markdown links where markdownStyledAnnotatedString placed their labels. */
internal fun AnnotatedString.renderedMarkdownLinks(): List<MarkdownLinkTarget> =
    getStringAnnotations(MARKDOWN_LINK_TARGET_TAG, 0, length).mapNotNull { annotation ->
        normalizedMarkdownLinkUrl(annotation.item)?.let { url ->
            MarkdownLinkTarget(start = annotation.start, end = annotation.end, url = url)
        }
    }

internal const val MARKDOWN_SPOILER_TAG = "subshell-markdown-spoiler"
internal const val MARKDOWN_SPOILER_LABEL = "Spoiler"

/** Hidden inline spoilers where markdownStyledAnnotatedString placed their labels. */
internal fun AnnotatedString.hiddenMarkdownSpoilers(): List<AnnotatedString.Range<String>> =
    getStringAnnotations(MARKDOWN_SPOILER_TAG, 0, length)

internal fun markdownStyledAnnotatedString(
    source: String,
    linkColor: Color,
    codeBackground: Color,
    codeFontFamily: FontFamily,
    revealedSpoilers: Set<String> = emptySet(),
): AnnotatedString = buildAnnotatedString {
    appendMarkdownInline(
        source = markdownListMarkersAsBullets(source),
        linkColor = linkColor,
        codeBackground = codeBackground,
        codeFontFamily = codeFontFamily,
        revealedSpoilers = revealedSpoilers,
    )
}

// ISSUE82_LIST_ITEMS_ARE_BULLETS_V1: "* item" (how Reddit's editor writes a
// list), "- item" and "+ item" at the start of a line are list items, not an
// emphasis opener; they show a bullet. Numbered items already read as text.
private val MARKDOWN_LIST_MARKER = Regex("""(?m)^([ \t]*)[*+-] (?=\S)""")

internal fun markdownListMarkersAsBullets(source: String): String =
    source.replace(MARKDOWN_LIST_MARKER, "$1\u2022 ")

// ISSUE79_BACKSLASH_ESCAPES_V1: Reddit's editor saves literal markup
// characters escaped (my\_var, \*not bold\*, 1\. item).
private const val MARKDOWN_ESCAPABLE = "\\`*_{}[]()<>#+-.!|~>^\"'$%&,/:;=?@"

// ISSUE80_NESTED_INLINE_MARKUP_V1: the text inside bold, strike, italic,
// spoilers and link labels was appended raw, so "**[rules](url)**" showed its
// brackets and URL; it is parsed again with the same rules.
private fun AnnotatedString.Builder.appendMarkdownInline(
    source: String,
    linkColor: Color,
    codeBackground: Color,
    codeFontFamily: FontFamily,
    revealedSpoilers: Set<String>,
) {
    fun appendInner(inner: String) = appendMarkdownInline(
        source = inner,
        linkColor = linkColor,
        codeBackground = codeBackground,
        codeFontFamily = codeFontFamily,
        revealedSpoilers = revealedSpoilers,
    )
    val linkTokens = plainLinkTokenRanges(source)
    var index = 0
    while (index < source.length) {
        when {
            source[index] == '\\' && source.getOrNull(index + 1)?.let { it in MARKDOWN_ESCAPABLE } == true -> {
                append(source[index + 1])
                index += 2
            }
            source.startsWith("```", index) -> {
                val close = source.indexOf("```", startIndex = index + 3)
                if (close >= 0) {
                    val code = source.substring(index + 3, close).trim('\n')
                    withStyle(
                        SpanStyle(
                            fontFamily = codeFontFamily,
                            background = codeBackground,
                            letterSpacing = 0.sp,
                        ),
                    ) {
                        append(code)
                    }
                    index = close + 3
                } else {
                    append(source[index])
                    index += 1
                }
            }
            source.startsWith("***", index) && source.indexOf("***", startIndex = index + 3) >= 0 -> {
                val close = source.indexOf("***", startIndex = index + 3)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    appendInner(source.substring(index + 3, close))
                }
                index = close + 3
            }
            source.startsWith("**", index) -> {
                val close = source.indexOf("**", startIndex = index + 2)
                if (close >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInner(source.substring(index + 2, close))
                    }
                    index = close + 2
                } else {
                    append(source[index])
                    index += 1
                }
            }
            source.startsWith("~~", index) -> {
                val close = source.indexOf("~~", startIndex = index + 2)
                if (close >= 0) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInner(source.substring(index + 2, close))
                    }
                    index = close + 2
                } else {
                    append(source[index])
                    index += 1
                }
            }
            source.startsWith(">!", index) -> {
                val close = source.indexOf("!<", startIndex = index + 2)
                if (close >= 0) {
                    // ISSUE81_SPOILERS_HIDDEN_UNTIL_TAPPED_V1: an inline spoiler was
                    // tinted but readable. Until it is revealed only a "Spoiler"
                    // label is in the text (screen readers and link targets do not
                    // see the hidden words); the label carries the spoiler text so
                    // a tap can reveal it.
                    val spoilerText = source.substring(index + 2, close)
                    if (spoilerText in revealedSpoilers) {
                        withStyle(SpanStyle(background = codeBackground)) {
                            appendInner(spoilerText)
                        }
                    } else {
                        pushStringAnnotation(MARKDOWN_SPOILER_TAG, spoilerText)
                        withStyle(SpanStyle(background = codeBackground, fontWeight = FontWeight.Medium)) {
                            append(MARKDOWN_SPOILER_LABEL)
                        }
                        pop()
                    }
                    index = close + 2
                } else {
                    append(source[index])
                    index += 1
                }
            }
            // ISSUE82_LIST_ITEMS_ARE_BULLETS_V1: a '*' opens emphasis only
            // when text follows it and closes only after text ("2 * 3 * 4"
            // is arithmetic).
            source[index] == '*' && source.getOrNull(index + 1) != '*' &&
                source.getOrNull(index + 1)?.isWhitespace() == false -> {
                val close = singleStarEmphasisClose(source, index)
                if (close >= 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInner(source.substring(index + 1, close))
                    }
                    index = close + 1
                } else {
                    append(source[index])
                    index += 1
                }
            }
            // COMMENTTEXT03_NO_INTRAWORD_UNDERSCORE_ITALIC_V1 (#151): an
            // underscore inside a word (Python_(programming_language), a_b)
            // is text, as on Reddit; it no longer eats the underscores of URLs.
            // ISSUE52_UNDERSCORE_EMPHASIS_AT_WORD_BOUNDARIES_V1: the closing
            // underscore must end a word too, neither may touch a space on
            // the inside, and underscores in bare URLs and u/ r/ mentions are
            // always text.
            source[index] == '_' && opensUnderscoreEmphasis(source, index, linkTokens) -> {
                val close = underscoreEmphasisClose(source, index, linkTokens)
                if (close >= 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInner(source.substring(index + 1, close))
                    }
                    index = close + 1
                } else {
                    append(source[index])
                    index += 1
                }
            }
            source[index] == '`' -> {
                val close = source.indexOf('`', startIndex = index + 1)
                if (close >= 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = codeFontFamily,
                            background = codeBackground,
                            letterSpacing = 0.sp,
                        ),
                    ) {
                        append(source.substring(index + 1, close))
                    }
                    index = close + 1
                } else {
                    append(source[index])
                    index += 1
                }
            }
            // ISSUE8_INLINE_REDDIT_MEDIA_V1: a resolved Reddit emote is drawn
            // inline (see markdownEmoteInlineContent); any other media
            // placeholder has left the text as media or has no address.
            source[index] == '!' && redditMediaPlaceholderAt(source, index) != null -> {
                val placeholder = redditMediaPlaceholderAt(source, index)!!
                if (placeholder.isEmote && placeholder.url != null) {
                    appendInlineContent(
                        id = markdownEmoteInlineId(placeholder),
                        alternateText = markdownEmoteAlternateText(placeholder),
                    )
                }
                index = placeholder.range.last + 1
            }
            // ISSUE24_GIPHY_COMMENT_MEDIA_V1: an inline-image placeholder that
            // reached the renderer has no address we could resolve (its media
            // is appended as a URL when Reddit gives us one). Drop it rather
            // than paint a stray "!" with a link-styled alt text.
            source[index] == '!' && source.getOrNull(index + 1) == '[' -> {
                val labelEnd = source.indexOf(']', startIndex = index + 2)
                val urlStart = if (labelEnd >= 0 && source.getOrNull(labelEnd + 1) == '(') labelEnd + 2 else -1
                val urlEnd = if (urlStart >= 0) markdownLinkTargetEnd(source, urlStart) else -1
                if (labelEnd >= 0 && urlEnd >= 0) {
                    index = urlEnd + 1
                } else {
                    append(source[index])
                    index += 1
                }
            }
            source[index] == '[' -> {
                val labelEnd = source.indexOf(']', startIndex = index + 1)
                val urlStart = if (labelEnd >= 0 && source.getOrNull(labelEnd + 1) == '(') labelEnd + 2 else -1
                // COMMENTTEXT03_BALANCED_LINK_PARENS_V1 (#151): "(...Python_(language))".
                val urlEnd = if (urlStart >= 0) markdownLinkTargetEnd(source, urlStart) else -1
                if (labelEnd >= 0 && urlEnd >= 0) {
                    // COMMENTTEXT03_RENDERER_LINK_RANGES_V1 (#152): the link
                    // range is where the label lands here, not the first
                    // match of its words searched for afterwards.
                    val labelStart = length
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        appendInner(source.substring(index + 1, labelEnd))
                    }
                    addStringAnnotation(
                        tag = MARKDOWN_LINK_TARGET_TAG,
                        annotation = source.substring(urlStart, urlEnd),
                        start = labelStart,
                        end = length,
                    )
                    index = urlEnd + 1
                } else {
                    append(source[index])
                    index += 1
                }
            }
            else -> {
                append(source[index])
                index += 1
            }
        }
    }
}

private fun List<IntRange>.covers(index: Int): Boolean = any { range -> index in range }

private fun singleStarEmphasisClose(source: String, open: Int): Int {
    var candidate = source.indexOf('*', startIndex = open + 1)
    while (candidate >= 0) {
        if (source.getOrNull(candidate - 1)?.isWhitespace() == false && candidate > open + 1) {
            return candidate
        }
        candidate = source.indexOf('*', startIndex = candidate + 1)
    }
    return -1
}

private fun opensUnderscoreEmphasis(source: String, index: Int, linkTokens: List<IntRange>): Boolean =
    source.getOrNull(index - 1)?.isLetterOrDigit() != true &&
        source.getOrNull(index + 1)?.isWhitespace() == false &&
        !linkTokens.covers(index)

private fun underscoreEmphasisClose(source: String, open: Int, linkTokens: List<IntRange>): Int {
    var candidate = source.indexOf('_', startIndex = open + 2)
    while (candidate >= 0) {
        if (
            !source[candidate - 1].isWhitespace() &&
            source.getOrNull(candidate + 1)?.isLetterOrDigit() != true &&
            !linkTokens.covers(candidate)
        ) {
            return candidate
        }
        candidate = source.indexOf('_', startIndex = candidate + 1)
    }
    return -1
}

internal fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    if (source.isEmpty()) return emptyList()

    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmedStart = line.trimStart()
        when {
            trimmedStart.startsWith("```") -> {
                flushParagraph()
                val language = trimmedStart.removePrefix("```").trim().ifEmpty { null }
                val code = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    code += lines[index]
                    index += 1
                }
                if (index < lines.size) index += 1
                blocks += MarkdownBlock.Code(
                    code = code.joinToString("\n"),
                    language = language,
                )
            }
            line.startsWith("    ") || line.startsWith('\t') -> {
                flushParagraph()
                val code = mutableListOf<String>()
                while (index < lines.size) {
                    val codeLine = lines[index]
                    when {
                        codeLine.startsWith("    ") -> {
                            code += codeLine.removePrefix("    ")
                            index += 1
                        }
                        codeLine.startsWith('\t') -> {
                            code += codeLine.removePrefix("\t")
                            index += 1
                        }
                        codeLine.isBlank() && index + 1 < lines.size &&
                            (lines[index + 1].startsWith("    ") || lines[index + 1].startsWith('\t')) -> {
                            code += ""
                            index += 1
                        }
                        else -> break
                    }
                }
                blocks += MarkdownBlock.Code(
                    code = code.joinToString("\n"),
                    language = null,
                )
            }
            trimmedStart.startsWith(">!") && trimmedStart.trimEnd().endsWith("!<") -> {
                flushParagraph()
                val spoilerText = trimmedStart
                    .removePrefix(">!")
                    .removeSuffix("!<")
                    .trim()
                blocks += MarkdownBlock.Spoiler(spoilerText)
                index += 1
            }
            trimmedStart.startsWith(">") -> {
                flushParagraph()
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote += lines[index]
                        .trimStart()
                        .removePrefix(">")
                        .removePrefix(" ")
                    index += 1
                }
                blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            }
            line.isBlank() -> {
                flushParagraph()
                index += 1
            }
            else -> {
                paragraph += line
                index += 1
            }
        }
    }
    flushParagraph()
    return blocks
}

// ISSUE8_INLINE_REDDIT_MEDIA_V1: emotes sit in the line at about text height,
// scaled from Reddit's own size (20x20 for most emotes).
private const val MARKDOWN_EMOTE_HEIGHT_EM = 1.4f

internal fun markdownEmoteInlineId(placeholder: RedditMediaPlaceholder): String =
    "reddit-emote:${placeholder.url}"

internal fun markdownEmoteAlternateText(placeholder: RedditMediaPlaceholder): String =
    ":${placeholder.emoteName ?: "emote"}:"

private fun markdownEmoteWidthEm(placeholder: RedditMediaPlaceholder): Float {
    val width = placeholder.width
    val height = placeholder.height
    val aspect = if (width != null && height != null && width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.5f, 4f)
    } else {
        1f
    }
    return MARKDOWN_EMOTE_HEIGHT_EM * aspect
}

@Composable
private fun markdownEmoteInlineContent(source: String): Map<String, InlineTextContent> {
    val emotes = remember(source) {
        parseRedditMediaPlaceholders(source)
            .filter { placeholder -> placeholder.isEmote && placeholder.url != null }
            .distinctBy(::markdownEmoteInlineId)
    }
    if (emotes.isEmpty()) return emptyMap()
    return emotes.associate { placeholder ->
        markdownEmoteInlineId(placeholder) to InlineTextContent(
            placeholder = Placeholder(
                width = markdownEmoteWidthEm(placeholder).em,
                height = MARKDOWN_EMOTE_HEIGHT_EM.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            RemoteImage(
                uri = placeholder.url.orEmpty(),
                label = markdownEmoteAlternateText(placeholder),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                aspectRatio = null,
                containerColor = Color.Transparent,
                showFailureDetails = false,
            )
        }
    }
}
