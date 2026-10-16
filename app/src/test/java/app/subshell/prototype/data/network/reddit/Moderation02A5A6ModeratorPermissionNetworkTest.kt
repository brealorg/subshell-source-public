package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorPermission
import app.subshell.prototype.data.account.RedditModeratorPermissionUpdateRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Moderation02A5A6ModeratorPermissionNetworkTest {
    @Test
    fun selectedPermissionsUseCanonicalSetPermissionsForm() {
        val form = redditModeratorPermissionFormRequest(
            RedditModeratorPermissionUpdateRequest(
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
        assertEquals(listOf("r", "subshell", "api", "setpermissions"), form.pathSegments)
        assertEquals(
            listOf(
                "api_type" to "json",
                "name" to "target_user",
                "permissions" to "+access,+mail,+posts",
                "type" to "moderator",
            ),
            form.fields,
        )
    }

    @Test
    fun allPermissionsUseExplicitAllGrant() {
        val form = redditModeratorPermissionFormRequest(
            RedditModeratorPermissionUpdateRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                allPermissions = true,
            ),
        )
        assertEquals("+all", form.fields.toMap()["permissions"])
        assertEquals("moderator", form.fields.toMap()["type"])
    }

    @Test
    fun redditJsonApiErrorTupleBecomesSanitizedPermissionFailureDetails() {
        val details = moderatorPermissionFailureDetails(
            listOf(
                JsonArray(
                    listOf(
                        JsonPrimitive("INVALID_PERMISSIONS"),
                        JsonPrimitive("invalid moderator permission"),
                        JsonPrimitive("permissions"),
                    ),
                ),
            ),
        )
        requireNotNull(details)
        assertEquals("INVALID_PERMISSIONS · field=permissions", details.providerCode?.value)
        assertEquals("invalid moderator permission", details.providerMessage?.value)
    }

    @Test
    fun redditPermissionProviderTextIsSanitizedBeforePresentation() {
        val details = moderatorPermissionFailureDetails(
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
    fun permissionUpdateCannotCarryNoPermissions() {
        RedditModeratorPermissionUpdateRequest(
            accountStableId = "acct",
            communityName = "subshell",
            username = "target_user",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun allPermissionsCannotBeMixedWithSpecificPermissionUpdate() {
        RedditModeratorPermissionUpdateRequest(
            accountStableId = "acct",
            communityName = "subshell",
            username = "target_user",
            allPermissions = true,
            permissions = setOf(RedditModeratorPermission.POSTS),
        )
    }
}
