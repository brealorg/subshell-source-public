package app.subshell.prototype.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.ListThumbnailSide
import app.subshell.prototype.ui.PostLayout
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.externalPostUrl
import app.subshell.prototype.ui.symbols.Symbols

/**
 * ISSUE234_FEED_POST_LAYOUTS_V1 (owner 2026-10-18): Compact, List and Grid. They
 * share PostCard's state, swipe actions, menu and dialogs; only the drawing
 * differs. No vote buttons (owner): voting and saving go through swipe actions,
 * the menu and TalkBack's custom actions. Media is a still picture here; only
 * Large plays video and animates GIFs.
 */
@Composable
internal fun FeedPostLayoutContent(
    post: Post,
    state: SubshellState,
    layout: PostLayout,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    onOpen: () -> Unit,
    onComments: () -> Unit,
    onOpenCommunity: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = feedPostAccessibilityActions(post, state, vote, saved, onComments)
    val openModifier = Modifier
        .clickable(onClickLabel = "Open post", role = Role.Button, onClick = onOpen)
        .semantics(mergeDescendants = true) { customActions = actions }
    when (layout) {
        PostLayout.COMPACT -> CompactPostCard(post, state, vote, saved, read, onOpenCommunity, overflowMenu, modifier, openModifier)
        PostLayout.LIST -> ListPostRow(post, state, vote, saved, read, overflowMenu, modifier, openModifier)
        PostLayout.GRID -> GridPostTile(post, state, read, overflowMenu, modifier, openModifier)
        PostLayout.LARGE -> Unit
    }
}

private fun feedPostAccessibilityActions(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    onComments: () -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (state.postVoteControlEnabled(post)) {
        add(CustomAccessibilityAction(if (vote == 1) "Remove upvote" else "Upvote") { state.vote(post, 1); true })
        add(CustomAccessibilityAction(if (vote == -1) "Remove downvote" else "Downvote") { state.vote(post, -1); true })
    }
    if (state.postSaveControlEnabled(post)) {
        add(CustomAccessibilityAction(if (saved) "Unsave" else "Save") { state.toggleSaved(post); true })
    }
    add(CustomAccessibilityAction("Comments") { onComments(); true })
}

