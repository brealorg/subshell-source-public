package app.subshell.prototype.data.network.reddit

import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.network.SubshellNetworkJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Moderation02A5A2MutedUserNetworkTest {
    @Test
    fun mutedListUsesCanonicalCommunityRelationshipEndpointAndOpaqueCursor() {
        val request = redditMutedUsersRequest("SubShell", "t2_AbC-123")
        assertEquals(listOf("r", "subshell", "about", "muted"), request.pathSegments)
        assertEquals(
            listOf("limit" to "100", "raw_json" to "1", "after" to "t2_AbC-123"),
            request.queryParameters,
        )
    }

    @Test
    fun muteAndUnmuteUseCanonicalSubredditFriendRelationshipForms() {
        val mute = redditModeratorMuteFormRequest(
            RedditModeratorUserMuteRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                muted = true,
                moderatorNote = "Modmail abuse",
            ),
        )
        assertEquals(listOf("r", "subshell", "api", "friend"), mute.pathSegments)
        assertEquals("muted", mute.fields.toMap()["type"])
        assertEquals("target_user", mute.fields.toMap()["name"])
        assertEquals("Modmail abuse", mute.fields.toMap()["note"])
        assertNull(mute.fields.toMap()["duration"])
        assertNull(mute.fields.toMap()["ban_reason"])
        assertNull(mute.fields.toMap()["ban_message"])

        val unmute = redditModeratorUnmuteFormRequest(
            RedditModeratorUserMuteRequest(
                accountStableId = "acct",
                communityName = "subshell",
                username = "target_user",
                muted = false,
            ),
        )
        assertEquals(listOf("r", "subshell", "api", "unfriend"), unmute.pathSegments)
        assertEquals(
            mapOf("api_type" to "json", "name" to "target_user", "type" to "muted"),
            unmute.fields.toMap(),
        )
    }

    @Test
    fun mutedRelationshipProjectionKeepsSecureAvatarAndNote() {
        val listing = SubshellNetworkJson.providerJson.decodeFromString<RedditMutedUsersListingDto>(
            """
            {
              "data": {
                "after": "nextCursor",
                "children": [
                  {
                    "name":"Target_User",
                    "note":"Modmail abuse",
                    "date":1234,
                    "snoovatar_img":"https://i.redd.it/avatar.png?x=1&amp;y=2"
                  },
                  {"name":"bad user"}
                ]
              }
            }
            """.trimIndent(),
        )
        val page = listing.toMutedUsersPage()
        assertEquals("nextCursor", page.nextCursor)
        assertEquals(1, page.items.size)
        assertEquals("target_user", page.items.single().username)
        assertEquals("Modmail abuse", page.items.single().note)
        assertEquals(1234L, page.items.single().createdEpochSeconds)
        assertEquals(
            "https://i.redd.it/avatar.png?x=1&y=2",
            page.items.single().avatarImageUri,
        )
    }
}
