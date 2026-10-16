package app.subshell.prototype.ui

import java.net.URI
import java.net.URLDecoder

data class RedditIntentLaunch(
    val requestId: Long,
    val rawUri: String,
)

internal sealed interface RedditTarget {
    data class Post(
        val postId: String,
    ) : RedditTarget

    data class Comment(
        val postId: String,
        val commentId: String,
    ) : RedditTarget

    data class Community(
        val name: String,
        // REDDITURL01_COMMUNITY_SORT_LINK_V1: `/r/<sub>/top` and friends.
        val sort: CommunitySort? = null,
        // REDDITURL06_WIKI_PAGE_LINK_V1: `/r/<sub>/wiki/<page>`; "" for the
        // wiki itself (`/r/<sub>/wiki/`), null for no wiki at all.
        val wikiPage: String? = null,
    ) : RedditTarget

    // REDDITURL01_MAIN_FEED_LINK_V1: `/r/popular` and `/r/all` are the Home
    // feed sources, not communities.
    data class Feed(
        val source: FeedSource,
    ) : RedditTarget

    data class User(
        val username: String,
        // REDDITURL04_USER_TAB_LINK_V1: `/user/<u>/submitted|comments|...`.
        val tab: UserProfileLinkTab? = null,
    ) : RedditTarget

    // REDDITURL04_SEARCH_LINK_V1: `/search?q=` and `/r/<sub>/search?q=`;
    // [community] is set only when Reddit restricts the search to it.
    data class Search(
        val query: String,
        val community: String? = null,
    ) : RedditTarget

    // ISSUE41_INLINE_VIDEO_LINK_TARGET_V1
    // `reddit.com/link/<post>/video/<media>/player` names a video embedded in
    // a text post or comment. The media id alone addresses it on v.redd.it,
    // so it plays even when the post itself can no longer be loaded.
    data class InlineVideo(
        /** Null for a bare v.redd.it or /video/ link, which names no post. */
        val postId: String?,
        val mediaId: String,
    ) : RedditTarget

    // REDDITURL04_MULTIREDDIT_LINK_V1: `/user/<owner>/m/<name>` (any owner)
    // and `/me/m/<name>` ([owner] null: the signed-in account's own).
    data class CustomFeed(
        val owner: String?,
        val name: String,
    ) : RedditTarget

    // REDDITURL04_COMPOSE_LINK_V1: `/message/compose?to=<user>&subject=&message=`.
    // Subshell's New message starts a Reddit Chat, which has no subject.
    data class Compose(
        val recipient: String,
        val subject: String? = null,
        val message: String? = null,
    ) : RedditTarget

    // REDDITURL03_SHARE_LINK_REDIRECT_V1: `/r/<sub>/s/<id>` and
    // `/u/<user>/s/<id>` name their target only through Reddit's redirect.
    data class ShareLink(
        val url: String,
    ) : RedditTarget

    // REDDITURL02_MEDIA_LINK_TARGET_V1: an image or GIF on Reddit's media
    // hosts, opened in the media viewer instead of the browser.
    data class Image(
        val url: String,
        val animated: Boolean,
    ) : RedditTarget
}

internal fun redditViewIntentLaunchOrNull(
    action: String?,
    rawUri: String?,
    requestId: Long,
): RedditIntentLaunch? {
    if (action != REDDIT_ACTION_VIEW) return null
    val value = rawUri?.trim()?.takeIf(String::isNotEmpty) ?: return null
    // ISSUE41_UNSUPPORTED_REDDIT_LINK_NOTICE_V1: a Reddit link Subshell cannot
    // route still becomes a launch, so the app can say so instead of doing
    // nothing. Every other URL is left alone.
    if (resolveRedditTarget(value) == null && !isRedditWebLink(value)) return null
    return RedditIntentLaunch(
        requestId = requestId,
        rawUri = value,
    )
}

internal fun RedditTarget.postIdOrNull(): String? = when (this) {
    is RedditTarget.Post -> postId
    is RedditTarget.Comment -> postId
    is RedditTarget.Community,
    is RedditTarget.User,
    is RedditTarget.InlineVideo,
    is RedditTarget.Feed,
    is RedditTarget.Image,
    is RedditTarget.ShareLink,
    is RedditTarget.Search,
    is RedditTarget.Compose,
    is RedditTarget.CustomFeed,
    -> null
}

