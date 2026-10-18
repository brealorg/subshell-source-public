package app.subshell.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Post

/**
 * ISSUE234_FEED_POST_LAYOUTS_V1 (owner 2026-10-18): Grid is rows of tiles inside
 * the feed's own LazyColumn (owner chose even rows), so scroll anchors,
 * auto-append, pull-to-refresh and scroll-to-top keep their one list type. A
 * row's key is its first post's key; [feedPostItemKeys] lists the keys in the
 * order the list emits them.
 */
internal const val FEED_GRID_MIN_TILE_WIDTH_DP = 180
private const val FEED_GRID_MAX_COLUMNS = 6

internal fun feedGridColumnsFor(widthDp: Float): Int =
    (widthDp / FEED_GRID_MIN_TILE_WIDTH_DP).toInt().coerceIn(2, FEED_GRID_MAX_COLUMNS)

/** Grid only where a list holds posts alone; a list mixing posts with other rows shows List instead. */
internal fun PostLayout.forFeed(gridAllowed: Boolean): PostLayout =
    if (this == PostLayout.GRID && !gridAllowed) PostLayout.LIST else this

/** The gap between feed items; Large keeps each screen's own spacing. */
internal fun feedItemSpacing(layout: PostLayout, largeSpacing: Dp): Dp = when (layout) {
    PostLayout.LARGE -> largeSpacing
    PostLayout.COMPACT, PostLayout.GRID -> 8.dp
    PostLayout.LIST -> 2.dp
}

internal fun feedPostItemKeys(
    posts: List<Post>,
    layout: PostLayout,
    columns: Int,
    key: (Post) -> String = Post::id,
): List<String> = if (layout == PostLayout.GRID) {
    posts.chunked(columns.coerceAtLeast(1)).map { row -> key(row.first()) }
} else {
    posts.map(key)
}

/**
 * [horizontalPadding] is the margin a screen gives each post; in Grid it goes on
 * the row, so [content]'s modifier carries it only outside Grid.
 */
internal fun LazyListScope.feedPostItems(
    posts: List<Post>,
    layout: PostLayout,
    columns: Int,
    key: (Post) -> String = Post::id,
    horizontalPadding: Dp = 0.dp,
    content: @Composable (post: Post, modifier: Modifier) -> Unit,
) {
    if (layout != PostLayout.GRID) {
        items(posts, key = key) { post -> content(post, Modifier.padding(horizontal = horizontalPadding)) }
        return
    }
    val safeColumns = columns.coerceAtLeast(1)
    items(posts.chunked(safeColumns), key = { row -> key(row.first()) }) { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { post ->
                Box(Modifier.weight(1f)) { content(post, Modifier) }
            }
            repeat(safeColumns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Measures the list it is applied to and says how many grid columns fit. */
@Stable
internal class FeedGridColumns(private val density: Density) {
    var count by mutableIntStateOf(2)
        private set

    val measureModifier: Modifier = Modifier.onSizeChanged { size ->
        val widthDp = with(density) { size.width.toDp().value }
        count = feedGridColumnsFor(widthDp - 2 * GRID_SIDE_MARGIN_DP)
    }

    private companion object {
        const val GRID_SIDE_MARGIN_DP = 16f
    }
}

@Composable
internal fun rememberFeedGridColumns(): FeedGridColumns {
    val density = LocalDensity.current
    return remember(density) { FeedGridColumns(density) }
}
