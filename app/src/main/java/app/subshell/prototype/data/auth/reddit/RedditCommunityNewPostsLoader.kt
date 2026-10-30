package app.subshell.prototype.data.auth.reddit

import app.subshell.prototype.data.auth.AuthAccountSessionCoordinator
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityNewPostsLoader
import app.subshell.prototype.data.auth.COMMUNITY_NEW_COMMENTS_PAGE_LIMIT
import app.subshell.prototype.data.auth.COMMUNITY_NEW_POSTS_PAGE_LIMIT
import app.subshell.prototype.data.auth.CommunityComment
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.reddit.RedditListingSort
import app.subshell.prototype.model.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call

// ISSUE239_NEW_POSTS_IN_A_WATCHED_COMMUNITY_V1: the first New page of a
// community, read with the active account's own token. Read only, never cached.
internal class RedditCommunityNewPostsLoader(
    private val callFactory: Call.Factory,
    private val endpoint: NetworkEndpoint,
    private val userAgent: String,
    private val configuration: OAuthProviderConfiguration,
    private val accountCoordinator: AuthAccountSessionCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthenticatedRedditCommunityNewPostsLoader {
    override suspend fun load(accountStableId: String, communityName: String): List<Post>? =
        readCommunityListingWithAccount(
            callFactory = callFactory,
            endpoint = endpoint,
            userAgent = userAgent,
            configuration = configuration,
            accountCoordinator = accountCoordinator,
            ioDispatcher = ioDispatcher,
            accountStableId = accountStableId,
            communityName = communityName,
            sort = RedditListingSort.NEW,
            limit = COMMUNITY_NEW_POSTS_PAGE_LIMIT,
        )

    // ISSUE239_NEW_COMMENTS_IN_A_WATCHED_COMMUNITY_V1: /r/<name>/comments, the
    // community's newest comments, with the same token and guards as the posts.
    override suspend fun loadComments(accountStableId: String, communityName: String): List<CommunityComment>? =
        withContext(ioDispatcher) {
            val client = activeAccountRedditClient(
                callFactory = callFactory,
                endpoint = endpoint,
                userAgent = userAgent,
                configuration = configuration,
                accountCoordinator = accountCoordinator,
                accountStableId = accountStableId,
            ) ?: return@withContext null
            when (
                val result = client.getJson(
                    request = redditCommunityCommentsJsonRequest(communityName),
                    deserializer = RedditCommunityCommentsListingDto.serializer(),
                )
            ) {
                is NetworkResult.Success -> result.value.toCommunityComments()
                else -> null
            }
        }
}

internal fun redditCommunityCommentsJsonRequest(communityName: String): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf("r", communityName.removePrefix("r/"), "comments"),
        queryParameters = listOf(
            "raw_json" to "1",
            "limit" to COMMUNITY_NEW_COMMENTS_PAGE_LIMIT.toString(),
        ),
    )

@Serializable
internal data class RedditCommunityCommentsListingDto(
    val data: RedditCommunityCommentsDataDto = RedditCommunityCommentsDataDto(),
)

@Serializable
internal data class RedditCommunityCommentsDataDto(
    val children: List<RedditCommunityCommentThingDto> = emptyList(),
)

@Serializable
internal data class RedditCommunityCommentThingDto(
    val kind: String? = null,
    val data: RedditCommunityCommentDataDto = RedditCommunityCommentDataDto(),
)

@Serializable
internal data class RedditCommunityCommentDataDto(
    val id: String = "",
    val author: String? = null,
    val body: String = "",
    @SerialName("link_title")
    val linkTitle: String? = null,
    val permalink: String? = null,
    @SerialName("over_18")
    val over18: Boolean = false,
)

/** Comments only; one without an id or author (deleted) is left out. */
internal fun RedditCommunityCommentsListingDto.toCommunityComments(): List<CommunityComment> =
    data.children
        .filter { it.kind == null || it.kind == "t1" }
        .mapNotNull { thing ->
            val comment = thing.data
            val author = comment.author?.trim()?.takeIf { it.isNotEmpty() && it != "[deleted]" }
            if (comment.id.isBlank() || author == null) return@mapNotNull null
            CommunityComment(
                id = comment.id,
                author = author,
                body = comment.body,
                postTitle = comment.linkTitle.orEmpty(),
                permalink = comment.permalink,
                nsfw = comment.over18,
            )
        }
