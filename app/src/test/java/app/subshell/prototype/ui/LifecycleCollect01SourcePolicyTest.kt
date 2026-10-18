package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LIFECYCLECOLLECT01 (owner 2026-10-18, Google currency audit): the UI
 * collects flows with collectAsStateWithLifecycle, so collection stops while
 * the app is in the background (Google's recommendation for Android). A plain
 * collectAsState() keeps collecting after the activity is stopped.
 */
class LifecycleCollect01SourcePolicyTest {
    private val root: File = requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)

    @Test
    fun noAppSourceCollectsAFlowWithoutTheLifecycle() {
        val sources = File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" }
        val offenders = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> Regex("""\.collectAsState\(""").containsMatchIn(line) }
                .map { (index, _) -> "${file.relativeTo(root)}:${index + 1}" }
        }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