internal fun resolveRedditTarget(raw: String?): RedditTarget? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty)?.let(::unwrapRedditLink) ?: return null

    if (value.startsWith("t3_", ignoreCase = true)) {
        return normalizedRedditPostTargetId(value)
            ?.let { postId -> RedditTarget.Post(postId) }
    }

    val candidate = when {
        value.startsWith("/") -> value
        value.startsWith("r/", ignoreCase = true) ||
            value.startsWith("u/", ignoreCase = true) ||
            value.startsWith("user/", ignoreCase = true) -> "/$value"
        else -> value
    }

    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()
    val pathSegments = uri.path
        .orEmpty()
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(::withoutRedditPathSuffix)
        .filter(String::isNotEmpty)

    if (host != null) {
        if (uri.scheme?.lowercase() !in REDDIT_WEB_SCHEMES) return null
        redditMediaHostTargetOrNull(uri, host)?.let { return it }
        if (host == REDDIT_SHORT_HOST) {
            if (pathSegments.size != 1) return null
            return pathSegments.singleOrNull()
                ?.let(::normalizedRedditPostTargetId)
                ?.let(RedditTarget::Post)
        }
        if (!isRedditWebHost(host)) return null
    } else if (uri.scheme != null) {
        return null
    }

    if (pathSegments.isEmpty()) return null

    if (pathSegments.first().equals("link", ignoreCase = true)) {
        return inlineVideoTargetOrNull(pathSegments)
    }

    // REDDITURL02_MEDIA_LINK_TARGET_V1: Reddit's image proxy page and video
    // pages by media id.
    if (pathSegments.size == 1 && pathSegments[0].equals("media", ignoreCase = true)) {
        return redditMediaProxyTargetOrNull(uri)
    }
    if (pathSegments.size == 2 && pathSegments[0].equals("video", ignoreCase = true)) {
        return redditVideoMediaIdOrNull(pathSegments[1])?.let { RedditTarget.InlineVideo(null, it) }
    }
    if (
        pathSegments.size == 4 &&
        pathSegments[0].equals("r", ignoreCase = true) &&
        pathSegments[2].equals("video", ignoreCase = true)
    ) {
        return redditVideoMediaIdOrNull(pathSegments[3])?.let { RedditTarget.InlineVideo(null, it) }
    }

    if (
        pathSegments.size == 4 &&
        pathSegments[2] == "s" &&
        (pathSegments[0].equals("r", ignoreCase = true) || isRedditUserPathPrefix(pathSegments[0])) &&
        validRedditThingId(pathSegments[3])
    ) {
        return RedditTarget.ShareLink("https://www.reddit.com/" + pathSegments.joinToString("/"))
    }

    if (
        pathSegments.size == 2 &&
        pathSegments[0].equals("message", ignoreCase = true) &&
        pathSegments[1].equals("compose", ignoreCase = true)
    ) {
        return composeTargetOrNull(uri)
    }

    customFeedTargetOrNull(pathSegments)?.let { return it }

    // `/r/search` is a community; only `/search` and `/r/<sub>/search` are searches.
    // LOWUI_SEARCH_ONLY_AT_SEARCH_PATHS_V1 (OCB-088): a post whose title slug is
    // "search" (/r/x/comments/id/search) is a post, not a search.
    val searchPath = pathSegments.size == 1 ||
        (pathSegments.size == 3 && pathSegments[0].equals("r", ignoreCase = true))
    if (pathSegments.last().equals("search", ignoreCase = true) && searchPath) {
        return searchTargetOrNull(uri, pathSegments)
    }
    if (pathSegments.size == 3 && isRedditUserPathPrefix(pathSegments[0])) {
        val tab = UserProfileLinkTab.fromSegment(pathSegments[2])
        val username = normalizedRedditUsernameTargetName(pathSegments[1])
        if (tab != null && username != null) return RedditTarget.User(username, tab)
    }

    // REDDITURL01_GALLERY_LINK_V1: a gallery id is the post id (Reddit
    // redirects /gallery/<id> to /comments/<id>).
    if (pathSegments.first().equals("gallery", ignoreCase = true)) {
        return pathSegments.takeIf { it.size == 2 }
            ?.get(1)
            ?.let(::normalizedRedditPostTargetId)
            ?.let(RedditTarget::Post)
    }

    // LOWUI_COMMENTS_ONLY_WHERE_A_POST_PATH_HAS_IT_V1 (OCB-089): "comments" is
    // the post marker only first or after r/<sub> or u/<name>; r/comments and
    // u/comments are a community and a user.
    val commentsIndex = when {
        pathSegments.first().equals("comments", ignoreCase = true) -> 0
        pathSegments.size > 2 && pathSegments[2].equals("comments", ignoreCase = true) -> 2
        else -> -1
    }
    if (commentsIndex >= 0) {
        val supportedCommentsShape = commentsIndex == 0 ||
            (
                commentsIndex == 2 &&
                    pathSegments[0].equals("r", ignoreCase = true) &&
                    normalizedRedditCommunityTargetName(pathSegments[1]) != null
            ) ||
            // REDDITURL01_USER_PROFILE_POST_LINK_V1: posts in a user profile.
            (
                commentsIndex == 2 &&
                    isRedditUserPathPrefix(pathSegments[0]) &&
                    normalizedRedditUsernameTargetName(pathSegments[1]) != null
            )
        if (!supportedCommentsShape) return null
        val postId = pathSegments.getOrNull(commentsIndex + 1)
            ?.let(::normalizedRedditPostTargetId)
            ?: return null
        val commentId = pathSegments
            .getOrNull(commentsIndex + 3)
            ?.let(::normalizedRedditCommentTargetId)
        return commentId
            ?.let { RedditTarget.Comment(postId = postId, commentId = it) }
            ?: RedditTarget.Post(postId)
    }

    if (pathSegments.first().equals("r", ignoreCase = true)) {
        return communityTargetOrNull(pathSegments)
    }

    if (isRedditUserPathPrefix(pathSegments.first())) {
        if (pathSegments.size != 2) return null
        val username = pathSegments[1]
            .let(::normalizedRedditUsernameTargetName)
            ?: return null
        return RedditTarget.User(username)
    }

    return null
}

