package app.subshell.prototype.auth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxM3A2R2C2AutomaticReadFailureIsolationPolicyTest {
    @Test
    fun automaticReadFailureCannotMasqueradeAsInboxRefreshFailure() {
        val userDir = File(requireNotNull(System.getProperty("user.dir")))

        fun source(relative: String): String {
            val candidates = listOf(
                File(userDir, "src/main/java/$relative"),
                File(userDir, "app/src/main/java/$relative"),
            )
            return candidates.firstOrNull(File::isFile)?.readText()
                ?: error("Source not found: $relative")
        }

        val resultModel = source(
            "app/subshell/prototype/data/auth/AuthInboxReadMutation.kt",
        )
        assertTrue(resultModel.contains("import app.subshell.prototype.data.error.FailureDetails"))
        assertTrue(resultModel.contains("val details: FailureDetails? = null"))

        val writer = source(
            "app/subshell/prototype/data/auth/reddit/RedditInboxReadMutationWriter.kt",
        )
        // ISSUE106 successor: 1 (markRead); mark-all and its read_all_messages
        // branch are gone.
        assertEquals(1, writer.windowed(
            "details = redditWriteHttpFailureDetails(".length,
            step = 1,
            partialWindows = false,
        ).count { it == "details = redditWriteHttpFailureDetails(" })
        assertTrue(writer.contains("statusCode = result.statusCode"))
        assertTrue(writer.contains("metadata = result.metadata"))
        assertTrue(writer.contains("details = details,"))

        val viewModel = source(
            // INBOX01: read mutations live in InboxCoordinator now.
            "app/subshell/prototype/auth/InboxCoordinator.kt",
        )
        assertTrue(viewModel.contains(
            "SUBSHELL_INBOXM3A2R2C1_LOCAL_SEEN_SERVER_FAILURE_STABILITY_V1",
        ))
        assertTrue(viewModel.contains(
            "SUBSHELL_INBOXM3A2R2C2_AUTOMATIC_READ_FAILURE_ISOLATION_V1",
        ))

        val markStart = viewModel.indexOf("fun markRedditInboxRead(")
        // ISSUE106: mark-all is gone; the next function ends the block.
        val markEnd = viewModel.indexOf("fun resolveRedditInboxTarget(", markStart)
        assertTrue(markStart >= 0)
        assertTrue(markEnd > markStart)
        val markReadBlock = viewModel.substring(markStart, markEnd)

        assertFalse(markReadBlock.contains("failureKind ="))
        assertFalse(markReadBlock.contains("failureRetryable ="))
        assertFalse(markReadBlock.contains("failureDetails ="))
        assertEquals(2, markReadBlock.windowed(
            "locallySeenIds = latest.locallySeenIds,".length,
            step = 1,
            partialWindows = false,
        ).count { it == "locallySeenIds = latest.locallySeenIds," })
        assertTrue(markReadBlock.contains("result.details?.httpStatusCode"))
        assertTrue(markReadBlock.contains("ui=isolated"))
    }
}
