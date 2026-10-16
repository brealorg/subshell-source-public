package app.subshell.prototype.ui.components

import kotlin.math.absoluteValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE76_ACCENT_INDEX_NEVER_NEGATIVE_V1 (#76): a seed whose hashCode is
 * Int.MIN_VALUE gave a negative colour index and crashed composition.
 */
class Issue76AccentIndexTest {
    @Test
    fun aSeedHashingToIntMinValueGetsAValidColour() {
        val seed = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, seed.hashCode())
        assertTrue(accentIndex(seed, 5) in 0 until 5)
        accentFor(seed)
    }

    @Test
    fun everyOtherSeedKeepsItsColour() {
        val seeds = listOf("subshell", "AskReddit", "pics", "brealorg", "u/someone", "", "Ünïcödé", "a".repeat(40))
        for (seed in seeds) {
            assertEquals(seed, seed.hashCode().absoluteValue % 5, accentIndex(seed, 5))
        }
    }
}