@Composable
private fun CompactPostCard(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    onOpenCommunity: () -> Unit,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(openModifier.padding(start = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Open community", role = Role.Button, onClick = onOpenCommunity),
                ) {
                    PostMetaLine(post, state, read)
                }
                overflowMenu()
            }
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (postThumbnailAvailable(post)) {
                PostThumbnail(
                    post = post,
                    state = state,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 12.dp)
                        .fillMaxWidth()
                        .height(CompactMediaHeight)
                        .clip(MaterialTheme.shapes.small),
                )
            }
            PostStatsLine(post, state, vote, saved, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ListPostRow(
    post: Post,
    state: SubshellState,
    vote: Int,
    saved: Boolean,
    read: Boolean,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    val thumbnailLeft = state.listThumbnailSide == ListThumbnailSide.LEFT
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = openModifier
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasThumbnail = postThumbnailAvailable(post, linkTile = true)
            val thumbnail: @Composable () -> Unit = {
                if (hasThumbnail) {
                    PostThumbnail(
                        post = post,
                        state = state,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
            }
            if (thumbnailLeft && hasThumbnail) {
                thumbnail()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                PostMetaLine(post, state, read)
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                PostStatsLine(post, state, vote, saved, Modifier.padding(top = 4.dp))
            }
            if (!thumbnailLeft && hasThumbnail) {
                Spacer(Modifier.width(12.dp))
                thumbnail()
            }
            Box(Modifier.align(Alignment.Top)) { overflowMenu() }
        }
    }
}

@Composable
private fun GridPostTile(
    post: Post,
    state: SubshellState,
    read: Boolean,
    overflowMenu: @Composable () -> Unit,
    modifier: Modifier,
    openModifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(openModifier) {
            // Every tile has the same picture shape and a two-line title, so a
            // row's tiles line up (owner chose even rows).
            if (postThumbnailAvailable(post)) {
                PostThumbnail(post = post, state = state, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            } else {
                val accent = state.communityFor(post.community)?.let { Color(it.accent) } ?: accentFor(post.community)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(accent.copy(alpha = 0.16f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = post.body.ifBlank { post.title },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = post.title,
                style = MaterialTheme.typography.labelLarge,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = "${state.subredditLabel(post.community)} · ${compactNumber(state.effectivePostScore(post))}" +
                            if (shouldShowUnreadMarker(state.showUnreadMarker, read)) " · new" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                overflowMenu()
            }
        }
    }
}

/** Community, age, unread dot and the labels a reader must see before opening. */
@Composable
private fun PostMetaLine(post: Post, state: SubshellState, read: Boolean) {
    val identity = state.communityFor(post.community)
    val accent = identity?.let { Color(it.accent) } ?: accentFor(post.community)
    Row(
        modifier = Modifier.heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIdentityImage(
            uri = identity?.iconImageUri,
            contentDescription = null,
            modifier = Modifier.size(20.dp).clip(CircleShape),
        ) {
            CommunityAvatar(name = post.community, accent = accent, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${state.subredditLabel(post.community)} · ${post.age}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (shouldShowUnreadMarker(state.showUnreadMarker, read)) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = "Unread post" },
            )
        }
        postLabels(post).forEach { label ->
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (label == "NSFW") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
            )
        }
    }
}

internal fun postLabels(post: Post): List<String> = buildList {
    if (post.pinned) add("PINNED")
    if (post.moderatorPost) add("MOD")
    if (post.spoiler) add("SPOILER")
    if (post.nsfw) add("NSFW")
}

@Composable
private fun PostStatsLine(post: Post, state: SubshellState, vote: Int, saved: Boolean, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = if (vote == -1) Symbols.ArrowDownward else Symbols.ArrowUpward,
            contentDescription = when (vote) {
                1 -> "Upvoted"
                -1 -> "Downvoted"
                else -> null
            },
            modifier = Modifier.size(16.dp),
            tint = when (vote) {
                1 -> MaterialTheme.colorScheme.primary
                -1 -> MaterialTheme.colorScheme.tertiary
                else -> quiet
            },
        )
        Text(
            text = compactNumber(state.effectivePostScore(post)),
            style = MaterialTheme.typography.labelMedium,
            color = quiet,
            modifier = Modifier.semantics { contentDescription = "${state.effectivePostScore(post)} points" },
        )
        Spacer(Modifier.width(8.dp))
        Icon(Symbols.Comment, contentDescription = null, modifier = Modifier.size(16.dp), tint = quiet)
        Text(
            text = compactNumber(post.comments),
            style = MaterialTheme.typography.labelMedium,
            color = quiet,
            modifier = Modifier.semantics { contentDescription = "${post.comments} comments" },
        )
        if (saved) {
            Spacer(Modifier.width(8.dp))
            Icon(Symbols.BookmarkFilled, contentDescription = "Saved", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (post.kind == PostKind.LINK) {
            post.domain?.takeIf(String::isNotBlank)?.let { domain ->
                Spacer(Modifier.width(8.dp))
                Text(domain, style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Whether a post has a picture to show. A link without a preview has none; List
 * still gives it a small link tile ([linkTile]), Compact and Grid show text.
 */
internal fun postThumbnailAvailable(post: Post, linkTile: Boolean = false): Boolean = when {
    postThumbnailUri(post) != null || (post.mediaAsset ?: post.gallery.firstOrNull()?.asset) != null -> true
    post.kind in MediaViewerKinds -> true
    post.kind == PostKind.LINK -> linkTile
    else -> false
}

internal fun postThumbnailUri(post: Post): String? =
    post.remoteImageUri?.takeIf(String::isNotBlank)
        ?: post.gallery.firstOrNull()?.remoteImageUri?.takeIf(String::isNotBlank)

/**
 * A still picture of a post. Sensitive media follows the reader's setting
 * exactly as Large does: hidden shows nothing, load-on-tap loads nothing until
 * tapped, blurred stays blurred until revealed.
 */
@Composable
internal fun PostThumbnail(post: Post, state: SubshellState, modifier: Modifier = Modifier) {
    val sensitive = (post.spoiler || post.nsfw) && post.kind != PostKind.TEXT
    // NSFWMEDIA01_SHOW_NSFW_MEDIA_SETTING_V1
    val revealed = !sensitive || post.id in state.revealedMediaIds || state.nsfwMediaShownUncovered(post)
    val mode = state.sensitiveMediaPreviewMode
    val hidden = sensitive && !revealed && mode == SensitiveMediaPreviewMode.HIDE_PREVIEW
    val coverOnly = sensitive && !revealed && mode == SensitiveMediaPreviewMode.LOAD_ON_TAP
    val label = post.mediaLabel.ifBlank { post.title }
    val uriHandler = LocalUriHandler.current
    // A tap on a still opens what Large's media opens; a post kind without
    // its own media viewer leaves the tap to the row (it opens the post).
    val onClick: (() -> Unit)? = when {
        hidden -> ({ state.openMedia(post) })
        sensitive && !revealed -> ({ state.revealMedia(post) })
        post.kind == PostKind.LINK -> externalPostUrl(post)?.let { url -> fun() { runCatching { uriHandler.openUri(url) } } }
        post.kind in MediaViewerKinds -> ({ state.openMedia(post) })
        else -> null
    }
    Box(
        modifier = modifier
            .testTag(POST_THUMBNAIL_TEST_TAG)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = when {
                            hidden -> "Open hidden media"
                            sensitive && !revealed -> "Show sensitive media"
                            post.kind == PostKind.LINK -> "Open link"
                            else -> "Open media"
                        },
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hidden -> Icon(Symbols.VisibilityOff, contentDescription = "Sensitive media hidden", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            coverOnly -> Icon(Symbols.Visibility, contentDescription = "Sensitive media, tap to load", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val blurModifier = if (revealed) Modifier else Modifier.blur(16.dp)
                val uri = postThumbnailUri(post)
                val asset = post.mediaAsset ?: post.gallery.firstOrNull()?.asset
                when {
                    uri != null -> RemoteImage(
                        uri = uri,
                        label = label,
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                        contentScale = ContentScale.Crop,
                        aspectRatio = null,
                        showFailureDetails = false,
                    )
                    asset != null -> LocalAssetStill(asset, label, Modifier.fillMaxSize().then(blurModifier))
                    else -> Unit
                }
                if (uri == null && asset == null && post.kind == PostKind.LINK) {
                    Icon(Symbols.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    thumbnailBadge(post)?.let { icon -> ThumbnailBadge(icon, Modifier.align(Alignment.Center)) }
                }
            }
        }
    }
}

private fun thumbnailBadge(post: Post): ImageVector? = when (post.kind) {
    PostKind.VIDEO, PostKind.GIF -> Symbols.PlayArrowFilled
    PostKind.GALLERY -> Symbols.PhotoLibrary
    PostKind.LINK -> Symbols.Link
    else -> null
}

@Composable
private fun ThumbnailBadge(icon: ImageVector, modifier: Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(4.dp).size(18.dp))
    }
}

/** First frame of a bundled picture or GIF (sample posts), decoded once. */
@Composable
private fun LocalAssetStill(asset: app.subshell.prototype.model.MediaAsset, label: String, modifier: Modifier) {
    val resource = presentationMediaResource(asset) ?: return
    val resources = LocalResources.current
    val bitmap = remember(resources, resource) { BitmapFactory.decodeResource(resources, resource)?.asImageBitmap() } ?: return
    Image(bitmap = bitmap, contentDescription = label, contentScale = ContentScale.Crop, modifier = modifier)
}

internal const val POST_THUMBNAIL_TEST_TAG = "post-thumbnail"
private val MediaViewerKinds = setOf(PostKind.IMAGE, PostKind.VIDEO, PostKind.GIF, PostKind.GALLERY)
internal val PostLayoutMenuWidth = 200.dp
private val CompactMediaHeight = 160.dp
private val ListThumbnailSize = 72.dp

/**
 * ISSUE234_LAYOUT_CHOICE_BESIDE_THE_FEED_V1 (owner 2026-10-18): the layout is
 * chosen where the feed is read, beside its sort (Drive and Files put their
 * list/grid choice in the toolbar). The icon shows the current layout. It is the
 * same setting as Settings → Feeds → Post layout.
 */
@Composable
internal fun PostLayoutMenuButton(state: SubshellState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(postLayoutIcon(state.postLayout), contentDescription = "Post layout: ${state.postLayout.label}")
        }
        // One width for every choice: the menu was as wide as its longest row,
        // so "Compact ✓" and List's thumbnail rows moved it sideways (owner, phone).
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(PostLayoutMenuWidth),
        ) {
            PostLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.label) },
                    modifier = Modifier.semantics { selected = layout == state.postLayout },
                    leadingIcon = { Icon(postLayoutIcon(layout), contentDescription = null) },
                    trailingIcon = if (layout == state.postLayout) {
                        { Icon(Symbols.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        state.updatePostLayout(layout)
                        if (layout != PostLayout.LIST) expanded = false
                    },
                )
            }
            if (state.postLayout == PostLayout.LIST) {
                HorizontalDivider()
                ListThumbnailSide.entries.forEach { side ->
                    DropdownMenuItem(
                        text = { Text("Thumbnail ${side.label.lowercase()}") },
                        modifier = Modifier.semantics { selected = side == state.listThumbnailSide },
                        trailingIcon = if (side == state.listThumbnailSide) {
                            { Icon(Symbols.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            state.updateListThumbnailSide(side)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

internal fun postLayoutIcon(layout: PostLayout): ImageVector = when (layout) {
    PostLayout.LARGE -> Symbols.ViewAgenda
    PostLayout.COMPACT -> Symbols.ViewDay
    PostLayout.LIST -> Symbols.ViewList
    PostLayout.GRID -> Symbols.GridView
}
