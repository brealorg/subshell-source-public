package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.auth.pickRandomNsfwCommunity
import app.subshell.prototype.data.account.NsfwAccountPreferences
import app.subshell.prototype.data.account.NsfwPreferencesResult
import app.subshell.prototype.data.auth.AuthenticatedRedditCommunityDiscoveryTarget
import app.subshell.prototype.data.error.failureDetailsOrNull
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.data.network.forbiddenReasonFromBody
import app.subshell.prototype.model.Community
import app.subshell.prototype.model.CommunityDiscoveryItem
import app.subshell.prototype.model.CommunityDiscoverySort
import app.subshell.prototype.ui.app.nsfwAccountPreferenceNeedsNotice
import app.subshell.prototype.ui.error.CommunityForbiddenReason
import app.subshell.prototype.ui.error.ErrorActionKind
import app.subshell.prototype.ui.error.communityForbiddenPresentation
import app.subshell.prototype.ui.error.communityForbiddenReason
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #211: NSFW content follows "Show NSFW posts" — searches and discovery send
 * include_over_18 (ISSUE211_SEARCH_FOLLOWS_SHOW_NSFW_V1), the account's own
 * over_18 is read (ISSUE211_ACCOUNT_NSFW_PREFERENCE_V1), a 403 says why
 * (ISSUE211_FORBIDDEN_REASON_IS_READ_V1 / ..._COMMUNITY_SAYS_WHY_V1), a
 * quarantined community can be opted into (ISSUE211_QUARANTINE_OPT_IN_V1) and
 * a random NSFW community is picked from Reddit's listings
 * (ISSUE211_RANDOM_NSFW_COMMUNITY_V1).
 */
class Issue211NsfwContentTest {
    private fun param(request: JsonGetRequest, name: String): String? =
        request.queryParameters.firstOrNull { (key, _) -> key == name }?.second

    @Test
    fun postSearchSendsIncludeOver18FollowingTheSetting() {
        assertEquals("on", param(redditSearchJsonRequest("cats", includeOver18 = true), "include_over_18"))
        assertEquals("off", param(redditSearchJsonRequest("cats", includeOver18 = false), "include_over_18"))
        assertNull(param(redditSearchJsonRequest("cats"), "include_over_18"))
    }

    @Test
    fun popularAndFullCommunitySearchSendIncludeOver18() {
        val popular = redditCommunityDiscoveryJsonRequest(null, null, null, null, 25, listingIncludeOver18 = true)
        val search = redditCommunityDiscoveryJsonRequest("cats", CommunityDiscoverySort.RELEVANCE, null, null, 25, listingIncludeOver18 = false)
        assertEquals("on", param(popular, "include_over_18"))
        assertEquals("off", param(search, "include_over_18"))
        assertNull(param(redditCommunityDiscoveryJsonRequest(null, null, null, null, 25), "include_over_18"))
        // A listing fetched with NSFW keeps its own cache key; the others are unchanged.
        assertEquals("popular|nsfw", AuthenticatedRedditCommunityDiscoveryTarget.popular(true).cacheToken)
        assertEquals("popular", AuthenticatedRedditCommunityDiscoveryTarget.popular(false).cacheToken)
        assertEquals("popular", AuthenticatedRedditCommunityDiscoveryTarget.Popular.cacheToken)
    }

    @Test
    fun theAccountPreferenceIsReadStrictlyAndOnlyOffAsksForANotice() {
        val json = Json
        val on = parseNsfwPreferences(json.parseToJsonElement("""{"over_18":true,"search_include_over_18":false}"""))
        assertEquals(NsfwPreferencesResult.Known(NsfwAccountPreferences(true, false)), on)
        val off = parseNsfwPreferences(json.parseToJsonElement("""{"over_18":false}"""))
        assertTrue(nsfwAccountPreferenceNeedsNotice(off))
        assertFalse(nsfwAccountPreferenceNeedsNotice(on))
        assertEquals(NsfwPreferencesResult.Unavailable, parseNsfwPreferences(json.parseToJsonElement("""{"over_18":"false"}""")))
        assertFalse(nsfwAccountPreferenceNeedsNotice(NsfwPreferencesResult.Failed))
        assertEquals("over_18,search_include_over_18", param(nsfwPreferencesGetRequest(), "fields"))
    }

    @Test
    fun aForbiddenAnswerCarriesRedditsReason() = runBlocking {
        assertEquals("private", forbiddenReasonFromBody("""{"reason": "private", "message": "Forbidden", "error": 403}"""))
        assertNull(forbiddenReasonFromBody("<html>nope</html>"))
        assertNull(forbiddenReasonFromBody("""{"reason": "no spaces allowed here"}"""))

        val client = SubshellHttpClient(
            callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(403).message("Forbidden")
                    .body("""{"reason": "quarantined", "error": 403}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build(),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        )
        val result = client.getJson(JsonGetRequest(pathSegments = listOf("r", "x", "hot")), JsonElement.serializer())
        assertEquals("quarantined", (result as NetworkResult.HttpFailure).providerReason)
        val details = result.failureDetailsOrNull()
        assertEquals(CommunityForbiddenReason.QUARANTINED, communityForbiddenReason(details))
    }

    @Test
    fun aForbiddenCommunitySaysWhyAndOnlyQuarantineOffersAnAction() {
        val privateNotice = communityForbiddenPresentation(CommunityForbiddenReason.PRIVATE, "r/x", showingCachedContent = false)
        assertEquals("r/x is private", privateNotice.title)
        assertNull(privateNotice.primaryAction)
        val quarantine = communityForbiddenPresentation(CommunityForbiddenReason.QUARANTINED, "r/x", showingCachedContent = false)
        assertEquals("View anyway", quarantine.primaryAction?.label)
        assertEquals(ErrorActionKind.RETRY, quarantine.primaryAction?.kind)
        assertNull(communityForbiddenReason(null))
        // A banned community answers 404 with reason banned.
        val banned = app.subshell.prototype.data.error.FailureDetails(
            httpStatusCode = 404,
            providerCode = app.subshell.prototype.data.error.SafeFailureText.fromUntrusted("banned"),
        )
        assertEquals(CommunityForbiddenReason.BANNED, communityForbiddenReason(banned))
        assertEquals(
            "r/x is banned",
            communityForbiddenPresentation(CommunityForbiddenReason.BANNED, "r/x", showingCachedContent = false).title,
        )
    }

    @Test
    fun theQuarantineOptInNamesTheCommunity() {
        val request = quarantineOptInFormRequest("r/Example")
        assertEquals(listOf("api", "quarantine_optin"), request.pathSegments)
        assertEquals(listOf("sr_name" to "Example"), request.fields)
    }

    private fun item(name: String, nsfw: Boolean) = CommunityDiscoveryItem(
        community = Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L),
        nsfw = nsfw,
    )

    @Test
    fun aRandomNsfwCommunityIsPickedOnlyFromNsfwCommunitiesThatOpen() = runBlocking {
        val candidates = listOf(item("safe", false), item("a", true), item("b", true), item("gone", true))
        val checked = mutableListOf<String>()

        repeat(20) {
            val picked = pickRandomNsfwCommunity(candidates, previous = "a", random = Random(it), isAvailable = { name ->
                checked += name
                name != "gone"
            })
            assertEquals("b", picked)
        }
        assertFalse("safe" in checked)
        assertNull(pickRandomNsfwCommunity(listOf(item("safe", false)), null, Random(1), { true }))
        assertNull(pickRandomNsfwCommunity(listOf(item("gone", true)), null, Random(1), { false }))
    }
}
