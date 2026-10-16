package app.subshell.prototype.auth

import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.network.reddit.redditApprovedUsersRequest
import app.subshell.prototype.data.network.reddit.redditBannedUsersRequest
import app.subshell.prototype.data.network.reddit.redditMutedUsersRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MODUSERS01 (#125): the ban/mute/approve status check paged the whole list,
 * at most 10 pages of 100, so in a community with more than 1 000 bans a new
 * ban never got past its preflight and an unban never confirmed. The first
 * read now asks Reddit for the one user (user=<name>).
 */
class ModUsers01UserFilterTest {
    // 2 500 banned users, none of them the target, served 100 per page.
    private val bannedList = (1..2_500).map { RedditBannedUser("user$it") }

    @Test
    fun aBanInABigCommunityPassesItsPreflightWithOneFilteredRead() = runBlocking {
        var banned = false
        var writes = 0
        val filters = mutableListOf<String?>()
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = reader { after, username ->
                filters += username
                if (username != null) {
                    // Reddit's filtered answer: the user if banned, else nothing.
                    val hit = if (banned) listOf(RedditBannedUser(username)) else emptyList()
                    AuthenticatedRedditBannedUsersResult.Loaded("subshell", hit, null)
                } else {
                    page(after)
                }
            },
            writer = writer {
                writes++
                banned = it.banned
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "current_user" },
        )
        coordinator.open("acct", "subshell")
        coordinator.load("acct")
        filters.clear()

        coordinator.mutate(RedditModeratorUserBanRequest("acct", "subshell", "target_user", banned = true))

        assertEquals(1, writes)
        assertEquals(true, coordinator.uiState.value.verifiedBanned)
        assertNull(coordinator.uiState.value.failureKind)
        // One filtered preflight read and one filtered read-back.
        assertEquals(listOf("target_user", "target_user"), filters)
    }

    @Test
    fun theListRequestsCarryTheUserFilterOnlyWhenAsked() {
        for (build in listOf(::redditBannedUsersRequest, ::redditMutedUsersRequest, ::redditApprovedUsersRequest)) {
            assertEquals("Target_User".lowercase(), build("subshell", null, "Target_User").queryParameters.toMap()["user"])
            assertNull(build("subshell", "t2_x", null).queryParameters.toMap()["user"])
        }
    }

    private fun page(after: String?): AuthenticatedRedditBannedUsersResult {
        val start = after?.removePrefix("p")?.toInt() ?: 0
        val end = minOf(start + 100, bannedList.size)
        return AuthenticatedRedditBannedUsersResult.Loaded(
            communityName = "subshell",
            users = bannedList.subList(start, end),
            nextCursor = if (end < bannedList.size) "p$end" else null,
        )
    }

    private fun reader(block: suspend (String?, String?) -> AuthenticatedRedditBannedUsersResult) =
        object : AuthenticatedRedditBannedUsersReader {
            override suspend fun load(
                accountStableId: String,
                rawCommunityName: String,
                after: String?,
                username: String?,
            ) = block(after, username)
        }

    private fun writer(block: suspend (RedditModeratorUserBanRequest) -> AuthenticatedRedditModeratorUserMutationResult) =
        object : AuthenticatedRedditModeratorUserWriter {
            override suspend fun mutate(request: RedditModeratorUserBanRequest) = block(request)
        }
}
