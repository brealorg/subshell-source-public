package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.ModeratorUserManagementFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorUserBanRequest
import io.github.brealorg.subshell.data.account.RedditModeratorUserMuteRequest
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapability
import io.github.brealorg.subshell.data.auth.AuthCommentWriteCapabilityResult
import io.github.brealorg.subshell.data.auth.AuthSessionResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditApprovedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditApprovedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserApprovalWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMutationResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMuteWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersResult
import io.github.brealorg.subshell.data.auth.OAuthClientMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * STEP3 (moderation02a5a1-a5a3): one ban, mute or approval at a time — a
 * second change while one runs is refused; finishing an authorization reloads
 * the three lists from Reddit and never sends a change again; an
 * authorization finished for another account changes nothing.
 */
class Step3ModeratorUserManagementCoordinatorTest {
    private val account = "acct"
    private val writes = mutableListOf<String>()
    private val reads = mutableListOf<String>()
    private var upgradedFor = account

    private val capability = object : AuthCommentWriteCapability {
        override suspend fun beginAuthorization(accountStableId: String) = "https://example.test/authorize"
        override fun hasPendingAuthorization() = false
        override fun hasStoredCallback() = true
        override fun cancelPendingAuthorization() = Unit
        override suspend fun completePendingAuthorization(): AuthCommentWriteCapabilityResult =
            AuthCommentWriteCapabilityResult.Upgraded(upgradedFor, "me")
        override suspend fun ensureAuthorized(accountStableId: String): AuthSessionResult = error("unused")
    }

    private val banned = object : AuthenticatedRedditBannedUsersReader {
        override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
            AuthenticatedRedditBannedUsersResult.Loaded("subshell", emptyList(), null).also { reads += "banned" }
    }
    private val muted = object : AuthenticatedRedditMutedUsersReader {
        override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
            AuthenticatedRedditMutedUsersResult.Loaded("subshell", emptyList(), null).also { reads += "muted" }
    }
    private val approved = object : AuthenticatedRedditApprovedUsersReader {
        override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
            AuthenticatedRedditApprovedUsersResult.Loaded("subshell", emptyList(), null).also { reads += "approved" }
    }

    private fun coordinator(
        banWrite: suspend () -> AuthenticatedRedditModeratorUserMutationResult = {
            AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired
        },
    ) = ModeratorUserManagementCoordinator(
        capability = capability,
        reader = banned,
        writer = object : AuthenticatedRedditModeratorUserWriter {
            override suspend fun mutate(request: RedditModeratorUserBanRequest): AuthenticatedRedditModeratorUserMutationResult {
                writes += "ban"
                return banWrite()
            }
        },
        mutedReader = muted,
        muteWriter = object : AuthenticatedRedditModeratorUserMuteWriter {
            override suspend fun mutate(request: RedditModeratorUserMuteRequest): AuthenticatedRedditModeratorUserMutationResult {
                writes += "mute"
                return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            }
        },
        approvedReader = approved,
        approvalWriter = object : AuthenticatedRedditModeratorUserApprovalWriter {
            override suspend fun mutate(request: io.github.brealorg.subshell.data.account.RedditModeratorUserApprovalRequest): AuthenticatedRedditModeratorUserMutationResult {
                writes += "approve"
                return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            }
        },
        clientMode = OAuthClientMode.REDREADER_COMPATIBILITY,
        activeAccountStableId = { account },
        activeAccountUsername = { "me" },
    )

    private val ban = RedditModeratorUserBanRequest(account, "subshell", "target_user", banned = true)
    private val mute = RedditModeratorUserMuteRequest(account, "subshell", "other_user", muted = true)

    @Test
    fun aSecondChangeWhileOneRunsIsRefused() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val c = coordinator(banWrite = {
            release.await()
            AuthenticatedRedditModeratorUserMutationResult.Acknowledged
        })
        c.open(account, "subshell")
        c.load(account)

        val first = async(start = CoroutineStart.UNDISPATCHED) { c.mutate(ban) }
        withTimeout(5_000) { c.mutateMuted(mute) }

        assertEquals(listOf("ban"), writes)
        assertEquals(ModeratorUserManagementFailureKind.ALREADY_MUTATING, c.uiState.value.failureKind)
        release.complete(Unit)
        first.await()
        assertFalse(c.uiState.value.mutationInProgress)
    }

    @Test
    fun finishingAnAuthorizationReloadsTheListsAndNeverResends() = runBlocking {
        val c = coordinator()
        c.open(account, "subshell")
        c.load(account)
        c.mutate(ban)
        assertEquals(listOf("ban"), writes)
        reads.clear()

        c.handleCallbackResultTokenIfOwned("result-token")

        assertEquals(listOf("ban"), writes)
        assertEquals(setOf("banned", "muted", "approved"), reads.toSet())
        assertFalse(c.uiState.value.authorizing)
        assertFalse(c.uiState.value.authorizationRequired)
    }

    @Test
    fun anAuthorizationForAnotherAccountChangesNothing() = runBlocking {
        upgradedFor = "someone_else"
        val c = coordinator()
        c.open(account, "subshell")
        c.load(account)
        reads.clear()

        c.handleCallbackResultTokenIfOwned("result-token")

        assertEquals(emptyList<String>(), reads)
        assertEquals(emptyList<String>(), writes)
        assertEquals(ModeratorUserManagementFailureKind.SESSION, c.uiState.value.failureKind)
    }
}
