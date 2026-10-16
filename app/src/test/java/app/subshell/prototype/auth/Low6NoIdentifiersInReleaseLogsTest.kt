package app.subshell.prototype.auth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOW6 (OCB-401/410/422/443): the auth coordinators logged account ids,
 * usernames, communities, search queries and inbox item ids with Log.d, which
 * release builds keep. They log through authDebugLog, a no-op in release
 * builds; nothing in auth/ calls the debug/info/verbose log directly.
 */
class Low6NoIdentifiersInReleaseLogsTest {
    @Test
    fun authCoordinatorsLogOnlyThroughTheDebugBuildGate() {
        val directory = listOf(
            File(System.getProperty("user.dir"), "src/main/java/app/subshell/prototype/auth"),
            File(System.getProperty("user.dir"), "app/src/main/java/app/subshell/prototype/auth"),
        ).first(File::isDirectory)
        val direct = directory.listFiles().orEmpty()
            .filter { it.extension == "kt" && it.name != "AuthDebugLog.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.name}:${index + 1}".takeIf {
                        Regex("""(^|[^A-Za-z_.])(android\.util\.)?Log\.[dvi]\(""").containsMatchIn(line)
                    }
                }
            }

        assertEquals(emptyList<String>(), direct)
        assertTrue(File(directory, "AuthDebugLog.kt").readText().contains("if (BuildConfig.DEBUG) Log.d(tag, message)"))
    }
}
