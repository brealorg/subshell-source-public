package app.subshell.prototype.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDeleteFlowPolicyTest {
    @Test
    fun postDeleteRequestRequiresCanonicalT3Fullname() {
        val request = PostDeleteRequest(
            accountStableId = "reddit:redreader:user-1",
            postFullname = "t3_abc123",
            postAuthor = "owner",
        )
        assertEquals("t3_abc123", request.postFullname)
    }

    @Test
    fun postDeleteStateMatchesRawOrCanonicalPostId() {
        val state = PostDeleteUiState(
            phase = PostDeletePhase.SENDING,
            accountStableId = "account",
            postFullname = "t3_abc123",
        )
        assertTrue(state.matches("account", "abc123"))
        assertTrue(state.matches("account", "t3_abc123"))
        assertFalse(state.matches("other", "abc123"))
    }

    @Test
    fun ownPostKindsRemainDeleteCapable() {
        assertTrue(ownContentActions("owner", "owner", OwnContentType.SELF_POST).canDelete)
        assertTrue(ownContentActions("owner", "owner", OwnContentType.OTHER_POST).canDelete)
        assertFalse(ownContentActions("owner", "someone-else", OwnContentType.OTHER_POST).canDelete)
    }
}
