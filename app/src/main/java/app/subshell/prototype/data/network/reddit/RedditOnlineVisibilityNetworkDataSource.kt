package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.account.OnlineVisibilityIssue
import app.subshell.prototype.data.account.OnlineVisibilityResult
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.JsonPatchRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface OnlineVisibilityNetwork {
    suspend fun read(): OnlineVisibilityResult
    suspend fun patch(value: Boolean): OnlineVisibilityResult
}

/** Only show_presence crosses this boundary. No complete preferences are retained. */
internal class RedditOnlineVisibilityNetworkDataSource(
    private val client: SubshellHttpClient,
) : OnlineVisibilityNetwork {
    override suspend fun read(): OnlineVisibilityResult = client.getJson(
        request = onlineVisibilityGetRequest(),
        deserializer = JsonElement.serializer(),
    ).toOnlineVisibilityResult()

    override suspend fun patch(value: Boolean): OnlineVisibilityResult = client.patchJson(
        request = onlineVisibilityPatchRequest(value),
        serializer = JsonObject.serializer(),
        deserializer = JsonElement.serializer(),
    ).toOnlineVisibilityResult()
}

internal fun onlineVisibilityGetRequest() = JsonGetRequest(
    pathSegments = listOf("api", "v1", "me", "prefs"),
    queryParameters = listOf("fields" to "show_presence"),
)

internal fun onlineVisibilityPatchRequest(value: Boolean) = JsonPatchRequest(
    pathSegments = listOf("api", "v1", "me", "prefs"),
    body = buildJsonObject { put("show_presence", value) },
)

internal fun parseOnlineVisibility(element: JsonElement): OnlineVisibilityResult {
    val value = (element as? JsonObject)?.get("show_presence") as? JsonPrimitive
    // Do not coerce strings, numbers, null or omitted fields into booleans.
    if (value == null || value.isString) return OnlineVisibilityResult.Unavailable
    return value.booleanOrNull?.let(OnlineVisibilityResult::Known)
        ?: OnlineVisibilityResult.Unavailable
}

internal fun NetworkResult<JsonElement>.toOnlineVisibilityResult(): OnlineVisibilityResult =
    when (this) {
        is NetworkResult.Success -> parseOnlineVisibility(value)
        is NetworkResult.HttpFailure -> OnlineVisibilityResult.Failed(
            issue = if (statusCode == 401) OnlineVisibilityIssue.AUTHORIZATION
                else OnlineVisibilityIssue.HTTP,
            httpStatusCode = statusCode,
            failureDetails = failureDetailsOrNull(),
        )
        is NetworkResult.TransportFailure ->
            OnlineVisibilityResult.Failed(OnlineVisibilityIssue.TRANSPORT, failureDetails = failureDetailsOrNull())
        is NetworkResult.ProtocolFailure ->
            OnlineVisibilityResult.Failed(OnlineVisibilityIssue.PROTOCOL, failureDetails = failureDetailsOrNull())
    }
