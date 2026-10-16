package app.subshell.prototype.data.cache

import app.subshell.prototype.model.ProfileCommentFixture
import app.subshell.prototype.model.ThreadPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Profile01a5CacheCodecPolicyTest {
    @Test
    fun extendedProfileCommentRoundTrips() {
        val comment = ProfileCommentFixture(
            id = "profile01a5-comment",
            author = "subshell_dev",
            postId = "profile01a5-post",
            age = "2d",
            body = "Profile comment cache metadata",
            score = 1,
            placement = ThreadPlacement.MIDDLE,
            community = "subshell",
            postTitle = "PROFILE01A5 cache target",
        )

        assertEquals(
            comment,
            SubshellCacheCodec.decodeProfileComment(
                SubshellCacheCodec.encodeProfileComment(comment),
            ),
        )
    }

    @Test
    fun sevenFieldProfileCommentPayloadRemainsReadable() {
        val comment = ProfileCommentFixture(
            id = "profile01a5-legacy-comment",
            author = "subshell_dev",
            postId = "profile01a5-legacy-post",
            age = "3d",
            body = "Legacy seven field payload",
            score = 2,
            placement = ThreadPlacement.BOTTOM,
        )

        val currentPayload =
            SubshellCacheCodec.encodeProfileComment(comment)

        assertTrue(
            currentPayload.endsWith("-1:-1:"),
        )

        val sevenFieldPayload =
            currentPayload.removeSuffix("-1:-1:")

        assertEquals(
            comment,
            SubshellCacheCodec.decodeProfileComment(
                sevenFieldPayload,
            ),
        )
    }
}
