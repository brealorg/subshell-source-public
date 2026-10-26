package app.subshell.prototype.ui.components

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SheetValue
import androidx.compose.ui.text.style.TextDecoration
import app.subshell.prototype.media.isRemoteMediaGone
import app.subshell.prototype.media.redGifsCacheKeyOf
import app.subshell.prototype.media.redGifsQualitySources
import app.subshell.prototype.media.remoteMediaDiagnosticScope
import app.subshell.prototype.ui.mediaDownloadFolder
import app.subshell.prototype.ui.downloadGalleryItems
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.media3.common.MimeTypes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.subshell.prototype.R
import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.model.CrosspostPreview
import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.InlineMedia
import app.subshell.prototype.model.MediaAsset
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.media.RemoteAnimatedImage
import app.subshell.prototype.media.RemoteImageLoader
import app.subshell.prototype.media.RemoteMediaLoadResult
import app.subshell.prototype.media.VideoMedia
import app.subshell.prototype.media.VideoMediaSource
import app.subshell.prototype.media.VideoMediaSourceKind
import app.subshell.prototype.media.VideoMediaTextTrackSource
import app.subshell.prototype.media.splitAssetVideoMedia
import app.subshell.prototype.media.adaptiveAssetVideoMedia
import app.subshell.prototype.media.localRawVideoMedia
import app.subshell.prototype.ui.MediaCaptionMode
import app.subshell.prototype.ui.RedditExternalPollHandoffStore
import app.subshell.prototype.ui.noticeFailure
import app.subshell.prototype.ui.resolveRedditTarget
import app.subshell.prototype.ui.PostMediaActionTarget
import app.subshell.prototype.ui.downloadPostMedia
import app.subshell.prototype.ui.externalPostUrl
import app.subshell.prototype.ui.sharePostLink
import app.subshell.prototype.ui.sharePostMedia
import app.subshell.prototype.ui.REDDIT_LINK_HANDLING_HOSTS
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.GoogleFeedVideoFrame
import app.subshell.prototype.ui.SensitiveMediaPreviewMode
import app.subshell.prototype.ui.sensitiveMediaUsesPosterOnly
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.ErrorPresentation
import app.subshell.prototype.ui.error.ReportErrorPresentationDiagnostic
import app.subshell.prototype.ui.error.RemoteMediaKind
import app.subshell.prototype.ui.error.mediaSourceHost
import app.subshell.prototype.ui.error.remoteMediaErrorPresentation
import app.subshell.prototype.ui.mediaActionsAtStart
import app.subshell.prototype.ui.symbols.Symbols
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberBottomSheetState

fun mediaResource(asset: MediaAsset?): Int? = when (asset) {
    MediaAsset.VIDEO_LAB_A,
    MediaAsset.VIDEO_LAB_B,
    MediaAsset.VIDEO_LAB_C,
    MediaAsset.VIDEO_LAB_D,
    MediaAsset.VIDEO_LAB_E,
    MediaAsset.VIDEO_LAB_F,
    MediaAsset.VIDEO_LAB_G,
    MediaAsset.VIDEO_LAB_H,
    MediaAsset.VIDEO_LAB_I,
    MediaAsset.VIDEO_LAB_J,
    MediaAsset.VIDEO_LAB_K,
    MediaAsset.VIDEO_LAB_L,
    MediaAsset.VIDEO_LAB_M,
    MediaAsset.VIDEO_LAB_N,
    MediaAsset.VIDEO_LAB_O,
    MediaAsset.VIDEO_LAB_P,
    MediaAsset.VIDEO_LAB_Q,
    MediaAsset.VIDEO_LAB_R,
    MediaAsset.VIDEO_LAB_S,
    MediaAsset.VIDEO_LAB_T,
    MediaAsset.VIDEO_LAB_U,
    MediaAsset.VIDEO_LAB_V,
    MediaAsset.VIDEO_LAB_W,
    MediaAsset.VIDEO_LAB_X,
    MediaAsset.VIDEO_LAB_Y,
    MediaAsset.VIDEO_LAB_Z,
    MediaAsset.MEDIA03_DASH,
    MediaAsset.MEDIA03_HLS,
    MediaAsset.MEDIA03_FALLBACK,
    MediaAsset.MEDIA03_SPLIT_AV,
    MediaAsset.MEDIA03_SPLIT_AUDIO_FALLBACK,
    -> null

    MediaAsset.HOMELAB -> R.drawable.media_homelab
    MediaAsset.ANDROID_DESK -> R.drawable.media_android_desk
    MediaAsset.LINUX_DESK -> R.drawable.media_linux_desk
    MediaAsset.GIF_HIGH_QUALITY -> R.drawable.gif_high_quality
    MediaAsset.GIF_HOMEMADE -> R.drawable.gif_homemade
    MediaAsset.GIF_PIXEL_ART -> R.drawable.gif_pixel_art
    MediaAsset.PIXEL_GALLERY_CAMPFIRE -> R.drawable.pixel_gallery_campfire
    MediaAsset.PIXEL_GALLERY_TERMINAL -> R.drawable.pixel_gallery_terminal
    MediaAsset.PIXEL_GALLERY_CAT -> R.drawable.pixel_gallery_cat
    MediaAsset.PIXEL_GALLERY_MOON -> R.drawable.pixel_gallery_moon
    null -> null
}

internal fun prototypeVideoRawResourceName(asset: MediaAsset?): String? = when (asset) {
    MediaAsset.VIDEO_LAB_A -> "subshell_video_lab_a"
    MediaAsset.VIDEO_LAB_B -> "subshell_video_lab_b"
    MediaAsset.VIDEO_LAB_C -> "subshell_video_lab_c"
    MediaAsset.VIDEO_LAB_D -> "subshell_video_lab_d"
    MediaAsset.VIDEO_LAB_E -> "subshell_video_lab_e"
    MediaAsset.VIDEO_LAB_F -> "subshell_video_lab_f"
    MediaAsset.VIDEO_LAB_G -> "subshell_video_lab_g"
    MediaAsset.VIDEO_LAB_H -> "subshell_video_lab_h"
    MediaAsset.VIDEO_LAB_I -> "subshell_video_lab_i"
    MediaAsset.VIDEO_LAB_J -> "subshell_video_lab_j"
    MediaAsset.VIDEO_LAB_K -> "subshell_video_lab_k"
    MediaAsset.VIDEO_LAB_L -> "subshell_video_lab_l"
    MediaAsset.VIDEO_LAB_M -> "subshell_video_lab_m"
    MediaAsset.VIDEO_LAB_N -> "subshell_video_lab_n"
    MediaAsset.VIDEO_LAB_O -> "subshell_video_lab_o"
    MediaAsset.VIDEO_LAB_P -> "subshell_video_lab_p"
    MediaAsset.VIDEO_LAB_Q -> "subshell_video_lab_q"
    MediaAsset.VIDEO_LAB_R -> "subshell_video_lab_r"
    MediaAsset.VIDEO_LAB_S -> "subshell_video_lab_s"
    MediaAsset.VIDEO_LAB_T -> "subshell_video_lab_t"
    MediaAsset.VIDEO_LAB_U -> "subshell_video_lab_u"
    MediaAsset.VIDEO_LAB_V -> "subshell_video_lab_v"
    MediaAsset.VIDEO_LAB_W -> "subshell_video_lab_w"
    MediaAsset.VIDEO_LAB_X -> "subshell_video_lab_x"
    MediaAsset.VIDEO_LAB_Y -> "subshell_video_lab_y"
    MediaAsset.VIDEO_LAB_Z -> "subshell_video_lab_z"
    else -> null
}

internal fun prototypeVideoPosterResourceName(asset: MediaAsset?): String? = when (asset) {
    MediaAsset.VIDEO_LAB_A,
    MediaAsset.MEDIA03_DASH,
    MediaAsset.MEDIA03_SPLIT_AV,
    -> "subshell_video_lab_a"

    MediaAsset.VIDEO_LAB_B -> "subshell_video_lab_b"
    MediaAsset.VIDEO_LAB_C -> "subshell_video_lab_c"
    MediaAsset.VIDEO_LAB_D -> "subshell_video_lab_d"
    MediaAsset.VIDEO_LAB_E,
    MediaAsset.MEDIA03_HLS,
    -> "subshell_video_lab_e"

    MediaAsset.VIDEO_LAB_F -> "subshell_video_lab_f"
    MediaAsset.VIDEO_LAB_G -> "subshell_video_lab_g"
    MediaAsset.VIDEO_LAB_H -> "subshell_video_lab_h"
    MediaAsset.VIDEO_LAB_I -> "subshell_video_lab_i"
    MediaAsset.VIDEO_LAB_J -> "subshell_video_lab_j"
    MediaAsset.VIDEO_LAB_K -> "subshell_video_lab_k"
    MediaAsset.VIDEO_LAB_L -> "subshell_video_lab_l"
    MediaAsset.VIDEO_LAB_M -> "subshell_video_lab_m"
    MediaAsset.VIDEO_LAB_N -> "subshell_video_lab_n"
    MediaAsset.VIDEO_LAB_O -> "subshell_video_lab_o"
    MediaAsset.VIDEO_LAB_P -> "subshell_video_lab_p"
    MediaAsset.VIDEO_LAB_Q -> "subshell_video_lab_q"
    MediaAsset.VIDEO_LAB_R -> "subshell_video_lab_r"
    MediaAsset.VIDEO_LAB_S -> "subshell_video_lab_s"
    MediaAsset.VIDEO_LAB_T -> "subshell_video_lab_t"
    MediaAsset.VIDEO_LAB_U -> "subshell_video_lab_u"
    MediaAsset.VIDEO_LAB_V -> "subshell_video_lab_v"
    MediaAsset.VIDEO_LAB_W -> "subshell_video_lab_w"
    MediaAsset.VIDEO_LAB_X -> "subshell_video_lab_x"
    MediaAsset.VIDEO_LAB_Y -> "subshell_video_lab_y"
    MediaAsset.VIDEO_LAB_Z,
    MediaAsset.MEDIA03_FALLBACK,
    MediaAsset.MEDIA03_SPLIT_AUDIO_FALLBACK,
    -> "subshell_video_lab_z"

    else -> null
}

