package app.subshell.prototype.data.auth

import app.subshell.prototype.data.auth.reddit.RedditOAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth06bRedReaderCompatibilityPolicyTest {
    @Test
    fun compatibilitySelectionAcceptsUrlSafeInstalledClientIdentifiersWithoutFixedLength() {
        val fourteenCharacters = "aBcd_1234-Efgh"
        val twentyTwoCharacters = "Abcd_1234-Efgh5678_Ijk"

        assertTrue(RedditOAuth.isValidInstalledClientId(fourteenCharacters))
        assertTrue(RedditOAuth.isValidInstalledClientId(twentyTwoCharacters))
        assertFalse(RedditOAuth.isValidInstalledClientId(" short "))
        assertFalse(RedditOAuth.isValidInstalledClientId("contains a space"))
        assertFalse(RedditOAuth.isValidInstalledClientId("contains/slash"))
    }

    @Test
    fun compatibilityRuntimeUsesExactRedReaderIdentityWithoutTouchingNativeConfiguration() {
        val clientId = "auth06b_fixture_client"
        val selection = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.redReaderCompatibilitySelection(clientId),
            nativeConfigured = true,
            nativeClientId = "native_fixture_client",
            nativeRedirectUri = "https://example.test/oauth/callback",
            nativeUserAgent = "android:test:1 (subshell)",
        ) ?: error("Compatibility selection was unavailable")

        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, selection.configuration.clientMode)
        assertEquals(clientId, selection.configuration.clientId)
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            selection.configuration.redirectUri,
        )
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_USER_AGENT,
            selection.userAgent,
        )
        assertEquals("Reddit · RedReader compatibility", selection.providerLabel)
    }

    @Test
    fun nativeAndCompatibilityModesAreExplicitAndFingerprintIsNonReversibleBoundary() {
        val clientId = "auth06b_fixture_client"
        val nativeUnavailable = RedditOAuth.selectRuntimeConfiguration(
            storedSelection = RedditOAuth.nativeProductionSelection(),
            nativeConfigured = false,
            nativeClientId = "",
            nativeRedirectUri = "",
            nativeUserAgent = "android:test:1 (subshell)",
        )
        assertNull(nativeUnavailable)

        val compatibility = RedditOAuth.redReaderCompatibilitySelection(clientId)
        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, compatibility.mode)
        val fingerprint = oauthClientIdFingerprint(clientId)
        assertFalse(fingerprint.contains(clientId))
        assertNotEquals(clientId, fingerprint)
        assertEquals(43, fingerprint.length)

        assertThrows(IllegalArgumentException::class.java) {
            StoredOAuthClientSelection(
                provider = OAuthProvider.REDDIT,
                mode = OAuthClientMode.NATIVE_PRODUCTION,
                clientId = clientId,
            )
        }
    }

    @Test
    fun authorizationTransactionBindsModeRedirectAndClientFingerprint() {
        val configuration = RedditOAuth.installedApplication(
            clientId = "auth06b_fixture_client",
            redirectUri = RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            scopes = RedditOAuth.coreReadScopes,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        )

        assertEquals(OAuthClientMode.REDREADER_COMPATIBILITY, configuration.clientMode)
        assertEquals(
            oauthClientIdFingerprint("auth06b_fixture_client"),
            configuration.clientIdFingerprint,
        )
        val authorizationUrl = configuration.authorizationUrl("state-value")
        assertEquals(
            RedditOAuth.REDREADER_COMPATIBILITY_REDIRECT_URI,
            authorizationUrl.queryParameter("redirect_uri"),
        )
    }
}
