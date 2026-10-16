package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditRemovalReason
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.map
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal sealed interface RedditRemovalReasonWriteNetworkResult {
    data object Updated : RedditRemovalReasonWriteNetworkResult
    data class HttpFailure(val statusCode: Int, val retryable: Boolean) : RedditRemovalReasonWriteNetworkResult
    data class TransportFailure(val retryable: Boolean) : RedditRemovalReasonWriteNetworkResult
    data object ProtocolFailure : RedditRemovalReasonWriteNetworkResult
}

// MOD01A21_CLASSIC_REDDIT_REMOVAL_REASON_TRANSPORT_V1
internal class RedditRemovalReasonNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun reasons(
        rawCommunityName: String,
    ): NetworkResult<List<RedditRemovalReason>> {
        val communityName = normalizeRedditCommunityName(rawCommunityName)
        return httpClient.getJson(
            request = redditRemovalReasonsRequest(communityName),
            deserializer = RedditRemovalReasonsDto.serializer(),
        ).map(RedditRemovalReasonsDto::toDomain)
    }

    suspend fun attachRemovalReason(
        thingFullname: String,
        reasonId: String?,
        modNote: String?,
    ): RedditRemovalReasonWriteNetworkResult {
        val request = redditAttachRemovalReasonRequest(
            thingFullname = thingFullname,
            reasonId = reasonId,
            modNote = modNote,
        )
        return when (val result = httpClient.postFormStatus(request)) {
            is NetworkResult.Success -> RedditRemovalReasonWriteNetworkResult.Updated
            is NetworkResult.HttpFailure -> RedditRemovalReasonWriteNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
            )
            is NetworkResult.TransportFailure -> RedditRemovalReasonWriteNetworkResult.TransportFailure(
                retryable = result.retryable,
            )
            is NetworkResult.ProtocolFailure -> RedditRemovalReasonWriteNetworkResult.ProtocolFailure
        }
    }
}

internal fun redditRemovalReasonsRequest(
    rawCommunityName: String,
): JsonGetRequest {
    val communityName = normalizeRedditCommunityName(rawCommunityName)
    return JsonGetRequest(
        pathSegments = listOf("api", "v1", communityName, "removal_reasons"),
        queryParameters = listOf("raw_json" to "1"),
    )
}

internal fun redditAttachRemovalReasonRequest(
    thingFullname: String,
    reasonId: String?,
    modNote: String?,
): FormPostRequest {
    require(thingFullname.matches(Regex("t[13]_[a-z0-9]+")))
    val normalizedReasonId = reasonId?.trim().orEmpty()
    val normalizedModNote = modNote?.trim().orEmpty().take(MAX_MOD_NOTE_CHARS)
    val json = buildJsonObject {
        putJsonArray("item_ids") { add(thingFullname) }
        // CORRECT01_MOD_NOTE_ONLY_REASON_ID_NULL_V1 (OCB-639): no reason is
        // JSON null (as PRAW sends), not an empty id.
        put("reason_id", normalizedReasonId.ifEmpty { null })
        put("mod_note", normalizedModNote)
    }.toString()
    return FormPostRequest(
        pathSegments = listOf("api", "v1", "modactions", "removal_reasons"),
        fields = listOf(
            "api_type" to "json",
            "json" to json,
        ),
    )
}

@Serializable
internal data class RedditRemovalReasonsDto(
    val order: List<String> = emptyList(),
    val data: Map<String, RedditRemovalReasonDto> = emptyMap(),
)

@Serializable
internal data class RedditRemovalReasonDto(
    val id: String? = null,
    val title: String? = null,
    val message: String? = null,
)

internal fun RedditRemovalReasonsDto.toDomain(): List<RedditRemovalReason> =
    order.asSequence()
        .mapNotNull { rawId ->
            val mapId = rawId.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val dto = data[rawId] ?: data[mapId] ?: return@mapNotNull null
            val id = dto.id?.trim()?.takeIf(String::isNotEmpty) ?: mapId
            val title = dto.title
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.takeIf(String::isNotEmpty)
                ?.take(MAX_REMOVAL_REASON_TITLE_CHARS)
                ?: return@mapNotNull null
            val message = dto.message
                ?.trim()
                ?.take(MAX_REMOVAL_REASON_MESSAGE_CHARS)
                .orEmpty()
            RedditRemovalReason(id = id, title = title, message = message)
        }
        .distinctBy(RedditRemovalReason::id)
        .take(MAX_REMOVAL_REASONS)
        .toList()

private const val MAX_REMOVAL_REASONS = 100
private const val MAX_REMOVAL_REASON_TITLE_CHARS = 200
private const val MAX_REMOVAL_REASON_MESSAGE_CHARS = 4_000
private const val MAX_MOD_NOTE_CHARS = 100