private fun optionalNamedResource(
    context: Context,
    type: String,
    name: String,
): Int? = context.resources
    .getIdentifier(name, type, context.packageName)
    .takeIf { resource -> resource != 0 }

private fun optionalPrototypeRawResource(
    context: Context,
    asset: MediaAsset?,
): Int? = prototypeVideoRawResourceName(asset)
    ?.let { name ->
        optionalNamedResource(
            context = context,
            type = "raw",
            name = name,
        )
    }

@Composable
internal fun presentationMediaResource(asset: MediaAsset?): Int? {
    val context = LocalContext.current
    return mediaResource(asset)
        ?: prototypeVideoPosterResourceName(asset)
            ?.let { name ->
                remember(asset, context.packageName) {
                    optionalNamedResource(
                        context = context,
                        type = "drawable",
                        name = name,
                    )
                }
            }
}

private fun bundledPrototypeAssetExists(
    context: Context,
    assetPath: String,
): Boolean = runCatching {
    context.assets.open(assetPath).use { input ->
        input.read()
    }
    true
}.getOrDefault(false)

internal fun hasPlayableVideoSource(post: Post): Boolean {
    val hasLocalVideo =
        !post.localMediaUri.isNullOrBlank() &&
            post.localMediaMimeType?.startsWith(
                prefix = "video/",
                ignoreCase = true,
            ) == true
    if (
        hasLocalVideo ||
        !post.remoteVideoUri.isNullOrBlank() ||
        !post.remoteVideoHlsUri.isNullOrBlank() ||
        !post.remoteVideoFallbackUri.isNullOrBlank()
    ) {
        return true
    }

    return when (post.mediaAsset) {
        MediaAsset.VIDEO_LAB_A,
        MediaAsset.VIDEO_LAB_B,
        MediaAsset.VIDEO_LAB_C,
        MediaAsset.VIDEO_LAB_D,
        MediaAsset.VIDEO_LAB_E,
        MediaAsset.VIDEO_LAB_F,
        MediaAsset.VIDEO_LAB_G,
        MediaAsset.VIDEO_LAB_H,
        MediaAsset.VIDEO_LAB_I,
        MediaAsset.VIDEO_LAB_J,
        MediaAsset.VIDEO_LAB_K,
        MediaAsset.VIDEO_LAB_L,
        MediaAsset.VIDEO_LAB_M,
        MediaAsset.VIDEO_LAB_N,
        MediaAsset.VIDEO_LAB_O,
        MediaAsset.VIDEO_LAB_P,
        MediaAsset.VIDEO_LAB_Q,
        MediaAsset.VIDEO_LAB_R,
        MediaAsset.VIDEO_LAB_S,
        MediaAsset.VIDEO_LAB_T,
        MediaAsset.VIDEO_LAB_U,
        MediaAsset.VIDEO_LAB_V,
        MediaAsset.VIDEO_LAB_W,
        MediaAsset.VIDEO_LAB_X,
        MediaAsset.VIDEO_LAB_Y,
        MediaAsset.VIDEO_LAB_Z,
        MediaAsset.MEDIA03_DASH,
        MediaAsset.MEDIA03_HLS,
        MediaAsset.MEDIA03_FALLBACK,
        MediaAsset.MEDIA03_SPLIT_AV,
        MediaAsset.MEDIA03_SPLIT_AUDIO_FALLBACK,
        -> true

        else -> false
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PostMedia(
    post: Post,
    state: SubshellState,
    modifier: Modifier = Modifier,
    inPostDetail: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val mediaScope = rememberCoroutineScope()
    var mediaActionTarget by remember(post.id) {
        mutableStateOf<PostMediaActionTarget?>(null)
    }
    var mediaSharePreparing by remember(post.id) {
        mutableStateOf(false)
    }
    var mediaDownload by remember(post.id) {
        mutableStateOf<MediaDownloadUiState?>(null)
    }
    val mediaErrorHost = LocalViewportErrorHostState.current
    val openExternalLink: () -> Unit = {
        externalPostUrl(post)?.let { url ->
            runCatching { uriHandler.openUri(url) }
                .onFailure {
                    // ISSUE222_NO_TOASTS_V1
                    mediaErrorHost.noticeFailure("Couldn't open the link", "No app on this device can open it")
                }
        }
    }
    val showPostMediaActions: () -> Unit = {
        mediaActionTarget = PostMediaActionTarget(post)
    }

    val hasSensitivePreview = when (post.kind) {
        PostKind.IMAGE, PostKind.VIDEO, PostKind.GIF, PostKind.GALLERY -> true
        PostKind.LINK -> post.remoteImageUri != null
        else -> false
    }
    val sensitive = hasSensitivePreview && (post.spoiler || post.nsfw)
    val revealed = !sensitive || post.id in state.revealedMediaIds
    val mode = state.sensitiveMediaPreviewMode
    val posterOnlySensitiveVideo = sensitiveMediaUsesPosterOnly(
        post = post,
        mode = mode,
        revealed = revealed,
    )

    if (sensitive && !revealed && mode == SensitiveMediaPreviewMode.HIDE_PREVIEW) {
        if (post.kind == PostKind.LINK) {
            LinkPanel(post, onOpenLink = openExternalLink)
        } else {
            HiddenSensitiveMediaRow(
                post = post,
                onOpen = { state.openMedia(post) },
                modifier = modifier,
            )
        }
        return
    }

    if (sensitive && !revealed && mode == SensitiveMediaPreviewMode.LOAD_ON_TAP) {
        SensitiveMediaCover(
            post = post,
            preloaded = false,
            onReveal = { state.revealMedia(post) },
            // ISSUE234_FEED_MEDIA_CAPPED_AT_4_5_V1: like feed images and the inline
            // player, the cover is no taller than 4:5.
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(boundedInlineVideoAspectRatio(mediaAspectRatio(post))),
        )
        return
    }

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (revealed) Modifier else Modifier.blur(22.dp)),
        ) {
            if (post.localMediaUri != null && post.kind != PostKind.VIDEO) {
                LocalUriMedia(
                    uri = post.localMediaUri,
                    mimeType = post.localMediaMimeType,
                    contentDescription = post.mediaLabel,
                    modifier = Modifier
                        .aspectRatio(16f / 9f)
                        .combinedClickable(
                            onClick = { state.openMedia(post) },
                            onLongClick = showPostMediaActions,
                        ),
                )
            } else when (post.kind) {
                PostKind.IMAGE -> {
                    val imageUri = post.remoteImageUri
                    if (imageUri != null) {
                        RemoteImage(
                            uri = imageUri,
                            label = post.mediaLabel,
                            modifier = Modifier.combinedClickable(
                                onClick = { state.openMedia(post) },
                                onLongClick = showPostMediaActions,
                            ),
                            contentScale = ContentScale.Fit,
                            adaptToSourceAspectRatio = true,
                        )
                    } else {
                        StaticMedia(
                            asset = post.mediaAsset,
                            label = post.mediaLabel,
                            modifier = Modifier.combinedClickable(
                                onClick = { state.openMedia(post) },
                                onLongClick = showPostMediaActions,
                            ),
                        )
                    }
                }
                PostKind.VIDEO -> GoogleFeedVideoFrame(
                    mediaId = post.id,
                    state = state,
                    enabled = !posterOnlySensitiveVideo && state.sensitiveMediaAutoplayAllowed(post),
                ) {
                    LocalVideoPlayer(
                        post = post,
                        state = state,
                        posterOnly = posterOnlySensitiveVideo,
                        onLongPress = showPostMediaActions,
                        inPostDetail = inPostDetail,
                    )
                }
                PostKind.GIF -> {
                    val gifUri = post.remoteImageUri
                    if (gifUri != null) {
                        RemoteGifMedia(
                            uri = gifUri,
                            label = post.mediaLabel,
                            playing = state.activeAnimatedMediaId == post.id,
                            modifier = Modifier.combinedClickable(
                                onClick = { state.openMedia(post) },
                                onLongClick = showPostMediaActions,
                            ),
                            contentScale = ContentScale.Fit,
                            adaptToSourceAspectRatio = true,
                        )
                    } else {
                        GifMedia(
                            asset = post.mediaAsset,
                            label = post.mediaLabel,
                            playing = state.activeAnimatedMediaId == post.id,
                            modifier = Modifier
                                // ISSUE234_FEED_MEDIA_CAPPED_AT_4_5_V1
                                .aspectRatio(boundedInlineVideoAspectRatio(mediaAspectRatio(post)))
                                .combinedClickable(
                                    onClick = { state.openMedia(post) },
                                    onLongClick = showPostMediaActions,
                                ),
                        )
                    }
                }
                PostKind.GALLERY -> GalleryMedia(
                    post = post,
                    state = state,
                    onLongPress = { item ->
                        mediaActionTarget = PostMediaActionTarget(post, item)
                    },
                )
                PostKind.LINK -> {
                    Column {
                        post.remoteImageUri?.let { imageUri ->
                            Box(Modifier.fillMaxWidth()) {
                                RemoteImage(
                                    uri = imageUri,
                                    label = post.mediaLabel.ifBlank { post.title },
                                    modifier = Modifier.combinedClickable(
                                        onClick = openExternalLink,
                                        onLongClick = showPostMediaActions,
                                    ),
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.66f),
                                    contentColor = Color.White,
                                ) {
                                    Icon(
                                        imageVector = Symbols.OpenInNew,
                                        contentDescription = "Open external link",
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        }
                        LinkPanel(post, onOpenLink = openExternalLink)
                    }
                }
                PostKind.CROSSPOST -> CrosspostPanel(post, state)
                PostKind.POLL -> {
                    val pollUrl = redditPollWebUrl(post)
                    PollPanel(
                        post = post,
                        state = state,
                        canOpenOnReddit = pollUrl != null,
                        onOpenReddit = {
                            val intent = redditPollExternalIntent(context, post)
                            if (intent == null) {
                                mediaErrorHost.noticeFailure("Couldn't open the poll on Reddit", "No app on this device can open it")
                            } else {
                                val handoffStore = RedditExternalPollHandoffStore(context)
                                handoffStore.mark(post.id)
                                if (runCatching { context.startActivity(intent) }.isFailure) {
                                    handoffStore.clear()
                                    mediaErrorHost.noticeFailure("Couldn't open the poll on Reddit", "No app on this device can open it")
                                }
                            }
                        },
                    )
                }
                PostKind.TEXT -> Unit
            }
        }
        if (!revealed) {
            SensitiveMediaCover(
                post = post,
                preloaded = true,
                onReveal = { state.revealMedia(post) },
                modifier = Modifier.matchParentSize(),
            )
        }
        if (
            revealed &&
            state.mediaLabelsVisible &&
            post.mediaLabel.isNotBlank() &&
            post.kind !in listOf(PostKind.LINK, PostKind.GALLERY)
        ) {
            MediaBadge(post.mediaLabel, Modifier.align(Alignment.BottomStart))
        }
    }

    mediaActionTarget?.let { target ->
        val videoShareTarget =
            target.galleryItem?.format == MediaFormat.VIDEO ||
                (target.galleryItem == null && target.post.kind == PostKind.VIDEO)

        // ISSUE120_MEDIA_SHEET_STAYS_WHILE_BUSY_V1: the sheet animates to
        // Hidden before onDismissRequest runs; ignoring the request while a
        // share or download ran left an invisible sheet that swallowed every
        // touch. While busy the sheet refuses to hide and stays visible with
        // its progress.
        val mediaActionBusy by rememberUpdatedState(mediaSharePreparing || mediaDownload != null)
        val mediaActionSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            confirmValueChange = { target -> mediaActionSheetMayChange(target, mediaActionBusy) },
        )
        ModalBottomSheet(
            sheetState = mediaActionSheetState,
            onDismissRequest = {
                if (!mediaSharePreparing && mediaDownload == null) mediaActionTarget = null
            },
        ) {
            val downloadState = mediaDownload
            if (downloadState != null) {
                MediaDownloadPanel(downloadState)
            } else if (mediaSharePreparing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (videoShareTarget) Symbols.PlayArrowFilled else Symbols.Share,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Text(
                        if (videoShareTarget) "Preparing video" else "Preparing media",
                        modifier = Modifier.padding(top = 18.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Text(
                        "Getting it ready to share",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(top = 20.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                    )

                    Text(
                        if (videoShareTarget) "Larger videos can take a moment" else "Large files can take a moment",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Media actions",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    content = { Text("Share media") },
                    leadingContent = {
                        Icon(Symbols.Share, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        // LOWUX2_EVERY_SHARE_SHOWS_ITS_PREPARATION_V1 (OCB-138): an
                        // image or GIF share closed the sheet at once and its later
                        // end closed whatever sheet was open then; every share now
                        // keeps the sheet with its progress until the chooser opens.
                        mediaSharePreparing = true
                        mediaScope.launch {
                            val shared = sharePostMedia(context, target)
                            mediaSharePreparing = false
                            mediaActionTarget = null
                            if (!shared) {
                                mediaErrorHost?.reportMediaActionFailure(
                                    title = "Could not share",
                                    message = "This media could not be prepared for sharing",
                                )
                            }
                        }
                    },
                )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Share post link") },
                leadingContent = {
                    Icon(Symbols.Link, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    mediaActionTarget = null
                    sharePostLink(context, post, mediaErrorHost)
                },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text("Download") },
                leadingContent = {
                    Icon(Symbols.Download, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    mediaDownload = MediaDownloadUiState.Saving(done = 0, total = 1)
                    mediaScope.launch {
                        val destination = downloadPostMedia(context, target)
                        if (destination != null) {
                            mediaDownload = MediaDownloadUiState.Saved(
                                count = 1,
                                total = 1,
                                folder = mediaDownloadFolder(destination),
                            )
                            delay(MEDIA_DOWNLOAD_SAVED_VISIBLE_MS)
                        } else {
                            mediaErrorHost?.reportMediaActionFailure(
                                title = "Could not download",
                                message = "This media could not be saved",
                            )
                        }
                        mediaDownload = null
                        mediaActionTarget = null
                    }
                },
            )
            val galleryItems = target.post.gallery
            if (target.post.kind == PostKind.GALLERY && galleryItems.size > 1) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    content = { Text("Download all ${galleryItems.size}") },
                    leadingContent = {
                        Icon(Symbols.PhotoLibrary, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        mediaDownload = MediaDownloadUiState.Saving(done = 0, total = galleryItems.size)
                        mediaScope.launch {
                            val result = downloadGalleryItems(
                                items = galleryItems,
                                download = { item ->
                                    downloadPostMedia(context, PostMediaActionTarget(target.post, item))
                                },
                                onProgress = { done, total ->
                                    mediaDownload = MediaDownloadUiState.Saving(done, total)
                                },
                            )
                            val firstSaved = result.savedPaths.firstOrNull()
                            if (firstSaved != null) {
                                mediaDownload = MediaDownloadUiState.Saved(
                                    count = result.savedPaths.size,
                                    total = galleryItems.size,
                                    folder = mediaDownloadFolder(firstSaved),
                                )
                                delay(MEDIA_DOWNLOAD_SAVED_VISIBLE_MS)
                            } else {
                                mediaErrorHost?.reportMediaActionFailure(
                                    title = "Could not download",
                                    message = "None of the ${galleryItems.size} items could be saved",
                                )
                            }
                            mediaDownload = null
                            mediaActionTarget = null
                        }
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
            }
        }
    }
}

private data class LocalMediaDrawableState(
    val drawable: Drawable? = null,
    val failed: Boolean = false,
)

@Composable
fun LocalUriMedia(
    uri: String,
    mimeType: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onSourceAspectRatioChanged: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val drawableState by produceState(
        initialValue = LocalMediaDrawableState(),
        key1 = uri,
        key2 = context.contentResolver,
        key3 = mimeType,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                // ISSUE85_LOCAL_MEDIA_DECODED_TO_SCREEN_SIZE_V1: a camera photo
                // decoded at full size (50 MP is ~200 MB) is over what a canvas
                // draws (100 MB) and crashed; it is decoded down to the screen.
                val maxDimension = localMediaMaxDimension(context.resources.displayMetrics)
                val drawable = if (mimeType?.startsWith("video/") == true) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, parsed)
                        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(
                                -1L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                maxDimension,
                                maxDimension,
                            )
                        } else {
                            retriever.getFrameAtTime()
                        }
                        frame?.let { BitmapDrawable(context.resources, it) }
                    } finally {
                        retriever.release()
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(context.contentResolver, parsed),
                    ) { decoder, info, _ ->
                        val sample = localMediaSampleSize(info.size.width, info.size.height, maxDimension)
                        if (sample > 1) decoder.setTargetSampleSize(sample)
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed).use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = localMediaSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                    }
                    context.contentResolver.openInputStream(parsed).use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }?.let { bitmap -> BitmapDrawable(context.resources, bitmap) }
                }
                // LOWUX2_UNDECODABLE_LOCAL_MEDIA_SAYS_SO_V1 (OCB-139): no frame or
                // bitmap (a codec the device lacks) spun forever; it is a failure.
                LocalMediaDrawableState(drawable = drawable, failed = drawable == null)
            }.getOrElse {
                LocalMediaDrawableState(failed = true)
            }
        }
    }
    val drawable = drawableState.drawable

    LaunchedEffect(drawable) {
        val width = drawable?.intrinsicWidth ?: 0
        val height = drawable?.intrinsicHeight ?: 0
        if (width > 0 && height > 0) {
            onSourceAspectRatioChanged(width.toFloat() / height.toFloat())
        }
    }

    DisposableEffect(drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? AnimatedImageDrawable)?.start()
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (drawable as? AnimatedImageDrawable)?.stop()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            drawable != null -> AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(drawable)
                        this.contentDescription = contentDescription
                    }
                },
                update = { view ->
                    if (view.drawable !== drawable) view.setImageDrawable(drawable)
                },
                modifier = Modifier.fillMaxSize(),
            )
            drawableState.failed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Symbols.HideImage, contentDescription = null)
                    Text(
                        "Preview unavailable",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            else -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        if (mimeType.equals("image/gif", ignoreCase = true)) {
            MediaBadge("GIF", Modifier.align(Alignment.TopStart))
        } else if (mimeType?.startsWith("video/") == true) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Icon(
                    Symbols.PlayArrowFilled,
                    contentDescription = "Video preview",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun SensitiveMediaCover(
    post: Post,
    preloaded: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (preloaded) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onReveal,
        modifier = modifier,
        color = if (preloaded) {
            Color.Black.copy(alpha = preloadedSensitiveCoverAlpha())
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (post.kind == PostKind.VIDEO) Symbols.VideocamOff else Symbols.HideImage,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = foreground,
            )
            Text(
                if (post.nsfw) "Sensitive media · tap to reveal" else "Spoiler · tap to reveal",
                modifier = Modifier.padding(top = 8.dp),
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
            )
            if (!preloaded) {
                Text(
                    "Loads after you tap",
                    modifier = Modifier.padding(top = 3.dp),
                    color = foreground,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun HiddenSensitiveMediaRow(
    post: Post,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (post.kind == PostKind.VIDEO) Symbols.VideocamOff else Symbols.HideImage,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (post.nsfw) {
                    "Sensitive preview hidden · open separately"
                } else {
                    "Spoiler preview hidden · open separately"
                },
                modifier = Modifier.padding(start = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun StaticMedia(
    asset: MediaAsset?,
    label: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    val resource = presentationMediaResource(asset)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun MediaFailurePlaceholder(
    presentation: ErrorPresentation,
    diagnosticScopeKey: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    sourceUri: String? = null,
) {
    val gone = presentation.failureDetails?.isRemoteMediaGone() == true
    if (!gone) {
        ReportErrorPresentationDiagnostic(
            scopeKey = diagnosticScopeKey,
            presentation = presentation,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (gone) Symbols.HideImage else Symbols.Error,
            contentDescription = null,
            tint = if (gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Text(
            text = presentation.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Text(
            text = presentation.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        presentation.technicalDetail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (presentation.primaryAction?.kind == ErrorActionKind.RETRY) {
            TextButton(onClick = onRetry) {
                Text(presentation.primaryAction.label)
            }
        }
        val host = mediaSourceHost(sourceUri)
        if (presentation.primaryAction?.kind == ErrorActionKind.OPEN_IN_BROWSER && sourceUri != null && host != null) {
            val uriHandler = LocalUriHandler.current
            Text(
                text = host,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { runCatching { uriHandler.openUri(sourceUri.trim()) } }) {
                Text(presentation.primaryAction.label)
            }
        }
    }
}

@Composable
fun RemoteImage(
    uri: String,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    aspectRatio: Float? = 16f / 9f,
    adaptToSourceAspectRatio: Boolean = false,
    adaptToExactSourceAspectRatio: Boolean = false,
    containerColor: Color? = null,
    showFittedBackdrop: Boolean = false,
    showFailureDetails: Boolean = true,
    onSourceAspectRatioChanged: (Float) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var retryNonce by remember(uri) { mutableIntStateOf(0) }
    val state by produceState<RemoteImageLoadState>(
        initialValue = RemoteImageLoadState.Loading,
        key1 = uri,
        key2 = retryNonce,
    ) {
        // LOWUX2_RETRY_SHOWS_LOADING_V1 (OCB-140): a Retry or a new address
        // showed the old failure or image until the answer; it loads visibly.
        value = RemoteImageLoadState.Loading
        value = when (val result = RemoteImageLoader.load(uri)) {
            is RemoteMediaLoadResult.Ready -> RemoteImageLoadState.Ready(result.value)
            is RemoteMediaLoadResult.Failed -> RemoteImageLoadState.Failed(result.details)
        }
    }
    LaunchedEffect(state) {
        val bitmap = (state as? RemoteImageLoadState.Ready)?.bitmap
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            onSourceAspectRatioChanged(bitmap.width.toFloat() / bitmap.height.toFloat())
        }
    }
    val resolvedAspectRatio = when {
        adaptToExactSourceAspectRatio && state is RemoteImageLoadState.Ready -> {
            val bitmap = (state as RemoteImageLoadState.Ready).bitmap
            if (bitmap.width > 0 && bitmap.height > 0) {
                bitmap.width.toFloat() / bitmap.height.toFloat()
            } else {
                aspectRatio
            }
        }
        adaptToSourceAspectRatio && state is RemoteImageLoadState.Ready -> {
            val bitmap = (state as RemoteImageLoadState.Ready).bitmap
            boundedFeedImageAspectRatio(bitmap.width, bitmap.height)
        }
        else -> aspectRatio
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(resolvedAspectRatio?.let { ratio -> Modifier.aspectRatio(ratio) } ?: Modifier)
            .background(containerColor ?: MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            RemoteImageLoadState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
            is RemoteImageLoadState.Failed -> {
                if (showFailureDetails) {
                    MediaFailurePlaceholder(
                        presentation = remoteMediaErrorPresentation(
                            kind = RemoteMediaKind.IMAGE,
                            details = current.details,
                            sourceUri = uri,
                        ),
                        diagnosticScopeKey = remoteMediaDiagnosticScope("media_image", uri),
                        onRetry = { retryNonce += 1 },
                        sourceUri = uri,
                    )
                } else {
                    Icon(
                        Symbols.HideImage,
                        contentDescription = "Image unavailable",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is RemoteImageLoadState.Ready -> {
                val bitmap = current.bitmap.asImageBitmap()
                if (showFittedBackdrop) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.22f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
                overlay()
            }
        }
    }
}

private sealed interface RemoteGifLoadState {
    data object Loading : RemoteGifLoadState
    data class Failed(val details: FailureDetails) : RemoteGifLoadState
    data class Ready(val image: RemoteAnimatedImage) : RemoteGifLoadState
}

@Composable
fun RemoteGifMedia(
    uri: String,
    label: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    aspectRatio: Float? = 16f / 9f,
    adaptToSourceAspectRatio: Boolean = false,
    adaptToExactSourceAspectRatio: Boolean = false,
    containerColor: Color? = null,
    onSourceAspectRatioChanged: (Float) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var retryNonce by remember(uri) { mutableIntStateOf(0) }
    val state by produceState<RemoteGifLoadState>(
        initialValue = RemoteGifLoadState.Loading,
        key1 = uri,
        key2 = retryNonce,
    ) {
        value = RemoteGifLoadState.Loading
        value = when (val result = RemoteImageLoader.loadAnimated(uri)) {
            is RemoteMediaLoadResult.Ready -> RemoteGifLoadState.Ready(result.value)
            is RemoteMediaLoadResult.Failed -> RemoteGifLoadState.Failed(result.details)
        }
    }
    // LOWUI4_GIF_REPORTS_ITS_ASPECT_RATIO_V1 (OCB-141): like RemoteImage, so a
    // caller sizing by the callback also gets it for a GIF.
    LaunchedEffect(state) {
        val image = (state as? RemoteGifLoadState.Ready)?.image
        if (image != null && image.width > 0 && image.height > 0) {
            onSourceAspectRatioChanged(image.width.toFloat() / image.height.toFloat())
        }
    }
    val resolvedAspectRatio = when {
        adaptToExactSourceAspectRatio && state is RemoteGifLoadState.Ready -> {
            val image = (state as RemoteGifLoadState.Ready).image
            image.width.toFloat() / image.height.toFloat()
        }
        adaptToSourceAspectRatio && state is RemoteGifLoadState.Ready -> {
            val image = (state as RemoteGifLoadState.Ready).image
            boundedFeedImageAspectRatio(image.width, image.height)
        }
        else -> aspectRatio
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(resolvedAspectRatio?.let { ratio -> Modifier.aspectRatio(ratio) } ?: Modifier)
            .background(containerColor ?: Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            RemoteGifLoadState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
            is RemoteGifLoadState.Failed -> MediaFailurePlaceholder(
                presentation = remoteMediaErrorPresentation(
                    kind = RemoteMediaKind.GIF,
                    details = current.details,
                    sourceUri = uri,
                ),
                diagnosticScopeKey = remoteMediaDiagnosticScope("media_gif", uri),
                onRetry = { retryNonce += 1 },
                sourceUri = uri,
            )
            is RemoteGifLoadState.Ready -> {
                val drawable = current.image.drawable
                DisposableEffect(drawable, playing) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val stopAnimatedDrawable = updateAnimatedDrawablePlayback(
                            drawable,
                            playing,
                        )
                        onDispose { stopAnimatedDrawable() }
                    } else {
                        onDispose {}
                    }
                }
                val scaleType = when (contentScale) {
                    ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                    ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                    else -> ImageView.ScaleType.FIT_CENTER
                }
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            this.scaleType = scaleType
                            setImageDrawable(drawable)
                            contentDescription = label
                        }
                    },
                    update = { view ->
                        view.scaleType = scaleType
                        if (view.drawable !== drawable) view.setImageDrawable(drawable)
                        view.contentDescription = label
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!playing) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.68f),
                    ) {
                        Icon(
                            Symbols.PlayArrowFilled,
                            contentDescription = "GIF paused",
                            modifier = Modifier.padding(11.dp),
                            tint = Color.White,
                        )
                    }
                }
                overlay()
            }
        }
    }
}

@Composable
fun RemoteMedia(
    uri: String,
    label: String,
    format: MediaFormat,
    playing: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    aspectRatio: Float? = 16f / 9f,
    adaptToSourceAspectRatio: Boolean = false,
    adaptToExactSourceAspectRatio: Boolean = false,
    containerColor: Color? = null,
    showFittedBackdrop: Boolean = false,
    onSourceAspectRatioChanged: (Float) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (format == MediaFormat.GIF) {
        RemoteGifMedia(
            uri = uri,
            label = label,
            playing = playing,
            modifier = modifier,
            contentScale = contentScale,
            aspectRatio = aspectRatio,
            adaptToSourceAspectRatio = adaptToSourceAspectRatio,
            adaptToExactSourceAspectRatio = adaptToExactSourceAspectRatio,
            containerColor = containerColor,
            // The fitted backdrop is drawn from a still bitmap; a GIF has none.
            onSourceAspectRatioChanged = onSourceAspectRatioChanged,
            overlay = overlay,
        )
    } else {
        RemoteImage(
            uri = uri,
            label = label,
            modifier = modifier,
            contentScale = contentScale,
            aspectRatio = aspectRatio,
            adaptToSourceAspectRatio = adaptToSourceAspectRatio,
            adaptToExactSourceAspectRatio = adaptToExactSourceAspectRatio,
            containerColor = containerColor,
            showFittedBackdrop = showFittedBackdrop,
            onSourceAspectRatioChanged = onSourceAspectRatioChanged,
            overlay = overlay,
        )
    }
}

internal fun boundedFeedImageAspectRatio(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 16f / 9f
    return (width.toFloat() / height.toFloat()).coerceIn(4f / 5f, 16f / 9f)
}

private sealed interface RemoteImageLoadState {
    data object Loading : RemoteImageLoadState
    data class Failed(val details: FailureDetails) : RemoteImageLoadState
    data class Ready(val bitmap: Bitmap) : RemoteImageLoadState
}

@Composable
fun GifMedia(
    asset: MediaAsset?,
    label: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val resource = presentationMediaResource(asset) ?: return
    val drawable = remember(resource, resources) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeAnimatedDrawable(resources, resource)
        } else {
            @Suppress("DEPRECATION")
            resources.getDrawable(resource)
        }
    }
    DisposableEffect(drawable, playing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val stopAnimatedDrawable = updateAnimatedDrawablePlayback(drawable, playing)
            onDispose { stopAnimatedDrawable() }
        } else {
            onDispose {}
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                    contentDescription = label
                }
            },
            update = { view ->
                if (view.drawable !== drawable) view.setImageDrawable(drawable)
            },
            modifier = Modifier.fillMaxSize(),
        )
        MediaBadge("GIF", Modifier.align(Alignment.TopStart))
        if (!playing) {
            Surface(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
            ) {
                Icon(
                    Symbols.PlayArrowFilled,
                    contentDescription = "GIF paused",
                    modifier = Modifier.padding(11.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

// SUBSHELL_QUALITY01A_API28_ANIMATED_DRAWABLE_GUARD_V1
@RequiresApi(Build.VERSION_CODES.P)
private fun updateAnimatedDrawablePlayback(
    drawable: Drawable,
    playing: Boolean,
): () -> Unit {
    val animated = drawable as? AnimatedImageDrawable
    if (playing) {
        animated?.start()
    } else {
        animated?.stop()
    }
    return { animated?.stop() }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedDrawable(resources: android.content.res.Resources, resource: Int): Drawable =
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, resource))

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryMedia(
    post: Post,
    state: SubshellState,
    onLongPress: (GalleryItem) -> Unit,
) {
    if (post.gallery.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    val initialPage = (state.galleryIndices[post.id] ?: 0)
        .coerceIn(0, post.gallery.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { post.gallery.size },
    )
    LaunchedEffect(post.id, pagerState.currentPage) {
        state.updateMediaIndex(post.id, pagerState.currentPage)
    }
    val activeIndex = pagerState.currentPage
    val activeItem = post.gallery.getOrNull(activeIndex)
    val mediaActionsAtStart = state.interactionSide.mediaActionsAtStart()
    // VIDEO10_INTERACTION_SIDE_IS_PHYSICAL_IN_RTL_V1: the chosen side is physical.
    val galleryCounterAlignment = if (mediaActionsAtStart) {
        AbsoluteAlignment.TopRight
    } else {
        AbsoluteAlignment.TopLeft
    }
    val galleryFullscreenAlignment = if (mediaActionsAtStart) {
        AbsoluteAlignment.TopLeft
    } else {
        AbsoluteAlignment.TopRight
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 32.dp,
                    top = 10.dp,
                    end = 32.dp,
                    bottom = 10.dp,
                ),
                pageSpacing = 8.dp,
                // ISSUE121_GALLERY_PAGE_KEY_UNIQUE_V1: a gallery may list one
                // media_id twice; a repeated pager key crashed the feed.
                key = { index -> galleryPageKey(post.gallery[index].id, index) },
            ) { index ->
                val item = post.gallery[index]
                GalleryTile(
                    item = item,
                    playing = state.activeAnimatedMediaId == post.id && index == activeIndex,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = { state.openMedia(post, index) },
                            onLongClick = { onLongPress(item) },
                        ),
                )
            }
            MediaBadge(
                "${(activeIndex + 1).coerceAtMost(post.gallery.size)} / ${post.gallery.size}",
                Modifier
                    .align(galleryCounterAlignment)
                    .padding(horizontal = 32.dp, vertical = 10.dp),
            )
            // M3225_MEDIA_BUTTONS_ARE_FILLED_TONAL_ICON_BUTTONS_V1: same scrim colours,
            // M3 icon button states.
            FilledTonalIconButton(
                onClick = { state.openMedia(post, activeIndex) },
                modifier = Modifier
                    .align(galleryFullscreenAlignment)
                    .padding(horizontal = 32.dp, vertical = 10.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.66f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    Symbols.Fullscreen,
                    contentDescription = "Open image ${activeIndex + 1} of ${post.gallery.size} full screen",
                )
            }
            activeItem?.let { item ->
                when (state.mediaCaptionMode) {
                    MediaCaptionMode.OVERLAY -> {
                        if (item.caption.isNotBlank() || item.outboundUrl.isNotBlank()) {
                            GalleryMetadataOverlay(
                                item = item,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 40.dp, end = 40.dp, bottom = 18.dp)
                                    .fillMaxWidth(),
                                onOpenLink = { url ->
                                    runCatching { uriHandler.openUri(url) }
                                },
                            )
                        }
                    }
                    MediaCaptionMode.BELOW_MEDIA -> Unit
                    MediaCaptionMode.HIDDEN -> {
                        if (item.outboundUrl.isNotBlank()) {
                            GalleryMetadataOverlay(
                                item = item,
                                showCaption = false,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 40.dp, end = 40.dp, bottom = 18.dp)
                                    .fillMaxWidth(),
                                onOpenLink = { url ->
                                    runCatching { uriHandler.openUri(url) }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.mediaCaptionMode == MediaCaptionMode.BELOW_MEDIA) {
            activeItem
                ?.takeIf { item ->
                    item.caption.isNotBlank() || item.outboundUrl.isNotBlank()
                }
                ?.let { item ->
                    GalleryMetadataBelow(
                        item = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 10.dp),
                        onOpenLink = { url ->
                            runCatching { uriHandler.openUri(url) }
                        },
                    )
                }
        }
    }
}

@Composable
fun GalleryMetadataOverlay(
    item: GalleryItem,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
    onOpenLink: (String) -> Unit = {},
) {
    GalleryMetadataSurface(
        item = item,
        overlay = true,
        showCaption = showCaption,
        modifier = modifier,
        onOpenLink = onOpenLink,
    )
}

@Composable
fun GalleryMetadataBelow(
    item: GalleryItem,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    GalleryMetadataSurface(
        item = item,
        overlay = false,
        showCaption = true,
        modifier = modifier,
        onOpenLink = onOpenLink,
    )
}

@Composable
private fun GalleryMetadataSurface(
    item: GalleryItem,
    overlay: Boolean,
    showCaption: Boolean,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    val caption = item.caption.trim().takeIf { showCaption }.orEmpty()
    val outboundUrl = item.outboundUrl.trim()
    if (caption.isEmpty() && outboundUrl.isEmpty()) return

    val foreground = if (overlay) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        color = if (overlay) {
            Color.Black.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (caption.isNotEmpty()) {
                Text(
                    caption,
                    color = foreground,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (outboundUrl.isNotEmpty()) {
                // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-143)
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Open link", role = Role.Button) { onOpenLink(outboundUrl) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // ISSUE218_GALLERY_LINK_READS_ON_THE_OVERLAY_V1: over the dark overlay
                    // the link takes the overlay's foreground, underlined (primary on
                    // black was ~3:1 in light theme); on a surface it stays primary.
                    val linkColor = if (overlay) foreground else MaterialTheme.colorScheme.primary
                    Icon(
                        Symbols.OpenInNew,
                        contentDescription = null,
                        tint = linkColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        galleryOutboundLinkLabel(outboundUrl),
                        color = linkColor,
                        textDecoration = if (overlay) TextDecoration.Underline else null,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun galleryOutboundLinkLabel(url: String): String =
    runCatching { java.net.URI(url).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)
        ?: url

@Composable
fun GalleryTile(item: GalleryItem, playing: Boolean, modifier: Modifier = Modifier) {
    val imageUri = item.remoteImageUri
    if (item.localMediaUri != null) {
        LocalUriMedia(
            uri = item.localMediaUri,
            mimeType = item.localMediaMimeType,
            contentDescription = item.label,
            modifier = modifier,
        )
    } else if (imageUri != null) {
        RemoteMedia(
            uri = imageUri,
            label = item.label,
            format = item.format,
            playing = playing,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            aspectRatio = null,
            containerColor = Color.Black,
            showFittedBackdrop = true,
        )
    } else {
        when (item.format) {
            MediaFormat.GIF -> GifMedia(item.asset, item.label, playing, modifier)
            MediaFormat.IMAGE -> StaticMedia(item.asset, item.label, modifier)
            MediaFormat.VIDEO -> StaticMedia(item.asset, item.label, modifier)
        }
    }
}

@Composable
fun InlineCommentMedia(media: InlineMedia, playing: Boolean, modifier: Modifier = Modifier) {
    when (media.format) {
        MediaFormat.GIF -> GifMedia(
            asset = media.asset,
            label = media.label,
            playing = playing,
            modifier = modifier.width(220.dp).aspectRatio(if (media.asset == MediaAsset.GIF_HOMEMADE) 4f / 7f else 1f),
        )
        MediaFormat.IMAGE -> StaticMedia(media.asset, media.label, modifier.width(220.dp))
        MediaFormat.VIDEO -> StaticMedia(media.asset, media.label, modifier.width(220.dp))
    }
}

@Composable
private fun LinkPanel(
    post: Post,
    onOpenLink: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onOpenLink),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(post.domain ?: "External link", style = MaterialTheme.typography.labelLarge)
                Text(post.mediaLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Symbols.OpenInNew, contentDescription = "Open link preview")
        }
    }
}

private fun issue2HttpsUriOrNull(raw: String): URI? {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val parsed = runCatching { URI(value) }.getOrNull() ?: return null
    if (
        !parsed.scheme.equals("https", ignoreCase = true) ||
        parsed.host.isNullOrBlank() ||
        parsed.userInfo != null
    ) {
        return null
    }
    return parsed
}

private fun issue2DecodeQueryComponent(value: String): String? =
    runCatching {
        URLDecoder.decode(
            value.replace("+", "%2B"),
            "UTF-8",
        )
    }.getOrNull()

private fun URI.issue2QueryParameter(name: String): String? =
    rawQuery
        ?.split('&')
        ?.asSequence()
        ?.map { part ->
            val separator = part.indexOf('=')
            if (separator < 0) {
                part to ""
            } else {
                part.substring(0, separator) to part.substring(separator + 1)
            }
        }
        ?.firstOrNull { (rawName, _) ->
            issue2DecodeQueryComponent(rawName) == name
        }
        ?.second
        ?.let(::issue2DecodeQueryComponent)

internal fun redditStaticImageTargetOrNull(raw: String): String? {
    val initialRaw = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val initial = issue2HttpsUriOrNull(initialRaw) ?: return null

    val candidateRaw = if (
        initial.host.lowercase() in REDDIT_LINK_HANDLING_HOSTS &&
        initial.path.equals("/media", ignoreCase = true)
    ) {
        initial.issue2QueryParameter("url")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
    } else {
        initialRaw
    }

    val candidate = issue2HttpsUriOrNull(candidateRaw) ?: return null
    if (!candidate.host.equals("i.redd.it", ignoreCase = true)) return null

    val path = candidate.path.orEmpty().lowercase()
    if (
        !path.endsWith(".jpg") &&
        !path.endsWith(".jpeg") &&
        !path.endsWith(".png") &&
        !path.endsWith(".webp")
    ) {
        return null
    }
    return candidate.toASCIIString()
}

internal fun normalizedRedditShareUrlOrNull(raw: String): String? {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return null
    val parsed = issue2HttpsUriOrNull(value) ?: return null
    if (!parsed.host.equals("sh.reddit.com", ignoreCase = true)) return null

    return value.replaceFirst(
        Regex("^https://sh\\.reddit\\.com", RegexOption.IGNORE_CASE),
        "https://www.reddit.com",
    )
}

@Composable
private fun CrosspostPanel(post: Post, state: SubshellState) {
    val source = post.crosspost ?: return
    val uriHandler = LocalUriHandler.current
    val sourcePost = source.toEmbeddedPost()
    val sourceTarget = resolveRedditTarget(source.permalink)
        ?: resolveRedditTarget(source.id)
    val openSource: () -> Unit = {
        sourceTarget?.let { target ->
            state.openRedditTarget(
                target = target,
                resolvedPost = sourcePost,
            )
        }
    }

    fun openCrosspostBodyLink(rawUrl: String) {
        val normalizedShareUrl = normalizedRedditShareUrlOrNull(rawUrl)
        val canonicalUrl = normalizedShareUrl ?: rawUrl

        redditStaticImageTargetOrNull(canonicalUrl)?.let { imageUrl ->
            state.openMedia(
                sourcePost.copy(
                    kind = PostKind.IMAGE,
                    mediaLabel = "Image",
                    mediaAsset = null,
                    remoteVideoUri = null,
                    localMediaUri = null,
                    localMediaMimeType = null,
                    gallery = emptyList(),
                    crosspost = null,
                    poll = null,
                    remoteImageUri = imageUrl,
                    videoWidth = null,
                    videoHeight = null,
                    externalUrl = imageUrl,
                ),
            )
            return
        }

        val target = resolveRedditTarget(canonicalUrl)
        if (
            target != null &&
            state.openRedditTarget(
                target = target,
                resolvedPost = sourcePost,
            )
        ) {
            return
        }

        // sh.reddit.com is Reddit-owned but not a stable public App Link host.
        // If its path cannot be resolved at all, keep the user in Subshell by
        // opening the already-resolved crosspost source instead of a dead browser host.
        // A resolvable link to a post that is not loaded goes on to the
        // in-app link path below, which fetches that post (REDDITURL02).
        if (normalizedShareUrl != null && target == null) {
            openSource()
            return
        }

        runCatching { uriHandler.openUri(rawUrl) }
    }
    LaunchedEffect(source.community) {
        state.ensureCommunityIdentity(source.community)
    }
    val sourceCommunityIdentity = state.communityFor(source.community)
    val sourceCommunityAccent = sourceCommunityIdentity
        ?.let { community -> Color(community.accent) }
        ?: accentFor(source.community)
    val pointLabel = if (source.score == 1) "point" else "points"
    val commentLabel = if (source.comments == 1) "comment" else "comments"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = sourceTarget != null,
                            role = Role.Button,
                            onClick = openSource,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CommunityAvatar(
                        name = source.community,
                        accent = sourceCommunityAccent,
                        modifier = Modifier.size(30.dp),
                        iconImageUri = sourceCommunityIdentity?.iconImageUri,
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.subredditLabel(source.community),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (source.age.isNotBlank()) {
                                Text(
                                    text = " · ${source.age}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "u/${source.author}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                source.flair?.takeIf(String::isNotBlank)?.let { flair ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        // ISSUE44_POST_FLAIR_RICHTEXT_V1
                        if (source.flairRichText.isEmpty()) {
                            Text(
                                text = redditFlairFallbackText(flair).ifBlank { flair },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            FlairRichTextLabel(
                                text = flair,
                                richText = source.flairRichText,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                textStyle = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = source.title,
                    modifier = Modifier.clickable(
                        enabled = sourceTarget != null,
                        role = Role.Button,
                        onClick = openSource,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (source.body.isNotBlank()) {
                    MarkdownText(
                        text = source.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        onLinkClick = ::openCrosspostBodyLink,
                    )
                }
            }

            if (source.kind != PostKind.TEXT) {
                PostMedia(
                    post = sourcePost.withCrosspostSensitivity(post),
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "${compactNumber(source.score)} $pointLabel · " +
                    "${compactNumber(source.comments)} $commentLabel",
                modifier = Modifier
                    .clickable(
                        enabled = sourceTarget != null,
                        role = Role.Button,
                        onClick = openSource,
                    )
                    .padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ISSUE83_CROSSPOST_MEDIA_KEEPS_ITS_OWN_SENSITIVITY_V1: Reddit marks a
// crosspost NSFW or spoiler on its own (an NSFW community, the crossposter's
// choice); the embedded media was covered only by the source's flags.
internal fun Post.withCrosspostSensitivity(crosspost: Post): Post =
    copy(nsfw = nsfw || crosspost.nsfw, spoiler = spoiler || crosspost.spoiler)

// ISSUE84_OPAQUE_SENSITIVE_COVER_WITHOUT_BLUR_V1: Modifier.blur is a no-op
// below Android 12, so the preloaded NSFW/spoiler image showed sharp under a
// 52 % veil; there the veil is opaque (the image still preloads, so a tap
// reveals it at once).
internal fun preloadedSensitiveCoverAlpha(sdkInt: Int = Build.VERSION.SDK_INT): Float =
    if (sdkInt >= Build.VERSION_CODES.S) 0.52f else 1f

private fun CrosspostPreview.toEmbeddedPost(): Post =
    Post(
        id = id.ifBlank {
            permalink?.takeIf(String::isNotBlank)
                ?: "crosspost:$community:$title"
        },
        community = community,
        author = author,
        age = age.ifBlank { "—" },
        title = title,
        body = body,
        score = score,
        comments = comments,
        kind = kind,
        mediaLabel = mediaLabel,
        remoteVideoUri = remoteVideoUri,
        remoteVideoHlsUri = remoteVideoHlsUri,
        remoteVideoFallbackUri = remoteVideoFallbackUri,
        gallery = gallery,
        domain = domain,
        poll = poll,
        flair = flair,
        flairRichText = flairRichText,
        spoiler = spoiler,
        nsfw = nsfw,
        remoteImageUri = remoteImageUri,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        externalUrl = externalUrl,
        permalink = permalink,
    )

internal fun redditPollWebUrl(post: Post): String? {
    val postId = post.id
        .trim()
        .removePrefix("t3_")
        .lowercase()
    if (
        postId.isEmpty() ||
        postId.any { character -> character !in 'a'..'z' && character !in '0'..'9' }
    ) {
        return null
    }
    return java.net.URI(
        "https",
        "www.reddit.com",
        "/poll/$postId",
        null,
    ).toString()
}

private const val CUSTOM_TABS_SESSION_EXTRA =
    "android.support.customtabs.extra.SESSION"
private const val CUSTOM_TABS_SEND_TO_EXTERNAL_HANDLER_EXTRA =
    "android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER"
private const val CUSTOM_TABS_INITIAL_NAVIGATION_CAN_LEAVE_BROWSER_EXTRA =
    "androidx.browser.customtabs.extra.INITIAL_NAVIGATION_CAN_LEAVE_BROWSER"

internal fun redditPollExternalIntent(context: Context, post: Post): Intent? {
    val url = redditPollWebUrl(post) ?: return null
    val uri = Uri.parse(url)
    val browserProbeUri = Uri.Builder()
        .scheme("https")
        .authority("example.com")
        .build()
    val browserProbe = Intent(Intent.ACTION_VIEW, browserProbeUri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val defaultBrowserPackage = context.packageManager
        .resolveActivity(
            browserProbe,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        ?.activityInfo
        ?.packageName
        ?.takeUnless { packageName -> packageName == "android" }

    if (defaultBrowserPackage != null) {
        val customTabsSession = Bundle().apply {
            putBinder(CUSTOM_TABS_SESSION_EXTRA, null)
        }
        return Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(defaultBrowserPackage)
            .putExtra(CUSTOM_TABS_SEND_TO_EXTERNAL_HANDLER_EXTRA, false)
            .putExtra(CUSTOM_TABS_INITIAL_NAVIGATION_CAN_LEAVE_BROWSER_EXTRA, false)
            .putExtras(customTabsSession)
    }

    return Intent.createChooser(
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
        "Open poll on Reddit",
    )
}

internal fun shouldOpenRedditPollOptionExternally(
    localMutationsAllowed: Boolean,
    canOpenOnReddit: Boolean,
): Boolean = !localMutationsAllowed && canOpenOnReddit

@Composable
private fun PollPanel(
    post: Post,
    state: SubshellState,
    canOpenOnReddit: Boolean,
    onOpenReddit: () -> Unit,
) {
    val poll = post.poll ?: return
    var pendingChoice by remember(post.id) { mutableStateOf<String?>(null) }
    val localSubmittedChoice = state.pollVotes[post.id].takeIf { state.localMutationsAllowed }
    val selectedChoice = localSubmittedChoice ?: pendingChoice
    val localResultsVisible = localSubmittedChoice != null
    val resultsVisible = poll.resultsVisible || localResultsVisible
    val total = poll.totalVotes + if (localResultsVisible) 1 else 0
    val readOnlyOptionOpensExternally = shouldOpenRedditPollOptionExternally(
        localMutationsAllowed = state.localMutationsAllowed,
        canOpenOnReddit = canOpenOnReddit,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // ISSUE219_POLL_OPTIONS_ARE_ONE_RADIO_GROUP_V1
        Column(
            Modifier.padding(14.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Symbols.Poll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        state.localMutationsAllowed && localResultsVisible -> "Local poll result"
                        state.localMutationsAllowed -> "Choose one"
                        poll.isClosed -> "Poll closed"
                        else -> "Read-only Reddit poll"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            poll.options.forEach { option ->
                val selected = selectedChoice == option.id
                val optionVotes = option.votes + if (localSubmittedChoice == option.id) 1 else 0
                val fraction = (optionVotes.toFloat() / total.coerceAtLeast(1))
                    .coerceIn(0f, 1f)
                val selectable = state.localMutationsAllowed && !localResultsVisible
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                // LOWUI7_CHOICE_ROWS_SAY_WHICH_IS_SELECTED_V1 (OCB-144)
                                selectable -> Modifier.selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                ) { pendingChoice = option.id }
                                readOnlyOptionOpensExternally ->
                                    Modifier.clickable(onClick = onOpenReddit)
                                else -> Modifier
                            },
                        ),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            when {
                                resultsVisible -> Text(
                                    text = "${(fraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                selected -> Icon(
                                    Symbols.Check,
                                    contentDescription = if (selectable) null else "Selected",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (resultsVisible) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 7.dp),
                            )
                        }
                    }
                }
            }

            if (state.localMutationsAllowed && !localResultsVisible) {
                Button(
                    onClick = { pendingChoice?.let { state.votePoll(post.id, it) } },
                    enabled = pendingChoice != null,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Vote")
                }
            } else if (!state.localMutationsAllowed && canOpenOnReddit) {
                TextButton(
                    onClick = onOpenReddit,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (poll.isClosed) "Open on Reddit" else "Open on Reddit to vote")
                }
            }

            Text(
                buildString {
                    append(compactNumber(total))
                    append(" votes · ")
                    append(poll.closesIn)
                    if (localResultsVisible) append(" · stored locally")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaBadge(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(10.dp),
        color = Color.Black.copy(alpha = 0.66f),
        shape = CircleShape,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ISSUE11A1_ORDERED_REMOTE_VIDEO_SOURCE_PROJECTION_V1
internal fun remoteVideoMediaSource(uri: String): VideoMediaSource {
    val normalizedPath = uri.substringBefore('?').lowercase()
    val (kind, mimeType) = when {
        normalizedPath.endsWith(".mpd") ->
            VideoMediaSourceKind.DASH to MimeTypes.APPLICATION_MPD
        normalizedPath.endsWith(".m3u8") ->
            VideoMediaSourceKind.HLS to MimeTypes.APPLICATION_M3U8
        else ->
            VideoMediaSourceKind.PROGRESSIVE to MimeTypes.VIDEO_MP4
    }
    return VideoMediaSource(
        uri = uri,
        kind = kind,
        mimeType = mimeType,
        cacheKey = redGifsCacheKeyOf(uri),
    )
}

internal fun remoteVideoMediaSources(post: Post): List<VideoMediaSource> =
    sequenceOf(
        post.remoteVideoUri,
        post.remoteVideoHlsUri,
        post.remoteVideoFallbackUri,
    )
        .mapNotNull { uri ->
            uri
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }
        .distinct()
        // ISSUE256_REDGIFS_HD_THEN_SD_V1: a RedGIFs clip is tried in HD, then SD,
        // before Reddit's copies.
        .flatMap(::redGifsQualitySources)
        .map(::remoteVideoMediaSource)
        .toList()

internal fun videoMediaForPost(context: Context, post: Post): VideoMedia? {
    if (!hasPlayableVideoSource(post)) return null
    post.localMediaUri
        ?.takeIf { uri ->
            uri.isNotBlank() &&
                post.localMediaMimeType?.startsWith("video/", ignoreCase = true) == true
        }
        ?.let { uri ->
            return VideoMedia(
                mediaId = post.id,
                sources = listOf(
                    VideoMediaSource(
                        uri = uri,
                        kind = VideoMediaSourceKind.PROGRESSIVE,
                        mimeType = post.localMediaMimeType ?: MimeTypes.VIDEO_MP4,
                    ),
                ),
            )
        }
    remoteVideoMediaSources(post)
        .takeIf(List<VideoMediaSource>::isNotEmpty)
        ?.let { sources ->
            return VideoMedia(
                mediaId = post.id,
                sources = sources,
            )
        }
    // H7 (#227): the prototype lab assets are not on the production path above.
    return prototypeVideoMedia(context, post)
}

/** Bundled lab assets (offline preview posts); a Reddit post never reaches this. */
private fun prototypeVideoMedia(context: Context, post: Post): VideoMedia? {
    return when (post.mediaAsset) {
        MediaAsset.MEDIA03_DASH ->
            if (
                bundledPrototypeAssetExists(
                    context,
                    "media03/dash/manifest.mpd",
                )
            ) {
                adaptiveAssetVideoMedia(
                    mediaId = post.id,
                    assetPath = "media03/dash/manifest.mpd",
                    kind = VideoMediaSourceKind.DASH,
                )
            } else {
                null
            }

        MediaAsset.MEDIA03_HLS ->
            if (
                bundledPrototypeAssetExists(
                    context,
                    "media03/hls/master.m3u8",
                )
            ) {
                adaptiveAssetVideoMedia(
                    mediaId = post.id,
                    assetPath = "media03/hls/master.m3u8",
                    kind = VideoMediaSourceKind.HLS,
                )
            } else {
                null
            }

        MediaAsset.MEDIA03_FALLBACK -> {
            val fallbackResource = optionalPrototypeRawResource(
                context,
                MediaAsset.VIDEO_LAB_Z,
            ) ?: return null
            adaptiveAssetVideoMedia(
                mediaId = post.id,
                assetPath = "media03/missing/primary.mpd",
                kind = VideoMediaSourceKind.DASH,
                fallbackSources = localRawVideoMedia(
                    context = context,
                    mediaId = post.id,
                    rawResource = fallbackResource,
                ).sources,
            )
        }

        MediaAsset.MEDIA03_SPLIT_AV ->
            if (
                bundledPrototypeAssetExists(
                    context,
                    "media03/split/video.mp4",
                ) &&
                bundledPrototypeAssetExists(
                    context,
                    "media03/split/audio.mp4",
                )
            ) {
                splitAssetVideoMedia(
                    mediaId = post.id,
                    videoAssetPath = "media03/split/video.mp4",
                    audioAssetPath = "media03/split/audio.mp4",
                )
            } else {
                null
            }

        MediaAsset.MEDIA03_SPLIT_AUDIO_FALLBACK ->
            if (
                bundledPrototypeAssetExists(
                    context,
                    "media03/split/video.mp4",
                ) &&
                bundledPrototypeAssetExists(
                    context,
                    "media03/split/fallback.mp4",
                )
            ) {
                splitAssetVideoMedia(
                    mediaId = post.id,
                    videoAssetPath = "media03/split/video.mp4",
                    audioAssetPath = "media03/split/missing-audio.mp4",
                    fallbackSources = listOf(
                        VideoMediaSource(
                            uri = "asset:///media03/split/fallback.mp4",
                            kind = VideoMediaSourceKind.PROGRESSIVE,
                            mimeType = MimeTypes.VIDEO_MP4,
                        ),
                    ),
                )
            } else {
                null
            }

        else -> {
            val rawResource = optionalPrototypeRawResource(
                context = context,
                asset = post.mediaAsset,
            ) ?: return null
            localRawVideoMedia(
                context = context,
                mediaId = post.id,
                rawResource = rawResource,
                subtitleTracks = if (
                    post.mediaAsset == MediaAsset.VIDEO_LAB_A &&
                    bundledPrototypeAssetExists(
                        context,
                        "media03/subtitles/video-lab-a-en.vtt",
                    ) &&
                    bundledPrototypeAssetExists(
                        context,
                        "media03/subtitles/video-lab-a-nb.vtt",
                    )
                ) {
                    listOf(
                        VideoMediaTextTrackSource(
                            uri = "asset:///media03/subtitles/video-lab-a-en.vtt",
                            language = "en",
                            label = "English",
                        ),
                        VideoMediaTextTrackSource(
                            uri = "asset:///media03/subtitles/video-lab-a-nb.vtt",
                            language = "nb",
                            label = "Norsk bokmål",
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }
    }
}

internal fun mediaAspectRatio(post: Post): Float {
    val videoWidth = post.videoWidth
    val videoHeight = post.videoHeight
    if (videoWidth != null && videoHeight != null && videoWidth > 0 && videoHeight > 0) {
        return videoWidth.toFloat() / videoHeight.toFloat()
    }
    return when (post.mediaAsset) {
        MediaAsset.VIDEO_LAB_B,
        MediaAsset.VIDEO_LAB_F,
        MediaAsset.VIDEO_LAB_V -> 9f / 16f
        MediaAsset.VIDEO_LAB_C,
        MediaAsset.VIDEO_LAB_O,
        MediaAsset.VIDEO_LAB_Y -> 1f
        MediaAsset.VIDEO_LAB_D,
        MediaAsset.VIDEO_LAB_N -> 4f / 5f
        MediaAsset.VIDEO_LAB_G -> 21f / 9f
        MediaAsset.VIDEO_LAB_I -> 3f
        MediaAsset.VIDEO_LAB_J -> 1f / 2f
        MediaAsset.VIDEO_LAB_K -> 4f / 3f
        MediaAsset.VIDEO_LAB_L -> 3f / 4f
        MediaAsset.VIDEO_LAB_M -> 960f / 402f
        MediaAsset.VIDEO_LAB_P -> 2f
        MediaAsset.VIDEO_LAB_Q -> 2f / 3f
        MediaAsset.VIDEO_LAB_R -> 3f / 2f
        MediaAsset.VIDEO_LAB_S -> 10f / 16f
        MediaAsset.VIDEO_LAB_T -> 16f / 10f
        MediaAsset.VIDEO_LAB_U -> 5f / 4f
        MediaAsset.VIDEO_LAB_W -> 960f / 412f
        MediaAsset.GIF_HOMEMADE -> 4f / 7f
        MediaAsset.GIF_PIXEL_ART -> 1f
        else -> 16f / 9f
    }
}

// ISSUE121_GALLERY_PAGE_KEY_UNIQUE_V1
internal fun galleryPageKey(itemId: String, index: Int): String = "$itemId#$index"

// ISSUE85_LOCAL_MEDIA_DECODED_TO_SCREEN_SIZE_V1: the longest side a local image
// is decoded to: the screen's longest side, at most 4096 px.
internal fun localMediaMaxDimension(metrics: android.util.DisplayMetrics): Int =
    maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(1, LOCAL_MEDIA_MAX_DIMENSION)

// The smallest power of two that brings the longest side within maxDimension.
internal fun localMediaSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    val longest = maxOf(width, height)
    if (longest <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (longest / sample > maxDimension) sample *= 2
    return sample
}

private const val LOCAL_MEDIA_MAX_DIMENSION = 4096

// ISSUE120_MEDIA_SHEET_STAYS_WHILE_BUSY_V1
@OptIn(ExperimentalMaterial3Api::class)
internal fun mediaActionSheetMayChange(target: SheetValue, busy: Boolean): Boolean =
    target != SheetValue.Hidden || !busy
