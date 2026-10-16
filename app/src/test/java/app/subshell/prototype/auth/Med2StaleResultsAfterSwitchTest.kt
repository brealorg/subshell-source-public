package app.subshell.prototype.auth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionDirectory
import app.subshell.prototype.data.account.AccountSessionDirectorySnapshot
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommunityMembershipAction
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsLoader
import app.subshell.prototype.data.auth.AuthenticatedRedditSubscriptionsResult
import app.subshell.prototype.data.auth.reddit.AuthenticatedCommunityMembershipResult
import app.subshell.prototype.data.identityLookupTokenProvider
import app.subshell.prototype.data.network.NetworkBearerTokenProvider
import app.subshell.prototype.model.Community
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** MED2: answers that arrive after an account or community change (OCB-403, 432, 471). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Med2StaleResultsAfterSwitchTest {
    private val accountA = "reddit:a"
    private val accountB = "reddit:b"

    @Test
    fun membershipReconciliationStopsWhenTheAccountChanges() {
        val loader = GatedLoader()
        val directory = SwitchableDirectory(accountA)
        val viewModel = AuthConnectViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            unavailableAuthApplicationRuntime().copy(
                redditSubscriptionsLoader = loader,
                accountSessionDirectory = directory,
                communityMembershipWriter = { request -> AuthenticatedCommunityMembershipResult.Updated(request) },
            ),
        )

        viewModel.updateCommunityMembership(accountA, "kotlin", CommunityMembershipAction.JOIN)
        idle()
        loader.pendingSync!!.complete(loaded("android"))
        idle()
        assertEquals(1, loader.synchronizeCalls)

        directory.active = accountB
        idleFor(30_000)

        assertEquals(1, loader.synchronizeCalls)
    }

    @Test
    fun aBanAnswerForAnotherCommunityIsNotShownInTheOpenOne() = runBlocking {
        val readBack = CompletableDeferred<Unit>()
        var reads = 0
        var banned = false
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?): AuthenticatedRedditBannedUsersResult {
                    reads++
                    if (username != null && reads == 3) readBack.await()
                    return AuthenticatedRedditBannedUsersResult.Loaded(
                        rawCommunityName,
                        if (banned && username != null) listOf(RedditBannedUser(username)) else emptyList(),
                        null,
                    )
                }
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest): AuthenticatedRedditModeratorUserMutationResult {
                    banned = request.banned
                    return AuthenticatedRedditModeratorUserMutationResult.Acknowledged
                }
            },
            clientMode = null,
            activeAccountStableId = { accountA },
            activeAccountUsername = { "me" },
        )
        coordinator.open(accountA, "first")
        coordinator.load(accountA)

        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            coordinator.mutate(RedditModeratorUserBanRequest(accountA, "first", "target_user", banned = true))
        }
        // The ban waits on its read-back; the moderator moves to another community.
        coordinator.dismiss()
        coordinator.open(accountA, "second")
        readBack.complete(Unit)
        job.join()

        val state = coordinator.uiState.value
        assertEquals("second", state.communityName)
        assertNull(state.verifiedUsername)
        assertTrue(state.bannedUsers.isEmpty())
    }

    @Test
    fun anIdentityLookup401EndsNoSession() {
        var unauthorizedCalls = 0
        val active = object : NetworkBearerTokenProvider {
            override fun bearerToken() = "token-b"
            override fun onUnauthorized() {
                unauthorizedCalls++
            }
        }

        val provider = identityLookupTokenProvider(active)
        provider.onUnauthorized()

        assertEquals("token-b", provider.bearerToken())
        assertEquals(0, unauthorizedCalls)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun loaded(vararg names: String) = AuthenticatedRedditSubscriptionsResult.Loaded(
        communities = names.map { name ->
            Community(id = "t5_$name", name = name, description = "", members = "1", accent = 0L, joined = true)
        },
        fromCache = false,
    )

    private class GatedLoader : AuthenticatedRedditSubscriptionsLoader {
        var synchronizeCalls = 0
        var pendingSync: CompletableDeferred<AuthenticatedRedditSubscriptionsResult>? = null

        override suspend fun cached(accountStableId: String) = AuthenticatedRedditSubscriptionsResult.NotCached

        override suspend fun synchronize(accountStableId: String): AuthenticatedRedditSubscriptionsResult {
            synchronizeCalls++
            val gate = CompletableDeferred<AuthenticatedRedditSubscriptionsResult>()
            pendingSync = gate
            return gate.await()
        }
    }

    private class SwitchableDirectory(var active: String) : AccountSessionDirectory {
        override fun snapshot() = AccountSessionDirectorySnapshot(
            accounts = listOf("reddit:a", "reddit:b").map { id ->
                AccountSessionSummary(
                    stableId = id,
                    username = id.removePrefix("reddit:"),
                    source = AccountSessionSource.AUTHENTICATED,
                    state = AccountSessionState.CONNECTED,
                    providerLabel = "Reddit",
                )
            },
            activeAccountId = active,
        )

        override fun selectAccount(stableId: String) = snapshot()
    }
}
