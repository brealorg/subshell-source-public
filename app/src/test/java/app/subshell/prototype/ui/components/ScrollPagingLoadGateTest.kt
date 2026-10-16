package app.subshell.prototype.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollPagingLoadGateTest {
    @Test
    fun completedAppendCanContinueAtNearEndWithoutRequiringANewScroll() {
        val gate = ScrollPagingLoadGate()

        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "after-1",
            ),
        )
        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = true,
                nearEnd = false,
                token = "after-1",
            ),
        )
        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = true,
                nearEnd = true,
                token = "after-1",
            ),
        )

        assertFalse(
            gate.shouldRequest(
                enabled = false,
                scrolling = false,
                nearEnd = true,
                token = "after-1",
            ),
        )
        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "after-1",
            ),
        )

        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "after-2",
            ),
        )
        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = true,
                nearEnd = true,
                token = "after-2",
            ),
        )

        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = false,
                token = "after-3",
            ),
        )
        assertFalse(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "after-3",
            ),
        )
        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = true,
                nearEnd = true,
                token = "after-3",
            ),
        )
    }

    @Test
    fun cursorTokenAllowsContinuationWhenAppendAddsNoNewVisibleItems() {
        val gate = ScrollPagingLoadGate()

        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = true,
                nearEnd = true,
                token = "t3_cursor_a",
            ),
        )
        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "t3_cursor_b",
            ),
        )
        assertTrue(
            gate.shouldRequest(
                enabled = true,
                scrolling = false,
                nearEnd = true,
                token = "t3_cursor_c",
            ),
        )
    }
}
