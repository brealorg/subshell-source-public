package app.subshell.prototype

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SECAUDIT25 (security audit #25): the release signing inputs were a data
 * class, whose generated toString() prints both keystore passwords. No data
 * class in the build script may hold a password.
 */
class SecAudit25SigningInputsToStringTest {
    private val root: File = requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)

    @Test
    fun noDataClassInTheBuildScriptHoldsAPassword() {
        val script = File(root, "app/build.gradle.kts").readText()
        val dataClasses = Regex("""data\s+class\s+(\w+)\s*\(([^)]*)\)""").findAll(script)
        val offenders = dataClasses
            .filter { it.groupValues[2].contains("password", ignoreCase = true) }
            .map { it.groupValues[1] }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
