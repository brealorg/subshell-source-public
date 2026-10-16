package app.subshell.prototype.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthInboxReadCapability
import app.subshell.prototype.data.auth.AuthInboxReadCapabilityResult
import app.subshell.prototype.data.auth.AuthSessionResult
import app.subshell.prototype.data.auth.OAuthClientMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (mod01a22, modmail01a1): asking for the inbox permission starts a
 * fresh sign-in pinned to the account that asked; an account that already
 * has the permission is not sent to the browser again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step3InboxAuthorizationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        // The granted path schedules the background inbox sync.
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class Capability(private val granted: Boolean) : AuthInboxReadCapability {
        val begun = mutableListOf<String>()
        override suspend fun beginAuthorization(accountStableId: String): String {
            begun += accountStableId
            return "https://example.test/authorize"
        }
        override suspend fun hasScopeGrant(accountStableId: String): Boolean = granted
        override fun hasPendingAuthorization(): Boolean = false
        override fun hasStoredCallback(): Boolean = false
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthInboxReadCapabilityResult =
            throw UnsupportedOperationException()
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }

    private fun coordinator(capability: Capability) = InboxCoordinator(
        application = ApplicationProvider.getApplicationContext<Application>(),
        redditInboxLoader = null,
        inboxReadMutationWriter = null,
        inboxReadCapability = capability,
        inboxReadClientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        accountSessionDirectory = null,
        scope = scope,
    )

    @Test
    fun theInboxSignInIsAFreshSignInForTheAccountThatAsked() = runBlocking {
        val capability = Capability(granted = false)

        val ready = coordinator(capability).beginInboxAuthorization("reddit:mod") as AuthBrowserLaunchResult.Ready

        assertTrue(ready.freshAccountSession)
        assertEquals(listOf("reddit:mod"), capability.begun)
    }

    @Test
    fun anAccountThatAlreadyHasThePermissionIsNotSentToTheBrowser() = runBlocking {
        val capability = Capability(granted = true)

        val result = coordinator(capability).beginInboxAuthorization("reddit:mod")

        assertEquals(AuthBrowserLaunchResult.NotStarted, result)
        assertEquals(emptyList<String>(), capability.begun)
    }
}
