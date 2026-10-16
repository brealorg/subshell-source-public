package app.subshell.prototype.data.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Reddit01c1OwnerBoundCapabilityPolicyTest {
    @Test
    fun sharedAuthorizerKeepsCommentEditAndDeleteOwnersDistinct() = runBlocking<Unit> {
        val authorizer = RecordingAuthorizer()
        val edit = OwnerBoundAuthCommentWriteCapability(
            authorizer = authorizer,
            owner = AuthCapabilityAuthorizationOwner.COMMENT_EDIT,
        )
        val delete = OwnerBoundAuthCommentWriteCapability(
            authorizer = authorizer,
            owner = AuthCapabilityAuthorizationOwner.COMMENT_DELETE,
        )

        edit.beginAuthorization("reddit:account")

        assertTrue(edit.hasPendingAuthorization())
        assertFalse(delete.hasPendingAuthorization())
        assertEquals(
            AuthCapabilityAuthorizationOwner.COMMENT_EDIT,
            authorizer.pendingOwner,
        )

        delete.cancelPendingAuthorization()

        assertTrue(edit.hasPendingAuthorization())

        edit.cancelPendingAuthorization()

        assertFalse(edit.hasPendingAuthorization())
    }

    @Test
    fun deleteAuthorizationUsesItsOwnOwnerOnTheSameAuthorizer() = runBlocking<Unit> {
        val authorizer = RecordingAuthorizer()
        val delete = OwnerBoundAuthCommentWriteCapability(
            authorizer = authorizer,
            owner = AuthCapabilityAuthorizationOwner.COMMENT_DELETE,
        )

        delete.beginAuthorization("reddit:account")

        assertEquals(
            AuthCapabilityAuthorizationOwner.COMMENT_DELETE,
            authorizer.pendingOwner,
        )
        assertTrue(delete.hasPendingAuthorization())
    }

    private class RecordingAuthorizer : AuthCapabilityAuthorizer {
        var pendingOwner: AuthCapabilityAuthorizationOwner? = null
        var callbackOwner: AuthCapabilityAuthorizationOwner? = null

        override suspend fun beginAuthorization(
            accountStableId: String,
            owner: AuthCapabilityAuthorizationOwner,
        ): String {
            require(accountStableId.isNotBlank())
            pendingOwner = owner
            callbackOwner = null
            return "https://example.invalid/oauth"
        }

        override fun hasPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ): Boolean = pendingOwner == owner

        override fun hasStoredCallback(
            owner: AuthCapabilityAuthorizationOwner,
        ): Boolean = callbackOwner == owner

        override fun cancelPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ) {
            if (pendingOwner == owner) {
                pendingOwner = null
            }
            if (callbackOwner == owner) {
                callbackOwner = null
            }
        }

        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ): AuthCapabilityAuthorizationResult =
            AuthCapabilityAuthorizationResult.Failed(
                AuthCapabilityAuthorizationFailureKind.CALLBACK,
            )

        override suspend fun ensureAuthorized(
            accountStableId: String,
        ): AuthSessionResult = AuthSessionResult.ReauthenticationRequired
    }
}
