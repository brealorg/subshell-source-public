package app.subshell.prototype.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-policy checks. These do not replace rendered Compose/UI tests. */
class Moderation02A3ContentControlsPresentationPolicyTest {
    private fun source(name: String) =
        File("src/main/java/app/subshell/prototype/ui/components/$name.kt").readText()

    @Test
    fun bothModerationQueuesExposeTheSameControlEntryWithoutReplacingA2Review() {
        listOf("InboxModeratorQueue", "ModeratorToolsSheet").forEach { name ->
            val s = source(name)
            assertEquals(1, Regex("ModeratorReportsButton\\(item\\)").findAll(s).count())
            assertEquals(1, Regex("ModeratorContentControlsButton\\(item\\)").findAll(s).count())
            assertEquals(1, Regex("ModeratorSpamAction\\(").findAll(s).count())
            assertTrue(s.contains("FlowRow("))
            assertTrue(s.contains("Text(\"Approve\")"))
            assertTrue(s.contains("Text(\"Remove\")"))
        }
    }

    @Test
    fun postsAndCommentsCanReopenControlsOutsideTheQueue() {
        val common = source("Common")
        val post = File("src/main/java/app/subshell/prototype/ui/screens/PostScreen.kt").readText()
        assertTrue(common.contains("MODERATION02A3_POST_CONTENT_CONTROLS_REOPEN_V1"))
        assertTrue(common.contains("ModeratorContentControlsMenuItem("))
        assertTrue(post.contains("MODERATION02A3_COMMENT_CONTENT_CONTROLS_REOPEN_V1"))
        assertTrue(post.contains("ModeratorContentControlsMenuItem("))
    }

    @Test
    fun dialogRequiresExplicitConfirmationAndShowsExactTarget() {
        val s = source("ModeratorContentControls")
        assertTrue(s.contains("AlertDialog("))
        assertTrue(s.contains("pinned.target.fullname"))
        assertTrue(s.contains("pinned.target.communityName"))
        assertTrue(s.contains("enabled = current == pinned && !busy"))
        assertTrue(s.contains("onConfirm(exact)"))
        assertEquals(1, Regex("onConfirm\\(exact\\)").findAll(s).count())
        assertTrue(s.contains("Only content authored by the active moderator can be distinguished here."))
    }

    @Test
    fun materialThemeAndAccessibleControlsAreUsedWithoutParallelPalette() {
        val s = source("ModeratorContentControls")
        assertTrue(s.contains("MaterialTheme.colorScheme"))
        assertTrue(s.contains("MaterialTheme.shapes"))
        assertTrue(s.contains("heightIn(min = 48.dp)"))
        assertTrue(s.contains("LiveRegionMode.Polite"))
        listOf("Color(0x", "Color.White", "Color.Black", "@Suppress").forEach {
            assertFalse(s.contains(it))
        }
    }

    @Test
    fun presentationDoesNotDispatchWithoutPinnedConfirmation() {
        val s = source("ModeratorContentControls")
        assertFalse(s.contains("LaunchedEffect"))
        assertFalse(s.contains("println("))
        assertFalse(s.contains("Log."))
        assertFalse(s.contains("onModerate("))
    }
}
