package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.NsfwAccountPreferences
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1 (#211): only over_18 and
// search_include_over_18 are asked for and read; no other preference
// crosses this boundary or is kept.

internal fun interface NsfwPreferencesNetwork {
    suspend fun read(): NsfwPreferencesResult
}

internal class RedditNsfwPreferencesNetworkDataSource(
    private val client: SubshellHttpClient,
) : NsfwPreferencesNetwork {
    override suspend fun read(): NsfwPreferencesResult =
        when (val result = client.getJson(request = nsfwPreferencesGetRequest(), deserializer = JsonElement.serializer())) {
            is NetworkResult.Success -> parseNsfwPreferences(result.value)
            else -> NsfwPreferencesResult.Failed.also {
                app.subshell.prototype.auth.authDebugLog("subshell-nsfw", "account-prefs network ${result.javaClass.simpleName}")
            }
        }
}

internal fun nsfwPreferencesGetRequest() = JsonGetRequest(
    pathSegments = listOf("api", "v1", "me", "prefs"),
    queryParameters = listOf("fields" to "over_18,search_include_over_18"),
)

internal fun parseNsfwPreferences(element: JsonElement): NsfwPreferencesResult {
    val fields = element as? JsonObject ?: return NsfwPreferencesResult.Unavailable
    fun flag(name: String): Boolean? =
        (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
    val over18 = flag("over_18") ?: return NsfwPreferencesResult.Unavailable
    return NsfwPreferencesResult.Known(
        NsfwAccountPreferences(over18 = over18, searchIncludeOver18 = flag("search_include_over_18")),
    )
}

// ISSUE211_QUARANTINE_OPT_IN_V1: the opt-in is its own request (never in the
// listing sources, which never write).
internal fun quarantineOptInFormRequest(communityName: String) = app.subshell.prototype.data.network.FormPostRequest(
    pathSegments = listOf("api", "quarantine_optin"),
    fields = listOf("sr_name" to communityName.trim().removePrefix("r/")),
)

internal suspend fun SubshellHttpClient.sendQuarantineOptIn(
    communityName: String,
): app.subshell.prototype.data.account.QuarantineOptInResult =
    when (val result = postFormStatus(quarantineOptInFormRequest(communityName))) {
        is NetworkResult.Success -> app.subshell.prototype.data.account.QuarantineOptInResult.Sent
        // ISSUE211_QUARANTINE_OPT_IN_REDIRECT_IS_SUCCESS_V1: Reddit answers a
        // successful opt-in with a redirect to the community (302, seen on the
        // owner's phone; `self.redirect('/r/%s')` in Reddit's source), and the
        // client does not follow redirects.
        is NetworkResult.HttpFailure -> if (result.statusCode in 300..399) {
            app.subshell.prototype.data.account.QuarantineOptInResult.Sent
        } else {
            app.subshell.prototype.data.account.QuarantineOptInResult.Refused(result.statusCode)
        }
        is NetworkResult.TransportFailure -> app.subshell.prototype.data.account.QuarantineOptInResult.OutcomeUnknown
        is NetworkResult.ProtocolFailure -> app.subshell.prototype.data.account.QuarantineOptInResult.OutcomeUnknown
    }
