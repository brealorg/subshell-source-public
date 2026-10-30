package app.subshell.prototype.data.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.data.auth.EncryptedAuthSessionStore
import app.subshell.prototype.data.auth.OAuthEndpointResult
import app.subshell.prototype.data.auth.OAuthProvider
import app.subshell.prototype.data.auth.OAuthProviderConfiguration
import app.subshell.prototype.data.auth.OAuthTokenEndpointClient
import app.subshell.prototype.data.network.JsonGetRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.NetworkResult
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import app.subshell.prototype.harness.FakeAndroidKeyStore
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE32_REDDIT_REFUSALS_IN_THE_REPORT_V1 (#32): when Reddit starts removing
 * API access (2027-01-12) the first reader report should show how the refusal
 * looks. Every refused API call (401, 403, 429, 451) and every refused sign-in
 * or token refresh is kept as one counted line with Reddit's reason token;
 * nothing else of the request or answer is kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue32RedditRefusalsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val reported = mutableListOf<Triple<String, Int, String?>>()

    @After
    fun uninstall() {
        RedditRefusals.installForTest(null)
    }

    private fun recordInto(list: MutableList<Triple<String, Int, String?>>) {
        RedditRefusals.installForTest { source, status, reason -> list += Triple(source, status, reason) }
    }

    private fun answering(code: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("answer")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun apiCall(code: Int, body: String): NetworkResult<JsonElement> = runBlocking {
        SubshellHttpClient(
            callFactory = answering(code, body),
            endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
            json = SubshellNetworkJson.providerJson,
            userAgent = "subshell-test",
        ).getJson(JsonGetRequest(pathSegments = listOf("api", "v1", "me")), JsonElement.serializer())
    }

    private fun tokenRefresh(code: Int, body: String): OAuthEndpointResult<*> = runBlocking {
        OAuthTokenEndpointClient(
            callFactory = answering(code, body),
            configuration = OAuthProviderConfiguration(
                provider = OAuthProvider.REDDIT,
                authorizationEndpoint = "https://example.test/authorize".toHttpUrl(),
                tokenEndpoint = "https://example.test/token".toHttpUrl(),
                revokeEndpoint = "https://example.test/revoke".toHttpUrl(),
                clientId = "client-id",
                redirectUri = "subshell-auth-test://oauth/callback",
                scopes = setOf("identity"),
                permanentAccess = true,
            ),
            userAgent = "subshell-test/1.0",
        ).refresh("refresh-token")
    }

    @Test
    fun aRefusedApiCallIsReportedWithRedditsReason() {
        recordInto(reported)

        apiCall(403, """{"reason": "app_not_registered", "message": "Forbidden"}""")
        apiCall(401, """{"message": "Unauthorized", "error": 401}""")
        apiCall(429, "")
        apiCall(451, """{"reason": "legal"}""")

        assertEquals(
            listOf(
                Triple(REDDIT_REFUSAL_API, 403, "app_not_registered"),
                Triple(REDDIT_REFUSAL_API, 401, null),
                Triple(REDDIT_REFUSAL_API, 429, null),
                Triple(REDDIT_REFUSAL_API, 451, "legal"),
            ),
            reported,
        )
    }

    @Test
    fun ordinaryFailuresAndAnswersAreNotRefusals() {
        recordInto(reported)

        apiCall(404, """{"reason": "banned"}""")
        apiCall(500, "")
        apiCall(400, """{"reason": "bad"}""")
        apiCall(200, """{"name": "me"}""")

        assertTrue(reported.isEmpty())
    }

    @Test
    fun aRefusedSignInOrRefreshIsReportedWithItsOAuthError() {
        recordInto(reported)

        val refused = tokenRefresh(401, """{"error": "invalid_client"}""")
        // Reddit answers a revoked refresh token with 200 and an error (AUTH12).
        val revoked = tokenRefresh(200, """{"error": "invalid_grant"}""")
        tokenRefresh(200, """{"access_token":"a","token_type":"bearer","expires_in":3600,"scope":"identity"}""")

        assertTrue(refused is OAuthEndpointResult.HttpFailure)
        assertTrue(revoked is OAuthEndpointResult.HttpFailure)
        assertEquals(
            listOf(
                Triple(REDDIT_REFUSAL_TOKEN, 401, "invalid_client"),
                Triple(REDDIT_REFUSAL_TOKEN, 200, "invalid_grant"),
            ),
            reported,
        )
    }

    @Test
    fun theStoreCountsEachKindOnceAndKeepsOnlySafeTokens() {
        val directory = Files.createTempDirectory("issue32-refusals").toFile()
        val store = RedditRefusalStore(directory)

        store.record(REDDIT_REFUSAL_API, 403, "app_not_registered", atEpochMs = 1_000)
        store.record(REDDIT_REFUSAL_API, 403, "app_not_registered", atEpochMs = 5_000)
        store.record(REDDIT_REFUSAL_TOKEN, 401, "invalid_client", atEpochMs = 3_000)
        store.record(REDDIT_REFUSAL_API, 403, "Not A Token https://x", atEpochMs = 4_000)
        store.record("matrix", 403, null, atEpochMs = 6_000)

        val refusals = RedditRefusalStore(directory).read()
        assertEquals(
            listOf(
                RedditRefusal(REDDIT_REFUSAL_API, 403, "app_not_registered", 2, 1_000, 5_000),
                RedditRefusal(REDDIT_REFUSAL_API, 403, null, 1, 4_000, 4_000),
                RedditRefusal(REDDIT_REFUSAL_TOKEN, 401, "invalid_client", 1, 3_000, 3_000),
            ),
            refusals,
        )

        repeat(20) { store.record(REDDIT_REFUSAL_API, 403, "reason_$it", atEpochMs = 10_000L + it) }
        val kept = store.read()
        assertEquals(16, kept.size)
        assertEquals("reason_19", kept.first().reason)

        store.clear()
        assertTrue(store.read().isEmpty())
        directory.deleteRecursively()
    }

    @Test
    fun theReportShowsTheClientModeAndEachRefusalWithoutValues() {
        FakeAndroidKeyStore.install()
        try {
            val controller = RedditCompatibilityConfigurationController(context)
            assertEquals("none", controller.oauthClientModeDiagnosticToken())
            controller.save(CLIENT_ID)
            val mode = controller.oauthClientModeDiagnosticToken()

            val report = buildDiagnosticReport(
                snapshot = DiagnosticSnapshot(events = emptyList()),
                environment = ENVIRONMENT,
                oauthClientMode = mode,
                redditRefusals = listOf(
                    RedditRefusal(REDDIT_REFUSAL_TOKEN, 401, "invalid_client", 3, 1_767_225_600_000, 1_767_229_200_000),
                    RedditRefusal(REDDIT_REFUSAL_API, 403, null, 1, 1_767_225_600_000, 1_767_225_600_000),
                ),
            )

            val section = report.lines().dropWhile { it != "[reddit_refusals]" }.takeWhile { it.isNotBlank() }
            assertEquals(
                listOf(
                    "[reddit_refusals]",
                    "oauth_client_mode=redreader_compatibility",
                    "token http=401 reason=invalid_client count=3 first=2026-01-01T00:00:00.000Z last=2026-01-01T01:00:00.000Z",
                    "api http=403 count=1 first=2026-01-01T00:00:00.000Z last=2026-01-01T00:00:00.000Z",
                ),
                section,
            )
            assertFalse(report.contains(CLIENT_ID))

            val empty = buildDiagnosticReport(
                snapshot = DiagnosticSnapshot(events = emptyList()),
                environment = ENVIRONMENT,
                oauthClientMode = "none",
                redditRefusals = emptyList(),
            )
            assertTrue(empty.lines().contains("refusals=none"))
            // Older callers leave the section out.
            assertFalse(
                buildDiagnosticReport(DiagnosticSnapshot(events = emptyList()), ENVIRONMENT).contains("[reddit_refusals]"),
            )
        } finally {
            EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
            FakeAndroidKeyStore.uninstall()
        }
    }

    private companion object {
        // Made-up identifier in Reddit's installed-app format, not a real one.
        const val CLIENT_ID = "Issue32_Client-Id01"
        val ENVIRONMENT = DiagnosticReportEnvironment(
            appVersionName = "0.87-beta",
            appVersionCode = 251,
            buildType = "debug",
            debugSlot = "dev1",
            androidApi = 36,
            androidRelease = "16",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 6",
            screenWidthDp = 411,
            screenHeightDp = 891,
            smallestScreenWidthDp = 411,
        )
    }
}
