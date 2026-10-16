package app.subshell.prototype.ui

import java.nio.charset.StandardCharsets
import java.util.Base64

internal sealed interface FeedPostOrigin {
    data class MainFeed(
        val destination: MainDestination,
        val source: FeedSource,
        val sort: FeedSort,
    ) : FeedPostOrigin

    data class Community(
        val destination: MainDestination,
        val communityName: String,
        val sort: CommunitySort,
    ) : FeedPostOrigin

    data class Search(
        val destination: MainDestination,
        val originCommunity: String?,
        val query: String,
        val scope: SearchScope,
        val area: SearchArea,
    ) : FeedPostOrigin

    data class User(
        val destination: MainDestination,
        val username: String,
    ) : FeedPostOrigin

    data object Inbox : FeedPostOrigin

    data class Secondary(
        val destination: MainDestination,
        val secondaryDestination: SecondaryDestination,
    ) : FeedPostOrigin
}

internal enum class FeedPostEntryTarget {
    TOP,
    COMMENTS,
}

internal data class FeedPostRoute(
    val postId: String,
    val focusedCommentId: String?,
    val visitId: Int,
    val origin: FeedPostOrigin,
    val entryTarget: FeedPostEntryTarget = FeedPostEntryTarget.TOP,
)

private const val FEED_POST_ORIGIN_VERSION = "v1"
private const val FEED_POST_ORIGIN_SEPARATOR = '|'

internal fun encodeFeedPostOrigin(origin: FeedPostOrigin): String {
    val fields = when (origin) {
        is FeedPostOrigin.MainFeed -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "main",
            origin.destination.name,
            origin.source.name,
            origin.sort.name,
        )
        is FeedPostOrigin.Community -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "community",
            origin.destination.name,
            origin.communityName,
            origin.sort.name,
        )
        is FeedPostOrigin.Search -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "search",
            origin.destination.name,
            origin.originCommunity.orEmpty(),
            origin.query,
            origin.scope.name,
            origin.area.name,
        )
        is FeedPostOrigin.User -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "user",
            origin.destination.name,
            origin.username,
        )
        FeedPostOrigin.Inbox -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "inbox",
        )
        is FeedPostOrigin.Secondary -> listOf(
            FEED_POST_ORIGIN_VERSION,
            "secondary",
            origin.destination.name,
            origin.secondaryDestination.name,
        )
    }
    return fields.joinToString(FEED_POST_ORIGIN_SEPARATOR.toString()) { field -> field.toRouteToken() }
}

internal fun decodeFeedPostOrigin(encoded: String?): FeedPostOrigin? {
    val encodedFields = encoded?.split(FEED_POST_ORIGIN_SEPARATOR) ?: return null
    val fields = encodedFields.map { field -> field.fromRouteToken() ?: return null }
    if (fields.firstOrNull() != FEED_POST_ORIGIN_VERSION) return null

    fun destination(index: Int): MainDestination? = fields.getOrNull(index)
        ?.let { name -> MainDestination.entries.firstOrNull { it.name == name } }

    return when (fields.getOrNull(1)) {
        "main" -> FeedPostOrigin.MainFeed(
            destination = destination(2) ?: return null,
            source = fields.getOrNull(3)
                ?.let { name -> FeedSource.entries.firstOrNull { it.name == name } }
                ?: return null,
            sort = fields.getOrNull(4)
                ?.let { name -> FeedSort.entries.firstOrNull { it.name == name } }
                ?: return null,
        )
        "community" -> FeedPostOrigin.Community(
            destination = destination(2) ?: return null,
            communityName = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return null,
            sort = fields.getOrNull(4)
                ?.let(::decodeCommunitySortName)
                ?: return null,
        )
        "search" -> FeedPostOrigin.Search(
            destination = destination(2) ?: return null,
            originCommunity = fields.getOrNull(3)?.takeIf(String::isNotBlank),
            query = fields.getOrNull(4).orEmpty(),
            scope = fields.getOrNull(5)
                ?.let { name -> SearchScope.entries.firstOrNull { it.name == name } }
                ?: return null,
            area = fields.getOrNull(6)
                ?.let { name -> SearchArea.entries.firstOrNull { it.name == name } }
                ?: return null,
        )
        "user" -> FeedPostOrigin.User(
            destination = destination(2) ?: return null,
            username = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return null,
        )
        "inbox" -> FeedPostOrigin.Inbox
        "secondary" -> FeedPostOrigin.Secondary(
            destination = destination(2) ?: return null,
            secondaryDestination = fields.getOrNull(3)
                ?.let { name -> SecondaryDestination.entries.firstOrNull { it.name == name } }
                ?: return null,
        )
        else -> null
    }
}

private fun String.toRouteToken(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray(StandardCharsets.UTF_8))

private fun String.fromRouteToken(): String? = runCatching {
    String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}.getOrNull()
