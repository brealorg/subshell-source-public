package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxM3A2R2D1NullableActionProgressPolicyTest {
    @Test
    fun absentActionTargetCannotRenderAsProgress() {
        val userDir = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(
                userDir,
                "src/main/java/app/subshell/prototype/ui/screens/ConversationScreen.kt",
            ),
            File(
                userDir,
                "app/src/main/java/app/subshell/prototype/ui/screens/ConversationScreen.kt",
            ),
        )
        val source = candidates.firstOrNull(File::isFile)?.readText()
            ?: error("ConversationScreen.kt not found")

        assertTrue(
            source.contains(
                "SUBSHELL_INBOXM3A2R2D1_NULLABLE_ACTION_PROGRESS_GUARD_V1",
            ),
        )
        assertTrue(source.contains("liveReactionEventId != null &&"))
        assertTrue(source.contains("deleteEventId != null &&"))
        assertFalse(
            source.contains(
                "reactInProgress =\n" +
                    "                            liveState.reactingEventId ==",
            ),
        )
        assertFalse(
            source.contains(
                "deleteInProgress =\n" +
                    "                            liveState.deletingEventId ==",
            ),
        )
    }
}
