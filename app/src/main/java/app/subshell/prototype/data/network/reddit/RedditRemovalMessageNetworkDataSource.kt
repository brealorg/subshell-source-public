package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditRemovalMessage
import app.subshell.prototype.data.network.FormPostRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal sealed interface RedditRemovalMessageNetworkResult {
    data object Sent : RedditRemovalMessageNetworkResult
    data class HttpFailure(val statusCode: Int, val retryable: Boolean) : RedditRemovalMessageNetworkResult
    data class TransportFailure(val retryable: Boolean) : RedditRemovalMessageNetworkResult
    data object ProtocolFailure : RedditRemovalMessageNetworkResult
}

// MODRESPONSE01A1_REMOVAL_MESSAGE_TRANSPORT_V1
/**
 * `POST /api/v1/modactions/removal_link_message` (posts) and
 * `…/removal_comment_message` (comments), the same `api_type=json&json=…`
 * form the removal-reason attachment uses. Reddit requires the item to be
 * removed first and adds the object information to the message itself. The
 * public types answer with the created comment, the private ones with `{}`;
 * neither body is needed, so only the status is read.
 */
internal class RedditRemovalMessageNetworkDataSource(
    private val httpClient: SubshellHttpClient,
) {
    suspend fun send(
        thingFullname: String,
        message: RedditRemovalMessage,
    ): RedditRemovalMessageNetworkResult {
        val request = redditRemovalMessageRequest(thingFullname, message)
        return when (val result = httpClient.postFormStatus(request)) {
            is NetworkResult.Success -> RedditRemovalMessageNetworkResult.Sent
            is NetworkResult.HttpFailure -> RedditRemovalMessageNetworkResult.HttpFailure(
                statusCode = result.statusCode,
                retryable = result.retryable,
            )
            is NetworkResult.TransportFailure -> RedditRemovalMessageNetworkResult.TransportFailure(
                retryable = result.retryable,
            )
            is NetworkResult.ProtocolFailure -> RedditRemovalMessageNetworkResult.ProtocolFailure
        }
    }
}

internal fun redditRemovalMessageRequest(
    thingFullname: String,
    message: RedditRemovalMessage,
): FormPostRequest {
    val normalizedFullname = thingFullname.trim().lowercase()
    require(normalizedFullname.matches(Regex("t[13]_[a-z0-9]+")))
    val endpoint = if (normalizedFullname.startsWith("t3_")) {
        "removal_link_message"
    } else {
        "removal_comment_message"
    }
    val json = buildJsonObject {
        putJsonArray("item_id") { add(normalizedFullname) }
        put("message", message.body.trim())
        put("title", message.title.trim())
        put("type", message.type.apiValue)
    }.toString()
    return FormPostRequest(
        pathSegments = listOf("api", "v1", "modactions", endpoint),
        fields = listOf(
            "api_type" to "json",
            "json" to json,
        ),
    )
}
