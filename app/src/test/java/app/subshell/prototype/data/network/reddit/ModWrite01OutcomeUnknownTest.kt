package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.network.NetworkProtocolFailureKind
import app.subshell.prototype.data.account.ModeratorInviteFailureKind
import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.network.NetworkEndpoint
import app.subshell.prototype.data.network.SubshellHttpClient
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MODWRITE01 (#143, #142): a ban/mute/approval or a moderator invitation
 * answered with a 5xx (or 408/421/425), or with a 2xx whose body cannot be
 * read, may already be applied on Reddit. That is OUTCOME_UNKNOWN (the user
 * coordinator reads the list back; Invite stays off), never a plain failure.
 */
class ModWrite01OutcomeUnknownTest {
    private val account = "reddit:native_production:a"

    @Test
    fun userManagementWritesTreatAGatewayErrorOrUnreadableSuccessAsOutcomeUnknown() = runBlocking {
        for (answer in listOf(Answer(502, "{}"), Answer(408, "{}"), Answer(200, "<html>"))) {
            val source = userSource(answer)
            val expected = RedditModeratorUserMutationNetworkResult.Failed(
                kind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                httpStatusCode = answer.code.takeIf { it != 200 },
            )
            // Undo writes read only the status, so an unreadable 200 body is still a success there.
            // DIAG04_JSON_PATH_REACHES_THE_REPORT_V1: an unreadable body also says what it was.
            fun outcome(result: RedditModeratorUserMutationNetworkResult) =
                (result as? RedditModeratorUserMutationNetworkResult.Failed)?.copy(failureDetails = null) ?: result
            for (on in if (answer.code == 200) listOf(true) else listOf(true, false)) {
                val banned = source.setBanned(RedditModeratorUserBanRequest(account, "subshell", "bob", banned = on))
                assertEquals("ban=$on $answer", expected, outcome(banned))
                if (answer.code == 200) {
                    assertEquals(
                        NetworkProtocolFailureKind.UNEXPECTED_CONTENT_TYPE,
                        (banned as RedditModeratorUserMutationNetworkResult.Failed).failureDetails?.protocolKind,
                    )
                }
                assertEquals(
                    "mute=$on $answer",
                    expected,
                    outcome(source.setMuted(RedditModeratorUserMuteRequest(account, "subshell", "bob", muted = on))),
                )
                assertEquals(
                    "approve=$on $answer",
                    expected,
                    outcome(source.setApproved(RedditModeratorUserApprovalRequest(account, "subshell", "bob", approved = on))),
                )
            }
        }
        // A plain rejection stays a failure.
        assertEquals(
            RedditModeratorUserMutationNetworkResult.Failed(ModeratorUserManagementFailureKind.HTTP, 400),
            userSource(Answer(400, "{}"))
                .setBanned(RedditModeratorUserBanRequest(account, "subshell", "bob", banned = true)),
        )
    }

    @Test
    fun inviteTreatsAGatewayErrorOrUnreadableSuccessAsOutcomeUnknown() = runBlocking {
        val request = RedditModeratorInviteRequest(account, "subshell", "alice", allPermissions = true)
        for (answer in listOf(Answer(504, "{}"), Answer(425, "{}"), Answer(200, "<html>"))) {
            val result = inviteSource(answer).invite(request) as RedditModeratorInvitationNetworkResult.Failed
            assertEquals("$answer", ModeratorInviteFailureKind.OUTCOME_UNKNOWN, result.kind)
        }
        assertEquals(
            ModeratorInviteFailureKind.HTTP,
            (inviteSource(Answer(400, "{}")).invite(request) as RedditModeratorInvitationNetworkResult.Failed).kind,
        )
        assertEquals(
            ModeratorInviteFailureKind.AUTHORIZATION,
            (inviteSource(Answer(403, "{}")).invite(request) as RedditModeratorInvitationNetworkResult.Failed).kind,
        )
    }

    private data class Answer(val code: Int, val body: String)

    private fun userSource(answer: Answer) = RedditModeratorUserManagementNetworkDataSource(client(answer))

    private fun inviteSource(answer: Answer) = RedditModeratorInvitationNetworkDataSource(client(answer))

    private fun client(answer: Answer) = SubshellHttpClient(
        callFactory = OkHttpClient.Builder().addInterceptor { chain ->
            val html = answer.body.startsWith("<")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(answer.code).message("answer")
                .body(
                    answer.body.toResponseBody(
                        (if (html) "text/html" else "application/json").toMediaType(),
                    ),
                )
                .build()
        }.build(),
        endpoint = NetworkEndpoint.https("https://oauth.reddit.test/"),
        json = SubshellNetworkJson.providerJson,
        userAgent = "subshell-test",
    )
}
