package app.subshell.prototype.auth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxM3A2R2C1InboxReadLoopPolicyTest {
    @Test
    fun failedServerMarkReadCannotRearmAutomaticOpenMutation() {
        val userDir = File(requireNotNull(System.getProperty("user.dir")))

        fun source(relative: String): String {
            val candidates = listOf(
                File(userDir, "src/main/java/$relative"),
                File(userDir, "app/src/main/java/$relative"),
            )
            return candidates.firstOrNull(File::isFile)?.readText()
                ?: error("Source not found: $relative")
        }

        val viewModel = source(
            // INBOX01: the inbox reads and read mutations live in
            // InboxCoordinator since they moved out of the ViewModel.
            "app/subshell/prototype/auth/InboxCoordinator.kt",
        )
        val vmStart = viewModel.indexOf("fun markRedditInboxRead(")
        // ISSUE106: mark-all is gone; the next function ends the block.
        val vmEnd = viewModel.indexOf("fun resolveRedditInboxTarget(", vmStart)
        assertTrue(vmStart >= 0)
        assertTrue(vmEnd > vmStart)
        val vmBlock = viewModel.substring(vmStart, vmEnd)

        assertTrue(
            viewModel.contains(
                "SUBSHELL_INBOXM3A2R2C1_LOCAL_SEEN_SERVER_FAILURE_STABILITY_V1",
            ),
        )
        assertFalse(vmBlock.contains("restoreOptimisticSeen("))
        assertTrue(
            vmBlock.windowed(
                "locallySeenIds = latest.locallySeenIds,".length,
                step = 1,
                partialWindows = false,
            ).count { it == "locallySeenIds = latest.locallySeenIds," } == 2,
        )

        val writer = source(
            "app/subshell/prototype/data/auth/reddit/RedditInboxReadMutationWriter.kt",
        )
        val writerStart = writer.indexOf("override suspend fun markRead(")
        // ISSUE106: markAllRead is gone; the next function ends the block.
        val writerEnd = writer.indexOf(
            "private fun source(",
            writerStart,
        )
        assertTrue(writerStart >= 0)
        assertTrue(writerEnd > writerStart)
        val writerBlock = writer.substring(writerStart, writerEnd)

        assertTrue(
            writer.contains(
                "SUBSHELL_INBOXM3A2R2C1_LOCAL_SEEN_SERVER_FAILURE_STABILITY_WRITER_V1",
            ),
        )
        assertTrue(writerBlock.contains("cacheStore.markLiveInboxSeen(account, item.id)"))
        assertFalse(
            writerBlock.contains("cacheStore.clearLiveInboxSeen(account, item.id)"),
        )
    }
}
