package app.subshell.prototype.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup02A3RestorePreviewTest {
    @Test
    fun legacyAndUnchangedCommunityPayloadsProduceNoRows() {
        val current = mapOf(
            "root" to setOf("android", "subshell"),
            "packetmoss" to emptySet(),
        )

        assertTrue(joinedCommunitiesRestoreDiff(current, null).isEmpty())
        assertTrue(
            joinedCommunitiesRestoreDiff(
                current,
                mapOf("root" to setOf("android", "subshell")),
            ).isEmpty(),
        )
        assertEquals(
            0,
            joinedCommunitiesRestoreChangeCount(
                current,
                mapOf("root" to setOf("android", "subshell")),
            ),
        )
    }

    @Test
    fun perAccountDiffReportsAddsRemovalsTargetCountAndClearAll() {
        val current = mapOf(
            "root" to setOf("android", "subshell"),
            "packetmoss" to setOf("linux", "kotlin"),
        )

        val diffs = joinedCommunitiesRestoreDiff(
            current = current,
            target = mapOf(
                "root" to setOf("android", "compose"),
                "packetmoss" to emptySet(),
            ),
        )

        assertEquals(listOf("packetmoss", "root"), diffs.map { it.account })

        val packetmoss = diffs.first { it.account == "packetmoss" }
        assertEquals(0, packetmoss.added)
        assertEquals(2, packetmoss.removed)
        assertEquals(2, packetmoss.currentCount)
        assertEquals(0, packetmoss.targetCount)
        assertTrue(packetmoss.clearsAllCurrent)
        assertFalse(packetmoss.establishesExplicitEmpty)

        val root = diffs.first { it.account == "root" }
        assertEquals(1, root.added)
        assertEquals(1, root.removed)
        assertEquals(2, root.currentCount)
        assertEquals(2, root.targetCount)
        assertFalse(root.clearsAllCurrent)
        assertFalse(root.establishesExplicitEmpty)
    }

    @Test
    fun missingAccountExplicitEmptyRemainsAMeaningfulRestoreChange() {
        val diffs = joinedCommunitiesRestoreDiff(
            current = mapOf("root" to setOf("subshell")),
            target = mapOf("future_user" to emptySet()),
        )

        assertEquals(1, diffs.size)
        assertEquals("future_user", diffs.single().account)
        assertEquals(null, diffs.single().currentCount)
        assertEquals(0, diffs.single().added)
        assertEquals(0, diffs.single().removed)
        assertEquals(0, diffs.single().targetCount)
        assertFalse(diffs.single().clearsAllCurrent)
        assertTrue(diffs.single().establishesExplicitEmpty)
    }

    @Test
    fun dialogUsesStructuredSettingsCommunityAndSafetyPresentation() {
        val screen = source("ui/screens/BackupRestoreScreen.kt")

        assertTrue(screen.contains("// BACKUP02A3_RICHER_RESTORE_PREVIEW_V1"))
        assertTrue(screen.contains("Settings will stay unchanged."))
        assertTrue(screen.contains("val communityDiffs = joinedCommunitiesRestoreDiff("))
        assertTrue(screen.contains("\"u/\${diff.account}: +\${diff.added} / -\${diff.removed}"))
        assertTrue(screen.contains("will end at 0 joined communities."))
        assertTrue(screen.contains("\"u/\${diff.account}: set to 0 joined communities.\""))
        assertTrue(screen.contains("Joined communities will stay unchanged."))
        assertTrue(
            screen.contains(
                "Joined communities are not included in this older backup and will be kept unchanged.",
            ),
        )
        assertTrue(
            screen.contains(
                "A safety snapshot of your current setup will be created before restore.",
            ),
        )
        assertFalse(
            screen.contains(
                "This saved setup already matches your current settings.",
            ),
        )
    }

    private fun source(relative: String): String =
        File("src/main/java/app/subshell/prototype/$relative").readText()
}
