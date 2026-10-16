package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorInviteRequest
import app.subshell.prototype.data.account.RedditModeratorPermission
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A5ModeratorInviteNetworkTest {
    @Test
    fun allPermissionsUseCanonicalModeratorInviteFriendForm() {
        val form = redditModeratorInvitationFormRequest(
            RedditModeratorInviteRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                allPermissions = true,
            ),
        )
        assertEquals(listOf("r", "subshell", "api", "friend"), form.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "name" to "target_user",
                "permissions" to "+all",
                "type" to "moderator_invite",
            ),
            form.fields,
        )
    }

    @Test
    fun selectedPermissionsUseSortedExplicitGrantCodes() {
        val form = redditModeratorInvitationFormRequest(
            RedditModeratorInviteRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                permissions = setOf(
                    RedditModeratorPermission.POSTS,
                    RedditModeratorPermission.ACCESS,
                    RedditModeratorPermission.MAIL,
                ),
            ),
        )
        assertEquals("+access,+mail,+posts", form.fields.toMap()["permissions"])
        assertEquals("moderator_invite", form.fields.toMap()["type"])
    }

    @Test
    fun redditJsonApiErrorTupleBecomesSanitizedFailureDetails() {
        val details = moderatorInviteFailureDetails(
            listOf(
                JsonArray(
                    listOf(
                        JsonPrimitive("USER_REQUIRED"),
                        JsonPrimitive("please enter a username"),
                        JsonPrimitive("name"),
                    ),
                ),
            ),
        )
        requireNotNull(details)
        assertEquals("USER_REQUIRED · field=name", details.providerCode?.value)
        assertEquals("please enter a username", details.providerMessage?.value)
    }

    @Test
    fun redditApiErrorProviderTextIsSanitizedBeforePresentation() {
        val details = moderatorInviteFailureDetails(
            listOf(
                JsonArray(
                    listOf(
                        JsonPrimitive("BAD_OPTION"),
                        JsonPrimitive("Bearer secret-token\ntry again"),
                        JsonPrimitive("permissions"),
                    ),
                ),
            ),
        )
        requireNotNull(details)
        assertTrue(details.providerMessage?.value?.contains("secret-token") == false)
        assertTrue(details.providerMessage?.value?.contains('\n') == false)
        assertEquals("BAD_OPTION · field=permissions", details.providerCode?.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invitationCannotCarryNoPermissions() {
        RedditModeratorInviteRequest(
            accountStableId = "acct",
            communityName = "subshell",
            username = "target_user",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun allPermissionsCannotBeMixedWithSpecificPermissions() {
        RedditModeratorInviteRequest(
            accountStableId = "acct",
            communityName = "subshell",
            username = "target_user",
            allPermissions = true,
            permissions = setOf(RedditModeratorPermission.POSTS),
        )
    }
}
