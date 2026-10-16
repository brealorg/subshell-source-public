package app.subshell.prototype.data.auth.reddit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1CDestinationProjectionPolicyTest {
    @Test
    fun literalAboutFieldsMapWithoutConvertingMissingBooleansToPermission() {
        val about = Json.decodeFromString<RedditCommunityAboutData>("""
            {"allow_post_crossposts":true,"subreddit_type":"restricted","user_is_banned":false,
             "user_can_submit":true,"user_is_contributor":true,"user_is_moderator":false,"over18":true}
        """.trimIndent())
        val evidence = about.toCrosspostDestination("owner", "target")
        assertEquals("owner", evidence.accountStableId)
        assertEquals("target", evidence.community)
        assertEquals(true, evidence.allowCrossposts)
        assertEquals("restricted", evidence.subredditType)
        assertEquals(false, evidence.userIsBanned)
        assertEquals(true, evidence.userCanSubmit)
        assertEquals(true, evidence.userIsContributor)
        assertEquals(false, evidence.userIsModerator)
        assertEquals(true, evidence.nsfw)
    }

    @Test
    fun missingAboutFieldsRemainUnknownNotPublicOrAllowed() {
        val evidence = Json.decodeFromString<RedditCommunityAboutData>("{}").toCrosspostDestination("owner", "target")
        assertNull(evidence.allowCrossposts)
        assertNull(evidence.subredditType)
        assertNull(evidence.userIsBanned)
        assertNull(evidence.userCanSubmit)
        assertNull(evidence.nsfw)
    }

    @Test
    fun explicitNullIsUnknownWhileExplicitFalseRemainsDenial() {
        val about = Json.decodeFromString<RedditCommunityAboutData>("""
            {"allow_post_crossposts":false,"user_is_banned":null,"user_can_submit":false}
        """.trimIndent())
        val evidence = about.toCrosspostDestination("owner", "target")
        assertEquals(false, evidence.allowCrossposts)
        assertNull(evidence.userIsBanned)
        assertEquals(false, evidence.userCanSubmit)
    }
}
