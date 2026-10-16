package app.subshell.prototype.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkSettingsSearchIntegrationTest {
    @Test
    fun redditLinkHandlingIsSearchableThroughSettings02Index() {
        listOf("reddit links", "open links", "browser", "default").forEach { query ->
            val result = settingsSearchResult(query)
            assertTrue("section missing for $query", result.isSectionVisible("Media & links"))
            assertTrue("row missing for $query", result.isItemVisible("reddit-link-handling"))
        }
    }
}