/**
 * The profile tabs a Reddit link can name. Posts and Comments exist on every
 * profile; Saved and Upvotes only on the signed-in account's own.
 */
internal enum class UserProfileLinkTab(val profileTabIndex: Int, val ownProfileOnly: Boolean) {
    POSTS(0, false),
    COMMENTS(1, false),
    SAVED(2, true),
    UPVOTED(3, true),
    ;

    companion object {
        fun fromSegment(segment: String): UserProfileLinkTab? = when (segment.lowercase()) {
            "submitted", "posts" -> POSTS
            "comments" -> COMMENTS
            "saved" -> SAVED
            "upvoted" -> UPVOTED
            else -> null
        }
    }
}

/** `/user|u/<owner>/m/<name>[/<sort>]` and `/me/m/<name>[/<sort>]`. */
private fun customFeedTargetOrNull(pathSegments: List<String>): RedditTarget.CustomFeed? {
    val (owner, rest) = when {
        pathSegments.size >= 3 && pathSegments[0].equals("me", ignoreCase = true) ->
            null to pathSegments.drop(1)
        pathSegments.size >= 4 && isRedditUserPathPrefix(pathSegments[0]) ->
            (normalizedRedditUsernameTargetName(pathSegments[1]) ?: return null) to pathSegments.drop(2)
        else -> return null
    }
    if (!rest[0].equals("m", ignoreCase = true) || rest.size !in 2..3) return null
    if (rest.size == 3 && communitySortSegmentOrNull(rest[2]) == null) return null
    val name = rest[1].takeIf { segment ->
        segment.length in 1..100 && segment.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    } ?: return null
    return RedditTarget.CustomFeed(owner, name)
}

/** Decoded query parameters of a web link (`+` is a space, as in forms). */
private fun redditLinkQueryParameters(uri: URI): Map<String, String?> =
    uri.rawQuery
        ?.split('&')
        ?.mapNotNull { parameter ->
            parameter.split('=', limit = 2)
                .takeIf { it.size == 2 }
                ?.let { (name, value) -> name to decodedUrlComponentOrNull(value.replace('+', ' ')) }
        }
        ?.toMap()
        .orEmpty()

