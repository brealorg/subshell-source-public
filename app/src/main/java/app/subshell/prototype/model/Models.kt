package app.subshell.prototype.model

import androidx.compose.runtime.Immutable

@Immutable
data class Community(
    val id: String,
    val name: String,
    val description: String,
    val members: String,
    val accent: Long,
    val joined: Boolean = false,
    val iconImageUri: String? = null,
    val bannerImageUri: String? = null,
)

@Immutable
data class CommunityDiscoveryItem(
    val community: Community,
    val nsfw: Boolean,
)

@Immutable
data class RedditPeopleSearchItem(
    val username: String,
    val title: String,
    val description: String,
    val avatarImageUri: String? = null,
    val nsfw: Boolean = false,
)

enum class CommunityDiscoverySort(val queryValue: String) {
    RELEVANCE("relevance"),
    ACTIVITY("activity"),
}

internal const val COMMUNITY_DISCOVERY_MIN_SEARCH_QUERY_LENGTH = 2
internal const val COMMUNITY_DISCOVERY_MAX_SEARCH_QUERY_LENGTH = 100
internal const val COMMUNITY_AUTOCOMPLETE_MAX_QUERY_LENGTH = 25

enum class PostKind {
    TEXT,
    IMAGE,
    VIDEO,
    GIF,
    GALLERY,
    LINK,
    CROSSPOST,
    POLL,
}

enum class MediaAsset {
    VIDEO_LAB_A,
    VIDEO_LAB_B,
    VIDEO_LAB_C,
    VIDEO_LAB_D,
    VIDEO_LAB_E,
    VIDEO_LAB_F,
    VIDEO_LAB_G,
    VIDEO_LAB_H,
    VIDEO_LAB_I,
    VIDEO_LAB_J,
    VIDEO_LAB_K,
    VIDEO_LAB_L,
    VIDEO_LAB_M,
    VIDEO_LAB_N,
    VIDEO_LAB_O,
    VIDEO_LAB_P,
    VIDEO_LAB_Q,
    VIDEO_LAB_R,
    VIDEO_LAB_S,
    VIDEO_LAB_T,
    VIDEO_LAB_U,
    VIDEO_LAB_V,
    VIDEO_LAB_W,
    VIDEO_LAB_X,
    VIDEO_LAB_Y,
    VIDEO_LAB_Z,
    MEDIA03_DASH,
    MEDIA03_HLS,
    MEDIA03_FALLBACK,
    MEDIA03_SPLIT_AV,
    MEDIA03_SPLIT_AUDIO_FALLBACK,
    HOMELAB,
    ANDROID_DESK,
    LINUX_DESK,
    GIF_HIGH_QUALITY,
    GIF_HOMEMADE,
    GIF_PIXEL_ART,
    PIXEL_GALLERY_CAMPFIRE,
    PIXEL_GALLERY_TERMINAL,
    PIXEL_GALLERY_CAT,
    PIXEL_GALLERY_MOON,
}

enum class MediaFormat {
    IMAGE,
    GIF,
    VIDEO,
}

@Immutable
data class GalleryItem(
    val id: String,
    val label: String,
    val asset: MediaAsset? = null,
    val format: MediaFormat,
    val remoteImageUri: String? = null,
    val localMediaUri: String? = null,
    val localMediaMimeType: String? = null,
    val caption: String = "",
    val outboundUrl: String = "",
)

@Immutable
data class CrosspostPreview(
    val community: String,
    val author: String,
    val title: String,
    val score: Int,
    val id: String = "",
    val age: String = "",
    val body: String = "",
    val comments: Int = 0,
    val kind: PostKind = PostKind.TEXT,
    val mediaLabel: String = "",
    val remoteVideoUri: String? = null,
    val remoteVideoHlsUri: String? = null,
    val remoteVideoFallbackUri: String? = null,
    val gallery: List<GalleryItem> = emptyList(),
    val domain: String? = null,
    val poll: Poll? = null,
    val flair: String? = null,
    val spoiler: Boolean = false,
    val nsfw: Boolean = false,
    val remoteImageUri: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val externalUrl: String? = null,
    val permalink: String? = null,
    // ISSUE44_POST_FLAIR_RICHTEXT_V1
    val flairRichText: List<FlairRichTextSegment> = emptyList(),
)

@Immutable
data class PollOption(
    val id: String,
    val label: String,
    val votes: Int,
)

@Immutable
data class Poll(
    val options: List<PollOption>,
    val totalVotes: Int,
    val closesIn: String,
    val resultsVisible: Boolean = false,
    val isClosed: Boolean = false,
)

@Immutable
data class InlineMedia(
    val label: String,
    val asset: MediaAsset,
    val format: MediaFormat = MediaFormat.GIF,
)

@Immutable
data class UserFixture(
    val username: String,
    val description: String,
    val karma: String,
    val accountAge: String = "2y",
    val followers: String = "—",
    val bannerAsset: MediaAsset? = null,
    val avatarImageUri: String? = null,
    val bannerImageUri: String? = null,
)

enum class ThreadPlacement {
    TOP,
    MIDDLE,
    BOTTOM,
}

