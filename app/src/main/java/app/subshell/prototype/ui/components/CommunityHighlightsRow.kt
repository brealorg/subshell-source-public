package app.subshell.prototype.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.ScreenMargin
import app.subshell.prototype.ui.symbols.Symbols

internal const val COMMUNITY_HIGHLIGHTS_TEST_TAG = "community-highlights"

/**
 * ISSUE230_PINNED_POSTS_FIRST_V1: "Highlights row" — the community's pinned
 * posts as a row of small cards (title and age) above its feed, so long pinned
 * posts do not push the feed down. A tap opens the post.
 */
@Composable
internal fun CommunityHighlightsRow(
    posts: List<Post>,
    onOpen: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Community highlights",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(posts, key = Post::id) { post ->
                OutlinedCard(
                    onClick = { onOpen(post) },
                    modifier = Modifier
                        .width(220.dp)
                        .semantics { contentDescription = "Pinned post: ${post.title}, ${post.age}" },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Symbols.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Pinned · ${post.age}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            post.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
