package app.subshell.prototype.data.sync

import app.subshell.prototype.data.RepositoryLoadOperation
import app.subshell.prototype.data.RepositoryPageRequest
import app.subshell.prototype.data.RepositoryQueryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostFeedSyncPolicyTest {
    @Test
    fun initialAndRefreshRejectRemoteCursors() {
        listOf(
            RepositoryLoadOperation.INITIAL,
            RepositoryLoadOperation.REFRESH,
        ).forEach { operation ->
            val failure = validatePostFeedSyncPrecondition(
                request = RepositoryPageRequest(
                    cursor = "t3_unexpected",
                    queryKey = QUERY_KEY,
                    operation = operation,
                ),
                existingRemoteCursor = "t3_expected",
            )
            assertEquals(
                PostFeedSyncPreconditionFailureKind.CURSOR_NOT_ALLOWED,
                failure?.kind,
            )
            assertEquals("t3_unexpected", failure?.requestedRemoteCursor)
        }
    }

    @Test
    fun appendRequiresTheExactOpaqueRemoteCursor() {
        assertNull(
            validatePostFeedSyncPrecondition(
                request = RepositoryPageRequest(
                    cursor = "t3_expected",
                    queryKey = QUERY_KEY,
                    operation = RepositoryLoadOperation.APPEND,
                ),
                existingRemoteCursor = "t3_expected",
            ),
        )

        val mismatch = validatePostFeedSyncPrecondition(
            request = RepositoryPageRequest(
                cursor = "t3_stale",
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.APPEND,
            ),
            existingRemoteCursor = "t3_expected",
        )
        assertEquals(
            PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_MISMATCH,
            mismatch?.kind,
        )
        assertEquals("t3_expected", mismatch?.expectedRemoteCursor)
        assertEquals("t3_stale", mismatch?.requestedRemoteCursor)
    }

    @Test
    fun appendIsRejectedWhenTheCachedFeedIsTerminal() {
        val failure = validatePostFeedSyncPrecondition(
            request = RepositoryPageRequest(
                cursor = "t3_after_terminal",
                queryKey = QUERY_KEY,
                operation = RepositoryLoadOperation.APPEND,
            ),
            existingRemoteCursor = null,
        )
        assertEquals(
            PostFeedSyncPreconditionFailureKind.REMOTE_CURSOR_UNAVAILABLE,
            failure?.kind,
        )
        assertNull(failure?.expectedRemoteCursor)
    }

    companion object {
        private val QUERY_KEY = RepositoryQueryKey("net02|policy|home")
    }
}
