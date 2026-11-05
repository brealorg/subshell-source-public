package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.ContentSafetyFailureKind
import io.github.brealorg.subshell.data.account.ContentSafetyPhase
import io.github.brealorg.subshell.data.account.HiddenHistoryReconciliation
import io.github.brealorg.subshell.data.account.RedditContentReportRequest
import io.github.brealorg.subshell.data.account.RedditPostVisibilityRequest
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedContentSafetyResult
import io.github.brealorg.subshell.data.auth.reddit.AuthenticatedContentSafetyWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Safety01A32ReauthContinuityPolicyTest {
    @Test
    fun sameRedditIdentityCanResumeAfterStableIdRebind() = runBlocking {
        var activeAccountId = "acct-old"
        var activeUsername = "same_user"
        var writes = 0

        val capability = FakeCapability(
            completion = AuthCommentWriteCapabilityResult.Upgraded(
                accountStableId = "acct-new",
                username = "same_user",
            ),
        )
        val writer = object : AuthenticatedContentSafetyWriter {
            override suspend fun setPostHidden(
                request: RedditPostVisibilityRequest,
            ): AuthenticatedContentSafetyResult {
                writes += 1
                return if (writes == 1) {
                    AuthenticatedContentSafetyResult.AuthorizationRequired
                } else {
                    assertEquals("acct-new", request.accountStableId)
                    AuthenticatedContentSafetyResult.Updated(
                        accountStableId = request.accountStableId,
                        thingFullname = request.postFullname,
                        hidden = request.hidden,
                        hiddenHistoryReconciliation =
                            HiddenHistoryReconciliation.REFRESHED,
                    )
                }
            }

            override suspend fun report(
                request: RedditContentReportRequest,
            ): AuthenticatedContentSafetyResult = error("Unexpected report")
        }

        val coordinator = ContentSafetyCoordinator(
            capability = capability,
            writer = writer,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { activeAccountId },
            activeAccountUsername = { activeUsername },
        )

        coordinator.setPostHidden(
            RedditPostVisibilityRequest(
                accountStableId = "acct-old",
                postFullname = "t3_post1",
                hidden = true,
            ),
        )
        assertEquals(
            ContentSafetyPhase.AUTHORIZATION_REQUIRED,
            coordinator.uiState.value.phase,
        )
        coordinator.beginAuthorization()

        activeAccountId = "acct-new"
        activeUsername = "SAME_USER"
        coordinator.handleCallbackResultTokenIfOwned("callback")

        assertEquals(ContentSafetyPhase.SUCCEEDED, coordinator.uiState.value.phase)
        assertEquals("acct-new", coordinator.uiState.value.accountStableId)
        assertEquals(true, coordinator.uiState.value.hidden)
        assertEquals(2, writes)
    }

    @Test
    fun differentRedditIdentityStillFailsClosed() = runBlocking {
        var activeAccountId = "acct-old"
        var activeUsername = "original_user"
        var writes = 0

        val capability = FakeCapability(
            completion = AuthCommentWriteCapabilityResult.Upgraded(
                accountStableId = "acct-new",
                username = "different_user",
            ),
        )
        val writer = object : AuthenticatedContentSafetyWriter {
            override suspend fun setPostHidden(
                request: RedditPostVisibilityRequest,
            ): AuthenticatedContentSafetyResult {
                writes += 1
                return AuthenticatedContentSafetyResult.AuthorizationRequired
            }

            override suspend fun report(
                request: RedditContentReportRequest,
            ): AuthenticatedContentSafetyResult = error("Unexpected report")
        }

        val coordinator = ContentSafetyCoordinator(
            capability = capability,
            writer = writer,
            clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
            activeAccountStableId = { activeAccountId },
            activeAccountUsername = { activeUsername },
        )

        coordinator.setPostHidden(
            RedditPostVisibilityRequest(
                accountStableId = "acct-old",
                postFullname = "t3_post1",
                hidden = true,
            ),
        )
        coordinator.beginAuthorization()

        activeAccountId = "acct-new"
        activeUsername = "different_user"
        coordinator.handleCallbackResultTokenIfOwned("callback")

        assertEquals(ContentSafetyPhase.FAILED, coordinator.uiState.value.phase)
        assertEquals(
            ContentSafetyFailureKind.ACCOUNT_CHANGED,
            coordinator.uiState.value.failureKind,
        )
        assertEquals(1, writes)
    }

    private class FakeCapability(
        private val completion: AuthCommentWriteCapabilityResult,
    ) : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String): String =
            "https://example.invalid/authorize"

        override fun hasPendingAuthorization(): Boolean = false

        override fun hasStoredCallback(): Boolean = true

        override fun cancelPendingAuthorization() = Unit

        override suspend fun completePendingAuthorization():
            AuthCommentWriteCapabilityResult = completion

        override suspend fun ensureAuthorized(accountStableId: String):
            AuthSessionResult = error("Not used by fake writer")
    }
}
