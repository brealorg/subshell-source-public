package app.subshell.prototype.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxM3A2R2B2DirectChatRouteExitPolicyTest {
    @Test
    fun directChatTopBackStopsLiveStateBeforeClosingRoute() {
        val userDir = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(
                userDir,
                "src/main/java/app/subshell/prototype/ui/SubshellApp.kt",
            ),
            File(
                userDir,
                "app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt",
            ),
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
            ?: error("SubshellApp.kt not found from ${userDir.absolutePath}")
        // SubshellApp plus the route hosts it extracts into under ui/app.
        val extracted = File(sourceFile.parentFile, "app")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { file -> file.name }
            .joinToString("") { file -> file.readText() }
        val source = sourceFile.readText() + extracted

        val marker =
            "SUBSHELL_INBOXM3A2R2B2_EXPLICIT_DIRECT_CHAT_ROUTE_EXIT_STOP_V1"
        val markerIndex = source.indexOf(marker)
        assertTrue(markerIndex >= 0)

        val blockEnd = source.indexOf(
            "writeEnabled = state.activeAccountIsAuthenticated",
            startIndex = markerIndex,
        )
        assertTrue(blockEnd > markerIndex)

        val block = source.substring(markerIndex, blockEnd)
        assertTrue(block.contains("ConversationDetailKind.DIRECT_CHAT"))

        val stopIndex = block.indexOf("onStopRedditDirectChatLive(")
        val closeIndex = block.indexOf("state.closeConversation()")
        assertTrue(stopIndex >= 0)
        assertTrue(closeIndex > stopIndex)

        assertFalse(block.contains("onBack = state::closeConversation"))
    }
}
