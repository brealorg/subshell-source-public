package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOWUI3_UI_TRACES_LOGGED_ONLY_IN_DEBUG_BUILDS_V1 (OCB-120/242): the ui/
 * traces named posts, authors, communities, accounts and query keys with
 * Log.d, which release builds keep. They log through uiDebugLog, or directly
 * only behind a debug-build (or benchmark-probe) check a few lines above.
 */
class LowUi3NoIdentifiersInReleaseLogsTest {
    @Test
    fun uiLogsDebugTracesOnlyInDebugBuilds() {
        val directory = listOf(
            File(System.getProperty("user.dir"), "src/main/java/app/subshell/prototype/ui"),
            File(System.getProperty("user.dir"), "app/src/main/java/app/subshell/prototype/ui"),
        ).first(File::isDirectory)
        val directLog = Regex("""(^|[^A-Za-z_.])(android\.util\.)?Log\.[dvi]\(""")
        val unguarded = directory.walkTopDown()
            .filter { file -> file.extension == "kt" && file.name != "UiDebugLog.kt" }
            .flatMap { file ->
                val lines = file.readLines()
                lines.mapIndexedNotNull { index, line ->
                    val guard = lines.subList(maxOf(0, index - 4), index).joinToString("\n")
                    "${file.name}:${index + 1}".takeIf {
                        directLog.containsMatchIn(line) &&
                            "BuildConfig.DEBUG" !in guard &&
                            "FEED04B_BENCHMARK_PROBES" !in guard &&
                            "benchmarkProbeDescription" !in guard
                    }
                }
            }
            .toList()

        assertEquals(emptyList<String>(), unguarded)
        assertTrue(File(directory, "UiDebugLog.kt").readText().contains("if (BuildConfig.DEBUG) Log.d(tag, message)"))
    }
}
