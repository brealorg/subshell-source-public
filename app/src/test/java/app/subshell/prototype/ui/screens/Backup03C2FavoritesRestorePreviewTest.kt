package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup03C2FavoritesRestorePreviewTest {
    @Test
    fun nullTargetIsLegacyNoOp() {
        val current = mapOf("account-a" to setOf("android"))
        assertTrue(portableValuesRestoreDiff(current, null).isEmpty())
    }

    @Test
    fun identicalSuppliedRowProducesNoDiff() {
        assertTrue(
            portableValuesRestoreDiff(
                current = mapOf("account-a" to setOf("android")),
                target = mapOf("account-a" to setOf("android")),
            ).isEmpty(),
        )
    }

    @Test
    fun emptyModuleIsAuthoritativeClearForCurrentNonEmptyRows() {
        val diffs = portableValuesRestoreDiff(
            current = mapOf(
                "account-a" to setOf("android", "subshell"),
                "account-b" to emptySet(),
            ),
            target = emptyMap(),
        )

        assertEquals(1, diffs.size)
        assertEquals("account-a", diffs.single().account)
        assertEquals(0, diffs.single().added)
        assertEquals(2, diffs.single().removed)
        assertTrue(diffs.single().clearsAllCurrent)
    }

    @Test
    fun explicitEmptyUnknownRowEstablishesPortableEmptyOwnership() {
        val diff = portableValuesRestoreDiff(
            current = emptyMap(),
            target = mapOf("future-stable-id" to emptySet()),
        ).single()

        assertEquals("future-stable-id", diff.account)
        assertNull(diff.currentCount)
        assertEquals(0, diff.targetCount)
        assertTrue(diff.establishesExplicitEmpty)
        assertFalse(diff.clearsAllCurrent)
    }

    @Test
    fun addedAndRemovedCountsArePerStableAccountRow() {
        val diff = portableValuesRestoreDiff(
            current = mapOf("account-a" to setOf("android", "linux")),
            target = mapOf("account-a" to setOf("linux", "subshell")),
        ).single()

        assertEquals(1, diff.added)
        assertEquals(1, diff.removed)
        assertEquals(2, diff.currentCount)
        assertEquals(2, diff.targetCount)
    }
}
