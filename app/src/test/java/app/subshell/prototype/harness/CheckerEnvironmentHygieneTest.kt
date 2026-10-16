package app.subshell.prototype.harness

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No checker may assign a standard environment variable as a plain local
 * (`HOME=.../HomeScreen.kt`). Those names are already exported, so the
 * assignment changes the environment of every child checker: tools that
 * read `~` see a Kotlin file, and the success memo, keyed on the whole
 * environment, misses for the same checker reached through different
 * parents. A one-command prefix (`LC_ALL=C sort`, `TMPDIR=... \`) is fine.
 *
 * Gradle does not track `tools/` as a test input: run with `--rerun`
 * after a checker-only change.
 */
class CheckerEnvironmentHygieneTest {
    private val repositoryRoot: File by lazy {
        val userDir = System.getProperty("user.dir")
            ?: error("user.dir is unavailable")
        val start = File(userDir).canonicalFile
        generateSequence(start) { current -> current.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "app/src/main/java").isDirectory &&
                    File(candidate, "tools").isDirectory
            }
            ?: error("Could not resolve repository root from ${start.path}")
    }

    private val reserved = listOf(
        "HOME", "USER", "LOGNAME", "SHELL", "EDITOR", "VISUAL", "PAGER",
        "LANG", "LC_ALL", "TERM", "TMPDIR", "PWD", "OLDPWD", "PATH", "IFS",
    )

    // A whole-line assignment: NAME=value with nothing after the value.
    private val plainAssignment = Regex(
        """^\s*(${reserved.joinToString("|")})=("[^"]*"|'[^']*'|[^\s"'#;&|]*)\s*(#.*)?$""",
    )

    private fun checkers(): List<File> =
        File(repositoryRoot, "tools")
            .listFiles { file -> file.name.startsWith("check-") && file.name.endsWith(".sh") }
            .orEmpty()
            .sortedBy(File::getName)

    @Test
    fun noCheckerAssignsAStandardEnvironmentVariable() {
        val offenders = checkers().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                "${file.name}:${index + 1}: ${line.trim()}".takeIf { plainAssignment.matches(line) }
            }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun theGuardCatchesTheShapesThatCausedTheMemoMisses() {
        // The listing finds the real checkers (step 3 retires many, so no count).
        assertTrue(checkers().any { it.name == "check-source-contract.sh" })
        assertTrue(plainAssignment.matches("""HOME="${'$'}ROOT/app/src/main/java/x/HomeScreen.kt""""))
        assertTrue(plainAssignment.matches("HOME='app/src/main/java/x/HomeScreen.kt'"))
        assertTrue(plainAssignment.matches("EDITOR=app/src/main/java/x/Editor.kt"))
        assertTrue(!plainAssignment.matches("""TMPDIR="${'$'}TMP/baseline-tmp" \"""))
        assertTrue(!plainAssignment.matches("""LC_ALL=C sort -u "${'$'}TMP/a" -o "${'$'}TMP/a""""))
        assertTrue(!plainAssignment.matches("HOME_SCREEN_FILE='app/src/main/java/x/HomeScreen.kt'"))
    }
}
