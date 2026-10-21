package app.subshell.prototype.data.auth

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.auth.LoginNavigation
import app.subshell.prototype.auth.RedditCompatibilityConfigurationController
import app.subshell.prototype.auth.clientIdFromPaste
import app.subshell.prototype.auth.loginCallbackUriOf
import app.subshell.prototype.auth.loginNavigationFor
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.auth.reddit.RedditOAuth
import app.subshell.prototype.data.diagnostics.DiagnosticReportEnvironment
import app.subshell.prototype.data.diagnostics.DiagnosticSnapshot
import app.subshell.prototype.data.diagnostics.buildDiagnosticReport
import app.subshell.prototype.harness.FakeAndroidKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #240 (ISSUE240_READER_EDITS_THE_REDDIT_API_VALUES_V1): the reader may change
 * the redirect URI and user agent RedReader compatibility signs in with. They
 * stay in the encrypted no-backup client file (a file written before #240
 * reads as the defaults), the runtime and the login window use them, invalid
 * values are never stored, Reset brings the shipped ones back with the client
 * ID kept, a new client ID leaves the old accounts needing a new sign-in, and
 * diagnostics say only whether the values were changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue240RedditApiValuesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun installKeyStore() {
        FakeAndroidKeyStore.install()
    }

    @After
    fun uninstallKeyStore() {
        EncryptedAuthSessionStore(context).deleteOAuthClientSelection()
        FakeAndroidKeyStore.uninstall()
    }

    private fun runtimeFor(selection: StoredOAuthClientSelection) =
        requireNotNull(
            RedditOAuth.selectRuntimeConfiguration(
                storedSelection = selection,
                nativeConfigured = false,
                nativeClientId = "",
                nativeRedirectUri = "",
                nativeUserAgent = "native-test/1.0",
            ),
        )

    @Test
    fun aClientFileFromBeforeIssue240ReadsAsTheShippedValues() {
        val legacy = """{"provider":"REDDIT","mode":"REDREADER_COMPATIBILITY","clientId":"$CLIENT_ID"}"""

        val selection = Json.decodeFromString(StoredOAuthClientSelection.serializer(), legacy)
        val runtime = runtimeFor(selection)

        assertNull(selection.redirectUri)
        assertNull(selection.userAgent)
        assertEquals(RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI, runtime.configuration.redirectUri)
        assertEquals(RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT, runtime.userAgent)
    }

    @Test
    fun savedValuesReachTheRuntimeAndTheAuthorizationAndSurviveARestart() {
        RedditCompatibilityConfigurationController(context)
            .save(CLIENT_ID, redirectUri = CUSTOM_REDIRECT, userAgent = CUSTOM_USER_AGENT)

        val restarted = RedditCompatibilityConfigurationController(context)
        val values = restarted.apiValues()
        val runtime = runtimeFor(requireNotNull(EncryptedAuthSessionStore(context).oauthClientSelection()))
        val authorization = runtime.configuration.authorizationUrl(state = "s".repeat(32))

        assertTrue(values.clientIdConfigured)
        assertTrue(values.customized)
        assertEquals(CUSTOM_REDIRECT, values.redirectUri)
        assertEquals(CUSTOM_USER_AGENT, values.userAgent)
        assertEquals(CUSTOM_REDIRECT, runtime.configuration.redirectUri)
        assertEquals(CUSTOM_USER_AGENT, runtime.userAgent)
        assertEquals(CUSTOM_REDIRECT, authorization.queryParameter("redirect_uri"))
    }

    @Test
    fun theShippedValuesAreStoredAsDefaultsAndResetKeepsTheClientId() {
        val controller = RedditCompatibilityConfigurationController(context)
        controller.save(
            CLIENT_ID,
            redirectUri = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            userAgent = RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
        )
        val stored = requireNotNull(EncryptedAuthSessionStore(context).oauthClientSelection())
        assertNull("a shipped value is not a changed one", stored.redirectUri)
        assertNull(stored.userAgent)
        assertFalse(controller.apiValues().customized)

        controller.save(CLIENT_ID, redirectUri = CUSTOM_REDIRECT, userAgent = CUSTOM_USER_AGENT)
        controller.resetApiValues()

        val reset = requireNotNull(EncryptedAuthSessionStore(context).oauthClientSelection())
        assertEquals(CLIENT_ID, reset.clientId)
        assertNull(reset.redirectUri)
        assertNull(reset.userAgent)
        assertFalse(controller.apiValues().customized)
    }

    @Test
    fun aBlankClientIdKeepsTheStoredOneAndAPastedEmailLineKeepsOnlyTheValue() {
        val controller = RedditCompatibilityConfigurationController(context)
        controller.save("App ID: $CLIENT_ID")
        assertEquals(CLIENT_ID, EncryptedAuthSessionStore(context).oauthClientSelection()?.clientId)

        controller.save(clientId = "  ", redirectUri = CUSTOM_REDIRECT, userAgent = null)

        val stored = requireNotNull(EncryptedAuthSessionStore(context).oauthClientSelection())
        assertEquals(CLIENT_ID, stored.clientId)
        assertEquals(CUSTOM_REDIRECT, stored.redirectUri)
        assertEquals(CLIENT_ID, clientIdFromPaste("client id = $CLIENT_ID"))
        assertTrue(controller.isValidClientId("App ID: $CLIENT_ID"))
        assertFalse(controller.changesClientId(CLIENT_ID))
        assertTrue(controller.changesClientId(OTHER_CLIENT_ID))
    }

    @Test
    fun invalidValuesAreNeverStored() {
        val controller = RedditCompatibilityConfigurationController(context)
        controller.save(CLIENT_ID)
        val invalidRedirects = listOf(
            "redreader",
            "rr_oauth_redir",
            "https://www.reddit.com/callback",
            "http://localhost/callback",
            "my app://callback",
            "myapp://",
            "myapp://call back",
        )
        val invalidUserAgents = listOf("", "   ", "subshell/1.0 ✓", "line\nbreak", "x".repeat(513))

        invalidRedirects.forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                controller.save(CLIENT_ID, redirectUri = value)
            }
        }
        invalidUserAgents.forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                controller.save(CLIENT_ID, userAgent = value)
            }
        }
        val stored = requireNotNull(EncryptedAuthSessionStore(context).oauthClientSelection())
        assertNull(stored.redirectUri)
        assertNull(stored.userAgent)
        assertThrows(IllegalArgumentException::class.java) {
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.NATIVE_PRODUCTION,
                userAgent = CUSTOM_USER_AGENT,
            )
        }
    }

    @Test
    fun theLoginWindowAnswersOnTheRedirectTheAuthorizationAskedFor() {
        val custom = runtimeFor(RedditOAuth.redReaderCompatibilitySelection(CLIENT_ID, redirectUri = CUSTOM_REDIRECT))
        val shipped = runtimeFor(RedditOAuth.redReaderCompatibilitySelection(CLIENT_ID))
        val customCallback = loginCallbackUriOf(custom.configuration.authorizationUrl("s".repeat(32)).toString())
        val shippedCallback = loginCallbackUriOf(shipped.configuration.authorizationUrl("s".repeat(32)).toString())

        assertEquals(
            LoginNavigation.CALLBACK,
            loginNavigationFor("myreader://auth_done?state=x&code=y".toUri(), customCallback),
        )
        assertEquals(
            "the shipped callback is not the answer once another is configured",
            LoginNavigation.BLOCK,
            loginNavigationFor("redreader://rr_oauth_redir?state=x&code=y".toUri(), customCallback),
        )
        assertEquals(
            LoginNavigation.CALLBACK,
            loginNavigationFor("redreader://rr_oauth_redir?state=x&code=y".toUri(), shippedCallback),
        )
        assertEquals(
            LoginNavigation.CALLBACK,
            loginNavigationFor("redreader://rr_oauth_redir?code=y".toUri(), loginCallbackUriOf(null)),
        )
    }

    @Test
    fun aNewClientIdLeavesTheOldAccountsNeedingANewSignIn() = runBlocking {
        val store = EncryptedAuthSessionStore(context, cipher = PlainCipher)
        val oldConfiguration = runtimeFor(RedditOAuth.redReaderCompatibilitySelection(CLIENT_ID)).configuration
        val newConfiguration = runtimeFor(RedditOAuth.redReaderCompatibilitySelection(OTHER_CLIENT_ID)).configuration
        val accountId = authenticatedAccountId(OAuthProvider.REDDIT, oldConfiguration.clientMode, "t2me")

        suspend fun stateUnder(configuration: OAuthProviderConfiguration): AccountSessionState {
            store.clearAll()
            store.saveAccountRegistry(
                StoredAuthAccountRegistry(
                    accounts = listOf(
                        StoredAuthAccountRecord(
                            accountId = accountId,
                            provider = OAuthProvider.REDDIT,
                            providerAccountId = "t2me",
                            username = "me",
                            createdAtEpochMs = 1L,
                            lastUsedAtEpochMs = 1L,
                            clientMode = oldConfiguration.clientMode,
                        ),
                    ),
                    activeAccountId = accountId,
                ),
            )
            store.saveSession(
                accountId,
                StoredOAuthSession(
                    provider = OAuthProvider.REDDIT,
                    tokens = OAuthTokenSet(
                        "access-me",
                        "refresh-me",
                        "bearer",
                        oldConfiguration.scopes.joinToString(" "),
                        Long.MAX_VALUE,
                    ),
                    identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
                    clientMode = oldConfiguration.clientMode,
                    clientIdFingerprint = oldConfiguration.clientIdFingerprint,
                ),
            )
            val coordinator = AuthAccountSessionCoordinator(
                configuration = configuration,
                store = store,
                tokenClient = OAuthTokenEndpointClient(
                    callFactory = OkHttpClient(),
                    configuration = configuration,
                    userAgent = "subshell-test/1.0",
                ),
                identityClient = OAuthAccountIdentityClient { error("no identity in this test") },
                localFixtureUsernames = listOf("subshell"),
                storageDispatcher = Dispatchers.Unconfined,
            )
            return coordinator.hydratedSnapshot().accounts.single { it.stableId == accountId }.state
        }

        assertEquals(AccountSessionState.CONNECTED, stateUnder(oldConfiguration))
        assertEquals(AccountSessionState.REAUTHENTICATION_REQUIRED, stateUnder(newConfiguration))
        store.clearAll()
    }

    @Test
    fun diagnosticsSayOnlyWhetherTheValuesWereChanged() {
        val controller = RedditCompatibilityConfigurationController(context)
        controller.save(CLIENT_ID)
        assertEquals("default", controller.apiValuesDiagnosticToken())
        controller.save(CLIENT_ID, redirectUri = CUSTOM_REDIRECT, userAgent = CUSTOM_USER_AGENT)
        val token = controller.apiValuesDiagnosticToken()

        val report = buildDiagnosticReport(
            snapshot = DiagnosticSnapshot(events = emptyList()),
            environment = ENVIRONMENT.copy(redditApiValues = token),
        )

        assertEquals("custom", token)
        assertTrue(report.lines().contains("reddit_api_values=custom"))
        assertFalse(report.contains(CUSTOM_REDIRECT))
        assertFalse(report.contains(CUSTOM_USER_AGENT))
        assertFalse(report.contains(CLIENT_ID))
    }

    private object PlainCipher : AuthCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
        override fun decrypt(envelope: ByteArray): ByteArray = envelope
        override fun deleteKey() = Unit
    }

    private companion object {
        // Made-up identifiers in Reddit's installed-app format, not real ones.
        const val CLIENT_ID = "Issue240_Client-Id01"
        const val OTHER_CLIENT_ID = "Issue240_Client-Id02"
        const val CUSTOM_REDIRECT = "myreader://auth_done"
        const val CUSTOM_USER_AGENT = "org.example.reader/2.0"
        val ENVIRONMENT = DiagnosticReportEnvironment(
            appVersionName = "0.84-beta",
            appVersionCode = 248,
            buildType = "debug",
            debugSlot = "dev2",
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
