package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Moderation02A5A1BanUserNetworkTest {
    @Test
    fun bannedListUsesCanonicalCommunityRelationshipEndpointAndOpaqueCursor() {
        val request = redditBannedUsersRequest("SubShell", "t2_AbC-123")
        assertEquals(listOf("r", "subshell", "about", "banned"), request.pathSegments)
        assertEquals(
            listOf("limit" to "100", "raw_json" to "1", "after" to "t2_AbC-123"),
            request.queryParameters,
        )
    }

    @Test
    fun banAndUnbanUseCanonicalSubredditFriendRelationshipForms() {
        val ban = redditModeratorBanFormRequest(
            RedditModeratorUserBanRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                banned = true,
                durationDays = 7,
                banReason = "Rule 1",
                moderatorNote = "Internal note",
                messageToUser = "Please review the rules.",
            ),
        )
        assertEquals(listOf("r", "subshell", "api", "friend"), ban.pathSegments)
        assertEquals("banned", ban.fields.toMap()["type"])
        assertEquals("target_user", ban.fields.toMap()["name"])
        assertEquals("7", ban.fields.toMap()["duration"])
        assertEquals("Rule 1", ban.fields.toMap()["ban_reason"])
        assertEquals("Internal note", ban.fields.toMap()["note"])
        assertEquals("Please review the rules.", ban.fields.toMap()["ban_message"])

        val unban = redditModeratorUnbanFormRequest(
            RedditModeratorUserBanRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                banned = false,
            ),
        )
        assertEquals(listOf("r", "subshell", "api", "unfriend"), unban.pathSegments)
        assertEquals(
            mapOf("api_type" to "json", "name" to "target_user", "type" to "banned"),
            unban.fields.toMap(),
        )
    }

    @Test
    fun bannedRelationshipProjectionIsBoundedNormalizesUsersAndKeepsSecureAvatar() {
        val listing = SubshellNetworkJson.providerJson.decodeFromString<RedditBannedUsersListingDto>(
            """
            {
              "data": {
                "after": "nextCursor",
                "children": [
                  {
                    "name":"Target_User",
                    "id":"abc123",
                    "rel_id":"rel123",
                    "note":"Internal note",
                    "date":1234,
                    "snoovatar_img":"https://i.redd.it/snoovatar/avatar.png?x=1&amp;y=2"
                  },
                  {"name":"bad user","id":"ignored"}
                ]
              }
            }
            """.trimIndent(),
        )
        val page = listing.toBannedUsersPage()
        assertEquals("nextCursor", page.nextCursor)
        assertEquals(1, page.items.size)
        assertEquals("target_user", page.items.single().username)
        assertEquals("Internal note", page.items.single().note)
        assertEquals(1234L, page.items.single().createdEpochSeconds)
        assertEquals(
            "https://i.redd.it/snoovatar/avatar.png?x=1&y=2",
            page.items.single().avatarImageUri,
        )
        assertNull(RedditBannedUsersListingDto().toBannedUsersPage().nextCursor)
    }

    @Test
    fun bannedRelationshipRejectsInsecureAvatarUri() {
        val listing = SubshellNetworkJson.providerJson.decodeFromString<RedditBannedUsersListingDto>(
            """
            {
              "data": {
                "children": [
                  {"name":"Target_User","icon_img":"http://example.test/avatar.png"}
                ]
              }
            }
            """.trimIndent(),
        )
        assertNull(listing.toBannedUsersPage().items.single().avatarImageUri)
    }
}
