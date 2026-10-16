package app.subshell.prototype.data.auth

import kotlinx.coroutines.runBlocking
import app.subshell.prototype.data.account.PostManageOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Reddit01c3c2PostManageAuthorizationContextPolicyTest {
    @Test
    fun everyPostManageOperationMapsToOneStableAuthorizationContext() {
        assertEquals(
            PostManageAuthorizationContext.FLAIR,
            PostManageAuthorizationContext.fromOperation(
                PostManageOperation.FLAIR_ACCESS,
            ),
        )
        assertEquals(
            PostManageAuthorizationContext.FLAIR,
            PostManageAuthorizationContext.fromOperation(
                PostManageOperation.SET_FLAIR,
            ),
        )
        assertEquals(
            PostManageAuthorizationContext.MODERATION,
            PostManageAuthorizationContext.fromOperation(
                PostManageOperation.SET_NSFW,
            ),
        )
        assertEquals(
            PostManageAuthorizationContext.MODERATION,
            PostManageAuthorizationContext.fromOperation(
                PostManageOperation.SET_SPOILER,
            ),
        )
        assertEquals(
            PostManageAuthorizationContext.EDIT,
            PostManageAuthorizationContext.fromOperation(
                PostManageOperation.SET_SEND_REPLIES,
            ),
        )
    }

    @Test
    fun postManageContextWireKeysAreStableAndDistinct() {
        assertEquals(
            "flair",
            PostManageAuthorizationContext.FLAIR.wireKey,
        )
        assertEquals(
            "moderation",
            PostManageAuthorizationContext.MODERATION.wireKey,
        )
        assertEquals(
            "edit",
            PostManageAuthorizationContext.EDIT.wireKey,
        )
        assertEquals(
            PostManageAuthorizationContext.entries.size,
            PostManageAuthorizationContext.entries
                .map(PostManageAuthorizationContext::wireKey)
                .distinct()
                .size,
        )
    }

    @Test
    fun ownerContextBoundCapabilityPreservesLogicalOwnerAndContext() = runBlocking<Unit> {
        val authorizer = RecordingContextualAuthorizer()
        val capability = OwnerContextBoundAuthCommentWriteCapability(
            authorizer = authorizer,
            owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
            contextKey = PostManageAuthorizationContext.MODERATION.wireKey,
        )

        capability.beginAuthorization("reddit:account")

        assertEquals(
            AuthCapabilityAuthorizationOwner.POST_MANAGE,
            authorizer.pendingOwner,
        )
        assertEquals(
            "moderation",
            authorizer.pendingContext,
        )
        assertTrue(capability.hasPendingAuthorization())
        assertFalse(capability.hasStoredCallback())
    }

    private class RecordingContextualAuthorizer :
        AuthContextualCapabilityAuthorizer {
        var pendingOwner: AuthCapabilityAuthorizationOwner? = null
        var pendingContext: String? = null

        override suspend fun beginAuthorization(
            accountStableId: String,
            owner: AuthCapabilityAuthorizationOwner,
        ): String =
            beginAuthorization(accountStableId, owner, null)

        override suspend fun beginAuthorization(
            accountStableId: String,
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): String {
            pendingOwner = owner
            pendingContext = contextKey
            return "https://example.invalid/oauth"
        }

        override fun hasPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ): Boolean =
            hasPendingAuthorization(owner, null)

        override fun hasPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): Boolean =
            pendingOwner == owner &&
                pendingContext == contextKey

        override fun hasStoredCallback(
            owner: AuthCapabilityAuthorizationOwner,
        ): Boolean = false

        override fun hasStoredCallback(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): Boolean = false

        override fun cancelPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ) {
            cancelPendingAuthorization(owner, null)
        }

        override fun cancelPendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ) {
            if (
                pendingOwner == owner &&
                pendingContext == contextKey
            ) {
                pendingOwner = null
                pendingContext = null
            }
        }

        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
        ): AuthCapabilityAuthorizationResult =
            completePendingAuthorization(owner, null)

        override suspend fun completePendingAuthorization(
            owner: AuthCapabilityAuthorizationOwner,
            contextKey: String?,
        ): AuthCapabilityAuthorizationResult =
            AuthCapabilityAuthorizationResult.Failed(
                AuthCapabilityAuthorizationFailureKind.CALLBACK,
            )

        override suspend fun ensureAuthorized(
            accountStableId: String,
        ): AuthSessionResult =
            AuthSessionResult.ReauthenticationRequired
    }
}
