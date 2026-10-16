package app.subshell.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugFocusedCommentLaunchPolicyTest {
    @Test
    fun normalizesFullAndBareRedditIdentities() {
        assertEquals(
            DebugFocusedCommentLaunch(
                postId = "t3_1upifrl",
                commentId = "t1_ow1iwm8",
                username = "brealorg",
                requestId = 7L,
            ),
            debugFocusedCommentLaunchOrNull(
                postId = "1upifrl",
                commentId = "ow1iwm8",
                username = "u/brealorg",
                requestId = 7L,
            ),
        )
    }

    @Test
    fun rejectsIncompleteLaunchTargets() {
        assertNull(
            debugFocusedCommentLaunchOrNull(
                postId = "t3_1upifrl",
                commentId = null,
                username = "brealorg",
                requestId = 8L,
            ),
        )
    }
}
