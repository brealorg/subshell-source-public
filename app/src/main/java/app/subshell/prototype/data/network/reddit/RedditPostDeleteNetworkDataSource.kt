package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.normalizePostEditFullname
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.network.NetworkResponseMetadata
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.NetworkTransportFailureKind
import app.subshell.prototype.data.network.SubshellHttpClient

internal sealed interface RedditPostDeleteNetworkResult {
    data class Deleted(val metadata: NetworkResponseMetadata) : RedditPostDeleteNetworkResult
    data class HttpFailure(
        val statusCode: Int,
        val retryable: Boolean,
        val metadata: NetworkResponseMetadata,
        val providerReason: String? = null,
    ) : RedditPostDeleteNetworkResult
    data class TransportFailure(
        val kind: NetworkTransportFailureKind,
        val retryable: Boolean,
    ) : RedditPostDeleteNetworkResult
    data class ProtocolFailure(
        val kind: NetworkProtocolFailureKind,
        val jsonPath: String? = null,
    ) : RedditPostDeleteNetworkResult
}

// H3_POST_DELETE_READBACK_V1: whether Reddit still shows the post as its author's.
internal sealed interface RedditPostDeleteReadback {
    data class Deleted(val metadata: NetworkResponseMetadata) : RedditPostDeleteReadback
    data object Present : RedditPostDeleteReadback
    data object Unreadable : RedditPostDeleteReadback
}

internal class RedditPostDeleteNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun readBack(postFullname: String): RedditPostDeleteReadback {
        val target = normalizePostEditFullname(postFullname)
        return when (
            // ISSUE281_READS_AVOID_API_INFO_V1: read back from the post's thread.
            val result = httpClient.getJson(
                request = redditPostReadRequest(target),
                deserializer = redditThreadListing(RedditListingDto.serializer(), REDDIT_THREAD_POST_LISTING),
            )
        ) {
            is NetworkResult.Success -> {
                val children = result.value.data.children
                val matches = children.filter { thing ->
                    val name = thing.data.name?.trim()?.lowercase()
                        ?: thing.data.id.trim().lowercase().takeIf(String::isNotEmpty)?.let { id -> "t3_$id" }
                    name == target
                }
                val author = matches.singleOrNull()?.data?.author?.trim()
                when {
                    children.isEmpty() -> RedditPostDeleteReadback.Deleted(result.metadata)
                    matches.size != 1 -> RedditPostDeleteReadback.Unreadable
                    author.isNullOrEmpty() || author.equals("[deleted]", ignoreCase = true) ->
                        RedditPostDeleteReadback.Deleted(result.metadata)
                    else -> RedditPostDeleteReadback.Present
                }
            }
            // A thread Reddit no longer knows is gone, as an empty /api/info answer was.
            is NetworkResult.HttpFailure -> if (result.statusCode == 404) {
                RedditPostDeleteReadback.Deleted(result.metadata)
            } else {
                RedditPostDeleteReadback.Unreadable
            }
            else -> RedditPostDeleteReadback.Unreadable
        }
    }

    suspend fun delete(postFullname: String): RedditPostDeleteNetworkResult = when (
        val result = httpClient.postFormStatus(redditPostDeleteFormRequest(postFullname))
    ) {
        is NetworkResult.Success -> RedditPostDeleteNetworkResult.Deleted(result.metadata)
        is NetworkResult.HttpFailure -> RedditPostDeleteNetworkResult.HttpFailure(
            result.statusCode,
            result.retryable,
            result.metadata,
            result.providerReason,
        )
        is NetworkResult.TransportFailure -> RedditPostDeleteNetworkResult.TransportFailure(
            result.kind,
            result.retryable,
        )
        is NetworkResult.ProtocolFailure -> RedditPostDeleteNetworkResult.ProtocolFailure(result.kind, result.jsonPath)
    }
}

internal fun redditPostDeleteFormRequest(postFullname: String): FormPostRequest = FormPostRequest(
    pathSegments = listOf("api", "del"),
    fields = listOf("id" to normalizePostEditFullname(postFullname)),
)
