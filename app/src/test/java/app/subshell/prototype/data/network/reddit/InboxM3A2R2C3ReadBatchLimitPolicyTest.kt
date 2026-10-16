package app.subshell.prototype.data.network.reddit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxM3A2R2C3ReadBatchLimitPolicyTest {
    @Test
    fun totalSafetyBoundIsSeparateFromPerRequestRedditLimit() {
        val userDir = File(requireNotNull(System.getProperty("user.dir")))

        fun source(relative: String): String {
            val candidates = listOf(
                File(userDir, "src/main/java/$relative"),
                File(userDir, "app/src/main/java/$relative"),
            )
            return candidates.firstOrNull(File::isFile)?.readText()
                ?: error("Source not found: $relative")
        }

        val network = source(
            "app/subshell/prototype/data/network/reddit/" +
                "RedditInboxReadMutationNetworkDataSource.kt",
        )
        assertTrue(network.contains("REDDIT_INBOX_READ_MAX_IDS = 100"))
        assertTrue(network.contains("REDDIT_INBOX_READ_REQUEST_MAX_IDS = 25"))
        assertTrue(network.contains(".chunked(REDDIT_INBOX_READ_REQUEST_MAX_IDS)"))
        assertTrue(network.contains("redditReadTargetFullnameRequestBatches("))
        assertTrue(network.contains("normalizeRedditInboxReadRequestFullnames("))
        assertTrue(network.contains("exceeds the 25-item request bound"))

        val writer = source(
            "app/subshell/prototype/data/auth/reddit/" +
                "RedditInboxReadMutationWriter.kt",
        )
        assertTrue(writer.contains("redditReadTargetFullnameRequestBatches(targets)"))
        assertTrue(writer.contains("for ((batchIndex, batch) in targetBatches.withIndex())"))
        assertTrue(writer.contains("mutationSource.markRead(batch)"))
        assertTrue(writer.contains("subshell-inbox-read-batch"))
        assertFalse(writer.contains("source(session.tokens.accessToken).markRead(targets)"))
    }
}
