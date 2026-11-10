package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.harness.SUBSHELL_PACKAGE_DIR
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-policy checks. These do not replace rendered Compose/UI tests. */
class Moderation02A3ContentControlsPresentationPolicyTest {
    private fun source(name: String) =
        File("src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/components/$name.kt").readText()

    @Test
    fun bothModerationQueuesExposeTheSameControlEntryWithoutReplacingA2Review() {
        // ISSUE295_QUEUE_USES_THE_SHEET_V1: both queues use one action row
        // (Approve, Remove, ⋮); Reports, Lock or distinguish and Spam are in the
        // Moderate sheet its ⋮ opens.
        listOf("InboxModeratorQueue", "ModeratorToolsSheet").forEach { name ->
            val s = source(name)
            assertEquals(1, Regex("ModeratorQueueItemActions\\(item").findAll(s).count())
        }
        val row = source("ModeratorQuickActions").substringAfter("internal fun ModeratorQueueItemActions(")
        assertTrue(row.contains("Text(\"Approve\")"))
        assertTrue(row.contains("Text(\"Remove\")"))
        assertTrue(row.contains("entries.openSheet(target, null)"))
        val sheet = source("ModeratorQuickActions").substringAfter("internal fun ModeratorActionsSheet(")
        assertTrue(sheet.contains("\"Lock or distinguish…\""))
        assertTrue(sheet.contains("\"Reports…\""))
        assertTrue(sheet.contains("\"Spam\""))
    }

    @Test
    fun postsAndCommentsCanReopenControlsOutsideTheQueue() {
        val common = source("Common")
        val post = File("src/main/java/${SUBSHELL_PACKAGE_DIR}/ui/screens/PostScreen.kt").readText()
        // ISSUE295_ONE_MODERATE_ENTRY_OPENS_A_SHEET_V1: the ⋮ menus open the
        // moderator sheet, whose "Lock or distinguish…" reopens the controls.
        val sheet = source("ModeratorQuickActions")
        assertTrue(sheet.contains("MODERATION02A3_POST_CONTENT_CONTROLS_REOPEN_V1"))
        assertTrue(sheet.contains("MODERATION02A3_COMMENT_CONTENT_CONTROLS_REOPEN_V1"))
        assertTrue(sheet.contains("contentControls?.open?.invoke(target)"))
        assertTrue(common.contains("ModeratorMenuItem("))
        assertTrue(post.contains("ModeratorMenuItem("))
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
        // ISSUE295_QUEUE_USES_THE_SHEET_V1: the queue's 48 dp entry is the shared
        // action row now; the dialog keeps the theme roles.
        assertTrue(source("ModeratorQuickActions").contains("heightIn(min = 48.dp)"))
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
