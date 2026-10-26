package app.subshell.prototype.harness

import androidx.core.content.FileProvider

/**
 * FileProvider keeps each authority's roots in a static cache, built from the
 * first context that asked. Every Robolectric test has a new files folder, so a
 * root cached by an earlier test in the same JVM makes `getUriForFile` refuse
 * this test's files ("Failed to find configured root"). Tests that copy files
 * behind Subshell's provider clear it before and after they run.
 */
internal fun forgetFileProviderRoots() {
    val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    synchronized(cache.get(null) as Any) { (cache.get(null) as MutableMap<*, *>).clear() }
}
