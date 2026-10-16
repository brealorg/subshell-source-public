package app.subshell.prototype.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP3 (reddit01c3b): the inbox and custom-feed adapters authorize as their
 * own owner, ask the earlier capability whether the scope is already granted
 * (and, for custom feeds, for the session and for ensuring it), and hand on
 * the session an authorization upgraded to.
 */
class Step3OwnerBoundAdaptersTest {
    private val upgradedSession = StoredOAuthSession(
        provider = OAuthProvider.REDDIT,
        tokens = OAuthTokenSet("new", "refresh", "bearer", "identity read", Long.MAX_VALUE),
        identity = AuthAccountIdentity(providerAccountId = "t2me", username = "me"),
    )

    private class RecordingAuthorizer(private val session: StoredOAuthSession) : AuthCapabilityAuthorizer {
        val calls = mutableListOf<String>()
        override suspend fun beginAuthorization(accountStableId: String, owner: AuthCapabilityAuthorizationOwner): String {
            calls += "begin:${owner.wireKey}"
            return "https://example.invalid/oauth"
        }
        override fun hasPendingAuthorization(owner: AuthCapabilityAuthorizationOwner): Boolean {
            calls += "pending:${owner.wireKey}"
            return true
        }
        override fun hasStoredCallback(owner: AuthCapabilityAuthorizationOwner): Boolean {
            calls += "callback:${owner.wireKey}"
            return true
        }
        override fun cancelPendingAuthorization(owner: AuthCapabilityAuthorizationOwner) {
            calls += "cancel:${owner.wireKey}"
        }
        override suspend fun completePendingAuthorization(owner: AuthCapabilityAuthorizationOwner): AuthCapabilityAuthorizationResult {
            calls += "complete:${owner.wireKey}"
            return AuthCapabilityAuthorizationResult.Upgraded("reddit:me", "me", owner, session)
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            calls += "ensure"
            return AuthSessionResult.ReauthenticationRequired
        }
    }

    private class RecordingDelegate : AuthInboxReadScopeSource, AuthCustomFeedWriteTokenSource {
        val calls = mutableListOf<String>()
        override suspend fun hasScopeGrant(accountStableId: String): Boolean {
            calls += "grant"
            return false
        }
        override suspend fun authorizedSession(accountStableId: String): StoredOAuthSession? {
            calls += "session"
            return null
        }
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult {
            calls += "ensure"
            return AuthSessionResult.ReauthenticationRequired
        }
    }

    @Test
    fun theInboxAdapterAuthorizesAsInboxReadAndAsksTheDelegateForTheGrant() = runBlocking {
        val authorizer = RecordingAuthorizer(upgradedSession)
        val delegate = RecordingDelegate()
        val inbox = OwnerBoundAuthInboxReadCapability(delegate = delegate, authorizer = authorizer)

        inbox.beginAuthorization("reddit:me")
        assertTrue(inbox.hasPendingAuthorization())
        assertTrue(inbox.hasStoredCallback())
        val result = inbox.completePendingAuthorization()
        inbox.cancelPendingAuthorization()
        assertFalse(inbox.hasScopeGrant("reddit:me"))
        inbox.ensureAuthorized("reddit:me")

        assertEquals(
            listOf("begin:inbox_read", "pending:inbox_read", "callback:inbox_read", "complete:inbox_read", "cancel:inbox_read", "ensure"),
            authorizer.calls,
        )
        assertEquals(listOf("grant"), delegate.calls)
        assertSame(upgradedSession, (result as AuthInboxReadCapabilityResult.Upgraded).session)
    }

    @Test
    fun theCustomFeedAdapterAuthorizesAsItsOwnerAndLeavesSessionsToTheDelegate() = runBlocking {
        val authorizer = RecordingAuthorizer(upgradedSession)
        val delegate = RecordingDelegate()
        val feeds = OwnerBoundAuthCustomFeedWriteCapability(delegate = delegate, authorizer = authorizer)

        feeds.beginAuthorization("reddit:me")
        assertTrue(feeds.hasPendingAuthorization())
        assertTrue(feeds.hasStoredCallback())
        val result = feeds.completePendingAuthorization()
        feeds.cancelPendingAuthorization()
        feeds.hasScopeGrant("reddit:me")
        feeds.authorizedSession("reddit:me")
        feeds.ensureAuthorized("reddit:me")

        assertEquals(
            listOf(
                "begin:custom_feed_write",
                "pending:custom_feed_write",
                "callback:custom_feed_write",
                "complete:custom_feed_write",
                "cancel:custom_feed_write",
            ),
            authorizer.calls,
        )
        assertEquals(listOf("grant", "session", "ensure"), delegate.calls)
        assertSame(upgradedSession, (result as AuthCustomFeedWriteCapabilityResult.Upgraded).session)
    }
}
