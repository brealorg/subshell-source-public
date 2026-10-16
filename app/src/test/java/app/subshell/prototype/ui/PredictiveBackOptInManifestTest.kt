package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Issue #27: the app opts in to predictive back for the whole application,
 * so Android 13–15 show the system back animations too (Android 16+ enables
 * them by default for targetSdk >= 36). No activity may opt back out.
 * The manifest is the only place this is decided, so it is read as text.
 */
class PredictiveBackOptInManifestTest {
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

    @Test
    fun applicationOptsInAndNoComponentOptsOut() {
        val manifest = File(repositoryRoot, "app/src/main/AndroidManifest.xml").readText()
        val application = manifest.substringAfter("<application").substringBefore(">")

        assertEquals(
            1,
            Regex("""android:enableOnBackInvokedCallback="true"""").findAll(application).count(),
        )
        assertFalse(manifest.contains("""android:enableOnBackInvokedCallback="false""""))
    }
}
