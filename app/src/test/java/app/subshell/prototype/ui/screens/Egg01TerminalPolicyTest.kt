package app.subshell.prototype.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Egg01TerminalPolicyTest {
    @Test
    fun unlockGestureRequiresBottomStartAndEnoughUpwardPull() {
        assertTrue(
            easterEggPullStartsInUnlockZone(
                downY = 950f,
                viewportHeight = 1_000f,
                startZonePx = 120f,
            ),
        )
        assertFalse(
            easterEggPullStartsInUnlockZone(
                downY = 700f,
                viewportHeight = 1_000f,
                startZonePx = 120f,
            ),
        )
        assertTrue(easterEggPullIsArmed(upwardPullPx = 80f, thresholdPx = 72f))
        assertFalse(easterEggPullIsArmed(upwardPullPx = 60f, thresholdPx = 72f))
    }

    @Test
    fun helpDocumentsOnlyThePublicCommandSurface() {
        val result = executeEasterEggTerminalCommand(
            rawCommand = "help",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = listOf("help"),
        )

        val output = result.output.joinToString("\n")
        assertTrue(output.contains("help clear whoami version uptime history exit"))
        assertFalse(output.contains("subshell"))
    }

    @Test
    fun recursiveSubshellStopsAtFiveAndCollapsesOnTheNextAttempt() {
        var depth = 1
        repeat(4) {
            val result = executeEasterEggTerminalCommand(
                rawCommand = "subshell",
                shellDepth = depth,
                versionName = "test",
                uptimeSeconds = 0L,
                history = emptyList(),
            )
            assertFalse(result.collapse)
            depth = result.nextShellDepth
        }
        assertEquals(EASTER_EGG_MAX_SHELL_DEPTH, depth)

        val overflow = executeEasterEggTerminalCommand(
            rawCommand = "subshell",
            shellDepth = depth,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertTrue(overflow.collapse)
        assertEquals(listOf("ENOSPC: too many shells"), overflow.output)
        assertEquals(EASTER_EGG_MAX_SHELL_DEPTH, overflow.nextShellDepth)
    }

    @Test
    fun exitUnwindsNestedShellButDoesNotCloseTheRootShell() {
        val nestedExit = executeEasterEggTerminalCommand(
            rawCommand = "exit",
            shellDepth = 3,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(2, nestedExit.nextShellDepth)
        assertEquals(listOf("logout"), nestedExit.output)

        val rootExit = executeEasterEggTerminalCommand(
            rawCommand = "exit",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(1, rootExit.nextShellDepth)
        assertEquals(
            listOf("There is no escape. Use the back button like a normal person."),
            rootExit.output,
        )
    }

    @Test
    fun historyAndUptimeAreDeterministic() {
        val history = executeEasterEggTerminalCommand(
            rawCommand = "history",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = listOf("help", "version", "history"),
        )
        assertEquals(
            listOf("  1  help", "  2  version", "  3  history"),
            history.output,
        )
        assertEquals("01:01:01", formatEasterEggUptime(3_661L))
    }

    @Test
    fun suspiciousCommandsAreLocalJokesAndNeverRequestCollapse() {
        val sudo = executeEasterEggTerminalCommand(
            rawCommand = "sudo rm -rf /",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(
            listOf(
                "shelluser is not in the sudoers file.",
                "This incident will be reported to absolutely nobody.",
            ),
            sudo.output,
        )
        assertFalse(sudo.collapse)
        assertEquals(1, sudo.nextShellDepth)

        val rm = executeEasterEggTerminalCommand(
            rawCommand = "rm -rf /",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(listOf("rm: refusing to remove anything; reality is protected by plot armor"), rm.output)
        assertFalse(rm.collapse)
    }

    @Test
    fun manSubshellLeaksHintsWithoutExpandingPublicHelp() {
        val manual = executeEasterEggTerminalCommand(
            rawCommand = "man subshell",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        ).output.joinToString("\n")
        assertTrue(manual.contains("sudo(8)"))
        assertTrue(manual.contains("coffee(1)"))
        assertTrue(manual.contains("matrix(1)"))

        val publicHelp = executeEasterEggTerminalCommand(
            rawCommand = "help --all",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(listOf("nice try."), publicHelp.output)
    }

    @Test
    fun matrixCommandTogglesPresentationOnly() {
        val on = executeEasterEggTerminalCommand(
            rawCommand = "matrix",
            shellDepth = 2,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(true, on.matrixMode)
        assertEquals(2, on.nextShellDepth)
        assertFalse(on.collapse)

        val off = executeEasterEggTerminalCommand(
            rawCommand = "matrix   off",
            shellDepth = 2,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(false, off.matrixMode)
        assertEquals(2, off.nextShellDepth)
    }

    @Test
    fun packageManagerAndRabbitHoleCommandsAreDeterministic() {
        val pacman = executeEasterEggTerminalCommand(
            rawCommand = "pacman -Syu",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertTrue(pacman.output.joinToString("\n").contains("emotionally up to date"))

        val apt = executeEasterEggTerminalCommand(
            rawCommand = "apt update",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(listOf("E: wrong distro. Nice try."), apt.output)

        val grass = executeEasterEggTerminalCommand(
            rawCommand = "touch grass",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(listOf("ENOENT: grass not found in current namespace. Try outside."), grass.output)
    }

    @Test
    fun echoAndFortuneKeepTheFakeShellUsefulEnoughToExplore() {
        val echo = executeEasterEggTerminalCommand(
            rawCommand = "echo this is suspicious",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = emptyList(),
        )
        assertEquals(listOf("this is suspicious"), echo.output)

        val fortune = executeEasterEggTerminalCommand(
            rawCommand = "fortune",
            shellDepth = 1,
            versionName = "test",
            uptimeSeconds = 0L,
            history = listOf("one", "two"),
        )
        assertEquals(listOf("If it works on the Pixel 6, document it before touching anything."), fortune.output)
    }

}
