package io.github.brealorg.subshell.auth

import io.github.brealorg.subshell.data.account.ModeratorUserManagementFailureKind
import io.github.brealorg.subshell.data.account.RedditModeratorUserBanRequest
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditBannedUsersResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserMutationResult
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditModeratorUserWriter
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersReader
import io.github.brealorg.subshell.data.auth.AuthenticatedRedditMutedUsersResult
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.network.NetworkEndpoint
import io.github.brealorg.subshell.data.network.SubshellHttpClient
import io.github.brealorg.subshell.data.network.SubshellNetworkJson
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorUserManagementNetworkDataSource
import io.github.brealorg.subshell.data.network.reddit.RedditModeratorUserMutationNetworkResult
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LOW_NET_USER_MUTATION_KEEPS_REDDITS_ERROR_V1 (OCB-552): a ban/mute/approve
 * Reddit refuses with a json.errors entry keeps its code and message up to
 * the coordinator state; a later failure of another kind clears them.
 */
class Ocb552UserMutationErrorTest {
    @Test
    fun aRefusedBanKeepsRedditsErrorCodeAndMessage() = runBlocking {
        val source = RedditModeratorUserManagementNetworkDataSource(
            SubshellHttpClient(
                callFactory = OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("ok")
                        .body(
                            """{"json":{"errors":[["CANT_RESTRICT_MODERATOR","You can't do that to a moderator.","ban"]]}}"""
                                .toResponseBody("application/json".toMediaType()),
                        )
                        .build()
                }.build(),
                endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
                json = SubshellNetworkJson.providerJson,
                userAgent = "subshell-test",
            ),
        )

        val result = source.setBanned(
            RedditModeratorUserBanRequest("reddit:native_production:a", "subshell", "a_mod", banned = true),
        ) as RedditModeratorUserMutationNetworkResult.Failed

        assertEquals(ModeratorUserManagementFailureKind.VALIDATION, result.kind)
        assertEquals("CANT_RESTRICT_MODERATOR · field=ban", result.failureDetails?.providerCode?.value)
        assertEquals("You can't do that to a moderator.", result.failureDetails?.providerMessage?.value)
    }

    @Test
    fun theCoordinatorShowsTheRefusalsDetailsUntilAnotherFailure() = runBlocking<Unit> {
        val details = FailureDetails().withProviderDetails(code = "CANT_RESTRICT_MODERATOR", message = "No.")
        var bannedRead: () -> AuthenticatedRedditBannedUsersResult = {
            AuthenticatedRedditBannedUsersResult.Loaded("subshell", emptyList(), null)
        }
        val coordinator = ModeratorUserManagementCoordinator(
            capability = null,
            reader = object : AuthenticatedRedditBannedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    bannedRead()
            },
            writer = object : AuthenticatedRedditModeratorUserWriter {
                override suspend fun mutate(request: RedditModeratorUserBanRequest) =
                    AuthenticatedRedditModeratorUserMutationResult.Failed(
                        kind = ModeratorUserManagementFailureKind.VALIDATION,
                        details = details,
                    )
            },
            mutedReader = object : AuthenticatedRedditMutedUsersReader {
                override suspend fun load(accountStableId: String, rawCommunityName: String, after: String?, username: String?) =
                    AuthenticatedRedditMutedUsersResult.Loaded("subshell", emptyList(), null)
            },
            clientMode = null,
            activeAccountStableId = { "acct" },
            activeAccountUsername = { "me" },
        )
        coordinator.open("acct", "subshell")

        coordinator.mutate(RedditModeratorUserBanRequest("acct", "subshell", "a_mod", banned = true))
        assertEquals(ModeratorUserManagementFailureKind.VALIDATION, coordinator.uiState.value.failureKind)
        assertEquals(details, coordinator.uiState.value.failureDetails)

        bannedRead = {
            AuthenticatedRedditBannedUsersResult.Failed("subshell", ModeratorUserManagementFailureKind.TRANSPORT, retryable = true)
        }
        coordinator.load("acct", forceRefresh = true)
        assertEquals(ModeratorUserManagementFailureKind.TRANSPORT, coordinator.uiState.value.failureKind)
        assertNull(coordinator.uiState.value.failureDetails)
    }
}
