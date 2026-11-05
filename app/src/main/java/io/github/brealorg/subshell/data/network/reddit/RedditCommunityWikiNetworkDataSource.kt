package io.github.brealorg.subshell.data.network.reddit

import io.github.brealorg.subshell.data.normalizeRedditCommunityName
import io.github.brealorg.subshell.data.network.JsonGetRequest
import io.github.brealorg.subshell.data.network.NetworkResult
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.map
import io.github.brealorg.subshell.model.CommunityWikiIndex
import io.github.brealorg.subshell.model.CommunityWikiPage
import io.github.brealorg.subshell.model.CommunityWikiPageRef
import io.github.brealorg.subshell.model.normalizeCommunityWikiPagePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// WIKI01A1_TYPED_COMMUNITY_WIKI_TRANSPORT_V1
internal interface RedditCommunityWikiSource {
    suspend fun index(
        communityName: String,
    ): NetworkResult<CommunityWikiIndex>

    suspend fun page(
        communityName: String,
        pagePath: String,
    ): NetworkResult<CommunityWikiPage>
}

internal class RedditCommunityWikiNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) : RedditCommunityWikiSource {
    override suspend fun index(
        communityName: String,
    ): NetworkResult<CommunityWikiIndex> =
        httpClient.getJson(
            request = redditCommunityWikiIndexRequest(communityName),
            deserializer = RedditCommunityWikiPagesDto.serializer(),
        ).map { dto ->
            dto.toCommunityWikiIndex(communityName)
        }

    override suspend fun page(
        communityName: String,
        pagePath: String,
    ): NetworkResult<CommunityWikiPage> {
        val normalizedPath = normalizeCommunityWikiPagePath(pagePath)
        return httpClient.getJson(
            request = redditCommunityWikiPageRequest(
                communityName = communityName,
                pagePath = normalizedPath,
            ),
            deserializer = RedditCommunityWikiPageDto.serializer(),
        ).map { dto ->
            dto.toCommunityWikiPage(
                communityName = communityName,
                pagePath = normalizedPath,
            )
        }
    }
}

internal fun redditCommunityWikiIndexRequest(
    communityName: String,
): JsonGetRequest =
    JsonGetRequest(
        pathSegments = listOf(
            "r",
            normalizeRedditCommunityName(communityName),
            "wiki",
            "pages",
        ),
        queryParameters = listOf("raw_json" to "1"),
    )

internal fun redditCommunityWikiPageRequest(
    communityName: String,
    pagePath: String,
): JsonGetRequest {
    val normalizedCommunity = normalizeRedditCommunityName(communityName)
    val normalizedPath = normalizeCommunityWikiPagePath(pagePath)
    return JsonGetRequest(
        pathSegments =
            listOf("r", normalizedCommunity, "wiki") +
                normalizedPath.split('/'),
        queryParameters = listOf("raw_json" to "1"),
    )
}

@Serializable
internal data class RedditCommunityWikiPagesDto(
    val kind: String? = null,
    val data: List<String?> = emptyList(),
)

@Serializable
internal data class RedditCommunityWikiPageDto(
    val kind: String? = null,
    val data: RedditCommunityWikiPageDataDto = RedditCommunityWikiPageDataDto(),
)

@Serializable
internal data class RedditCommunityWikiPageDataDto(
    @SerialName("content_md")
    val contentMarkdown: String? = null,
    @SerialName("revision_id")
    val revisionId: String? = null,
    @SerialName("revision_date")
    val revisionDate: Double? = null,
    @SerialName("revision_by")
    val revisionBy: RedditCommunityWikiRevisionByDto? = null,
    val reason: String? = null,
)

@Serializable
internal data class RedditCommunityWikiRevisionByDto(
    val kind: String? = null,
    val data: RedditCommunityWikiRevisionByDataDto =
        RedditCommunityWikiRevisionByDataDto(),
)

@Serializable
internal data class RedditCommunityWikiRevisionByDataDto(
    val name: String? = null,
)

internal fun RedditCommunityWikiPagesDto.toCommunityWikiIndex(
    communityName: String,
): CommunityWikiIndex {
    val seen = mutableSetOf<String>()
    val pages = data.asSequence()
        .mapNotNull { raw ->
            raw?.let { value ->
                runCatching {
                    normalizeCommunityWikiPagePath(value)
                }
                    .getOrNull()
                    ?.takeIf { path -> seen.add(path.lowercase()) }
                    ?.let(::CommunityWikiPageRef)
            }
        }
        .take(MAX_COMMUNITY_WIKI_PAGES)
        .toList()

    return CommunityWikiIndex(
        communityName = normalizeRedditCommunityName(communityName),
        pages = pages,
    )
}

internal fun RedditCommunityWikiPageDto.toCommunityWikiPage(
    communityName: String,
    pagePath: String,
): CommunityWikiPage =
    CommunityWikiPage(
        communityName = normalizeRedditCommunityName(communityName),
        path = normalizeCommunityWikiPagePath(pagePath),
        markdown = data.contentMarkdown
            ?.trim()
            ?.take(MAX_COMMUNITY_WIKI_MARKDOWN_CHARS)
            .orEmpty(),
        revisionId = data.revisionId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_COMMUNITY_WIKI_REVISION_ID_CHARS),
        revisionDateEpochSeconds = data.revisionDate
            ?.takeIf { value -> value.isFinite() && value >= 0.0 }
            ?.toLong(),
        revisionAuthor = data.revisionBy
            ?.data
            ?.name
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_REDDIT_USERNAME_CHARS),
        revisionReason = data.reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_COMMUNITY_WIKI_REVISION_REASON_CHARS),
    )

private const val MAX_COMMUNITY_WIKI_PAGES = 500
private const val MAX_COMMUNITY_WIKI_MARKDOWN_CHARS = 500_000
private const val MAX_COMMUNITY_WIKI_REVISION_ID_CHARS = 128
private const val MAX_COMMUNITY_WIKI_REVISION_REASON_CHARS = 512
private const val MAX_REDDIT_USERNAME_CHARS = 100