@Immutable
data class ProfileCommentFixture(
    val id: String,
    val author: String,
    val postId: String,
    val age: String,
    val body: String,
    val score: Int,
    val placement: ThreadPlacement,
    val community: String? = null,
    val postTitle: String? = null,
)

// USERFLAIR01A3A_SHARED_RICHTEXT_DOMAIN_V1
@Immutable
data class FlairRichTextSegment(
    val elementType: String,
    val text: String? = null,
    val emojiRef: String? = null,
    val imageUrl: String? = null,
)

@Immutable
data class Post(
    val id: String,
    val community: String,
    val author: String,
    val age: String,
    val title: String,
    val body: String = "",
    val score: Int,
    val comments: Int,
    val kind: PostKind,
    val mediaLabel: String = "",
    val mediaAsset: MediaAsset? = null,
    val remoteVideoUri: String? = null,
    val remoteVideoHlsUri: String? = null,
    val remoteVideoFallbackUri: String? = null,
    val localMediaUri: String? = null,
    val localMediaMimeType: String? = null,
    val gallery: List<GalleryItem> = emptyList(),
    val domain: String? = null,
    val crosspost: CrosspostPreview? = null,
    val poll: Poll? = null,
    val flair: String? = null,
    val pinned: Boolean = false,
    val moderatorPost: Boolean = false,
    val spoiler: Boolean = false,
    val nsfw: Boolean = false,
    val saved: Boolean = false,
    val vote: Int = 0,
    val remoteImageUri: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val externalUrl: String? = null,
    val permalink: String? = null,
    // USERFLAIR01A2_AUTHOR_FLAIR_DOMAIN_V1
    val authorFlairText: String? = null,
    val authorFlairTextColor: String? = null,
    val authorFlairBackgroundColor: String? = null,
    val authorFlairRichText: List<FlairRichTextSegment> = emptyList(),
    // ISSUE44_POST_FLAIR_RICHTEXT_V1: Reddit's link_flair_richtext (emoji + text).
    val flairRichText: List<FlairRichTextSegment> = emptyList(),
)

@Immutable
data class CodeSample(
    val language: String,
    val source: String,
)

@Immutable
data class Comment(
    val id: String,
    val author: String,
    val age: String,
    val body: String,
    val score: Int,
    val depth: Int,
    val accent: Long,
    val award: String? = null,
    val isOp: Boolean = false,
    val isModerator: Boolean = false,
    val inlineMedia: InlineMedia? = null,
    val localMediaUri: String? = null,
    val localMediaMimeType: String? = null,
    val codeSample: CodeSample? = null,
    val children: List<Comment> = emptyList(),
    val createdEpochSeconds: Long? = null,
    val parentId: String? = null,
    val moreChildIds: List<String> = emptyList(),
    val moreCount: Int = 0,
    val vote: Int = 0,
    // COMMENTSAVE01A1_SERVER_SAVED_STATE_V1
    val saved: Boolean = false,
    // USERFLAIR01A2_AUTHOR_FLAIR_DOMAIN_V1
    val authorFlairText: String? = null,
    val authorFlairTextColor: String? = null,
    val authorFlairBackgroundColor: String? = null,
    val authorFlairRichText: List<FlairRichTextSegment> = emptyList(),
    // ISSUE226_REMOVED_COMMENT_IS_MARKED_V1: removed by a moderator; Reddit
    // tells moderators (`removed` / `banned_by`), everyone else sees it gone.
    val removedByModerator: Boolean = false,
)

internal val Comment.isMoreRepliesNode: Boolean
    get() = moreChildIds.isNotEmpty() || isContinueThreadNode

// ISSUE3_CONTINUE_THREAD_NODE_V1: replies Reddit leaves out below a comment
// ("Continue this thread", or past the requested depth). The id carries the
// kind, so the positional cache codec needs no new field; parentId is the
// comment whose replies are missing.
internal const val CONTINUE_THREAD_NODE_ID_PREFIX = "continue:"

internal val Comment.isContinueThreadNode: Boolean
    get() = id.startsWith(CONTINUE_THREAD_NODE_ID_PREFIX) && !parentId.isNullOrBlank()

internal fun continueThreadNode(parentFullname: String, depth: Int, accent: Long): Comment = Comment(
    id = "$CONTINUE_THREAD_NODE_ID_PREFIX$parentFullname",
    author = "Continue this thread",
    age = "",
    body = "",
    score = 0,
    depth = depth,
    accent = accent,
    parentId = parentFullname,
)

enum class ActivityKind {
    REPLY,
    MENTION,
    MESSAGE,
    MOD,
    POST_REPLY,
}

@Immutable
data class InboxItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val sender: String,
    val age: String,
    val preview: String,
    val unread: Boolean = false,
    val targetPostId: String? = null,
    val targetCommentId: String? = null,
    val conversation: List<MessageBubble> = emptyList(),
    val createdEpochSeconds: Long? = null,
    val subreddit: String? = null,
    val postTitle: String? = null,
    val contextPermalink: String? = null,
)

@Immutable
data class MessageBubble(
    val id: String,
    val sender: String,
    val age: String,
    val body: String,
    val outgoing: Boolean = false,
    val createdEpochSeconds: Long? = null,
)