/**
 * A compose link to a user. A link to `/r/<sub>` would start a modmail
 * conversation, which Subshell cannot do yet, so it resolves to nothing.
 */
private fun composeTargetOrNull(uri: URI): RedditTarget.Compose? {
    val parameters = redditLinkQueryParameters(uri)
    val to = parameters["to"]?.trim()?.removePrefix("/") ?: return null
    if (to.startsWith("r/", ignoreCase = true)) return null
    val recipient = normalizedRedditUsernameTargetName(to.removePrefix("user/").removePrefix("u/"))
        ?: return null
    return RedditTarget.Compose(
        recipient = recipient,
        subject = parameters["subject"]?.trim()?.takeIf(String::isNotEmpty),
        message = parameters["message"]?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun searchTargetOrNull(uri: URI, pathSegments: List<String>): RedditTarget.Search? {
    val parameters = redditLinkQueryParameters(uri)
    val query = parameters["q"]?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val community = when {
        pathSegments.size == 1 -> null
        pathSegments.size == 3 && pathSegments[0].equals("r", ignoreCase = true) ->
            normalizedRedditCommunityTargetName(pathSegments[1])
                ?.takeIf { parameters["restrict_sr"]?.lowercase() in RESTRICT_SR_ON }
                ?: return RedditTarget.Search(query)
        else -> return null
    }
    return RedditTarget.Search(query, community)
}

private val RESTRICT_SR_ON = setOf("on", "true", "1", "yes")

private fun redditMediaHostTargetOrNull(uri: URI, host: String): RedditTarget? {
    val file = uri.path.orEmpty().split('/').filter(String::isNotEmpty).singleOrNull() ?: return null
    return when (host) {
        REDDIT_VIDEO_HOST -> redditVideoMediaIdOrNull(file)?.let { RedditTarget.InlineVideo(null, it) }
        REDDIT_IMAGE_HOST -> redditImageKindOrNull(file)?.let { animated ->
            RedditTarget.Image("https://$REDDIT_IMAGE_HOST/$file", animated)
        }
        REDDIT_PREVIEW_HOST, REDDIT_EXTERNAL_PREVIEW_HOST -> redditImageKindOrNull(file)?.let { animated ->
            if (animated && host == REDDIT_PREVIEW_HOST) {
                // The preview host serves a still frame of a GIF; the GIF
                // itself lives on i.redd.it under the same file name.
                RedditTarget.Image("https://$REDDIT_IMAGE_HOST/$file", animated = true)
            } else {
                // Keep the signed query: the preview host rejects a URL without it.
                RedditTarget.Image(
                    "https://$host${uri.rawPath}" + (uri.rawQuery?.let { query -> "?$query" } ?: ""),
                    animated,
                )
            }
        }
        else -> null
    }
}

private fun redditMediaProxyTargetOrNull(uri: URI): RedditTarget? {
    val url = uri.rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { parameter ->
            parameter.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "url" }?.get(1)
        }
        ?.let(::decodedUrlComponentOrNull)
        ?: return null
    return when (val target = resolveRedditTarget(url)) {
        is RedditTarget.Image, is RedditTarget.InlineVideo -> target
        else -> null
    }
}

/** Null unless the file name ends in a still or animated image extension. */
private fun redditImageKindOrNull(file: String): Boolean? =
    when (file.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        in REDDIT_STILL_IMAGE_EXTENSIONS -> false
        "gif" -> true
        else -> null
    }

private fun redditVideoMediaIdOrNull(segment: String): String? =
    segment.lowercase().takeIf(::validRedditThingId)

private fun communityTargetOrNull(pathSegments: List<String>): RedditTarget? {
    if (pathSegments.size < 2) return null
    val community = pathSegments[1]
        .let(::normalizedRedditCommunityTargetName)
        ?: return null
    val feed = when (community.lowercase()) {
        "popular" -> FeedSource.POPULAR
        "all" -> FeedSource.ALL
        else -> null
    }
    val rest = pathSegments.drop(2)
    val sort = rest.singleOrNull()?.let(::communitySortSegmentOrNull)
    return when {
        rest.isEmpty() || sort != null ->
            feed?.let(RedditTarget::Feed) ?: RedditTarget.Community(community, sort)
        // REDDITURL06_WIKI_PAGE_LINK_V1: wiki links open that wiki page.
        feed == null && rest.first().lowercase() in REDDIT_COMMUNITY_WIKI_SEGMENTS ->
            RedditTarget.Community(
                community,
                wikiPage = rest.drop(1).joinToString("/").lowercase(),
            )
        // REDDITURL01_COMMUNITY_PAGE_LINK_V1: about pages open the community.
        feed == null && rest.first().lowercase() in REDDIT_COMMUNITY_PAGE_SEGMENTS ->
            RedditTarget.Community(community)
        else -> null
    }
}

private fun communitySortSegmentOrNull(segment: String): CommunitySort? =
    CommunitySort.entries.firstOrNull { sort -> sort.name.equals(segment, ignoreCase = true) }

private fun isRedditUserPathPrefix(segment: String): Boolean =
    segment.equals("u", ignoreCase = true) || segment.equals("user", ignoreCase = true)

/** `.json`, `.xml` and `.compact` name other views of the same page. */
private fun withoutRedditPathSuffix(segment: String): String {
    val suffix = REDDIT_PATH_VIEW_SUFFIXES.firstOrNull { suffix ->
        segment.endsWith(suffix, ignoreCase = true)
    } ?: return segment
    return segment.dropLast(suffix.length)
}

// REDDITURL01_UNWRAP_REDDIT_LINK_V1
// Reddit links also arrive wrapped: the app-link service carries the real
// URL in `$og_redirect`, Reddit e-mails put it encoded after `/CL0/`, and
// Google's AMP cache prefixes `/amp/s/`. Unwrapping needs no network.
internal fun unwrapRedditLink(raw: String): String {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return raw
    val host = uri.host?.lowercase() ?: return raw
    val unwrapped = when {
        host == REDDIT_APP_LINK_HOST ->
            uri.rawQuery
                ?.split('&')
                ?.firstNotNullOfOrNull { parameter ->
                    parameter.split('=', limit = 2)
                        .takeIf { it.size == 2 && it[0] == REDDIT_APP_LINK_REDIRECT_PARAMETER }
                        ?.get(1)
                }
                ?.let(::decodedUrlComponentOrNull)
        host == REDDIT_MAIL_CLICK_HOST -> {
            val segments = uri.rawPath.orEmpty().split('/')
            val marker = segments.indexOf(REDDIT_MAIL_CLICK_MARKER)
            segments.getOrNull(marker + 1)
                ?.takeIf { marker >= 0 }
                ?.let(::decodedUrlComponentOrNull)
        }
        host.removePrefix("www.").startsWith("google.") &&
            uri.rawPath.orEmpty().startsWith(GOOGLE_AMP_PREFIX) ->
            "https://" + uri.rawPath.removePrefix(GOOGLE_AMP_PREFIX) +
                (uri.rawQuery?.let { query -> "?$query" } ?: "")
        else -> null
    }
    return unwrapped?.trim()?.takeIf(String::isNotEmpty) ?: raw
}

// STEP3_URL_DECODE_WORKS_BEFORE_ANDROID_13_V1: decode(String, Charset) is API 33;
// on Android 8-12 it threw NoSuchMethodError, runCatching hid it, and the
// component stayed undecoded (found by the land lint gate's NewApi).
private fun decodedUrlComponentOrNull(value: String): String? =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()

private fun isRedditWebHost(host: String): Boolean =
    host in REDDIT_WEB_HOSTS || host.endsWith(".$REDDIT_ROOT_HOST")

private fun inlineVideoTargetOrNull(pathSegments: List<String>): RedditTarget.InlineVideo? {
    val playerSuffix = when (pathSegments.size) {
        4 -> true
        5 -> pathSegments[4].equals("player", ignoreCase = true)
        else -> false
    }
    if (!playerSuffix || !pathSegments[2].equals("video", ignoreCase = true)) return null
    val postId = normalizedRedditPostTargetId(pathSegments[1]) ?: return null
    val mediaId = pathSegments[3].lowercase().takeIf(::validRedditThingId) ?: return null
    return RedditTarget.InlineVideo(postId = postId, mediaId = mediaId)
}

internal fun isRedditWebLink(raw: String): Boolean {
    val uri = runCatching { URI(unwrapRedditLink(raw.trim())) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme?.lowercase() in REDDIT_WEB_SCHEMES &&
        (isRedditWebHost(host) || host == REDDIT_SHORT_HOST)
}

internal fun redditTargetMatchesPost(
    targetPostId: String,
    postId: String,
): Boolean {
    val target = normalizedRedditPostTargetId(targetPostId) ?: return false
    val post = normalizedRedditPostTargetId(postId) ?: return false
    return target == post
}

private fun normalizedRedditPostTargetId(value: String): String? = value
    .trim()
    .lowercase()
    .removePrefix("t3_")
    .takeIf(::validRedditThingId)

private fun normalizedRedditCommentTargetId(value: String): String? = value
    .trim()
    .lowercase()
    .removePrefix("t1_")
    .takeIf(::validRedditThingId)

private fun normalizedRedditCommunityTargetName(value: String): String? = value
    .trim()
    .removePrefix("r/")
    .removePrefix("/r/")
    .takeIf { name ->
        name.length in 1..REDDIT_COMMUNITY_NAME_MAX &&
            name.all { char -> char.isLetterOrDigit() || char == '_' }
    }

private fun normalizedRedditUsernameTargetName(value: String): String? = value
    .trim()
    .removePrefix("u/")
    .removePrefix("/u/")
    .removePrefix("user/")
    .removePrefix("/user/")
    .takeIf { name ->
        // ISSUE46_REDDIT_USERNAME_LENGTH_V1: Reddit names are 3..20
        // characters; "me" is Reddit's own-profile alias (user/me).
        (name.equals(REDDIT_OWN_PROFILE_ALIAS, ignoreCase = true) ||
            name.length in REDDIT_USERNAME_MIN..REDDIT_USERNAME_MAX) &&
            name.all { char ->
                char.isLetterOrDigit() || char == '_' || char == '-'
            }
    }

private fun validRedditThingId(value: String): Boolean =
    value.isNotEmpty() && value.all { char -> char.isLetterOrDigit() }

private val REDDIT_WEB_SCHEMES = setOf("http", "https")
private val REDDIT_WEB_HOSTS = setOf(
    "reddit.com",
    "www.reddit.com",
    "old.reddit.com",
    "new.reddit.com",
    "np.reddit.com",
    "m.reddit.com",
)
private const val REDDIT_ACTION_VIEW = "android.intent.action.VIEW"
private const val REDDIT_SHORT_HOST = "redd.it"
private const val REDDIT_ROOT_HOST = "reddit.com"
private const val REDDIT_VIDEO_HOST = "v.redd.it"
private const val REDDIT_IMAGE_HOST = "i.redd.it"
private const val REDDIT_PREVIEW_HOST = "preview.redd.it"
private const val REDDIT_EXTERNAL_PREVIEW_HOST = "external-preview.redd.it"
private val REDDIT_STILL_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val REDDIT_APP_LINK_HOST = "reddit.app.link"
private const val REDDIT_APP_LINK_REDIRECT_PARAMETER = "\$og_redirect"
private const val REDDIT_MAIL_CLICK_HOST = "click.redditmail.com"
private const val REDDIT_MAIL_CLICK_MARKER = "CL0"
private const val GOOGLE_AMP_PREFIX = "/amp/s/"
private val REDDIT_PATH_VIEW_SUFFIXES = listOf(".json", ".xml", ".compact")
private val REDDIT_COMMUNITY_PAGE_SEGMENTS = setOf("about")
private val REDDIT_COMMUNITY_WIKI_SEGMENTS = setOf("wiki", "w")
private const val REDDIT_COMMUNITY_NAME_MAX = 21
private const val REDDIT_USERNAME_MAX = 20
private const val REDDIT_USERNAME_MIN = 3
internal const val REDDIT_OWN_PROFILE_ALIAS = "me"
