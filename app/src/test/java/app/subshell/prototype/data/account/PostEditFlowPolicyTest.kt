package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PostEditFlowPolicyTest {
    @Test
    fun postEditRequiresCanonicalT3AndAllowsEmptyBody() {
        val request = PostEditRequest(
            accountStableId = "reddit:rr:user",
            postFullname = "t3_abc123",
            postAuthor = "brealorg",
            text = "",
        )

        assertEquals("t3_abc123", request.postFullname)
        assertEquals("", request.text)

        try {
            request.copy(postFullname = "abc123")
            fail("Expected non-canonical post fullname to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun ownSelfPostCanEditButOtherPostCannot() {
        assertTrue(
            ownContentActions(
                activeUsername = "brealorg",
                author = "BRealOrg",
                contentType = OwnContentType.SELF_POST,
            ).canEdit,
        )
        assertFalse(
            ownContentActions(
                activeUsername = "brealorg",
                author = "brealorg",
                contentType = OwnContentType.OTHER_POST,
            ).canEdit,
        )
    }
}
